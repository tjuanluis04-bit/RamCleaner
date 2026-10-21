package com.example.ramcleaner

import android.content.Context

object PrefsHelper {

    private const val PREFS_NAME = "ram_cleaner_prefs"
    private const val KEY_WHITELIST = "whitelist"
    private const val KEY_AUTO_ENABLED = "auto_enabled"
    private const val KEY_THRESHOLD = "threshold"
    private const val KEY_WIDGET_STATUS = "widget_status"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getWhitelist(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_WHITELIST, emptySet()) ?: emptySet()
    }

    fun setWhitelist(context: Context, whitelist: Set<String>) {
        prefs(context).edit().putStringSet(KEY_WHITELIST, whitelist).apply()
    }

    fun addToWhitelist(context: Context, pkg: String) {
        val current = getWhitelist(context).toMutableSet()
        current.add(pkg)
        setWhitelist(context, current)
    }

    fun removeFromWhitelist(context: Context, pkg: String) {
        val current = getWhitelist(context).toMutableSet()
        current.remove(pkg)
        setWhitelist(context, current)
    }

    fun isAutoEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_AUTO_ENABLED, false)
    }

    fun setAutoEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_ENABLED, enabled).apply()
    }

    /** Porcentaje de RAM usada (0-100) a partir del cual se dispara el auto-cierre. */
    fun getThreshold(context: Context): Int {
        return prefs(context).getInt(KEY_THRESHOLD, 85)
    }

    fun setThreshold(context: Context, threshold: Int) {
        prefs(context).edit().putInt(KEY_THRESHOLD, threshold).apply()
    }

    /**
     * Último texto de estado del widget ("Toca para limpiar",
     * "Cerrando...", "12 apps cerradas", mensaje de error, etc.), para que
     * el widget muestre algo coherente incluso justo después de un reinicio
     * del sistema, antes de que vuelva a tocarse.
     */
    fun getWidgetStatus(context: Context): String {
        return prefs(context).getString(KEY_WIDGET_STATUS, null) ?: "Toca para limpiar"
    }

    fun setWidgetStatus(context: Context, status: String) {
        prefs(context).edit().putString(KEY_WIDGET_STATUS, status).apply()
    }
}
