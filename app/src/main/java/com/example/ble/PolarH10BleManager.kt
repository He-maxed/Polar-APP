package com.example.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.dsp.ClinicalDatasetGenerator
import com.example.dsp.EcgFilter
import com.example.model.EcgSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

sealed class BleConnectionState {
    object Disconnected : BleConnectionState()
    object Scanning : BleConnectionState()
    data class Connecting(val deviceName: String) : BleConnectionState()
    data class Connected(val deviceName: String, val isSimulated: Boolean = false) : BleConnectionState()
    data class Error(val message: String) : BleConnectionState()
}

class PolarH10BleManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _batteryLevel = MutableStateFlow(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val _currentHeartRate = MutableStateFlow(73)
    val currentHeartRate: StateFlow<Int> = _currentHeartRate.asStateFlow()

    private val _liveSampleFlow = MutableSharedFlow<EcgSample>(replay = 50)
    val liveSampleFlow: SharedFlow<EcgSample> = _liveSampleFlow.asSharedFlow()

    private val _availableDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val availableDevices: StateFlow<List<BluetoothDevice>> = _availableDevices.asStateFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private val liveFilter = EcgFilter.LiveFilter(130f, 0.5f)
    private var simulationJob: Job? = null

    companion object {
        private const val TAG = "PolarH10Ble"
        val HR_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT_CHAR: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL_CHAR: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        val PMD_SERVICE_UUID: UUID = UUID.fromString("fb005c80-02e7-f387-0439-50e92364177d")
        val PMD_CONTROL_CHAR: UUID = UUID.fromString("fb005c81-02e7-f387-0439-50e92364177d")
        val PMD_DATA_CHAR: UUID = UUID.fromString("fb005c82-02e7-f387-0439-50e92364177d")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            startSimulationMode("Polar H10 (Clinical Stream)")
            return
        }

        try {
            val scanner = bluetoothAdapter.bluetoothLeScanner
            if (scanner == null) {
                startSimulationMode("Polar H10 (Clinical Stream)")
                return
            }

            _connectionState.value = BleConnectionState.Scanning
            val foundList = ArrayList<BluetoothDevice>()

            val scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    result?.device?.let { device ->
                        val name = device.name ?: ""
                        if (name.contains("Polar", ignoreCase = true) || name.contains("H10", ignoreCase = true)) {
                            if (!foundList.any { it.address == device.address }) {
                                foundList.add(device)
                                _availableDevices.value = foundList.toList()
                                connectDevice(device)
                            }
                        }
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.w(TAG, "Scan failed with error $errorCode, starting Clinical Simulation Stream")
                    startSimulationMode("Polar H10 (Clinical Stream)")
                }
            }

            scanner.startScan(scanCallback)
            // Timeout scan after 6s and fallback to simulation if no hardware device found
            Handler(Looper.getMainLooper()).postDelayed({
                if (_connectionState.value is BleConnectionState.Scanning) {
                    try {
                        scanner.stopScan(scanCallback)
                    } catch (_: Exception) {}
                    if (foundList.isEmpty()) {
                        startSimulationMode("Polar H10 (Clinical Stream)")
                    }
                }
            }, 6000)

        } catch (e: Exception) {
            Log.e(TAG, "Bluetooth scan exception", e)
            startSimulationMode("Polar H10 (Clinical Stream)")
        }
    }

    @SuppressLint("MissingPermission")
    fun connectDevice(device: BluetoothDevice) {
        stopSimulation()
        _connectionState.value = BleConnectionState.Connecting(device.name ?: "Polar H10")

        bluetoothGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    _connectionState.value = BleConnectionState.Connected(device.name ?: "Polar H10", isSimulated = false)
                    gatt?.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    _connectionState.value = BleConnectionState.Disconnected
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                    setupPmdStreaming(gatt)
                }
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
                characteristic ?: return
                when (characteristic.uuid) {
                    HR_MEASUREMENT_CHAR -> parseHrData(characteristic.value)
                    BATTERY_LEVEL_CHAR -> {
                        val level = characteristic.value?.firstOrNull()?.toInt() ?: 100
                        _batteryLevel.value = level
                    }
                    PMD_DATA_CHAR -> parsePmdEcgData(characteristic.value)
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun setupPmdStreaming(gatt: BluetoothGatt) {
        val pmdService = gatt.getService(PMD_SERVICE_UUID)
        if (pmdService != null) {
            val dataChar = pmdService.getCharacteristic(PMD_DATA_CHAR)
            if (dataChar != null) {
                gatt.setCharacteristicNotification(dataChar, true)
                val descriptor = dataChar.getDescriptor(CCCD_UUID)
                descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }

            // Start streaming command to PMD Control: Type 0x00 (ECG), 130Hz
            val ctrlChar = pmdService.getCharacteristic(PMD_CONTROL_CHAR)
            if (ctrlChar != null) {
                // 0x02 = START, 0x00 = ECG, 0x00 0x01 0x82 0x00 = 130Hz, 0x01 0x01 0x0E 0x00 = 14-bit
                val startEcgCmd = byteArrayOf(0x02, 0x00, 0x00, 0x01, 0x82.toByte(), 0x00, 0x01, 0x01, 0x0E, 0x00)
                ctrlChar.value = startEcgCmd
                gatt.writeCharacteristic(ctrlChar)
            }
        }
    }

    private fun parseHrData(data: ByteArray?) {
        if (data == null || data.isEmpty()) return
        val flags = data[0].toInt()
        val is16Bit = (flags and 0x01) != 0
        val hr = if (is16Bit && data.size > 2) {
            (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
        } else if (data.size > 1) {
            data[1].toInt() and 0xFF
        } else 73
        _currentHeartRate.value = hr
    }

    private fun parsePmdEcgData(data: ByteArray?) {
        if (data == null || data.size < 10) return
        // Polar PMD frame header: byte 0 is type (0x00 = ECG), bytes 1-8 timestamp
        val now = System.currentTimeMillis()
        var offset = 10
        while (offset + 3 <= data.size) {
            // 14-bit sign-extended microvolts
            val b0 = data[offset].toInt() and 0xFF
            val b1 = data[offset + 1].toInt() and 0xFF
            val raw = (b1 shl 8) or b0
            val uV = if (raw > 0x1FFF) (raw - 0x4000).toFloat() else raw.toFloat()
            val filteredMv = liveFilter.step(uV / 1000f)

            scope.launch {
                _liveSampleFlow.emit(EcgSample(now, uV, filteredMv))
            }
            offset += 3
        }
    }

    fun startSimulationMode(deviceName: String = "Polar H10 (Clinical Stream)") {
        stopSimulation()
        _connectionState.value = BleConnectionState.Connected(deviceName, isSimulated = true)
        _batteryLevel.value = 100
        _currentHeartRate.value = 73

        simulationJob = scope.launch {
            val waveform = ClinicalDatasetGenerator.generateEcgWaveform(
                durationSeconds = 60f,
                fs = 130f,
                heartRateBpm = 73f,
                includeExtrasystoles = true
            )

            var sampleIdx = 0
            val sampleDelayMs = (1000.0 / 130.0).toLong()

            while (isActive) {
                val now = System.currentTimeMillis()
                val mv = waveform[sampleIdx]
                val uV = mv * 1000f
                val filtered = liveFilter.step(mv)

                _liveSampleFlow.emit(EcgSample(now, uV, filtered))

                sampleIdx = (sampleIdx + 1) % waveform.size
                delay(sampleDelayMs)
            }
        }
    }

    fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopSimulation()
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (_: Exception) {}
        bluetoothGatt = null
        _connectionState.value = BleConnectionState.Disconnected
    }
}
