package com.example.ramcleaner

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class RamMonitorService : Service() {

    companion object {
        private const val CHANNEL_ID = "ram_monitor_channel"
        private const val NOTIFICATION_ID = 1001
        private const val CHECK_INTERVAL_MS = 30_000L

        fun start(context: Context) {
            val intent = Intent(context, RamMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RamMonitorService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var cleaning = false

    private val checkRunnable = object : Runnable {
        override fun run() {
            checkAndCleanIfNeeded()
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
        startForeground(NOTIFICATION_ID, buildNotification("Vigilando el uso de RAM..."))
        handler.post(checkRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(checkRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun checkAndCleanIfNeeded() {
        if (!PrefsHelper.isAutoEnabled(this)) {
            stopSelf()
            return
        }
        if (cleaning) return

        val percent = readUsedRamPercent()
        val threshold = PrefsHelper.getThreshold(this)

        updateNotification("RAM: $percent% (umbral: $threshold%)")

        if (percent >= threshold) {
            cleaning = true
            ShizukuHelper.runForceStop(this) { count, error ->
                cleaning = false
                val msg = if (error != null) "Error al limpiar: $error" else "Auto-limpieza: $count apps cerradas"
                updateNotification(msg)
            }
        }
    }

    private fun readUsedRamPercent(): Int {
        return try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            val used = info.totalMem - info.availMem
            ((used.toDouble() / info.totalMem.toDouble()) * 100.0).toInt().coerceIn(0, 100)
        } catch (e: Exception) {
            0
        }
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Monitor de RAM",
                    NotificationManager.IMPORTANCE_MIN
                )
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("RamCleaner")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_rocket)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }
}
