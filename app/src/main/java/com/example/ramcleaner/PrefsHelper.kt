package com.example.ramcleaner

import android.content.Context

object PrefsHelper {
    private const val PREFS_NAME = "ramcleaner_prefs"
    private const val KEY_WHITELIST = "whitelist"
    private const val KEY_AUTO_ENABLED = "auto_enabled"
    private const val KEY_THRESHOLD = "threshold_percent"

    const val DEFAULT_THRESHOLD = 20
    const val MIN_THRESHOLD = 5
    const val MAX_THRESHOLD = 50

    fun getWhitelist(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_WHITELIST, emptySet()) ?: emptySet()
    }

    fun setWhitelist(context: Context, packages: Set<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_WHITELIST, packages).apply()
    }

    fun isAutoEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_ENABLED, false)
    }

    fun setAutoEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_ENABLED, enabled).apply()
    }

    fun getThreshold(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD)
    }

    fun setThreshold(context: Context, percent: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_THRESHOLD, percent).apply()
    }
}
