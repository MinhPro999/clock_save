package com.minh.statusbarclock

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
    private val repairDebouncer = RepairDebouncer(REPAIR_DEBOUNCE_MS)

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

    /** True while a repair attempt is queued on [handler]. Kept as a flag
     *  because Handler.hasCallbacks needs API 29 (minSdk is 26). */
    private var repairScheduled = false

    val currentState: ClockState
        get() = machine.state

    fun enable(context: Context) {
        Log.i(TAG, "enable()")
        cancelScheduledRepairs()
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
        cancelScheduledRepairs()
        stopOverlayStrategy()
        ClockStateStore.setEnabled(context, false)
        machine.onDisable()
    }

    fun onAccessibilityConnected(service: AccessibilityService) {
        Log.i(TAG, "onAccessibilityConnected state=${machine.state}")
        // Reboot / cold start: a fresh process starts in OFF even when the
        // saved preference says enabled. Lift OFF back to a state where
        // activation is permitted, so the clock is restored without user
        // interaction, without ADB and without BOOT_COMPLETED.
        if (machine.restoreEnabledAfterReconnect(ClockStateStore.isEnabled(service))) {
            ensureHomeDetector(service)
            activate(service)
        }
    }

    fun onAccessibilityDisconnected() {
        Log.i(TAG, "onAccessibilityDisconnected state=${machine.state}")
        cancelScheduledRepairs()
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
                scheduleRepair()
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

        // Repair flow: verify the real attachment state on every foreground
        // event. No remove + add churn while the window is healthy — the
        // stale reference is only dropped when the window is really gone.
        overlay.ensureOverlayHealth()

        val shouldShow = ClockVisibilityPolicy.shouldShowOverlay(
            state = machine.state,
            foregroundPackage = foregroundPackage,
            isHomePackage = { pkg -> homeDetector?.isHomePackage(pkg) == true },
            currentVisible = overlay.isVisible
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

    /**
     * Schedules a repair attempt at least [REPAIR_DEBOUNCE_MS] after the last
     * granted attempt, so a failure -> retry -> failure loop cannot hammer the
     * window manager from a stream of window events.
     */
    private fun scheduleRepair() {
        if (repairScheduled) return
        repairScheduled = true
        val delay = repairDebouncer.delayMillis(SystemClock.elapsedRealtime())
        handler.postDelayed(repairRunnable, delay)
    }

    private fun cancelScheduledRepairs() {
        repairScheduled = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun repairIfNeeded() {
        repairScheduled = false
        if (!repairDebouncer.tryAcquire(SystemClock.elapsedRealtime())) {
            // Another repair already ran within the debounce window.
            scheduleRepair()
            return
        }
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
