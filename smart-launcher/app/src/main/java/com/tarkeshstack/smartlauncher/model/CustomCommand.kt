package com.tarkeshstack.smartlauncher.model

/** How a user-defined command's trigger phrase gets executed. */
enum class CustomCommandKind { OPEN_APP, DEEP_LINK }

/**
 * A command the user defined themselves: typing or saying [phrase] runs it.
 *
 * [kind] OPEN_APP just launches [packageName]. DEEP_LINK opens [deepLinkUri] via
 * ACTION_VIEW, optionally restricted to [packageName] so it opens in a specific app
 * rather than whatever the device would otherwise pick for that URI.
 */
data class CustomCommand(
    val id: String,
    val phrase: String,
    val label: String,
    val kind: CustomCommandKind,
    val packageName: String?,
    val deepLinkUri: String?,
)
