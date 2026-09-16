package com.example.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
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
import java.util.ArrayDeque
import java.util.UUID

sealed class BleConnectionState {
    object Disconnected : BleConnectionState()
    object Scanning : BleConnectionState()
    data class Connecting(val deviceName: String) : BleConnectionState()
    data class Connected(val deviceName: String, val isStreaming: Boolean = false) : BleConnectionState()
    data class Error(val message: String) : BleConnectionState()
}

/**
 * Robust Bluetooth Low Energy Manager for Polar H10 heart rate & ECG sensor.
 * Implements the official Polar Measurement Data (PMD) protocol:
 * - PMD Service: FB005C80-02E7-F387-1CAD-8ACD2D8DF0C8
 * - PMD Control Point: FB005C81-02E7-F387-1CAD-8ACD2D8DF0C8
 * - PMD Data: FB005C82-02E7-F387-1CAD-8ACD2D8DF0C8
 * - Standard GATT HR Service & Battery Service
 * - Enforces MTU negotiation (512 bytes) and serialized GATT command queuing
 * - Accurately unpacks 24-bit signed little-endian microvolt ECG samples at 130 Hz.
 */
class PolarH10BleManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _batteryLevel = MutableStateFlow(0)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val _currentHeartRate = MutableStateFlow(0)
    val currentHeartRate: StateFlow<Int> = _currentHeartRate.asStateFlow()

    private val _liveSampleFlow = MutableSharedFlow<EcgSample>(replay = 100)
    val liveSampleFlow: SharedFlow<EcgSample> = _liveSampleFlow.asSharedFlow()

    private val _availableDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val availableDevices: StateFlow<List<BluetoothDevice>> = _availableDevices.asStateFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private var connectedDeviceName: String = "Polar H10"
    private val liveFilter = EcgFilter.LiveFilter(fs = 130f, hpCutoffHz = 0.5f, lpCutoffHz = 40f)

    // Serialized GATT operation queue to prevent Android GATT dropping parallel requests
    private val gattQueue = ArrayDeque<() -> Unit>()
    private var isGattBusy = false

    companion object {
        private const val TAG = "PolarH10Ble"

        // Standard Bluetooth SIG UUIDs
        val HR_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT_CHAR: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL_CHAR: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // Official Polar PMD Service UUIDs
        val PMD_SERVICE_UUID: UUID = UUID.fromString("fb005c80-02e7-f387-1cad-8acd2d8df0c8")
        val PMD_CONTROL_CHAR: UUID = UUID.fromString("fb005c81-02e7-f387-1cad-8acd2d8df0c8")
        val PMD_DATA_CHAR: UUID = UUID.fromString("fb005c82-02e7-f387-1cad-8acd2d8df0c8")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var currentScanCallback: ScanCallback? = null

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
                                currentScanCallback = null
                                connectDevice(device)
                            }
                        }
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    _connectionState.value = BleConnectionState.Error("BLE scan failed with code $errorCode")
                }
            }

            currentScanCallback = scanCallback
            scanner.startScan(scanCallback)

            // Auto-stop scanning after 15 seconds if nothing found
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
        val name = device.name ?: "Polar H10"
        connectedDeviceName = name
        _connectionState.value = BleConnectionState.Connecting(name)
        clearGattQueue()
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @Synchronized
    private fun clearGattQueue() {
        gattQueue.clear()
        isGattBusy = false
    }

    @Synchronized
    private fun enqueueGattOp(op: () -> Unit) {
        gattQueue.add(op)
        if (!isGattBusy) {
            processNextGattOp()
        }
    }

    @Synchronized
    private fun processNextGattOp() {
        val next = gattQueue.poll()
        if (next != null) {
            isGattBusy = true
            try {
                next()
            } catch (e: Exception) {
                Log.e(TAG, "Error executing GATT operation", e)
                onGattOpCompleted()
            }
        } else {
            isGattBusy = false
        }
    }

    private fun onGattOpCompleted() {
        synchronized(this) {
            isGattBusy = false
            processNextGattOp()
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && gatt != null) {
                Log.d(TAG, "Connected to Polar GATT server. Requesting MTU = 512...")
                _connectionState.value = BleConnectionState.Connected(connectedDeviceName, isStreaming = false)
                // PMD ECG packets are large (~229 bytes). MTU negotiation is required.
                val mtuRequested = gatt.requestMtu(512)
                if (!mtuRequested) {
                    Log.w(TAG, "requestMtu returned false, proceeding directly to service discovery")
                    gatt.discoverServices()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from Polar GATT server.")
                clearGattQueue()
                bluetoothGatt = null
                _connectionState.value = BleConnectionState.Disconnected
                _currentHeartRate.value = 0
                _batteryLevel.value = 0
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            Log.d(TAG, "BLE MTU negotiation completed: mtu=$mtu, status=$status. Discovering services...")
            gatt?.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || gatt == null) {
                Log.e(TAG, "Service discovery failed with status $status")
                return
            }
            Log.d(TAG, "Services discovered. Initializing Polar PMD & Standard services...")
            setupPolarGattPipeline(gatt)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            Log.d(TAG, "Descriptor write finished: ${descriptor?.uuid} with status $status")
            onGattOpCompleted()
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            Log.d(TAG, "Characteristic write finished: ${characteristic?.uuid} with status $status")
            onGattOpCompleted()
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
                val value = characteristic.value ?: ByteArray(0)
                handleCharacteristicRead(characteristic.uuid, value)
            }
            onGattOpCompleted()
        }

        // For Android 13+ (API 33+)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleCharacteristicChanged(characteristic.uuid, value)
        }

        // For Android < 13
        @Suppress("DEPRECATION")
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            if (characteristic != null) {
                val value = characteristic.value ?: return
                handleCharacteristicChanged(characteristic.uuid, value)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupPolarGattPipeline(gatt: BluetoothGatt) {
        val pmdService = gatt.getService(PMD_SERVICE_UUID)
        val pmdControlChar = pmdService?.getCharacteristic(PMD_CONTROL_CHAR)
        val pmdDataChar = pmdService?.getCharacteristic(PMD_DATA_CHAR)

        val hrService = gatt.getService(HR_SERVICE_UUID)
        val hrChar = hrService?.getCharacteristic(HR_MEASUREMENT_CHAR)

        val batService = gatt.getService(BATTERY_SERVICE_UUID)
        val batChar = batService?.getCharacteristic(BATTERY_LEVEL_CHAR)

        // 1. Enable Indications/Notifications on PMD Control Point (for start/stop ACK responses)
        if (pmdControlChar != null) {
            enqueueGattOp {
                gatt.setCharacteristicNotification(pmdControlChar, true)
                val descriptor = pmdControlChar.getDescriptor(CCCD_UUID)
                if (descriptor != null) {
                    writeDescriptorVal(gatt, descriptor, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                } else {
                    onGattOpCompleted()
                }
            }
        }

        // 2. Enable Notifications on PMD Data Characteristic
        if (pmdDataChar != null) {
            enqueueGattOp {
                gatt.setCharacteristicNotification(pmdDataChar, true)
                val descriptor = pmdDataChar.getDescriptor(CCCD_UUID)
                if (descriptor != null) {
                    writeDescriptorVal(gatt, descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    onGattOpCompleted()
                }
            }
        }

        // 3. Write Start ECG Measurement Command to PMD Control Point
        // Opcode: 0x02 (START_MEASUREMENT)
        // Type: 0x00 (ECG)
        // Setting 0: 0x00 (Sample Rate), count=1, 130 Hz (0x82, 0x00)
        // Setting 1: 0x01 (Resolution), count=1, 14-bit (0x0E, 0x00)
        if (pmdControlChar != null) {
            enqueueGattOp {
                val startEcgCmd = byteArrayOf(
                    0x02, 0x00, 0x00, 0x01, 0x82.toByte(), 0x00, 0x01, 0x01, 0x0E, 0x00
                )
                writeCharacteristicVal(gatt, pmdControlChar, startEcgCmd)
            }
        }

        // 4. Enable Notifications on Standard Heart Rate Service
        if (hrChar != null) {
            enqueueGattOp {
                gatt.setCharacteristicNotification(hrChar, true)
                val descriptor = hrChar.getDescriptor(CCCD_UUID)
                if (descriptor != null) {
                    writeDescriptorVal(gatt, descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    onGattOpCompleted()
                }
            }
        }

        // 5. Read Battery Level
        if (batChar != null) {
            enqueueGattOp {
                gatt.readCharacteristic(batChar)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeDescriptorVal(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, value: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeCharacteristicVal(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, value: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(char, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            char.value = value
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(char)
        }
    }

    private fun handleCharacteristicRead(uuid: UUID, value: ByteArray) {
        if (uuid == BATTERY_LEVEL_CHAR && value.isNotEmpty()) {
            _batteryLevel.value = value[0].toInt() and 0xFF
        }
    }

    private fun handleCharacteristicChanged(uuid: UUID, value: ByteArray) {
        when (uuid) {
            PMD_DATA_CHAR -> parsePmdEcgData(value)
            HR_MEASUREMENT_CHAR -> parseHeartRateData(value)
            PMD_CONTROL_CHAR -> parsePmdControlPointResponse(value)
        }
    }

    private fun parsePmdControlPointResponse(data: ByteArray) {
        // PMD Control Point Response format:
        // Byte 0: 0xF0 (Response Code)
        // Byte 1: Opcode of request (0x02 = START_MEASUREMENT)
        // Byte 2: Status (0x00 = SUCCESS)
        if (data.size >= 3 && (data[0].toInt() and 0xFF) == 0xF0) {
            val reqOpcode = data[1].toInt() and 0xFF
            val status = data[2].toInt() and 0xFF
            if (reqOpcode == 0x02) {
                if (status == 0) {
                    Log.i(TAG, "Polar H10 PMD ECG stream confirmed active by sensor.")
                    val cur = _connectionState.value
                    if (cur is BleConnectionState.Connected) {
                        _connectionState.value = cur.copy(isStreaming = true)
                    }
                } else {
                    Log.e(TAG, "Polar H10 rejected ECG start command with error code: $status")
                }
            }
        }
    }

    private fun parseHeartRateData(data: ByteArray) {
        if (data.isEmpty()) return
        val flags = data[0].toInt()
        val is16Bit = (flags and 0x01) != 0
        val hr = if (is16Bit && data.size > 2) {
            (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
        } else if (data.size > 1) {
            data[1].toInt() and 0xFF
        } else 0
        _currentHeartRate.value = hr
    }

    /**
     * Parses official Polar PMD ECG packets:
     * - Byte 0: Measurement type (0x00 = ECG)
     * - Bytes 1-8: 64-bit uint timestamp in nanoseconds (little endian)
     * - Byte 9: Frame type (0x00 = uncompressed raw frame)
     * - Bytes 10+: Array of 3-byte signed 24-bit little-endian samples (microvolts µV)
     */
    private fun parsePmdEcgData(data: ByteArray) {
        if (data.size < 10) return
        if (data[0] != 0x00.toByte()) return // Ensure ECG data

        val sampleCount = (data.size - 10) / 3
        if (sampleCount <= 0) return

        val now = System.currentTimeMillis()
        val sampleIntervalMs = 1000.0 / 130.0 // 7.6923 ms per sample at 130 Hz
        val batch = ArrayList<EcgSample>(sampleCount)

        for (i in 0 until sampleCount) {
            val offset = 10 + i * 3
            val b0 = data[offset].toInt() and 0xFF
            val b1 = data[offset + 1].toInt() and 0xFF
            val b2 = data[offset + 2].toInt() // Sign-extended 8-bit to 32-bit int
            val rawMicrovolts = (b2 shl 16) or (b1 shl 8) or b0

            val uV = rawMicrovolts.toFloat()
            // Convert µV to mV (1000 µV = 1.0 mV) and pass through clinical filter
            val filteredMv = liveFilter.step(uV / 1000f)

            // The packet timestamp represents the time of the LAST sample in the packet
            val sampleTimeMs = now - ((sampleCount - 1 - i) * sampleIntervalMs).toLong()
            batch.add(EcgSample(sampleTimeMs, uV, filteredMv))
        }

        // Mark streaming state
        val curState = _connectionState.value
        if (curState is BleConnectionState.Connected && !curState.isStreaming) {
            _connectionState.value = curState.copy(isStreaming = true)
        }

        // Emit batch to live subscribers
        scope.launch {
            for (sample in batch) {
                _liveSampleFlow.emit(sample)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        clearGattQueue()
        try {
            val gatt = bluetoothGatt
            if (gatt != null) {
                // Send stop ECG measurement command
                val pmdService = gatt.getService(PMD_SERVICE_UUID)
                val pmdControlChar = pmdService?.getCharacteristic(PMD_CONTROL_CHAR)
                if (pmdControlChar != null) {
                    val stopCmd = byteArrayOf(0x03, 0x00)
                    writeCharacteristicVal(gatt, pmdControlChar, stopCmd)
                }
                gatt.disconnect()
                gatt.close()
            }
        } catch (_: Exception) {}
        bluetoothGatt = null
        _connectionState.value = BleConnectionState.Disconnected
        _currentHeartRate.value = 0
        _batteryLevel.value = 0
        liveFilter.reset()
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        try {
            val scanner = bluetoothAdapter?.bluetoothLeScanner
            val cb = currentScanCallback
            if (cb != null) {
                scanner?.stopScan(cb)
            }
        } catch (_: Exception) {}
        currentScanCallback = null
        if (_connectionState.value is BleConnectionState.Scanning) {
            _connectionState.value = BleConnectionState.Disconnected
        }
    }

    fun cleanup() {
        stopScan()
        disconnect()
    }
}
