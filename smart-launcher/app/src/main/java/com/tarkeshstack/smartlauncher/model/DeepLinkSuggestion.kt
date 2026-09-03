package com.tarkeshstack.smartlauncher.model

/** Marks a spot in a deep-link template that still needs a real keyword typed in before
 *  it will actually work — shared so any screen can tell whether a link is ready to run. */
const val DEEP_LINK_PLACEHOLDER = "REPLACE_ME"

/** A publicly documented deep-link pattern for a common app, shown as a starting point
 *  in the command builder — the user swaps [DEEP_LINK_PLACEHOLDER] for what they actually
 *  want. */
data class DeepLinkSuggestion(
    val appLabel: String,
    val description: String,
    val uriTemplate: String,
    val packageName: String?,
)

object DeepLinkSuggestions {
    val all = listOf(
        DeepLinkSuggestion(
            "YouTube", "Search YouTube",
            "https://www.youtube.com/results?search_query=$DEEP_LINK_PLACEHOLDER",
            "com.google.android.youtube",
        ),
        DeepLinkSuggestion(
            "Spotify", "Search Spotify",
            "spotify:search:$DEEP_LINK_PLACEHOLDER",
            "com.spotify.music",
        ),
        DeepLinkSuggestion(
            "Amazon", "Search Amazon",
            "https://www.amazon.com/s?k=$DEEP_LINK_PLACEHOLDER",
            "com.amazon.mShop.android.shopping",
        ),
        DeepLinkSuggestion(
            "Instagram", "Open a profile",
            "instagram://user?username=$DEEP_LINK_PLACEHOLDER",
            "com.instagram.android",
        ),
        DeepLinkSuggestion(
            "X (Twitter)", "Open a profile",
            "twitter://user?screen_name=$DEEP_LINK_PLACEHOLDER",
            "com.twitter.android",
        ),
        DeepLinkSuggestion(
            "Telegram", "Open a chat or channel",
            "tg://resolve?domain=$DEEP_LINK_PLACEHOLDER",
            "org.telegram.messenger",
        ),
        DeepLinkSuggestion(
            "Netflix", "Open a title page",
            "https://www.netflix.com/title/$DEEP_LINK_PLACEHOLDER",
            "com.netflix.mediaclient",
        ),
        DeepLinkSuggestion(
            "Google Maps", "Search a place",
            "geo:0,0?q=$DEEP_LINK_PLACEHOLDER",
            "com.google.android.apps.maps",
        ),
        DeepLinkSuggestion(
            "Play Store", "Open an app's store listing",
            "market://details?id=$DEEP_LINK_PLACEHOLDER",
            null,
        ),
        DeepLinkSuggestion(
            "Any website", "Open a web page",
            "https://$DEEP_LINK_PLACEHOLDER",
            null,
        ),
    )
}
