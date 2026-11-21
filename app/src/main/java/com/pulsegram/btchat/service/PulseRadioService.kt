package com.pulsegram.btchat.service

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
import androidx.core.app.NotificationCompat
import com.pulsegram.btchat.MainActivity
import com.pulsegram.btchat.bluetooth.PulseRadioEngine
import com.pulsegram.btchat.model.BtPhyMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground Service that keeps the PulseLink Bluetooth Extended Range engine alive reliably
 * when the phone screen is off, dynamically reflects the active radio/battery mode in its
 * notification, and cleanly shuts down when the user selects OFF.
 */
class PulseRadioService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var configObserverJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val engine = PulseRadioEngine.getInstance(this)

        if (intent?.action == ACTION_STOP) {
            engine.updateConfig { it.copy(phyMode = BtPhyMode.OFF) }
            engine.stopEngine()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val cfg = engine.config.value
        if (cfg.phyMode == BtPhyMode.OFF) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification(
            modeBadge = cfg.phyMode.shortBadge,
            presetTitle = cfg.powerPreset.title
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            // In case foreground permission is still being granted
        }

        engine.startEngine()
        observeConfigForNotificationUpdates(engine)
        return START_STICKY
    }

    private fun observeConfigForNotificationUpdates(engine: PulseRadioEngine) {
        configObserverJob?.cancel()
        configObserverJob = serviceScope.launch {
            engine.config.collectLatest { cfg ->
                if (cfg.phyMode == BtPhyMode.OFF) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    nm?.notify(
                        NOTIFICATION_ID,
                        buildNotification(
                            modeBadge = cfg.phyMode.shortBadge,
                            presetTitle = cfg.powerPreset.title
                        )
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PulseLink Радіо Імпульс",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Підтримує енергоощадний зв'язок Bluetooth Extended Range"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(modeBadge: String, presetTitle: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, PulseRadioService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("PulseLink активний • $modeBadge")
            .setContentText(presetTitle)
            .setContentIntent(pendingOpen)
            .addAction(android.R.drawable.ic_lock_power_off, "Вимкнути (OFF)", pendingStop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "pulsegram_radio_channel"
        private const val NOTIFICATION_ID = 1042
        const val ACTION_START = "com.pulsegram.btchat.action.START"
        const val ACTION_STOP = "com.pulsegram.btchat.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, PulseRadioService::class.java).apply {
                action = ACTION_START
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PulseRadioService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching {
                context.startService(intent)
            }
        }
    }
}
