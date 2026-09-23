package com.minh.statusbarclock

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeAlignerTest {

    @Test
    fun `delay lands just after the next minute boundary`() {
        val now = 12_345L
        assertEquals(60_000L - 12_345L + 50L, TimeAligner.millisUntilNextMinute(now))
    }

    @Test
    fun `at an exact boundary the next update waits one full minute plus buffer`() {
        assertEquals(60_050L, TimeAligner.millisUntilNextMinute(60_000L))
    }

    @Test
    fun `just before a boundary the delay is minimal`() {
        assertEquals(51L, TimeAligner.millisUntilNextMinute(59_999L))
    }

    @Test
    fun `wake time crosses exactly one minute boundary`() {
        val now = 1_700_000_123_456L
        val target = now + TimeAligner.millisUntilNextMinute(now)
        assertEquals(now / 60_000L + 1L, target / 60_000L)
        assertEquals(50L, target % 60_000L)
    }

    @Test
    fun `delay is always within one minute`() {
        val now = 987_654_321L
        val delay = TimeAligner.millisUntilNextMinute(now)
        assertEquals(true, delay in 1..60_050L)
    }
}
