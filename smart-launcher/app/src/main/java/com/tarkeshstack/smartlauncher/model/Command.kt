package com.tarkeshstack.smartlauncher.model

/** What the typed text is asking for, once parsed. */
enum class ActionType {
    RIDE_BOOK,
    CALL,
    MESSAGE,
    NAVIGATE,
    PLAY_MUSIC,
    PLAY_VIDEO,
    SEARCH_WEB,
    EMAIL,
    SHOP_SEARCH,
    SEARCH_IN_APP,
    CUSTOM,
    OPEN_APP,
    NONE,
}

/**
 * A recognized command, e.g. "book uber to airport" ->
 * ParsedCommand(RIDE_BOOK, target = "airport").
 *
 * [target] is the primary object of the command (a destination, a contact/number,
 * a search query, ...). [extra] holds a secondary detail when the grammar allows one,
 * e.g. a message body for MESSAGE or a subject for EMAIL.
 */
data class ParsedCommand(
    val action: ActionType,
    val target: String?,
    val extra: String? = null,
    val label: String,
)
