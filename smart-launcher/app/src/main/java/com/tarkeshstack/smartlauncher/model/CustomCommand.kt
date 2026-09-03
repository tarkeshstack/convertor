package com.tarkeshstack.smartlauncher.model

/** How a user-defined command's trigger phrase gets executed. */
enum class CustomCommandKind { OPEN_APP, DEEP_LINK, SYSTEM_SHORTCUT }

/**
 * A command the user defined themselves: typing or saying [phrase] runs it.
 *
 * [kind] OPEN_APP just launches [packageName]. DEEP_LINK opens [deepLinkUri] via
 * ACTION_VIEW, optionally restricted to [packageName] so it opens in a specific app
 * rather than whatever the device would otherwise pick for that URI. SYSTEM_SHORTCUT
 * fires [systemAction] as a bare Intent action, e.g. into Wi-Fi or wallpaper settings.
 *
 * [visibleOnHome] is per-command — each one is shown or hidden on the home screen's
 * quick-access list independently, rather than one all-or-nothing switch for all of
 * them; it's still always listed in the full "Your commands" screen either way.
 */
data class CustomCommand(
    val id: String,
    val phrase: String,
    val label: String,
    val kind: CustomCommandKind,
    val packageName: String?,
    val deepLinkUri: String?,
    val systemAction: String? = null,
    val visibleOnHome: Boolean = true,
)
