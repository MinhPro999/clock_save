package com.minh.statusbarclock

enum class ClockState {
    OFF,
    ON_PENDING_ACCESSIBILITY,
    ON_ACTIVE_OVERLAY,
    /** Kept only for a future SystemUI extension — the current runtime never
     *  enters this state. */
    ON_ACTIVE_SYSTEMUI,
    ERROR_RECOVERABLE
}

/**
 * Tiny pure state machine (no framework types) so transitions are unit-testable
 * without an Android device.
 */
class ClockStateMachine {

    var state: ClockState = ClockState.OFF
        private set

    fun onEnable() {
        state = ClockState.ON_PENDING_ACCESSIBILITY
    }

    /**
     * Reboot / cold-start restore: a fresh process starts in OFF even when
     * the saved preference says enabled. When the accessibility service
     * (re)connects while the saved state is enabled, OFF must be lifted to
     * ON_PENDING_ACCESSIBILITY so activation is permitted.
     *
     * Pure (no framework types) so the restore path is unit-testable.
     *
     * @param enabled the saved enabled preference at (re)connect time.
     * @return true when the machine is now in a state where activation may run.
     */
    fun restoreEnabledAfterReconnect(enabled: Boolean): Boolean {
        if (!enabled) return false
        if (state == ClockState.OFF) {
            state = ClockState.ON_PENDING_ACCESSIBILITY
        }
        return state == ClockState.ON_PENDING_ACCESSIBILITY ||
            state == ClockState.ERROR_RECOVERABLE
    }

    /** The accessibility overlay became the active runtime strategy. */
    fun onOverlayActive() {
        state = ClockState.ON_ACTIVE_OVERLAY
    }

    /**
     * Future SystemUI extension transition — currently unused by the runtime.
     * Kept so existing tests and future diagnostic work keep working.
     */
    fun onActivationResult(systemUiOk: Boolean, overlayOk: Boolean) {
        state = when {
            systemUiOk -> ClockState.ON_ACTIVE_SYSTEMUI
            overlayOk -> ClockState.ON_ACTIVE_OVERLAY
            else -> ClockState.ERROR_RECOVERABLE
        }
    }

    /** Called when the active SystemUI strategy lost support and overlay took over. */
    fun onSystemUiRepairFailed() {
        state = ClockState.ON_ACTIVE_OVERLAY
    }

    fun onRecoverableError() {
        state = ClockState.ERROR_RECOVERABLE
    }

    fun onAccessibilityDisconnected() {
        if (state == ClockState.ON_ACTIVE_SYSTEMUI || state == ClockState.ON_ACTIVE_OVERLAY) {
            state = ClockState.ON_PENDING_ACCESSIBILITY
        }
    }

    fun onDisable() {
        state = ClockState.OFF
    }
}
