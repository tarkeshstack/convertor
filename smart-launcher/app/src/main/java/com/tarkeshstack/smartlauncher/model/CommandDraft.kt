package com.tarkeshstack.smartlauncher.model

/** In-progress state for the add/edit-command form, hoisted above the command screens so
 *  it survives navigating away to "Get a link" (and the in-app browser beyond that) and
 *  back, instead of resetting whatever was already typed or chosen. A non-null [editingId]
 *  means saving replaces that existing command rather than creating a new one.
 *
 *  A command is exactly one of: a deep link ([deepLinkUri]/[deepLinkPackage]) or a system
 *  shortcut ([systemAction]/[systemActionLabel]) — picking one clears the other. */
data class CommandDraft(
    val editingId: String? = null,
    val phrase: String = "",
    val deepLinkUri: String = "",
    val deepLinkPackage: String = "",
    val placeholderValue: String = "",
    val justCaptured: Boolean = false,
    val systemAction: String = "",
    val systemActionLabel: String = "",
)
