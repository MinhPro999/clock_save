package com.minh.statusbarclock

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Formats the clock text. Pure logic (no Android framework) for testability.
 *
 * The caller decides [use24HourFormat] through android.text.format.DateFormat
 * .is24HourFormat(context), so the overlay always follows the system setting.
 * Locale and timezone are provided by the caller (system defaults).
 */
object ClockText {

    fun format(epochMillis: Long, use24HourFormat: Boolean, locale: Locale): String {
        val pattern = if (use24HourFormat) "HH:mm" else "hh:mm"
        return SimpleDateFormat(pattern, locale).format(Date(epochMillis))
    }
}
