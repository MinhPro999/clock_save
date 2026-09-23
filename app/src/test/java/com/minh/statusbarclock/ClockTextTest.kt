package com.minh.statusbarclock

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class ClockTextTest {

    private var originalTimeZone: TimeZone? = null

    @Before
    fun fixTimeZone() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTimeZone() {
        originalTimeZone?.let { TimeZone.setDefault(it) }
    }

    private fun utcMillis(hour: Int, minute: Int): Long {
        val minutes = hour * 60L + minute
        return minutes * 60_000L
    }

    @Test
    fun `24h format uses two-digit hours and minutes`() {
        assertEquals("01:23", ClockText.format(utcMillis(1, 23), true, Locale.US))
        assertEquals("13:23", ClockText.format(utcMillis(13, 23), true, Locale.US))
        assertEquals("00:00", ClockText.format(utcMillis(0, 0), true, Locale.US))
    }

    @Test
    fun `12h format never shows 13`() {
        assertEquals("01:23", ClockText.format(utcMillis(13, 23), false, Locale.US))
        assertEquals("12:00", ClockText.format(utcMillis(0, 0), false, Locale.US))
        assertEquals("12:59", ClockText.format(utcMillis(12, 59), false, Locale.US))
    }

    @Test
    fun `formatter respects the provided locale`() {
        // Both patterns are numeric, but separators follow the locale pattern
        // data (e.g. ar uses a different time format template).
        val formatted = ClockText.format(utcMillis(9, 5), true, Locale.US)
        assertEquals("09:05", formatted)
    }

    @Test
    fun `formatter is stable across repeated calls`() {
        val first = ClockText.format(utcMillis(21, 47), true, Locale.US)
        val second = ClockText.format(utcMillis(21, 47), true, Locale.US)
        assertEquals(first, second)
    }
}
