package com.tarkeshstack.smartlauncher.command

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.tarkeshstack.smartlauncher.data.ContactsRepository
import com.tarkeshstack.smartlauncher.data.InstalledAppsRepository
import com.tarkeshstack.smartlauncher.model.ActionType
import com.tarkeshstack.smartlauncher.model.CustomCommand
import com.tarkeshstack.smartlauncher.model.CustomCommandKind
import com.tarkeshstack.smartlauncher.model.ParsedCommand
import java.net.URLEncoder

sealed class ExecutionResult {
    data object Launched : ExecutionResult()
    data class AppNotInstalled(val appLabel: String) : ExecutionResult()
    data class NeedsContactsPermission(val retryText: String) : ExecutionResult()
    data class ContactNotFound(val name: String) : ExecutionResult()
    data class Failed(val reason: String) : ExecutionResult()
    /** No known search deep link for [appName]; caller should open the app by name instead. */
    data class UnknownAppSearch(val appName: String, val query: String) : ExecutionResult()
}

/**
 * Turns a [ParsedCommand] into a real Android [Intent] against an already-installed
 * app and starts it. This app never stores or types credentials into the target app —
 * it only launches it (optionally with a deep link carrying non-secret data such as a
 * destination address or a search query). Whatever session the target app already has
 * saved on the device is what the user lands in.
 */
