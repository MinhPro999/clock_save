package com.minh.statusbarclock

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility service — the independent lifecycle that owns the clock
 * runtime.
 *
 * Runtime flow:
 *   onAccessibilityEvent(TYPE_WINDOW_STATE_CHANGED)
 *     -> ClockController.onForegroundPackageChanged(event.packageName)
 *     -> HOME: hide overlay / other app: show overlay
 *
 * Privacy guarantees:
 * - canRetrieveWindowContent = false: never reads other apps' content.
 * - canPerformGestures = false: never injects gestures.
 * - only event.packageName is used (for the HOME decision); never text,
 *   contentDescription, rootInActiveWindow or AccessibilityNodeInfo.
 * - no screenshots; packages/content of other apps are never logged.
 */
class ClockAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "StatusBarClock"

        @Volatile
        var instance: ClockAccessibilityService? = null
            private set

        /** True when this service is enabled in system Accessibility settings. */
        fun isServiceEnabled(context: Context): Boolean {
            val expected = ComponentName(context, ClockAccessibilityService::class.java)
                .flattenToString()
            val enabled = try {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
            } catch (e: Exception) {
                Log.w(TAG, "Unable to read accessibility state", e)
                null
            } ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }

        /** Opens the system Accessibility settings screen (mandatory OS flow). */
        fun openAccessibilitySettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to open accessibility settings", e)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "onServiceConnected")
        instance = this
        ClockController.onAccessibilityConnected(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        // Foreground-window transitions only. The package name is passed to the
        // controller for the HOME decision. Text, contentDescription, the node
        // tree and screen content are never read or logged.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            ClockController.onForegroundPackageChanged(event.packageName?.toString())
        }
    }

    override fun onInterrupt() {
        // Nothing to do: the service never performs gestures or reads content.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "onUnbind")
        if (instance === this) {
            instance = null
        }
        ClockController.onAccessibilityDisconnected()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        if (instance === this) {
            instance = null
        }
        ClockController.onAccessibilityDisconnected()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ClockController.onConfigurationChanged()
    }
}
