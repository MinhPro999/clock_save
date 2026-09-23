package com.minh.statusbarclock

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Single decision point of the app.
 *
 * Flow:
 *   enable()
 *     -> SystemUiClockStrategy.start()
 *          +-- success -> ON_ACTIVE_SYSTEMUI
 *          +-- fail    -> AccessibilityOverlayClockStrategy.start()
 *                             +-- success -> ON_ACTIVE_OVERLAY
 *                             +-- fail    -> ERROR_RECOVERABLE
 *
 * The activity is only a UI control. The accessibility service is the
 * independent lifecycle that owns the strategies.
 */
object ClockController {

    private const val TAG = "StatusBarClock"
    private const val REPAIR_DEBOUNCE_MS = 1000L

    private val machine = ClockStateMachine()
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var overlayStrategy: AccessibilityOverlayClockStrategy? = null

    @Volatile
    private var systemUiStrategy: SystemUiClockStrategy? = null

    private val repairRunnable = Runnable { repairIfNeeded() }

    val currentState: ClockState
        get() = machine.state

    fun enable(context: Context) {
        Log.i(TAG, "enable()")
        handler.removeCallbacksAndMessages(null)
        ClockStateStore.setEnabled(context, true)
        machine.onEnable()
        ClockAccessibilityService.instance?.let { service ->
            activate(service)
        }
        // If the service is not connected yet, onAccessibilityConnected()
        // activates the feature once the user grants it.
    }

    fun disable(context: Context) {
        Log.i(TAG, "disable()")
        handler.removeCallbacksAndMessages(null)
        stopAllStrategies()
        ClockStateStore.setEnabled(context, false)
        machine.onDisable()
    }

    fun onAccessibilityConnected(service: AccessibilityService) {
        Log.i(TAG, "onAccessibilityConnected state=${machine.state}")
        if (ClockStateStore.isEnabled(service)) {
            activate(service)
        }
    }

    fun onAccessibilityDisconnected() {
        Log.i(TAG, "onAccessibilityDisconnected state=${machine.state}")
        handler.removeCallbacksAndMessages(null)
        stopAllStrategies()
        machine.onAccessibilityDisconnected()
        // When Android re-binds the service (e.g. after reboot),
        // onAccessibilityConnected() restores the feature.
    }

    /** Foreground window changed — verify/repair the clock if needed. */
    fun onForegroundWindowChanged() {
        if (machine.state != ClockState.ON_ACTIVE_SYSTEMUI &&
            machine.state != ClockState.ON_ACTIVE_OVERLAY
        ) {
            return
        }
        handler.removeCallbacks(repairRunnable)
        handler.postDelayed(repairRunnable, REPAIR_DEBOUNCE_MS)
    }

    fun onConfigurationChanged() {
        overlayStrategy?.onConfigurationChanged()
    }

    // --- Activation -----------------------------------------------------------

    private fun activate(service: AccessibilityService) {
        if (machine.state == ClockState.ON_ACTIVE_SYSTEMUI ||
            machine.state == ClockState.ON_ACTIVE_OVERLAY
        ) {
            return
        }

        // Priority 1: native SystemUI clock.
        val systemUi = SystemUiClockStrategy(service)
        val systemUiOk = systemUi.isSupported() && systemUi.start()
        if (systemUiOk) {
            systemUiStrategy = systemUi
            machine.onActivationResult(systemUiOk = true, overlayOk = false)
            Log.i(TAG, "Active strategy: SYSTEM_UI")
            return
        }

        // Priority 2: accessibility overlay fallback.
        val overlay = AccessibilityOverlayClockStrategy(service)
        val overlayOk = overlay.start()
        if (overlayOk) {
            overlayStrategy = overlay
        }
        machine.onActivationResult(systemUiOk = false, overlayOk = overlayOk)
        Log.i(
            TAG,
            if (overlayOk) "Active strategy: ACCESSIBILITY_OVERLAY"
            else "Activation failed: ERROR_RECOVERABLE"
        )
    }

    // --- Repair ---------------------------------------------------------------

    private fun repairIfNeeded() {
        when (machine.state) {
            ClockState.ON_ACTIVE_OVERLAY -> {
                // Re-attach if the system dropped the overlay window.
                val overlay = overlayStrategy ?: return
                if (!overlay.start()) {
                    machine.onRecoverableError()
                }
            }

            ClockState.ON_ACTIVE_SYSTEMUI -> {
                val systemUi = systemUiStrategy ?: return
                if (!systemUi.isSupported()) {
                    Log.w(TAG, "SystemUI strategy lost support — falling back to overlay")
                    fallbackToOverlay()
                }
            }

            else -> {
                // OFF / pending / error: nothing to repair.
            }
        }
    }

    private fun fallbackToOverlay() {
        val service = ClockAccessibilityService.instance ?: return
        systemUiStrategy?.stop()
        systemUiStrategy = null
        val overlay = AccessibilityOverlayClockStrategy(service)
        if (overlay.start()) {
            overlayStrategy = overlay
            machine.onSystemUiRepairFailed() // -> ON_ACTIVE_OVERLAY
        } else {
            machine.onRecoverableError()
        }
    }

    private fun stopAllStrategies() {
        systemUiStrategy?.stop()
        systemUiStrategy = null
        overlayStrategy?.stop()
        overlayStrategy = null
    }
}
