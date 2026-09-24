package com.minh.statusbarclock

import org.junit.Assert.assertEquals
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

    // --- Phase 2.1: SystemUI / system window events ---------------------------

    @Test
    fun `classify system ui package as system ui`() {
        assertEquals(
            ForegroundKind.SYSTEM_UI,
            ClockVisibilityPolicy.classifyForeground("com.android.systemui", isHome)
        )
    }

    @Test
    fun `classify home before system ui`() {
        // HOME has priority over the system-window list in the policy order.
        val homeAndSystemUi: (String) -> Boolean = { it == "com.android.systemui" }
        assertEquals(
            ForegroundKind.HOME,
            ClockVisibilityPolicy.classifyForeground("com.android.systemui", homeAndSystemUi)
        )
    }

    @Test
    fun `classify other app and unknown`() {
        assertEquals(
            ForegroundKind.APP,
            ClockVisibilityPolicy.classifyForeground("com.google.android.apps.maps", isHome)
        )
        assertEquals(
            ForegroundKind.UNKNOWN,
            ClockVisibilityPolicy.classifyForeground(null, isHome)
        )
    }

    @Test
    fun `system ui event keeps overlay visible while visible`() {
        assertTrue(
            ClockVisibilityPolicy.shouldShowOverlay(
                ClockState.ON_ACTIVE_OVERLAY,
                "com.android.systemui",
                isHome,
                currentVisible = true
            )
        )
    }

    @Test
    fun `system ui event keeps overlay hidden while hidden`() {
        assertFalse(
            ClockVisibilityPolicy.shouldShowOverlay(
                ClockState.ON_ACTIVE_OVERLAY,
                "com.android.systemui",
                isHome,
                currentVisible = false
            )
        )
    }

    @Test
    fun `single system ui event never hides a visible clock`() {
        // Maps -> SystemUI transient event -> the clock must stay visible.
        assertTrue(shouldShow(ClockState.ON_ACTIVE_OVERLAY, "com.google.android.apps.maps"))
        assertTrue(
            ClockVisibilityPolicy.shouldShowOverlay(
                ClockState.ON_ACTIVE_OVERLAY,
                "com.android.systemui",
                isHome,
                currentVisible = true
            )
        )
    }

    @Test
    fun `single system ui event never creates a duplicate clock on home`() {
        // HOME -> SystemUI transient event -> the clock must stay hidden.
        assertFalse(shouldShow(ClockState.ON_ACTIVE_OVERLAY, "com.example.launcher"))
        assertFalse(
            ClockVisibilityPolicy.shouldShowOverlay(
                ClockState.ON_ACTIVE_OVERLAY,
                "com.android.systemui",
                isHome,
                currentVisible = false
            )
        )
    }

    @Test
    fun `system ui state never drives visibility in non-active states`() {
        assertFalse(
            ClockVisibilityPolicy.shouldShowOverlay(
                ClockState.OFF,
                "com.android.systemui",
                isHome,
                currentVisible = true
            )
        )
        assertFalse(
            ClockVisibilityPolicy.shouldShowOverlay(
                ClockState.ON_PENDING_ACCESSIBILITY,
                "com.android.systemui",
                isHome,
                currentVisible = true
            )
        )
    }
}
