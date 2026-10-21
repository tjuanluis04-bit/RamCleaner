package com.example.ramcleaner

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Antes, si Shizuku se desconectaba justo cuando tocabas el tile, la
 * llamada se quedaba esperando una respuesta que nunca llegaba y el tile
 * se quedaba congelado en "Cerrando..." (STATE_UNAVAILABLE) sin forma de
 * recuperarse salvo reiniciando la app. Ahora ShizukuHelper SIEMPRE
 * responde (éxito, error, o timeout) en máximo unos segundos, así que el
 * tile se puede restaurar a su estado normal pase lo que pase.
 */
class RamCleanerTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        resetTile()
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return

        if (!ShizukuHelper.isShizukuAlive()) {
            tile.state = Tile.STATE_INACTIVE
            tile.subtitle = "Shizuku no está activo"
            tile.updateTile()
            return
        }

        tile.state = Tile.STATE_UNAVAILABLE
        tile.subtitle = "Cerrando apps..."
        tile.updateTile()

        ShizukuHelper.runForceStop(this) { count, error ->
            val currentTile = qsTile
            if (currentTile != null) {
                if (error != null) {
                    currentTile.state = Tile.STATE_INACTIVE
                    currentTile.subtitle = "Error: $error"
                } else {
                    currentTile.state = Tile.STATE_ACTIVE
                    currentTile.subtitle = "$count apps cerradas"
                }
                currentTile.updateTile()
            }
            // Pase lo que pase, el tile vuelve a su estado normal poco después,
            // para que nunca quede "pegado" mostrando el resultado anterior.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                resetTile()
            }, 2500L)
        }
    }

    private fun resetTile() {
        val tile = qsTile ?: return
        tile.state = if (ShizukuHelper.isShizukuAlive()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = if (ShizukuHelper.isShizukuAlive()) "Toca para limpiar" else "Shizuku no está activo"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_rocket)
        tile.updateTile()
    }
}
