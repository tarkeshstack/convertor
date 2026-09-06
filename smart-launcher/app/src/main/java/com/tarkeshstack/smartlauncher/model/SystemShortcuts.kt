package com.tarkeshstack.smartlauncher.model

/** A one-tap shortcut into a built-in Android system screen — Wi-Fi, notifications, app
 *  permissions, and the like — offered as a command kind alongside app deep links, since
 *  not everything worth a quick trigger phrase lives inside an app. */
data class SystemShortcut(
    val label: String,
    /** An Android Intent/Settings action string, e.g. "android.settings.WIFI_SETTINGS". */
    val action: String,
    val description: String,
)

object SystemShortcuts {
    val all = listOf(
        SystemShortcut(
            "Wi-Fi settings", "android.settings.WIFI_SETTINGS",
            "Opens Wi-Fi network settings",
        ),
        SystemShortcut(
            "Bluetooth settings", "android.settings.BLUETOOTH_SETTINGS",
            "Opens Bluetooth settings",
        ),
        SystemShortcut(
            "All settings", "android.settings.SETTINGS",
            "Opens the main Settings app",
        ),
        SystemShortcut(
            "Notifications", "android.settings.NOTIFICATION_SETTINGS",
            "Opens notification settings",
        ),
        SystemShortcut(
            "Downloads", "android.intent.action.VIEW_DOWNLOADS",
            "Opens the system Downloads list",
        ),
        SystemShortcut(
            "Permissions", "android.settings.MANAGE_APPLICATIONS_PERMISSIONS",
            "Opens the app permission manager",
        ),
        // There's no public intent to flip dark mode directly — this is the closest
        // there is, the Display settings screen it lives on.
        SystemShortcut(
            "Night mode", "android.settings.DISPLAY_SETTINGS",
            "Opens Display settings, where dark theme lives",
        ),
    )
}
