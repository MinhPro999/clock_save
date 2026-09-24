package com.minh.statusbarclock

/**
 * Pure overlay attachment-health rules (no framework types).
 *
 * [AccessibilityOverlayClockStrategy] sets `overlayAttached = true` only after
 * addView() succeeds — but an OEM may silently remove the window, leaving the
 * cached flag stale. These pure rules make that stale state detectable and
 * unit-testable without Android framework classes.
 */
object OverlayHealth {

    /** A cached attachment is real only when the whole chain agrees. */
    fun isActuallyAttached(
        overlayAttached: Boolean,
        viewPresent: Boolean,
        viewAttachedToWindow: Boolean
    ): Boolean = overlayAttached && viewPresent && viewAttachedToWindow

    /**
     * A re-attach is required only when the cached chain disagrees. This is
     * never true while the window is genuinely attached — that is what
     * prevents duplicate overlay windows.
     */
    fun needsReattach(
        overlayAttached: Boolean,
        viewPresent: Boolean,
        viewAttachedToWindow: Boolean
    ): Boolean = !isActuallyAttached(overlayAttached, viewPresent, viewAttachedToWindow)
}
