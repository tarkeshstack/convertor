package com.tarkeshstack.smartlauncher.model

/** Marks a spot in a deep-link template that still needs a real keyword typed in before
 *  it will actually work — shared so any screen can tell whether a link is ready to run. */
const val DEEP_LINK_PLACEHOLDER = "REPLACE_ME"

/** A publicly documented deep-link pattern for a common app, shown as a starting point
 *  in the command builder — the user swaps [DEEP_LINK_PLACEHOLDER] for what they actually
 *  want. [keywordHint] is a concrete example of what to type there, shown alongside the
 *  field so "what goes here?" (e.g. your own Instagram handle, or someone else's?) has an
 *  answer right next to it instead of being left to guess. */
data class DeepLinkSuggestion(
    val appLabel: String,
    val description: String,
    val uriTemplate: String,
    val packageName: String?,
    val keywordHint: String,
)

object DeepLinkSuggestions {
    val all = listOf(
        DeepLinkSuggestion(
            "YouTube", "Search YouTube",
            "https://www.youtube.com/results?search_query=$DEEP_LINK_PLACEHOLDER",
            "com.google.android.youtube",
            "e.g. a song, video title, or topic to search for",
        ),
        DeepLinkSuggestion(
            "Spotify", "Search Spotify",
            "spotify:search:$DEEP_LINK_PLACEHOLDER",
            "com.spotify.music",
            "e.g. a song, artist, or playlist to search for",
        ),
        DeepLinkSuggestion(
            "Amazon", "Search Amazon",
            "https://www.amazon.com/s?k=$DEEP_LINK_PLACEHOLDER",
            "com.amazon.mShop.android.shopping",
            "e.g. a product to search for",
        ),
        DeepLinkSuggestion(
            "Instagram", "Open a profile",
            "instagram://user?username=$DEEP_LINK_PLACEHOLDER",
            "com.instagram.android",
            "e.g. a username — your own, or someone else's",
        ),
        DeepLinkSuggestion(
            "X (Twitter)", "Open a profile",
            "twitter://user?screen_name=$DEEP_LINK_PLACEHOLDER",
            "com.twitter.android",
            "e.g. a username — your own, or someone else's",
        ),
        DeepLinkSuggestion(
            "Telegram", "Open a chat or channel",
            "tg://resolve?domain=$DEEP_LINK_PLACEHOLDER",
            "org.telegram.messenger",
            "e.g. a username, or a public channel's name",
        ),
        DeepLinkSuggestion(
            "Netflix", "Open a title page",
            "https://www.netflix.com/title/$DEEP_LINK_PLACEHOLDER",
            "com.netflix.mediaclient",
            "e.g. the numeric ID from that title's Netflix URL",
        ),
        DeepLinkSuggestion(
            "Google Maps", "Search a place",
            "geo:0,0?q=$DEEP_LINK_PLACEHOLDER",
            "com.google.android.apps.maps",
            "e.g. an address or a place name",
        ),
        DeepLinkSuggestion(
            "Play Store", "Open an app's store listing",
            "market://details?id=$DEEP_LINK_PLACEHOLDER",
            null,
            "e.g. the app's package name, like com.spotify.music",
        ),
        DeepLinkSuggestion(
            "Any website", "Open a web page",
            "https://$DEEP_LINK_PLACEHOLDER",
            null,
            "e.g. a domain, like example.com",
        ),
    )
}
