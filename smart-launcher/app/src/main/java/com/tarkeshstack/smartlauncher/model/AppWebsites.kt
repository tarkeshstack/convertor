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
        "com.olacabs.customer" to "https://www.olacabs.com",
        "com.google.android.gm" to "https://mail.google.com",
        "com.microsoft.office.outlook" to "https://outlook.com",
        "com.dropbox.android" to "https://www.dropbox.com",
        "com.google.android.apps.docs" to "https://drive.google.com",
        "com.google.android.apps.nbu.paisa.user" to "https://pay.google.com",
        "com.truecaller" to "https://www.truecaller.com",
        "us.zoom.videomeetings" to "https://zoom.us",
        "com.microsoft.teams" to "https://www.microsoft.com/microsoft-teams",
        "com.discord" to "https://discord.com",
        "tv.twitch.android.app" to "https://www.twitch.tv",
        "com.quora.android" to "https://www.quora.com",
        "com.ebay.mobile" to "https://www.ebay.com",
        "com.walmart.android" to "https://www.walmart.com",
        // Common Indian apps.
        "com.myairtelapp" to "https://www.airtel.in",
        "com.jio.myjio" to "https://www.jio.com",
        "net.one97.paytm" to "https://paytm.com",
        "com.phonepe.app" to "https://www.phonepe.com",
        "com.flipkart.android" to "https://www.flipkart.com",
        "com.myntra.android" to "https://www.myntra.com",
        "in.swiggy.android" to "https://www.swiggy.com",
        "com.application.zomato" to "https://www.zomato.com",
        "com.bt.bms" to "https://in.bookmyshow.com",
    )

    /** The curated mapping for a known package, when we have one. */
    fun urlFor(packageName: String?): String? = packageName?.let(byPackage::get)

    /** A real website to open for any app — the curated mapping when we have it,
     *  otherwise a best-guess ".com" domain built from the app's own name, so "Get a
     *  link" always lands on a real site instead of falling back to a web search. */
    fun websiteFor(packageName: String?, label: String): String =
        urlFor(packageName) ?: "https://www.${guessDomain(label)}.com"

    private fun guessDomain(label: String): String =
        label.lowercase().replace(Regex("[^a-z0-9]+"), "")
}
