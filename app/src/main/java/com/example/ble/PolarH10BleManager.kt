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
import com.example.dsp.EcgFilter
import com.example.model.EcgSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed class BleConnectionState {
    object Disconnected : BleConnectionState()
    object Scanning : BleConnectionState()
    data class Connecting(val deviceName: String) : BleConnectionState()
    data class Connected(val deviceName: String) : BleConnectionState()
    data class Error(val message: String) : BleConnectionState()
}

class PolarH10BleManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _batteryLevel = MutableStateFlow(0)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val _currentHeartRate = MutableStateFlow(0)
    val currentHeartRate: StateFlow<Int> = _currentHeartRate.asStateFlow()

    private val _liveSampleFlow = MutableSharedFlow<EcgSample>(replay = 50)
    val liveSampleFlow: SharedFlow<EcgSample> = _liveSampleFlow.asSharedFlow()

    private val _availableDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val availableDevices: StateFlow<List<BluetoothDevice>> = _availableDevices.asStateFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private val liveFilter = EcgFilter.LiveFilter(130f, 0.5f)

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
            _connectionState.value = BleConnectionState.Error("Bluetooth is disabled. Please turn on Bluetooth to connect Polar H10.")
            return
        }

        try {
            val scanner = bluetoothAdapter.bluetoothLeScanner
            if (scanner == null) {
                _connectionState.value = BleConnectionState.Error("BLE scanner unavailable on this device.")
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
                                _availableDevices.value = ArrayList(foundList)
                                // Auto-connect to first Polar device found
                                scanner.stopScan(this)
                                connectDevice(device)
                            }
                        }
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    _connectionState.value = BleConnectionState.Error("BLE scan failed with code $errorCode")
                }
            }

            scanner.startScan(scanCallback)

            // Stop scanning after 15 seconds if nothing found
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    scanner.stopScan(scanCallback)
                    if (_connectionState.value is BleConnectionState.Scanning) {
                        _connectionState.value = BleConnectionState.Error("No Polar H10 device found. Ensure sensor is on your chest and moisten electrodes.")
                    }
                } catch (_: Exception) {}
            }, 15000L)

        } catch (e: Exception) {
            _connectionState.value = BleConnectionState.Error("BLE scan exception: ${e.localizedMessage}")
        }
    }

    @SuppressLint("MissingPermission")
    fun connectDevice(device: BluetoothDevice) {
        _connectionState.value = BleConnectionState.Connecting(device.name ?: "Polar H10")
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT server.")
                _connectionState.value = BleConnectionState.Connected(gatt?.device?.name ?: "Polar H10")
                gatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server.")
                _connectionState.value = BleConnectionState.Disconnected
                bluetoothGatt = null
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                // 1. Enable notifications for Heart Rate Service
                val hrService = gatt.getService(HR_SERVICE_UUID)
                val hrChar = hrService?.getCharacteristic(HR_MEASUREMENT_CHAR)
                if (hrChar != null) {
                    gatt.setCharacteristicNotification(hrChar, true)
                    val descriptor = hrChar.getDescriptor(CCCD_UUID)
                    descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }

                // 2. Read Battery Service
                val batService = gatt.getService(BATTERY_SERVICE_UUID)
                val batChar = batService?.getCharacteristic(BATTERY_LEVEL_CHAR)
                if (batChar != null) {
                    gatt.readCharacteristic(batChar)
                }

                // 3. Start Polar PMD ECG Streaming (130Hz, 14-bit, 1 channel)
                enablePolarPmdStream(gatt)
            }
        }

        @Suppress("DEPRECATION")
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            if (characteristic == null) return
            when (characteristic.uuid) {
                HR_MEASUREMENT_CHAR -> parseHeartRateData(characteristic.value)
                PMD_DATA_CHAR -> parsePmdEcgData(characteristic.value)
            }
        }

        @Suppress("DEPRECATION")
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
                if (characteristic.uuid == BATTERY_LEVEL_CHAR) {
                    val battery = characteristic.value?.firstOrNull()?.toInt() ?: 100
                    _batteryLevel.value = battery
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun enablePolarPmdStream(gatt: BluetoothGatt) {
        val pmdService = gatt.getService(PMD_SERVICE_UUID) ?: return
        val pmdControlChar = pmdService.getCharacteristic(PMD_CONTROL_CHAR) ?: return
        val pmdDataChar = pmdService.getCharacteristic(PMD_DATA_CHAR) ?: return

        // Enable PMD data notifications
        gatt.setCharacteristicNotification(pmdDataChar, true)
        val descriptor = pmdDataChar.getDescriptor(CCCD_UUID)
        if (descriptor != null) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }

        // Send start ECG streaming command:
        // OpCode 0x02 (start), Measurement type 0x00 (ECG), Array size 0x01,
        // Setting 0x00 (sample rate), array size 0x01, Value 0x0082 (130 Hz in little endian),
        // Setting 0x01 (resolution), array size 0x01, Value 0x000E (14 bit)
        val startEcgCmd = byteArrayOf(
            0x02, 0x00, 0x00, 0x01, 0x82.toByte(), 0x00, 0x01, 0x01, 0x0E, 0x00
        )
        pmdControlChar.value = startEcgCmd
        gatt.writeCharacteristic(pmdControlChar)
    }

    private fun parseHeartRateData(data: ByteArray?) {
        if (data == null || data.isEmpty()) return
        val flags = data[0].toInt()
        val is16Bit = (flags and 0x01) != 0
        val hr = if (is16Bit && data.size > 2) {
            (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
        } else if (data.size > 1) {
            data[1].toInt() and 0xFF
        } else 0
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

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (_: Exception) {}
        bluetoothGatt = null
        _connectionState.value = BleConnectionState.Disconnected
        _currentHeartRate.value = 0
        _batteryLevel.value = 0
    }
}
