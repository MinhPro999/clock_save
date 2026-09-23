package com.minh.statusbarclock

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import java.util.Locale

/**
 * Priority-2 fallback: a tiny, non-interactive HH:mm clock drawn at the top-left
 * of the screen with TYPE_ACCESSIBILITY_OVERLAY.
 *
 * Guarantees:
 * - not touchable, not focusable: never blocks interaction
 * - no animation, no blinking, no notification
 * - redraws once per minute aligned to the minute boundary (no 1-second timer)
 * - follows the system 24-hour setting, locale and timezone
 * - position computed from the real status bar height (no blind y=0 assumptions)
 */
class AccessibilityOverlayClockStrategy(
    private val service: AccessibilityService
) : ClockStrategy {

    companion object {
        private const val TAG = "StatusBarClock"
        private const val REPAIR_DEBOUNCE_MS = 1000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val windowManager: WindowManager
        get() = service.getSystemService(AccessibilityService.WINDOW_SERVICE) as WindowManager

    private var clockView: TextView? = null

    private val updateRunnable = object : Runnable {
        override fun run() {
            renderTime()
            scheduleNextMinute()
        }
    }

    private val repairRunnable = Runnable {
        if (!isAttached()) {
            Log.w(TAG, "Overlay window is gone, re-attaching")
            attach()
        }
    }

    override fun isSupported(): Boolean = true

    override fun start(): Boolean {
        Log.i(TAG, "Starting overlay clock")
        if (!isAttached() && !attach()) {
            Log.e(TAG, "Failed to attach overlay window")
            return false
        }
        renderTime()
        scheduleNextMinute()
        return true
    }

    override fun stop() {
        Log.i(TAG, "Stopping overlay clock")
        handler.removeCallbacksAndMessages(null)
        detach()
    }

    /** Foreground window changed: make sure the overlay still exists (repair). */
    fun onForegroundWindowChanged() {
        handler.removeCallbacks(repairRunnable)
        handler.postDelayed(repairRunnable, REPAIR_DEBOUNCE_MS)
    }

    fun onConfigurationChanged() {
        Log.i(TAG, "Configuration changed, re-applying overlay layout")
        detach()
        if (attach()) {
            renderTime()
            scheduleNextMinute()
        }
    }

    // --- Window management ----------------------------------------------------

    private fun attach(): Boolean = try {
        clockView = createClockView().also { view ->
            windowManager.addView(view, createLayoutParams())
        }
        true
    } catch (e: Exception) {
        clockView = null
        Log.e(TAG, "addView failed", e)
        false
    }

    private fun detach() {
        clockView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                Log.w(TAG, "removeView failed", e)
            }
        }
        clockView = null
    }

    private fun isAttached(): Boolean = clockView != null

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
            statusBarHeightPx(),
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

    /** Real status bar height from Android resources, with a dp fallback. */
    private fun statusBarHeightPx(): Int {
        val id = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) {
            service.resources.getDimensionPixelSize(id)
        } else {
            (24 * service.resources.displayMetrics.density).toInt()
        }
    }
}
