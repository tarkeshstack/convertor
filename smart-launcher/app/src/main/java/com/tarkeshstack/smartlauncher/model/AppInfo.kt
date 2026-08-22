package com.tarkeshstack.smartlauncher.model

import android.graphics.drawable.Drawable

/** One installed, launchable app. */
data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable,
)
