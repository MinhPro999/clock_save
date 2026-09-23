package com.minh.statusbarclock

/**
 * Common contract for clock display strategies.
 *
 * Implementations:
 * - [SystemUiClockStrategy] (priority 1, native SystemUI clock)
 * - [AccessibilityOverlayClockStrategy] (priority 2, fallback overlay)
 */
interface ClockStrategy {
    /** Whether this strategy can work on the current device. */
    fun isSupported(): Boolean

    /** Activates the strategy. Returns true on success. */
    fun start(): Boolean

    /** Deactivates the strategy and restores any state it changed. */
    fun stop()
}
