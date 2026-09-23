package com.minh.statusbarclock

import android.content.Context

/**
 * The only persistent state of the app: enabled = true / false.
 * No database, no other keys.
 */
object ClockStateStore {

    private const val PREFS_NAME = "status_bar_clock_prefs"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }
}
