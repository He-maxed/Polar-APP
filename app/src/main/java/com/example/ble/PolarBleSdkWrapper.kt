package com.example.ble

import android.content.Context
import android.util.Log
import com.example.dsp.EcgFilter
import com.example.model.EcgSample
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarSensorSetting
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
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

/**
 * Official Polar BLE SDK Wrapper for Polar H10 heart rate & ECG sensors.
 * Manages device discovery, connection lifecycle, MTU negotiation,
 * online ECG data streaming, and standard HR / battery telemetry.
 */
class PolarBleSdkWrapper(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val disposables = CompositeDisposable()
    private var ecgDisposable: Disposable? = null
    private var scanDisposable: Disposable? = null

    private val liveFilter = EcgFilter.LiveFilter(fs = 130f, hpCutoffHz = 0.5f, lpCutoffHz = 40f)

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _batteryLevel = MutableStateFlow(0)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val _currentHeartRate = MutableStateFlow(0)
    val currentHeartRate: StateFlow<Int> = _currentHeartRate.asStateFlow()

    private val _liveSampleFlow = MutableSharedFlow<EcgSample>(replay = 100, extraBufferCapacity = 5000)
    val liveSampleFlow: SharedFlow<EcgSample> = _liveSampleFlow.asSharedFlow()

    private val _discoveredDevices = MutableStateFlow<List<PolarDeviceInfo>>(emptyList())
    val discoveredDevices: StateFlow<List<PolarDeviceInfo>> = _discoveredDevices.asStateFlow()

    private var connectedDeviceId: String? = null
    private var connectedDeviceName: String = "Polar H10"

    val api: PolarBleApi by lazy {
        PolarBleApiDefaultImpl.defaultImplementation(
            context.applicationContext,
            setOf(
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
                PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO
            )
        )
    }

    init {
        api.setPolarFilter(false)
        api.setApiLogger { message ->
            Log.d("PolarSdkLogger", message)
        }

        api.setApiCallback(object : PolarBleApiCallback() {
            override fun blePowerStateChanged(powered: Boolean) {
                Log.d(TAG, "Bluetooth power state changed: powered=$powered")
                if (!powered) {
                    _connectionState.value = BleConnectionState.Error("Bluetooth is disabled. Please turn on Bluetooth.")
                }
            }

            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                val name = polarDeviceInfo.name.ifEmpty { "Polar H10 (${polarDeviceInfo.deviceId})" }
                Log.i(TAG, "Connecting to Polar device: $name")
                connectedDeviceName = name
                connectedDeviceId = polarDeviceInfo.deviceId
                _connectionState.value = BleConnectionState.Connecting(name)
            }

            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                val name = polarDeviceInfo.name.ifEmpty { "Polar H10" }
                Log.i(TAG, "Connected to Polar device: $name [${polarDeviceInfo.deviceId}]")
                connectedDeviceName = name
                connectedDeviceId = polarDeviceInfo.deviceId
                _connectionState.value = BleConnectionState.Connected(name, isStreaming = false)
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.i(TAG, "Disconnected from Polar device: ${polarDeviceInfo.deviceId}")
                stopEcgStreaming()
                connectedDeviceId = null
                _connectionState.value = BleConnectionState.Disconnected
                _currentHeartRate.value = 0
                _batteryLevel.value = 0
                liveFilter.reset()
            }

            override fun streamingFeaturesReady(
                identifier: String,
                features: Set<PolarBleApi.PolarDeviceDataType>
            ) {
                Log.i(TAG, "Streaming features ready for $identifier: $features")
                if (features.contains(PolarBleApi.PolarDeviceDataType.ECG)) {
                    startEcgStreaming(identifier)
                }
            }

            override fun hrFeatureReady(identifier: String) {
                Log.i(TAG, "HR feature ready for $identifier")
            }

            override fun hrNotificationReceived(
                identifier: String,
                data: PolarHrData.PolarHrSample
            ) {
                _currentHeartRate.value = data.hr
            }

            override fun batteryLevelReceived(identifier: String, level: Int) {
                Log.i(TAG, "Battery level for $identifier: $level%")
                _batteryLevel.value = level
            }
        })
    }

    /**
     * Discovers nearby Polar devices using the official Polar BLE API.
     */
    fun startDiscovery() {
        scanDisposable?.dispose()
        _connectionState.value = BleConnectionState.Scanning
        val found = ArrayList<PolarDeviceInfo>()

        scanDisposable = api.searchForDevice()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { deviceInfo ->
                    if (!found.any { it.deviceId == deviceInfo.deviceId }) {
                        found.add(deviceInfo)
                        _discoveredDevices.value = ArrayList(found)
                        // Auto-connect to first Polar device found if scanning
                        if (deviceInfo.name.contains("Polar", ignoreCase = true) ||
                            deviceInfo.name.contains("H10", ignoreCase = true)
                        ) {
                            connect(deviceInfo.deviceId, deviceInfo.name)
                            stopDiscovery()
                        }
                    }
                },
                { error ->
                    Log.e(TAG, "Polar BLE scan failed: ${error.localizedMessage}", error)
                    _connectionState.value = BleConnectionState.Error("Scan failed: ${error.localizedMessage}")
                }
            )
    }

    fun stopDiscovery() {
        scanDisposable?.dispose()
        scanDisposable = null
        if (_connectionState.value is BleConnectionState.Scanning) {
            _connectionState.value = BleConnectionState.Disconnected
        }
    }

    fun connect(deviceId: String, deviceName: String = "Polar H10") {
        try {
            stopDiscovery()
            connectedDeviceId = deviceId
            connectedDeviceName = deviceName
            _connectionState.value = BleConnectionState.Connecting(deviceName)
            api.connectToDevice(deviceId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to Polar device $deviceId", e)
            _connectionState.value = BleConnectionState.Error("Failed to connect: ${e.localizedMessage}")
        }
    }

    fun disconnect() {
        val deviceId = connectedDeviceId
        stopEcgStreaming()
        if (deviceId != null) {
            try {
                api.disconnectFromDevice(deviceId)
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting device $deviceId", e)
            }
        }
        connectedDeviceId = null
        _connectionState.value = BleConnectionState.Disconnected
        _currentHeartRate.value = 0
        _batteryLevel.value = 0
        liveFilter.reset()
    }

    /**
     * Requests Polar H10 ECG stream settings and starts 130 Hz microvolt stream.
     */
    fun startEcgStreaming(deviceId: String) {
        ecgDisposable?.dispose()

        val disposable = api.requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.ECG)
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.computation())
            .flatMapPublisher { sensorSetting ->
                Log.i(TAG, "Starting ECG stream with settings: ${sensorSetting.settings}")
                api.startEcgStreaming(deviceId, sensorSetting)
            }
            .subscribe(
                { polarEcgData ->
                    handlePolarEcgData(polarEcgData)
                },
                { error ->
                    Log.e(TAG, "Polar ECG streaming error: ${error.localizedMessage}", error)
                    // Fallback to start with default ECG settings if requestStreamSettings failed
                    startEcgWithDefaultSettings(deviceId)
                }
            )

        ecgDisposable = disposable
        disposables.add(disposable)
    }

    private fun startEcgWithDefaultSettings(deviceId: String) {
        try {
            // Polar H10 default ECG settings: 130 Hz, 14-bit resolution
            val settingsMap = mapOf(
                PolarSensorSetting.SettingType.SAMPLE_RATE to 130,
                PolarSensorSetting.SettingType.RESOLUTION to 14,
                PolarSensorSetting.SettingType.RANGE to 0,
                PolarSensorSetting.SettingType.CHANNELS to 1
            )
            val defaultSetting = PolarSensorSetting(settingsMap)

            val disposable = api.startEcgStreaming(deviceId, defaultSetting)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .subscribe(
                    { polarEcgData ->
                        handlePolarEcgData(polarEcgData)
                    },
                    { error ->
                        Log.e(TAG, "Fallback ECG streaming failed: ${error.localizedMessage}", error)
                    }
                )
            ecgDisposable = disposable
            disposables.add(disposable)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ECG stream with default settings", e)
        }
    }

    private fun handlePolarEcgData(polarEcgData: com.polar.sdk.api.model.PolarEcgData) {
        val samples = polarEcgData.samples
        if (samples.isEmpty()) return

        val now = System.currentTimeMillis()
        val sampleIntervalMs = 1000.0 / 130.0
        val count = samples.size
        val batch = ArrayList<EcgSample>(count)

        for (i in 0 until count) {
            val s = samples[i]
            val uV = s.voltage.toFloat()
            val filteredMv = liveFilter.step(uV / 1000f)
            val sampleTimeMs = now - ((count - 1 - i) * sampleIntervalMs).toLong()
            batch.add(EcgSample(sampleTimeMs, uV, filteredMv))
        }

        val cur = _connectionState.value
        if (cur is BleConnectionState.Connected && !cur.isStreaming) {
            _connectionState.value = cur.copy(isStreaming = true)
        }

        for (sample in batch) {
            _liveSampleFlow.tryEmit(sample)
        }
    }

    fun stopEcgStreaming() {
        ecgDisposable?.dispose()
        ecgDisposable = null
        val cur = _connectionState.value
        if (cur is BleConnectionState.Connected && cur.isStreaming) {
            _connectionState.value = cur.copy(isStreaming = false)
        }
    }

    fun cleanup() {
        stopDiscovery()
        stopEcgStreaming()
        disposables.clear()
        try {
            api.cleanup()
            api.shutDown()
        } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "PolarBleSdkWrapper"
    }
}
