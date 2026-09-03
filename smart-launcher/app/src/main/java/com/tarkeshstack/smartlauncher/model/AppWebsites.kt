package com.tarkeshstack.smartlauncher.model

/** Known apps' website equivalents, so "Get a link" can open the in-app browser
 *  straight to the real site instead of a generic web search when the app is one we
 *  recognize — many of these URLs double as the app's own App Link too. */
object AppWebsites {
    private val byPackage = mapOf(
        "com.google.android.youtube" to "https://www.youtube.com",
        "com.spotify.music" to "https://open.spotify.com",
        "com.amazon.mShop.android.shopping" to "https://www.amazon.com",
        "com.instagram.android" to "https://www.instagram.com",
        "com.twitter.android" to "https://twitter.com",
        "org.telegram.messenger" to "https://web.telegram.org",
        "com.netflix.mediaclient" to "https://www.netflix.com",
        "com.google.android.apps.maps" to "https://maps.google.com",
        "com.whatsapp" to "https://web.whatsapp.com",
        "com.facebook.katana" to "https://www.facebook.com",
        "com.linkedin.android" to "https://www.linkedin.com",
        "com.pinterest" to "https://www.pinterest.com",
        "com.snapchat.android" to "https://www.snapchat.com",
        "com.reddit.frontpage" to "https://www.reddit.com",
        "com.zhiliaoapp.musically" to "https://www.tiktok.com",
        "com.ubercab" to "https://www.uber.com",
        "com.google.android.gm" to "https://mail.google.com",
    )

    fun urlFor(packageName: String?): String? = packageName?.let(byPackage::get)
}
