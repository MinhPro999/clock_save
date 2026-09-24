package com.minh.statusbarclock

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import java.util.Locale

/**
 * The runtime clock: a tiny, non-interactive HH:mm clock drawn at the top-left
 * of the screen with TYPE_ACCESSIBILITY_OVERLAY.
 *
 * Visibility rules:
 * - HOME foreground   -> hidden (view GONE, window stays attached)
 * - other app foreground -> visible
 * No add/remove churn per window event: show()/hide() only touch the view
 * visibility and do nothing when it is already correct.
 *
 * Guarantees:
 * - not touchable, not focusable: never blocks interaction
 * - no animation, no blinking, no notification
 * - redraws once per minute aligned to the minute boundary (no 1-second timer)
 * - follows the system 24-hour setting, locale and timezone
 */
class AccessibilityOverlayClockStrategy(
    private val service: AccessibilityService
) : ClockStrategy {

    companion object {
        private const val TAG = "StatusBarClock"
    }

    private val handler = Handler(Looper.getMainLooper())
    private val windowManager: WindowManager
        get() = service.getSystemService(AccessibilityService.WINDOW_SERVICE) as WindowManager

    /**
     * True only while the overlay window is really attached to the window
     * manager: set when addView() succeeds, cleared when removeView() runs
     * (success or exception). Never derived from clockView != null alone.
     */
    @Volatile
    private var overlayAttached: Boolean = false

    /** What the current foreground package dictates: true = overlay visible. */
    @Volatile
    private var desiredVisible: Boolean = false

    private var clockView: TextView? = null

    private val updateRunnable = object : Runnable {
        override fun run() {
            renderTime()
            scheduleNextMinute()
        }
    }

    override fun isSupported(): Boolean = true

    /** Attach if needed and make the overlay visible. */
    override fun start(): Boolean = show()

    override fun stop() {
        Log.i(TAG, "Stopping overlay clock")
        handler.removeCallbacksAndMessages(null)
        desiredVisible = false
        detach()
    }

    /**
     * Make the overlay visible, attaching the window if needed. Idempotent:
     * does nothing when the overlay is already visible.
     *
     * @return false only when the window could not be attached (recoverable).
     */
    fun show(): Boolean {
        desiredVisible = true
        if (overlayAttached) {
            val view = clockView
            if (view != null && view.visibility != View.VISIBLE) {
                renderTime()
                view.visibility = View.VISIBLE
                scheduleNextMinute()
            }
            return true
        }
        if (!attach()) {
            Log.e(TAG, "Failed to attach overlay window")
            return false
        }
        renderTime()
        scheduleNextMinute()
        return true
    }

    /**
     * Hide the overlay without removing the window. Idempotent: does nothing
     * when the overlay is already hidden or not attached at all.
     */
    fun hide() {
        desiredVisible = false
        if (!overlayAttached) return
        handler.removeCallbacksAndMessages(null)
        clockView?.visibility = View.GONE
    }

    fun onConfigurationChanged() {
        Log.i(TAG, "Configuration changed, re-applying overlay layout")
        if (!overlayAttached) return
        detach()
        if (desiredVisible) {
            if (attach()) {
                renderTime()
                scheduleNextMinute()
            }
        }
    }

    // --- Window management ----------------------------------------------------

    private fun attach(): Boolean {
        if (overlayAttached) return true
        val view = createClockView()
        return try {
            windowManager.addView(view, createLayoutParams())
            clockView = view
            overlayAttached = true
            true
        } catch (e: Exception) {
            clockView = null
            overlayAttached = false
            Log.e(TAG, "addView failed", e)
            false
        }
    }

    private fun detach() {
        val view = clockView
        if (view != null) {
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                Log.w(TAG, "removeView failed", e)
            }
        }
        clockView = null
        overlayAttached = false
    }

    // --- Rendering ------------------------------------------------------------

    private fun renderTime() {
        val view = clockView ?: return
        val now = System.currentTimeMillis()
        val use24Hour = DateFormat.is24HourFormat(service)
        view.text = ClockText.format(now, use24Hour, Locale.getDefault())
    }

    private fun scheduleNextMinute() {
        val delay = TimeAligner.millisUntilNextMinute(System.currentTimeMillis())
        handler.postDelayed(updateRunnable, delay)
    }

    private fun createClockView(): TextView {
        val density = service.resources.displayMetrics.density
        val paddingPx = (4 * density).toInt()
        return TextView(service).apply {
            textSize = 14f // sp, matches typical status bar clock size
            setTextColor(service.getColor(R.color.clock_overlay_text))
            setShadowLayer(2f, 0f, 1f, service.getColor(R.color.clock_overlay_shadow))
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL
            setPadding(paddingPx, 0, paddingPx, 0)
            isClickable = false
            isFocusable = false
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
}
