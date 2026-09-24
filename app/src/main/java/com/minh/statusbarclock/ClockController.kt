package com.minh.statusbarclock

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Single decision point of the app.
 *
 * Runtime path (Phase 2 — Accessibility Overlay is the only critical path):
 *
 *   enable()
 *     -> Accessibility Service connected
 *     -> determine foreground package
 *     -> HOME      -> hide overlay
 *     -> other app -> show overlay
 *
 * No foreground service, no WRITE_SECURE_SETTINGS, no SYSTEM_ALERT_WINDOW and
 * no ADB dependency are involved.
 *
 * [SystemUiClockStrategy] is kept as a future/diagnostic extension only and is
 * never part of the runtime path.
 */
object ClockController {

    private const val TAG = "StatusBarClock"
    private const val REPAIR_DEBOUNCE_MS = 1000L

    private val machine = ClockStateMachine()
    private val handler = Handler(Looper.getMainLooper())

    // Deliberate static reference: the strategy (and the service context it
    // holds) is stopped and cleared in onAccessibilityDisconnected(), so the
    // reference never outlives the accessibility service binding.
    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var overlayStrategy: AccessibilityOverlayClockStrategy? = null

    @Volatile
    private var homeDetector: HomeDetector? = null

    /** Last foreground package seen via TYPE_WINDOW_STATE_CHANGED. Used only
     *  for the HOME decision — never logged, never persisted. */
    @Volatile
    private var foregroundPackage: String? = null

    private val repairRunnable = Runnable { repairIfNeeded() }

    val currentState: ClockState
        get() = machine.state

    fun enable(context: Context) {
        Log.i(TAG, "enable()")
        handler.removeCallbacksAndMessages(null)
        ClockStateStore.setEnabled(context, true)
        machine.onEnable()
        ClockAccessibilityService.instance?.let { service ->
            ensureHomeDetector(service)
            activate(service)
        }
        // If the service is not connected yet, onAccessibilityConnected()
        // activates the feature once the user grants it.
    }

    fun disable(context: Context) {
        Log.i(TAG, "disable()")
        handler.removeCallbacksAndMessages(null)
        stopOverlayStrategy()
        ClockStateStore.setEnabled(context, false)
        machine.onDisable()
    }

    fun onAccessibilityConnected(service: AccessibilityService) {
        Log.i(TAG, "onAccessibilityConnected state=${machine.state}")
        if (ClockStateStore.isEnabled(service)) {
            ensureHomeDetector(service)
            activate(service)
        }
    }

    fun onAccessibilityDisconnected() {
        Log.i(TAG, "onAccessibilityDisconnected state=${machine.state}")
        handler.removeCallbacksAndMessages(null)
        stopOverlayStrategy()
        homeDetector = null
        foregroundPackage = null
        machine.onAccessibilityDisconnected()
        // When Android re-binds the service (e.g. after reboot),
        // onAccessibilityConnected() restores the feature.
    }

    /**
     * Foreground package changed (TYPE_WINDOW_STATE_CHANGED). Only the package
     * name is used, for the HOME decision — it is never logged.
     */
    fun onForegroundPackageChanged(packageName: String?) {
        foregroundPackage = packageName
        when (machine.state) {
            ClockState.ON_ACTIVE_OVERLAY -> applyVisibility()

            ClockState.ERROR_RECOVERABLE -> {
                // A new window change is a natural moment to retry activation.
                handler.removeCallbacks(repairRunnable)
                handler.postDelayed(repairRunnable, REPAIR_DEBOUNCE_MS)
            }

            else -> {
                // OFF / pending: remember the package, wait for activation.
            }
        }
    }

    fun onConfigurationChanged() {
        overlayStrategy?.onConfigurationChanged()
    }

    // --- Activation -----------------------------------------------------------

    private fun ensureHomeDetector(context: Context) {
        if (homeDetector == null) {
            homeDetector = HomeDetector(context)
        }
    }

    private fun activate(service: AccessibilityService) {
        if (machine.state == ClockState.ON_ACTIVE_OVERLAY) {
            // Feature already active — just refresh the visibility for the
            // current foreground package.
            applyVisibility()
            return
        }
        if (machine.state != ClockState.ON_PENDING_ACCESSIBILITY &&
            machine.state != ClockState.ERROR_RECOVERABLE
        ) {
            return
        }
        // Fresh activation on the overlay path. Any stale strategy window is
        // dropped first so only one overlay can exist at a time.
        stopOverlayStrategy()
        overlayStrategy = AccessibilityOverlayClockStrategy(service)
        machine.onOverlayActive()
        applyVisibility()
    }

    // --- Visibility -----------------------------------------------------------

    private fun applyVisibility() {
        val overlay = overlayStrategy ?: return
        if (machine.state != ClockState.ON_ACTIVE_OVERLAY) return

        val shouldShow = ClockVisibilityPolicy.shouldShowOverlay(
            state = machine.state,
            foregroundPackage = foregroundPackage,
            isHomePackage = { pkg -> homeDetector?.isHomePackage(pkg) == true }
        )
        if (shouldShow) {
            if (!overlay.show()) {
                Log.e(TAG, "Overlay activation failed — ERROR_RECOVERABLE")
                machine.onRecoverableError()
            }
        } else {
            overlay.hide()
        }
    }

    // --- Repair ---------------------------------------------------------------

    private fun repairIfNeeded() {
        when (machine.state) {
            ClockState.ON_ACTIVE_OVERLAY -> applyVisibility()

            ClockState.ERROR_RECOVERABLE -> {
                ClockAccessibilityService.instance?.let { service ->
                    activate(service)
                }
            }

            else -> {
                // OFF / pending: nothing to repair.
            }
        }
    }

    private fun stopOverlayStrategy() {
        overlayStrategy?.stop()
        overlayStrategy = null
    }
}
