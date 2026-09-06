package com.tarkeshstack.smartlauncher.model

import android.graphics.drawable.Drawable

/** One installed, launchable app. [installedAt] is when it was first installed
 *  (PackageInfo.firstInstallTime, epoch millis), used to rank recently installed apps
 *  ahead of older ones that have never been opened from this launcher. */
data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val installedAt: Long = 0L,
)
