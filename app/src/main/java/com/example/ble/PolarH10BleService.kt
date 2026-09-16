package com.example.ble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HolterTelemetry(
    val isStreaming: Boolean = false,
    val isRecording: Boolean = false,
    val heartRateBpm: Int = 0,
    val elapsedSeconds: Long = 0L
) {
    val requiresWakeLock: Boolean
        get() = isStreaming || isRecording
}

/**
 * Always-on Holter guardian: keeps the app alive through deep sleep with an
 * ongoing foreground notification and a PARTIAL_WAKE_LOCK while the sensor is
 * streaming or a recording is active. BLE ownership lives in MainViewModel;
 * the service only reflects telemetry pushed from there.
 */
class PolarH10BleService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var isForeground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        currentService = this
        createNotificationChannel()
        scope.launch {
            telemetry.collect { t ->
                updateWakeLock(t.requiresWakeLock)
                if (isForeground) {
                    notificationManager.notify(NOTIFICATION_ID, buildNotification(t))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_FOREGROUND) {
            stopForegroundInternal()
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        return START_STICKY
    }

    private fun startInForeground() {
        val notification = buildNotification(telemetry.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isForeground = true
    }

    private fun stopForegroundInternal() {
        isForeground = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun renderNotification() {
        if (isForeground) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(telemetry.value))
        }
    }

    private fun updateWakeLock(needed: Boolean) {
        if (needed && wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PolarEcg:H10HolterWakeLock").apply {
                setReferenceCounted(false)
                acquire()
            }
        } else if (!needed && wakeLock != null) {
            wakeLock?.release()
            wakeLock = null
        }
    }

    private val notificationManager: NotificationManager
        get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Polar ECG Holter Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the Polar H10 Holter recording through deep sleep"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(t: HolterTelemetry): Notification {
        val contentText = when {
            t.isRecording && t.isStreaming -> "Recording in Deep Sleep \u2022 ${t.heartRateBpm} bpm"
            t.isStreaming -> "Streaming live ECG \u2022 ${t.heartRateBpm} bpm"
            t.isRecording -> "Recording in Deep Sleep \u2022 No sensor"
            else -> "Ready \u2022 Monitoring standby"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Polar H10 Holter Active")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        scope.cancel()
        updateWakeLock(false)
        if (currentService === this) {
            currentService = null
        }
    }

    companion object {
        const val ACTION_STOP_FOREGROUND = "com.example.ble.ACTION_STOP_FOREGROUND"
        private const val CHANNEL_ID = "polar_ecg_holter_service_channel"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        private var currentService: PolarH10BleService? = null

        private val _telemetry = MutableStateFlow(HolterTelemetry())
        val telemetry: StateFlow<HolterTelemetry> = _telemetry.asStateFlow()

        fun updateTelemetry(update: HolterTelemetry) {
            _telemetry.value = update
            currentService?.renderNotification()
        }
    }
}