package com.minh.statusbarclock

/**
 * Pure debounce for overlay repair attempts: a minimum interval between two
 * repair attempts prevents a failure -> retry -> failure loop.
 *
 * Clock-injectable (no framework types) so the timing rules are fully
 * unit-testable.
 */
class RepairDebouncer(private val minIntervalMillis: Long) {

    init {
        require(minIntervalMillis > 0) { "minIntervalMillis must be positive" }
    }

    // Sentinel far below zero: `nowMillis - lastAttemptAt` never overflows.
    private var lastAttemptAt: Long = Long.MIN_VALUE / 2

    /**
     * Grants an attempt only when [minIntervalMillis] has passed since the
     * last granted attempt. Records the time of the granted attempt.
     */
    fun tryAcquire(nowMillis: Long): Boolean {
        if (nowMillis - lastAttemptAt < minIntervalMillis) return false
        lastAttemptAt = nowMillis
        return true
    }

    /**
     * Delay in milliseconds until the next attempt may be granted
     * (0 = an attempt may run now).
     */
    fun delayMillis(nowMillis: Long): Long {
        val elapsed = nowMillis - lastAttemptAt
        if (elapsed >= minIntervalMillis) return 0L
        return minIntervalMillis - elapsed
    }
}
