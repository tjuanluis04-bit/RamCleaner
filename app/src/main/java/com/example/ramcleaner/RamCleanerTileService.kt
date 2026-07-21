package com.example.ramcleaner

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

class RamCleanerTileService : TileService() {

    override fun onClick() {
        super.onClick()

        if (!ShizukuHelper.hasPermission()) {
            Toast.makeText(this, "Abre la app y da el permiso de Shizuku primero", Toast.LENGTH_LONG).show()
            val intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val pi = PendingIntent.getActivity(
                    this, 0, intent, PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pi)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }

        qsTile.state = Tile.STATE_UNAVAILABLE
        qsTile.subtitle = "Cerrando apps…"
        qsTile.updateTile()

        ShizukuHelper.runForceStop(this) { count, error ->
            qsTile.state = Tile.STATE_ACTIVE
            qsTile.subtitle = if (error != null) "Error" else "$count apps cerradas"
            qsTile.updateTile()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile.state = Tile.STATE_ACTIVE
        qsTile.subtitle = "Toca para limpiar"
        qsTile.updateTile()
    }
}
