package com.tarkeshstack.smartlauncher.command

import com.tarkeshstack.smartlauncher.model.ActionType
import com.tarkeshstack.smartlauncher.model.ParsedCommand

/**
 * Turns free-typed text into a [ParsedCommand], entirely offline (plain regex over a
 * small set of English verb patterns — no network call, no on-device model).
 *
 * Order matters: more specific patterns (e.g. "book uber") are tried before generic
 * ones (e.g. "call <name>"), so "call uber" books a ride rather than dialing a
 * contact named "uber". Anything that matches nothing falls through to OPEN_APP,
 * which the caller resolves by fuzzy-matching installed app labels.
 */
object CommandParser {

    private val ride = Regex(
        """^(?:book|get|order|call)\s+(?:an?\s+)?(?:uber|ola|cab|taxi|ride)(?:\s+(?:to|for)\s+(.+))?$""",
        RegexOption.IGNORE_CASE,
    )
    private val rideShort = Regex("""^uber\s+(?:to\s+)?(.+)$""", RegexOption.IGNORE_CASE)

    private val message = Regex(
        """^(?:message|text|whatsapp)\s+(.+?)(?:\s+(?:saying|that says|:)\s+(.+))?$""",
        RegexOption.IGNORE_CASE,
    )

    private val email = Regex(
        """^(?:email|mail)\s+(.+?)(?:\s+(?:about|regarding|subject)\s+(.+))?$""",
        RegexOption.IGNORE_CASE,
    )

    private val shop = Regex("""^(?:order|buy|shop for)\s+(.+)$""", RegexOption.IGNORE_CASE)

    private val navigate = Regex(
        """^(?:navigate to|directions to|go to|take me to|drive to)\s+(.+)$""",
        RegexOption.IGNORE_CASE,
    )

    private val playSpotify = Regex("""^play\s+(.+?)\s+on\s+spotify$""", RegexOption.IGNORE_CASE)
    private val playVideo = Regex(
        """^(?:play|watch)\s+(.+?)(?:\s+on\s+youtube)?$""",
        RegexOption.IGNORE_CASE,
    )

    private val call = Regex("""^(?:call|dial|phone)\s+(.+)$""", RegexOption.IGNORE_CASE)

    private val searchWeb = Regex("""^search(?:\s+for)?\s+(.+)$""", RegexOption.IGNORE_CASE)

    fun parse(rawInput: String): ParsedCommand {
        val text = rawInput.trim()
        if (text.isEmpty()) return ParsedCommand(ActionType.NONE, null, label = "")

        ride.matchEntire(text)?.let {
            val dest = it.groupValues[1].ifBlank { null }
            return ParsedCommand(
                ActionType.RIDE_BOOK,
                dest,
                label = if (dest != null) "Book a ride to $dest" else "Book a ride",
            )
        }
        rideShort.matchEntire(text)?.let {
            val dest = it.groupValues[1].ifBlank { null }
            return ParsedCommand(
                ActionType.RIDE_BOOK,
                dest,
                label = if (dest != null) "Book a ride to $dest" else "Open Uber",
            )
        }

        message.matchEntire(text)?.let {
            val recipient = it.groupValues[1].trim()
            val body = it.groupValues.getOrNull(2)?.ifBlank { null }
            return ParsedCommand(
                ActionType.MESSAGE, recipient, body,
                label = "Message $recipient" + (body?.let { b -> ": \"$b\"" } ?: ""),
            )
        }

        email.matchEntire(text)?.let {
            val recipient = it.groupValues[1].trim()
            val subject = it.groupValues.getOrNull(2)?.ifBlank { null }
            return ParsedCommand(
                ActionType.EMAIL, recipient, subject,
                label = "Email $recipient" + (subject?.let { s -> " about \"$s\"" } ?: ""),
            )
        }

        navigate.matchEntire(text)?.let {
            val dest = it.groupValues[1].trim()
            return ParsedCommand(ActionType.NAVIGATE, dest, label = "Navigate to $dest")
        }

        shop.matchEntire(text)?.let {
            val query = it.groupValues[1].trim()
            return ParsedCommand(ActionType.SHOP_SEARCH, query, label = "Shop for \"$query\" on Amazon")
        }

        playSpotify.matchEntire(text)?.let {
            val query = it.groupValues[1].trim()
            return ParsedCommand(ActionType.PLAY_MUSIC, query, label = "Play \"$query\" on Spotify")
        }
        playVideo.matchEntire(text)?.let {
            val query = it.groupValues[1].trim()
            return ParsedCommand(ActionType.PLAY_VIDEO, query, label = "Play \"$query\" on YouTube")
        }

        call.matchEntire(text)?.let {
            val who = it.groupValues[1].trim()
            return ParsedCommand(ActionType.CALL, who, label = "Call $who")
        }

        searchWeb.matchEntire(text)?.let {
            val query = it.groupValues[1].trim()
            return ParsedCommand(ActionType.SEARCH_WEB, query, label = "Search the web for \"$query\"")
        }

        // No verb recognized: treat the whole string as an app-name / fuzzy search.
        return ParsedCommand(ActionType.OPEN_APP, text, label = "Open \"$text\"")
    }
}
