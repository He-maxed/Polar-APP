package com.example.ble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.model.EcgSample
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Android Service managing the Polar H10 BLE connection lifecycle,
 * background streaming, and state synchronization across app navigation.
 */
class PolarH10BleService : Service() {

    private val binder = LocalBinder()
    private lateinit var sdkWrapper: PolarBleSdkWrapper
    private lateinit var nativeBleManager: PolarH10BleManager

    val connectionState: StateFlow<BleConnectionState>
        get() = sdkWrapper.connectionState

    val batteryLevel: StateFlow<Int>
        get() = sdkWrapper.batteryLevel

    val currentHeartRate: StateFlow<Int>
        get() = sdkWrapper.currentHeartRate

    val liveSampleFlow: SharedFlow<EcgSample>
        get() = sdkWrapper.liveSampleFlow

    inner class LocalBinder : Binder() {
        fun getService(): PolarH10BleService = this@PolarH10BleService
    }

    override fun onCreate() {
        super.onCreate()
        sdkWrapper = PolarBleSdkWrapper(this)
        nativeBleManager = PolarH10BleManager(this)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FOREGROUND -> startRecordingForeground()
            ACTION_STOP_FOREGROUND -> stopRecordingForeground()
        }
        return START_STICKY
    }

    fun startDeviceDiscovery() {
        sdkWrapper.startDiscovery()
    }

    fun stopDeviceDiscovery() {
        sdkWrapper.stopDiscovery()
    }

    fun connect(deviceId: String) {
        sdkWrapper.connect(deviceId)
    }

    fun disconnect() {
        sdkWrapper.disconnect()
        nativeBleManager.disconnect()
    }

    fun startRecordingForeground() {
        val notification = buildNotification("Recording ECG Stream", "Polar H10 active")
        startForeground(NOTIFICATION_ID, notification)
    }

    fun stopRecordingForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Polar ECG Monitoring Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors real-time Polar H10 telemetry and ECG recordings"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        sdkWrapper.cleanup()
        nativeBleManager.disconnect()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_FOREGROUND = "com.example.ble.ACTION_START_FOREGROUND"
        const val ACTION_STOP_FOREGROUND = "com.example.ble.ACTION_STOP_FOREGROUND"
        private const val CHANNEL_ID = "polar_ecg_service_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
