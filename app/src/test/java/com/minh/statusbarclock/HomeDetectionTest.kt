package com.minh.statusbarclock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDetectionTest {

    private val homePackages = setOf("com.example.launcher", "com.android.launcher3")

    @Test
    fun `home package is detected as home`() {
        assertTrue(HomeDetection.isHomePackage(homePackages, "com.example.launcher"))
        assertTrue(HomeDetection.isHomePackage(homePackages, "com.android.launcher3"))
    }

    @Test
    fun `other package is not home`() {
        assertFalse(HomeDetection.isHomePackage(homePackages, "com.google.android.apps.maps"))
        assertFalse(HomeDetection.isHomePackage(homePackages, "com.google.android.youtube"))
    }

    @Test
    fun `null package is not home`() {
        assertFalse(HomeDetection.isHomePackage(homePackages, null))
    }

    @Test
    fun `unknown launcher means no package is home`() {
        // Launcher could not be resolved: every package must be "not home" so
        // the controller shows the overlay instead of hiding the clock.
        assertFalse(HomeDetection.isHomePackage(emptySet(), "com.example.launcher"))
        assertFalse(HomeDetection.isHomePackage(emptySet(), "com.google.android.apps.maps"))
    }

    @Test
    fun `membership is purely dynamic with no hard-coded package`() {
        // Any OEM launcher in the resolved set counts as HOME; anything else
        // (including com.android.systemui) does not — no hard-coded list.
        val exoticLauncher = setOf("com.oem.box.launcher")
        assertTrue(HomeDetection.isHomePackage(exoticLauncher, "com.oem.box.launcher"))
        assertFalse(HomeDetection.isHomePackage(exoticLauncher, "com.android.systemui"))
    }
}
