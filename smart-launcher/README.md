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
- `ui/SearchScreen.kt` — Jetpack Compose search box + quick-action card + app list.

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

No other permissions are requested. This app does not use the internet itself; every
deep link is handed off to the target app, which makes its own network calls.
