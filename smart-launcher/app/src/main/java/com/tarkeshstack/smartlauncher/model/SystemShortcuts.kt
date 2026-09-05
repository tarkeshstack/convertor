package com.tarkeshstack.smartlauncher.model

/** A one-tap shortcut into a built-in Android system screen — Wi-Fi, Bluetooth, or the
 *  main Settings app — offered as a command kind alongside app deep links, since not
 *  everything worth a quick trigger phrase lives inside an app. */
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
    )
}
