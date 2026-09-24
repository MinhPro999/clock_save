package com.minh.statusbarclock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the Phase 2 overlay visibility rules:
 *   HOME package    -> overlay hidden
 *   other package   -> overlay visible
 *   unknown package -> overlay visible
 *   OFF             -> overlay hidden
 *   ON + service connected -> correct visibility
 */
class ClockVisibilityPolicyTest {

    private val homePackages = setOf("com.example.launcher")
    private val isHome: (String) -> Boolean =
        { pkg -> HomeDetection.isHomePackage(homePackages, pkg) }

    private fun shouldShow(state: ClockState, foregroundPackage: String?): Boolean =
        ClockVisibilityPolicy.shouldShowOverlay(state, foregroundPackage, isHome)

    @Test
    fun `home package hides overlay`() {
        assertFalse(shouldShow(ClockState.ON_ACTIVE_OVERLAY, "com.example.launcher"))
    }

    @Test
    fun `other package shows overlay`() {
        assertTrue(shouldShow(ClockState.ON_ACTIVE_OVERLAY, "com.google.android.apps.maps"))
        assertTrue(shouldShow(ClockState.ON_ACTIVE_OVERLAY, "com.google.android.youtube"))
    }

    @Test
    fun `unknown foreground package shows overlay`() {
        assertTrue(shouldShow(ClockState.ON_ACTIVE_OVERLAY, null))
    }

    @Test
    fun `off state hides overlay for non-home package`() {
        assertFalse(shouldShow(ClockState.OFF, "com.google.android.apps.maps"))
    }

    @Test
    fun `off state hides overlay on home`() {
        assertFalse(shouldShow(ClockState.OFF, "com.example.launcher"))
    }

    @Test
    fun `pending accessibility state hides overlay until service activates`() {
        assertFalse(shouldShow(ClockState.ON_PENDING_ACCESSIBILITY, "com.google.android.apps.maps"))
    }

    @Test
    fun `on and service connected shows overlay for other app`() {
        assertTrue(shouldShow(ClockState.ON_ACTIVE_OVERLAY, "com.google.android.apps.maps"))
    }

    @Test
    fun `on and service connected hides overlay on home`() {
        assertFalse(shouldShow(ClockState.ON_ACTIVE_OVERLAY, "com.example.launcher"))
    }

    @Test
    fun `on and service connected shows overlay when foreground package unknown`() {
        assertTrue(shouldShow(ClockState.ON_ACTIVE_OVERLAY, null))
    }

    @Test
    fun `systemUi active state never drives the overlay`() {
        // Future extension state must not affect the Phase 2 runtime.
        assertFalse(shouldShow(ClockState.ON_ACTIVE_SYSTEMUI, "com.google.android.apps.maps"))
        assertFalse(shouldShow(ClockState.ON_ACTIVE_SYSTEMUI, "com.example.launcher"))
    }

    @Test
    fun `error state hides overlay`() {
        assertFalse(shouldShow(ClockState.ERROR_RECOVERABLE, "com.google.android.apps.maps"))
    }

    @Test
    fun `unknown launcher falls back to showing overlay`() {
        // Launcher could not be determined (probe fails for every package):
        // priority is to never lose the clock -> show the overlay.
        val launcherUnknown: (String) -> Boolean = { false }
        assertTrue(
            ClockVisibilityPolicy.shouldShowOverlay(
                ClockState.ON_ACTIVE_OVERLAY,
                "com.example.launcher",
                launcherUnknown
            )
        )
    }
}
