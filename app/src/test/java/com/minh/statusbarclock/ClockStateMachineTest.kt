package com.minh.statusbarclock

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockStateMachineTest {

    @Test
    fun `initial state is OFF`() {
        assertEquals(ClockState.OFF, ClockStateMachine().state)
    }

    @Test
    fun `enable goes to pending accessibility`() {
        val machine = ClockStateMachine()
        machine.onEnable()
        assertEquals(ClockState.ON_PENDING_ACCESSIBILITY, machine.state)
    }

    @Test
    fun `systemUi success activates system ui strategy`() {
        val machine = ClockStateMachine()
        machine.onEnable()
        machine.onActivationResult(systemUiOk = true, overlayOk = false)
        assertEquals(ClockState.ON_ACTIVE_SYSTEMUI, machine.state)
    }

    @Test
    fun `systemUi failure falls back to overlay`() {
        val machine = ClockStateMachine()
        machine.onEnable()
        machine.onActivationResult(systemUiOk = false, overlayOk = true)
        assertEquals(ClockState.ON_ACTIVE_OVERLAY, machine.state)
    }

    @Test
    fun `both strategies failing is recoverable error`() {
        val machine = ClockStateMachine()
        machine.onEnable()
        machine.onActivationResult(systemUiOk = false, overlayOk = false)
        assertEquals(ClockState.ERROR_RECOVERABLE, machine.state)
    }

    @Test
    fun `disable returns to OFF from active overlay state`() {
        val machine = ClockStateMachine()
        machine.onEnable()
        machine.onActivationResult(systemUiOk = false, overlayOk = true)
        machine.onDisable()
        assertEquals(ClockState.OFF, machine.state)
    }

    @Test
    fun `disable returns to OFF from active systemUi state`() {
        val machine = ClockStateMachine()
        machine.onEnable()
        machine.onActivationResult(systemUiOk = true, overlayOk = false)
        machine.onDisable()
        assertEquals(ClockState.OFF, machine.state)
    }

    @Test
    fun `accessibility disconnect moves active states back to pending`() {
        val machine = ClockStateMachine()
        machine.onEnable()
        machine.onActivationResult(systemUiOk = true, overlayOk = false)
        machine.onAccessibilityDisconnected()
        assertEquals(ClockState.ON_PENDING_ACCESSIBILITY, machine.state)
    }

    @Test
    fun `systemUi repair failure moves to overlay state`() {
        val machine = ClockStateMachine()
        machine.onEnable()
        machine.onActivationResult(systemUiOk = true, overlayOk = false)
        machine.onSystemUiRepairFailed()
        assertEquals(ClockState.ON_ACTIVE_OVERLAY, machine.state)
    }

    @Test
    fun `pending state is untouched by accessibility disconnect`() {
        val machine = ClockStateMachine()
        machine.onEnable()
        machine.onAccessibilityDisconnected()
        assertEquals(ClockState.ON_PENDING_ACCESSIBILITY, machine.state)
    }

    @Test
    fun `overlay activation moves pending to active overlay`() {
        val machine = ClockStateMachine()
        machine.onEnable()
        machine.onOverlayActive()
        assertEquals(ClockState.ON_ACTIVE_OVERLAY, machine.state)
    }

    @Test
    fun `overlay activation retries from recoverable error`() {
        val machine = ClockStateMachine()
        machine.onEnable()
        machine.onActivationResult(systemUiOk = false, overlayOk = false)
        assertEquals(ClockState.ERROR_RECOVERABLE, machine.state)
        machine.onOverlayActive()
        assertEquals(ClockState.ON_ACTIVE_OVERLAY, machine.state)
    }
}
