# Smart Launcher

An Android app that searches every app installed on your phone and either opens it,
or — for a handful of common apps — parses a typed command and jumps straight to the
right action inside that app.

Type `uber` → it opens Uber. Type `book uber to airport` → it opens Uber with the
drop-off pre-filled. Type `call mom` → it looks "mom" up in your contacts and opens
the dialer with her number ready. All of this runs the real, already-installed app —
this project never stores or types your passwords into anything. Whatever session
Uber (or WhatsApp, or Maps) already has saved on your phone is what you land in,
because that's the target app's own data, not something this app manages.

## What it can do today

| You type | What happens |
|---|---|
| `uber` / any app name | Fuzzy-searches your installed apps as you type; tap or hit Enter to launch |
| `book uber to <place>` / `uber to <place>` | Opens Uber (or Ola) with pickup = current location, drop-off pre-filled |
| `call <name or number>` | Resolves a contact name to a phone number (needs Contacts permission) and opens the dialer pre-filled |
| `message <name or number> [saying <text>]` / `text ...` / `whatsapp ...` | Opens WhatsApp on that chat, pre-filled with the message; falls back to SMS if WhatsApp isn't installed |
| `navigate to <place>` / `directions to <place>` / `go to <place>` | Opens Google Maps turn-by-turn navigation |
| `play <song/artist>` / `play <query> on spotify` | Opens YouTube or Spotify search/playback |
| `search <query>` | Opens a web search in the browser |
| `email <address> [about <subject>]` | Opens Gmail (or any mail app) with a draft |
| `order <item>` / `buy <item>` | Opens Amazon search |

Every command above only fires if the target app is actually installed; if it isn't,
you get a clear "X isn't installed" message instead of a crash or a silent no-op.

## Conversation mode

Every turn — what you typed or said, and what the app did about it — is logged as a
running transcript on the search screen ("Opened Amazon", "Booked a ride to airport",
"Uber isn't installed", ...). That's on by default and needs nothing extra.

Tap the speaker icon in the header to turn on spoken replies too: the app reads its
response aloud via Android's text-to-speech engine, and — only for a turn you
started by voice, never by typing — automatically re-arms the mic afterward so you
can keep going without tapping it again each time. It's this app's own
speech-to-text feeding straight back into another turn, not literally another app's
voice-search button (see "Why 'audio search inside another app' works this way"
below).

### Why "audio search inside another app" works this way

There's no documented, public way for one Android app to open another app's own
voice-search screen directly — not for YouTube, Spotify, or (as far as I could find)
any other major app. The only way to fake it would be targeting that app's private,
undocumented internal activity name, which isn't published, isn't guaranteed to
exist across versions, and can crash — not something this app does. What it does
instead, and what actually delivers "say it once, land in results": this app's own
mic transcribes your speech, then opens the target app already acting on it (e.g.
saying "play lofi beats on youtube" opens YouTube already searching that). Combined
with conversation mode's auto-relisten, that's a full spoken back-and-forth without
ever needing the target app's own mic icon.

### What reading another app's on-screen content would take

This app cannot see or read what's on screen inside another app (e.g. "50 products
found" after opening Amazon) — Android sandboxes that by design. The only way around
it is the Accessibility Service, the same heavier mechanism (manual Settings grant,
breaks when an app's UI changes) that this project has deliberately stayed out of
for tap recording. It's left out here for the same reason; the conversation log
above covers what this app itself did, not what's rendered inside the app it opened.

`find <query> in <app>` / `search <query> on <app>` — real search deep link for
Amazon, YouTube, and Spotify; any other app opens with a message telling you to
search inside it manually, since there's no way to construct a working search URI
for an app we don't know.

## Voice input

Tap the mic icon to speak a search or command instead of typing it — it's
transcribed by Android's system speech service (the same one behind Google's voice
typing) and run immediately, exactly as if you'd typed it and hit Enter. Needs the
`RECORD_AUDIO` permission, requested the first time you tap the mic; no audio is
stored or sent anywhere by this app itself.

## Custom commands

Tap the gear icon to open the command manager and define your own trigger phrases.
Each one is either:

- **Open an app** — pick any installed app; typing/saying the phrase just launches it.
- **Deep link / URI** — give a URI (e.g. `myapp://some/screen`) and, optionally, the
  target app's package name to open it in specifically. Saying/typing the phrase
  fires `ACTION_VIEW` on that URI.

Custom commands are checked before the built-in parser, so your phrase wins even if
it overlaps with a recognized pattern. They're stored locally in a JSON file in the
app's private storage (`data/CustomCommandRepository.kt`) — nothing leaves the
device.

### Finding a deep link without knowing the syntax

The "Deep link / URI" form has four ways to fill in the URI field without hand-writing
one — none of them require the target app to support anything in particular:

