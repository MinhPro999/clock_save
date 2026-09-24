package com.minh.statusbarclock

/**
 * Common contract for clock display strategies.
 *
 * Runtime implementation (Phase 2): [AccessibilityOverlayClockStrategy].
 * [SystemUiClockStrategy] is a future/diagnostic extension only.
 */
interface ClockStrategy {
    /** Whether this strategy can work on the current device. */
    fun isSupported(): Boolean

    /** Activates the strategy. Returns true on success. */
    fun start(): Boolean

    /** Deactivates the strategy and restores any state it changed. */
    fun stop()
}
