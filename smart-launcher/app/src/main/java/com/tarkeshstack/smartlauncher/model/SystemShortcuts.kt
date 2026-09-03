package com.tarkeshstack.smartlauncher.model

/** A one-tap shortcut into a built-in Android system screen — wallpaper, Wi-Fi, NFC
 *  cards & payments, and the like — offered as a command kind alongside app deep links,
 *  since not everything worth a quick trigger phrase lives inside an app. */
data class SystemShortcut(
    val label: String,
    /** An Android Intent/Settings action string, e.g. "android.settings.WIFI_SETTINGS". */
    val action: String,
    val description: String,
)

object SystemShortcuts {
    val all = listOf(
        SystemShortcut(
            "Change wallpaper", "android.intent.action.SET_WALLPAPER",
            "Opens the wallpaper picker",
        ),
        SystemShortcut(
            "Wi-Fi settings", "android.settings.WIFI_SETTINGS",
            "Opens Wi-Fi network settings",
        ),
        SystemShortcut(
            "Bluetooth settings", "android.settings.BLUETOOTH_SETTINGS",
            "Opens Bluetooth settings",
        ),
        SystemShortcut(
            "Cards & payments", "android.settings.NFC_PAYMENT_SETTINGS",
            "Opens tap-to-pay and wallet cards",
        ),
        SystemShortcut(
            "Display settings", "android.settings.DISPLAY_SETTINGS",
            "Opens display and brightness settings",
        ),
        SystemShortcut(
            "Sound settings", "android.settings.SOUND_SETTINGS",
            "Opens sound and volume settings",
        ),
        SystemShortcut(
            "Battery settings", "android.settings.BATTERY_SAVER_SETTINGS",
            "Opens battery saver settings",
        ),
        SystemShortcut(
            "Airplane mode", "android.settings.AIRPLANE_MODE_SETTINGS",
            "Opens airplane mode settings",
        ),
        SystemShortcut(
            "Location settings", "android.settings.LOCATION_SOURCE_SETTINGS",
            "Opens location settings",
        ),
        SystemShortcut(
            "All settings", "android.settings.SETTINGS",
            "Opens the main Settings app",
        ),
    )
}
