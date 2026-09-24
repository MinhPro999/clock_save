package com.minh.statusbarclock

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * Detects the launcher (HOME) packages of this device without hard-coding any
 * package name: every package that can handle ACTION_MAIN + CATEGORY_HOME is
 * treated as HOME.
 *
 * The decision logic itself lives in [HomeDetection] (pure Kotlin) so it is
 * unit-testable without an Android device or Robolectric.
 */
class HomeDetector(context: Context) {

    companion object {
        private const val TAG = "StatusBarClock"
    }

    private val homePackages: Set<String> = resolveHomePackages(context.applicationContext)

    /**
     * True when [packageName] can handle ACTION_MAIN + CATEGORY_HOME.
     *
     * Safety rule: if the launcher could not be determined at all this returns
     * false, so the controller shows the overlay — never losing the clock has
     * priority over hiding it on Home.
     */
    fun isHomePackage(packageName: String?): Boolean =
        HomeDetection.isHomePackage(homePackages, packageName)

    private fun resolveHomePackages(context: Context): Set<String> = try {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, 0)
        }
        resolved.mapNotNull { it.activityInfo?.packageName }.toSet()
    } catch (e: Exception) {
        Log.w(TAG, "Unable to resolve launcher packages", e)
        emptySet()
    }
}

/**
 * Pure, framework-free HOME decision.
 *
 * The modelled failure mode: an empty [homePackages] set (launcher unknown)
 * makes every package "not home", which means "show the overlay".
 */
object HomeDetection {
    fun isHomePackage(homePackages: Set<String>, foregroundPackage: String?): Boolean =
        foregroundPackage != null && foregroundPackage in homePackages
}
