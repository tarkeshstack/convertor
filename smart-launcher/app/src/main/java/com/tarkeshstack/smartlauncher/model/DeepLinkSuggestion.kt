package com.tarkeshstack.smartlauncher.model

/** A publicly documented deep-link pattern for a common app, shown as a starting point
 *  in the command builder — the user swaps REPLACE_ME for what they actually want. */
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
            "https://www.youtube.com/results?search_query=REPLACE_ME",
            "com.google.android.youtube",
        ),
        DeepLinkSuggestion(
            "Spotify", "Search Spotify",
            "spotify:search:REPLACE_ME",
            "com.spotify.music",
        ),
        DeepLinkSuggestion(
            "Amazon", "Search Amazon",
            "https://www.amazon.com/s?k=REPLACE_ME",
            "com.amazon.mShop.android.shopping",
        ),
        DeepLinkSuggestion(
            "Instagram", "Open a profile",
            "instagram://user?username=REPLACE_ME",
            "com.instagram.android",
        ),
        DeepLinkSuggestion(
            "X (Twitter)", "Open a profile",
            "twitter://user?screen_name=REPLACE_ME",
            "com.twitter.android",
        ),
        DeepLinkSuggestion(
            "Telegram", "Open a chat or channel",
            "tg://resolve?domain=REPLACE_ME",
            "org.telegram.messenger",
        ),
        DeepLinkSuggestion(
            "Netflix", "Open a title page",
            "https://www.netflix.com/title/REPLACE_ME",
            "com.netflix.mediaclient",
        ),
        DeepLinkSuggestion(
            "Google Maps", "Search a place",
            "geo:0,0?q=REPLACE_ME",
            "com.google.android.apps.maps",
        ),
        DeepLinkSuggestion(
            "Play Store", "Open an app's store listing",
            "market://details?id=REPLACE_ME",
            null,
        ),
        DeepLinkSuggestion(
            "Any website", "Open a web page",
            "https://REPLACE_ME",
            null,
        ),
    )
}