class ActionExecutor(
    private val context: Context,
    private val apps: InstalledAppsRepository,
    private val contacts: ContactsRepository,
) {

    private companion object {
        const val PKG_UBER = "com.ubercab"
        const val PKG_OLA = "com.olacabs.customer"
        const val PKG_WHATSAPP = "com.whatsapp"
        const val PKG_MAPS = "com.google.android.apps.maps"
        const val PKG_YOUTUBE = "com.google.android.youtube"
        const val PKG_SPOTIFY = "com.spotify.music"
        const val PKG_AMAZON = "com.amazon.mShop.android.shopping"
        const val PKG_GMAIL = "com.google.android.gm"
    }

    suspend fun execute(command: ParsedCommand): ExecutionResult = when (command.action) {
        ActionType.RIDE_BOOK -> bookRide(command.target)
        ActionType.CALL -> call(command.target.orEmpty())
        ActionType.MESSAGE -> message(command.target.orEmpty(), command.extra)
        ActionType.NAVIGATE -> navigate(command.target.orEmpty())
        ActionType.PLAY_MUSIC -> playOnSpotify(command.target.orEmpty())
        ActionType.PLAY_VIDEO -> playOnYouTube(command.target.orEmpty())
        ActionType.SEARCH_WEB -> searchWeb(command.target.orEmpty())
        ActionType.EMAIL -> email(command.target.orEmpty(), command.extra)
        ActionType.SHOP_SEARCH -> shopOnAmazon(command.target.orEmpty())
        ActionType.SEARCH_IN_APP -> searchInApp(command.target.orEmpty(), command.extra.orEmpty())
        // CUSTOM is resolved and run directly via runCustomCommand() by the caller,
        // which has the CustomCommand list this executor doesn't hold; unreachable here.
        ActionType.CUSTOM, ActionType.OPEN_APP, ActionType.NONE -> ExecutionResult.Failed("Nothing to do")
    }

    fun openApp(packageName: String): ExecutionResult {
        val intent = apps.launchIntentFor(packageName) ?: return ExecutionResult.Failed("Can't open that app")
        start(intent)
        return ExecutionResult.Launched
    }

    fun runCustomCommand(command: CustomCommand): ExecutionResult = when (command.kind) {
        CustomCommandKind.OPEN_APP -> {
            val pkg = command.packageName
            if (pkg.isNullOrBlank()) ExecutionResult.Failed("This command has no app configured")
            else openApp(pkg)
        }
        CustomCommandKind.DEEP_LINK -> {
            val uri = command.deepLinkUri
            if (uri.isNullOrBlank()) {
                ExecutionResult.Failed("This command has no deep link configured")
            } else if (!command.packageName.isNullOrBlank() && !apps.isInstalled(command.packageName)) {
                ExecutionResult.AppNotInstalled(command.label)
            } else {
                viewDeepLink(uri, command.packageName)
            }
        }
    }

    private fun bookRide(destination: String?): ExecutionResult {
        val pkg = when {
            apps.isInstalled(PKG_UBER) -> PKG_UBER
            apps.isInstalled(PKG_OLA) -> PKG_OLA
            else -> return ExecutionResult.AppNotInstalled("Uber")
        }
        val uri = buildString {
            append("https://m.uber.com/ul/?action=setPickup&pickup=my_location")
            if (!destination.isNullOrBlank()) {
                append("&dropoff[formatted_address]=").append(encode(destination))
            }
        }
        return viewDeepLink(uri, pkg)
    }

    private suspend fun call(who: String): ExecutionResult {
        if (who.isBlank()) return ExecutionResult.Failed("Who should I call?")
        val number = phoneNumberFor(who) ?: return contactResolutionFailure(who)
        // ACTION_DIAL opens the dialer pre-filled; it does not require the CALL_PHONE
        // permission and still lets the user confirm with one tap.
        start(Intent(Intent.ACTION_DIAL, "tel:${Uri.encode(number)}".toUri()))
        return ExecutionResult.Launched
    }

    private suspend fun message(who: String, body: String?): ExecutionResult {
        if (who.isBlank()) return ExecutionResult.Failed("Who should I message?")
        val number = phoneNumberFor(who) ?: return contactResolutionFailure(who)
        val e164 = number.filter { it.isDigit() || it == '+' }
        if (apps.isInstalled(PKG_WHATSAPP)) {
            val uri = buildString {
                append("https://wa.me/").append(e164)
                if (!body.isNullOrBlank()) append("?text=").append(encode(body))
            }
            return viewDeepLink(uri, PKG_WHATSAPP)
        }
        // Fall back to SMS if WhatsApp isn't installed.
        val smsUri = "smsto:$e164".toUri()
        val intent = Intent(Intent.ACTION_SENDTO, smsUri)
        if (!body.isNullOrBlank()) intent.putExtra("sms_body", body)
        start(intent)
        return ExecutionResult.Launched
    }

    private fun navigate(destination: String): ExecutionResult {
        if (destination.isBlank()) return ExecutionResult.Failed("Navigate where?")
        if (!apps.isInstalled(PKG_MAPS)) return ExecutionResult.AppNotInstalled("Google Maps")
        val intent = Intent(Intent.ACTION_VIEW, "google.navigation:q=${encode(destination)}".toUri())
        intent.setPackage(PKG_MAPS)
        start(intent)
        return ExecutionResult.Launched
    }

    private fun playOnSpotify(query: String): ExecutionResult {
        if (!apps.isInstalled(PKG_SPOTIFY)) return ExecutionResult.AppNotInstalled("Spotify")
        val intent = Intent(Intent.ACTION_VIEW, "spotify:search:${encode(query)}".toUri())
        intent.setPackage(PKG_SPOTIFY)
        start(intent)
        return ExecutionResult.Launched
    }

    private fun playOnYouTube(query: String): ExecutionResult {
        if (!apps.isInstalled(PKG_YOUTUBE)) return ExecutionResult.AppNotInstalled("YouTube")
        return viewDeepLink("https://www.youtube.com/results?search_query=${encode(query)}", PKG_YOUTUBE)
    }

    private fun shopOnAmazon(query: String): ExecutionResult {
        if (!apps.isInstalled(PKG_AMAZON)) return ExecutionResult.AppNotInstalled("Amazon")
        return viewDeepLink("https://www.amazon.com/s?k=${encode(query)}", PKG_AMAZON)
    }

    /** Only a handful of apps expose a public search deep link; anything else falls back
     *  to just opening the named app, since we can't construct a working search URI blind. */
    private fun searchInApp(query: String, appNameRaw: String): ExecutionResult {
        if (query.isBlank()) return ExecutionResult.Failed("Search for what?")
        val appName = appNameRaw.trim().lowercase()
        return when {
            appName.contains("amazon") -> shopOnAmazon(query)
            appName.contains("youtube") -> playOnYouTube(query)
            appName.contains("spotify") -> playOnSpotify(query)
            appName.isBlank() -> ExecutionResult.Failed("Search for \"$query\" in which app?")
            else -> ExecutionResult.UnknownAppSearch(appNameRaw.trim(), query)
        }
    }

    private fun email(to: String, subject: String?): ExecutionResult {
        val intent = Intent(Intent.ACTION_SENDTO, "mailto:${Uri.encode(to)}".toUri())
        if (!subject.isNullOrBlank()) intent.putExtra(Intent.EXTRA_SUBJECT, subject)
        if (apps.isInstalled(PKG_GMAIL)) intent.setPackage(PKG_GMAIL)
        start(intent)
        return ExecutionResult.Launched
    }

    private fun searchWeb(query: String): ExecutionResult {
        if (query.isBlank()) return ExecutionResult.Failed("Search for what?")
        start(Intent(Intent.ACTION_VIEW, "https://www.google.com/search?q=${encode(query)}".toUri()))
        return ExecutionResult.Launched
    }

    // -- helpers --

    private suspend fun phoneNumberFor(who: String): String? {
        if (looksLikePhoneNumber(who)) return who
        return contacts.findPhoneNumberByName(who)
    }

    private fun contactResolutionFailure(who: String): ExecutionResult =
        if (!contacts.hasPermission()) ExecutionResult.NeedsContactsPermission(who)
        else ExecutionResult.ContactNotFound(who)

    private fun looksLikePhoneNumber(text: String): Boolean =
        text.count { it.isDigit() } >= 6 && text.all { it.isDigit() || it in "+-() " }

    private fun viewDeepLink(uri: String, targetPackage: String?): ExecutionResult {
        val intent = Intent(Intent.ACTION_VIEW, uri.toUri())
        if (!targetPackage.isNullOrBlank()) intent.setPackage(targetPackage)
        start(intent)
        return ExecutionResult.Launched
    }

    private fun start(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
