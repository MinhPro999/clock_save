package com.minh.statusbarclock

/** Classification of the current foreground window for the HOME decision. */
enum class ForegroundKind {
    /** The launcher (HOME) is foreground — hide the overlay. */
    HOME,

    /** A known system window (e.g. SystemUI chrome) — keep current visibility. */
    SYSTEM_UI,

    /** A known user application — show the overlay. */
    APP,

    /** Unknown or null package — show the overlay (never lose the clock). */
    UNKNOWN
}

/**
 * Pure decision logic for overlay visibility. No Android framework types, so
 * it is fully unit-testable without a device or Robolectric.
 */
object ClockVisibilityPolicy {

    /**
     * Classification order mirrors the required policy:
     *   HOME -> hide, SystemUI -> keep, user app / unknown -> show.
     *
     * HOME is checked before the system-window list so that a device whose
     * launcher shares a package with system chrome still hides the overlay.
     */
    fun classifyForeground(
        foregroundPackage: String?,
        isHomePackage: (String) -> Boolean
    ): ForegroundKind {
        val pkg = foregroundPackage ?: return ForegroundKind.UNKNOWN
        if (isHomePackage(pkg)) return ForegroundKind.HOME
        if (SystemPackageDetection.isSystemWindowPackage(pkg)) return ForegroundKind.SYSTEM_UI
        return ForegroundKind.APP
    }

    /**
     * The overlay must be visible only when the feature is active
     * ([ClockState.ON_ACTIVE_OVERLAY]) and the foreground package is NOT the
     * launcher (HOME).
     *
     * Safety rule: when the foreground package is unknown (null) the overlay is
     * shown — the priority is to never lose the clock. The same applies when
     * the launcher cannot be determined (the [isHomePackage] probe returns
     * false for every package).
     *
     * System windows (e.g. a short `com.android.systemui` event) must never
     * flip the overlay by themselves: their classification keeps the current
     * visibility ([currentVisible]) so a transient SystemUI event cannot hide
     * the clock or create a duplicate on Home.
     */
    fun shouldShowOverlay(
        state: ClockState,
        foregroundPackage: String?,
        isHomePackage: (String) -> Boolean,
        currentVisible: Boolean = true
    ): Boolean {
        if (state != ClockState.ON_ACTIVE_OVERLAY) return false
        return when (classifyForeground(foregroundPackage, isHomePackage)) {
            ForegroundKind.HOME -> false
            ForegroundKind.SYSTEM_UI -> currentVisible
            ForegroundKind.APP, ForegroundKind.UNKNOWN -> true
        }
    }
}
