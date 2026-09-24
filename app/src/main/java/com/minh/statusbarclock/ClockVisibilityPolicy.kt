package com.minh.statusbarclock

/**
 * Pure decision logic for overlay visibility. No Android framework types, so
 * it is fully unit-testable without a device or Robolectric.
 */
object ClockVisibilityPolicy {

    /**
     * The overlay must be visible only when the feature is active
     * ([ClockState.ON_ACTIVE_OVERLAY]) and the foreground package is NOT the
     * launcher (HOME).
     *
     * Safety rule: when the foreground package is unknown (null) the overlay is
     * shown — the priority is to never lose the clock. The same applies when
     * the launcher cannot be determined (the [isHomePackage] probe returns
     * false for every package).
     */
    fun shouldShowOverlay(
        state: ClockState,
        foregroundPackage: String?,
        isHomePackage: (String) -> Boolean
    ): Boolean {
        if (state != ClockState.ON_ACTIVE_OVERLAY) return false
        val pkg = foregroundPackage ?: return true
        return !isHomePackage(pkg)
    }
}
