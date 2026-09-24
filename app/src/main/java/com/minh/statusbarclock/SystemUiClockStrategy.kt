package com.minh.statusbarclock

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * FUTURE / DIAGNOSTIC extension — NOT part of the Phase 2 runtime path.
 *
 * The runtime always uses [AccessibilityOverlayClockStrategy]; this class is
 * never instantiated by [ClockController] and `knownHooks = emptyList()` cannot
 * make the application unusable.
 *
 * Pipeline per spec: Detect -> Probe -> Enable -> Verify -> Monitor -> Repair.
 *
 * Honesty rule: the strategy never performs blind actions. A third-party app
 * cannot write WRITE_SECURE_SETTINGS, so `settings put secure clock_seconds ...`
 * is NOT a valid release mechanism (ADB is diagnostic-only). The only legal way
 * to control SystemUI from an app is automating a documented, user-visible
 * OEM settings screen via Accessibility — and that must only run against a
 * device profile that was verified in hardware diagnostics.
 *
 * Until a verified hook for the target Android Box exists, this strategy
 * reports "unsupported". After `tools/diagnose_device.sh` produces evidence,
 * register a new [SystemUiHook] in [knownHooks].
 */
class SystemUiClockStrategy(private val context: Context) : ClockStrategy {

    companion object {
        private const val TAG = "StatusBarClock"

        // Device-specific, hardware-verified hooks. Empty until the target
        // Android Box is diagnosed (PENDING_DEVICE_TEST).
        private val knownHooks: List<SystemUiHook> = emptyList()
    }

    data class DeviceProfile(
        val apiLevel: Int,
        val release: String,
        val manufacturer: String,
        val model: String,
        val fingerprint: String,
        val systemUiPackage: String
    )

    data class ProbeResult(
        val clockSeconds: String?,
        val iconBlacklist: String?
    )

    /** A verified, OEM-specific way to keep the native SystemUI clock visible. */
    interface SystemUiHook {
        fun matches(profile: DeviceProfile): Boolean
        fun enable(context: Context, profile: DeviceProfile): Boolean
        fun disable(context: Context, profile: DeviceProfile)
    }

    private var activeHook: SystemUiHook? = null

    override fun isSupported(): Boolean = findMatchingHook() != null

    override fun start(): Boolean {
        val profile = detect()
        Log.i(TAG, "Detect: $profile")
        Log.i(TAG, "Probe: ${probe()}")
        val hook = findMatchingHook()
        if (hook == null) {
            Log.i(TAG, "SystemUI strategy unsupported on this device — no verified hook")
            return false
        }
        return try {
            val ok = hook.enable(context, profile)
            if (ok) {
                activeHook = hook
                Log.i(TAG, "SystemUI hook enabled: $hook")
            }
            ok
        } catch (e: Exception) {
            Log.e(TAG, "SystemUI hook enable failed", e)
            false
        }
    }

    override fun stop() {
        val hook = activeHook ?: return
        try {
            hook.disable(context, detect())
        } catch (e: Exception) {
            Log.w(TAG, "SystemUI hook disable failed", e)
        }
        activeHook = null
    }

    // --- Detect ---------------------------------------------------------------

    private fun detect(): DeviceProfile {
        val systemUiPackage = try {
            context.packageManager.getPackageInfo("com.android.systemui", 0).packageName
        } catch (e: PackageManager.NameNotFoundException) {
            "unknown"
        }
        return DeviceProfile(
            apiLevel = Build.VERSION.SDK_INT,
            release = Build.VERSION.RELEASE ?: "unknown",
            manufacturer = Build.MANUFACTURER ?: "unknown",
            model = Build.MODEL ?: "unknown",
            fingerprint = Build.FINGERPRINT ?: "unknown",
            systemUiPackage = systemUiPackage
        )
    }

    // --- Probe ----------------------------------------------------------------

    private fun probe(): ProbeResult = try {
        ProbeResult(
            clockSeconds = Settings.Secure.getString(context.contentResolver, "clock_seconds"),
            iconBlacklist = Settings.Secure.getString(context.contentResolver, "icon_blacklist")
        )
    } catch (e: Exception) {
        Log.w(TAG, "Probe of secure settings failed", e)
        ProbeResult(clockSeconds = null, iconBlacklist = null)
    }

    private fun findMatchingHook(): SystemUiHook? {
        val profile = detect()
        return knownHooks.firstOrNull { it.matches(profile) }
    }
}
