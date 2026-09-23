package com.minh.statusbarclock

/**
 * Minute-boundary time alignment.
 *
 * The clock only shows HH:mm, so there is no 1-second timer anywhere.
 * The overlay redraws once per minute, just after the minute boundary.
 */
object TimeAligner {

    /**
     * Delay (ms) from [nowMillis] until just after the next minute boundary.
     * A small 50 ms buffer guarantees the new minute value is already visible
     * to [java.util.Calendar]/[java.text.DateFormat] when the redraw happens.
     */
    fun millisUntilNextMinute(nowMillis: Long): Long {
        val millisIntoMinute = nowMillis % 60_000L
        return 60_000L - millisIntoMinute + 50L
    }
}
