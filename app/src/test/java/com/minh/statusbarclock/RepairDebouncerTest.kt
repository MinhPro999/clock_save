package com.minh.statusbarclock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2.1: repair debounce — a failure -> retry -> failure loop must be
 * broken by a minimum interval between repair attempts.
 */
class RepairDebouncerTest {

    private val debouncer = RepairDebouncer(minIntervalMillis = 1000L)

    @Test
    fun `first attempt is allowed`() {
        assertTrue(debouncer.tryAcquire(nowMillis = 5_000L))
    }

    @Test
    fun `attempt within the interval is blocked`() {
        assertTrue(debouncer.tryAcquire(nowMillis = 5_000L))
        assertFalse(debouncer.tryAcquire(nowMillis = 5_500L))
        assertFalse(debouncer.tryAcquire(nowMillis = 5_999L))
    }

    @Test
    fun `attempt after the interval is allowed`() {
        assertTrue(debouncer.tryAcquire(nowMillis = 5_000L))
        assertFalse(debouncer.tryAcquire(nowMillis = 5_900L))
        assertTrue(debouncer.tryAcquire(nowMillis = 6_000L))
    }

    @Test
    fun `delay is zero when an attempt may run now`() {
        assertEquals(0L, debouncer.delayMillis(nowMillis = 10_000L))
    }

    @Test
    fun `delay covers the remaining interval after an attempt`() {
        debouncer.tryAcquire(nowMillis = 5_000L)
        assertEquals(500L, debouncer.delayMillis(nowMillis = 5_500L))
        assertEquals(0L, debouncer.delayMillis(nowMillis = 6_000L))
    }

    @Test
    fun `rejects non-positive interval`() {
        var rejected = false
        try {
            RepairDebouncer(minIntervalMillis = 0L)
        } catch (e: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }
}