- **Share it in** — open the app you want, find the exact item/screen, tap its Share
  button, and pick "Smart Launcher" from the share sheet. This app registers as a
  share target (see the `SEND`/`text/plain` intent-filter in the manifest and
  `capture/ShareIntentParser.kt`); it pulls the link out of the shared text, and —
  when the sharing app supplies it — the source app's package too, then drops you
  straight into the command form with both pre-filled. Only works for apps that
  offer *some* Share/Copy-Link action.
- **Browse for a link** — a built-in mini browser (`ui/BrowseForLinkScreen.kt`, needs
  the `INTERNET` permission, used only when you're actively in this screen) for when
  an app has no Share option at all: navigate to the app's *website* version of what
  you want and tap "Use this page's link." Since most apps' websites use the same
  URL as their App Link, this often opens straight back into the native app too.
- **Paste from clipboard** — for apps whose "Copy Link" doesn't go through Android's
  share sheet: copy the link there, then tap this button here.
- **Suggestions** — a curated list of publicly documented link patterns for common
  apps (YouTube/Spotify/Amazon search, Instagram/X profiles, Telegram, Netflix,
  Google Maps, the Play Store).

Suggestions and captured links can carry a `REPLACE_ME` placeholder for the part
that's specific to what you want. You never edit that into the link text yourself —
a separate "Value to fill in" field appears, you type the plain value there (e.g.
`lofi beats`), and the app substitutes it into the real link before saving. The link
field stays showing the template; Save is disabled until the placeholder's been
filled in.

There's no step-recorder here: this app can't record and replay taps inside
other apps (that needs Android's Accessibility Service, a much heavier and more
fragile mechanism), so a custom command can only open an app or fire a deep link,
not drive a multi-screen flow.

## Why it can't "log you into" an app

Android sandboxes every app's data from every other app. There is no supported way
for one app to read or inject another app's saved login/session — and if there were,
it would be a serious security hole. So this launcher does the next best thing: it
opens the real target app (optionally with a deep link carrying non-secret details
like a destination or search text), and that app shows whatever state it's already
in on your device — logged in, if you're already logged in there.

## Project structure

- `command/CommandParser.kt` — offline regex-based parser that turns typed text into
  a `ParsedCommand` (no network call, no on-device model).
- `command/ActionExecutor.kt` — turns a `ParsedCommand` into a real `Intent` (deep
  link or plain launch) against an installed package.
- `data/InstalledAppsRepository.kt` — lists launchable apps via `PackageManager`.
- `data/ContactsRepository.kt` — optional, permission-gated name → phone number
  lookup for `call`/`message`.
- `data/CustomCommandRepository.kt` — loads/saves user-defined commands as JSON.
- `capture/ShareIntentParser.kt` — pulls a link out of an incoming Share intent.
- `ui/BrowseForLinkScreen.kt` — in-app mini browser for capturing a link from any
  app's website when it has no Share option.
- `voice/VoiceInputController.kt` — wraps Android's `SpeechRecognizer` for the mic
  button.
- `voice/VoiceOutputController.kt` — wraps Android's `TextToSpeech` for conversation
  mode's spoken replies.
- `model/Conversation.kt` — the transcript entry and speech-request types.
- `ui/SearchScreen.kt` — header, search box + mic, quick-action card, conversation
  transcript, app list.
- `ui/CommandManagerScreen.kt` — add/list/delete custom commands.
- `ui/AppPickerDialog.kt` — searchable modal app list, used for "Open an app" and the
  deep-link command's target-app field.

## Requirements

- Android 8.0 (API 26) or newer.
- Kotlin + Jetpack Compose, single-module Gradle project — open it directly in
  Android Studio (Koala or newer) and hit Run, or `./gradlew assembleDebug` from a
  machine with the Android SDK installed.

> This project was scaffolded and written in an environment without the Android SDK
> or a device/emulator available, so the app has **not** been built or run yet.
> Please build it in Android Studio (or `./gradlew assembleDebug` with the SDK on
> your `PATH`/`local.properties`) before relying on it — if anything doesn't
> compile, it's most likely a small import/API mismatch, not a structural issue.

This app lives at `smart-launcher/` inside the `convertor` repo, alongside the
unrelated Measurely (unit converter) app — they're independent Gradle/Capacitor
projects sharing one repo. A GitHub Actions workflow
(`.github/workflows/smart-launcher-build.yml`, scoped to changes under
`smart-launcher/`) runs `./gradlew assembleDebug` on every push and uploads the
debug APK as a run artifact, since GitHub's runners have full access to Google's
Maven repo where the sandbox that scaffolded this project didn't.

## Permissions

- `QUERY_ALL_PACKAGES` — required to list every installed app (Android 11+ package
  visibility).
- `READ_CONTACTS` — requested at runtime, only when you type `call <name>` or
  `message <name>` with a name rather than a number. Denying it just means you'll
  need to type the phone number directly.
- `RECORD_AUDIO` — requested at runtime, only when you tap the mic icon.
- `INTERNET` — install-time only, used exclusively by the "Browse for a link" mini
  browser in the command builder; the app makes no other network calls.

Outside the in-app browser, this app does not use the internet itself; every deep
link is handed off to the target app, which makes its own network calls.
