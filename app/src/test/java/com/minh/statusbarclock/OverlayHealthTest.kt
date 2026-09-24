package com.minh.statusbarclock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2.1: overlay attachment health — a cached `overlayAttached = true`
 * only proves addView() once succeeded; an OEM may silently remove the
 * window. These pure rules make that stale state detectable.
 */
class OverlayHealthTest {

    @Test
    fun `cached attachment with real view attachment is actually attached`() {
        assertTrue(OverlayHealth.isActuallyAttached(true, true, true))
        assertFalse(OverlayHealth.needsReattach(true, true, true))
    }

    @Test
    fun `attached flag with detached view is stale`() {
        assertFalse(OverlayHealth.isActuallyAttached(true, true, false))
        assertTrue(OverlayHealth.needsReattach(true, true, false))
    }

    @Test
    fun `attached flag without view reference is stale`() {
        assertFalse(OverlayHealth.isActuallyAttached(true, false, false))
        assertTrue(OverlayHealth.needsReattach(true, false, false))
    }

    @Test
    fun `never attached is not attached`() {
        assertFalse(OverlayHealth.isActuallyAttached(false, true, true))
    }

    @Test
    fun `no duplicate attach while window is healthy`() {
        // The only reattach trigger is a stale chain — while healthy, the
        // window must never be removed + re-added.
        assertFalse(OverlayHealth.needsReattach(true, true, true))
    }
}
