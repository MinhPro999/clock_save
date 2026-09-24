package com.minh.statusbarclock

/**
 * Pure, framework-free detection of system window packages.
 *
 * Only packages with hard evidence of emitting system-chrome
 * TYPE_WINDOW_STATE_CHANGED events are listed — deliberately no speculative
 * OEM package names. A single SystemUI event must never be used to conclude
 * that the user left HOME, so [ClockVisibilityPolicy] keeps the current
 * overlay visibility when such a package is seen.
 */
object SystemPackageDetection {

    private val systemWindowPackages: Set<String> = setOf(
        "com.android.systemui"
    )

    fun isSystemWindowPackage(packageName: String?): Boolean =
        packageName != null && packageName in systemWindowPackages
}
