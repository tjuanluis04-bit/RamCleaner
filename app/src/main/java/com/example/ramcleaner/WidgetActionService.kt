package com.example.ramcleaner

import android.app.Service
import android.content.Intent
import android.os.IBinder

class WidgetActionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        PrefsHelper.setWidgetStatus(this, "Cerrando apps...")
        AppWidgetRamCleaner.updateAllWidgets(this)

        if (!ShizukuHelper.isShizukuAlive()) {
            PrefsHelper.setWidgetStatus(this, "Shizuku no está activo")
            AppWidgetRamCleaner.updateAllWidgets(this)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        ShizukuHelper.runForceStop(this) { count, error ->
            val status = if (error != null) "✗ $error" else "✓ $count apps cerradas"
            PrefsHelper.setWidgetStatus(this, status)
            AppWidgetRamCleaner.updateAllWidgets(this)

            // Vuelve al mensaje normal poco después para invitar al próximo toque.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                PrefsHelper.setWidgetStatus(this, "Toca para limpiar")
                AppWidgetRamCleaner.updateAllWidgets(this)
                stopSelf(startId)
            }, 3000L)
        }
        return START_NOT_STICKY
    }
}
