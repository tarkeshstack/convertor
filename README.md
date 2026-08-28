# Wordly — Mobile App

Wordly is packaged as a native Android app using [Capacitor](https://capacitorjs.com/), wrapping the existing web app (`www/index.html`) in a native WebView shell.

## Project layout

- `www/index.html` — generated from the live site by `scripts/sync-wordly.py` (see below) — do not hand-edit
- `capacitor.config.json` — Capacitor app config (app id `com.wordly.app`, app name `Wordly`)
- `android/` — generated native Android Studio project
- `resources/`, `assets-src/` — source icon/splash images used to generate the Android launcher icons and splash screens
- `scripts/gen-icon.js` — regenerates the source icon/splash PNGs from SVG (uses `sharp`, install it as a dev dependency if you need to re-run this)
- `scripts/sync-wordly.py` — pulls the latest content from a checkout of [tarkeshstack/wordly](https://github.com/tarkeshstack/wordly) into `www/`, wrapping it with the native bridge and the speech-plugin shim (see below)

## Pulling in the latest site content

```bash
git clone --depth 1 https://github.com/tarkeshstack/wordly /tmp/wordly
python3 scripts/sync-wordly.py /tmp/wordly
npx cap sync android
```

This re-copies `manifest.json`/`sw.js`/`icon-*.png`, injects the postMessage speech shim into the embedded tool iframes, and re-appends the native bridge script — run it instead of hand-patching `www/index.html`.

## Building the app

Requires Node.js, the Android SDK (via Android Studio), and a JDK 17+.

```bash
npm install
npx cap sync android      # copy web assets + plugins into the native project
npx cap open android       # opens the project in Android Studio
```

From Android Studio, run the app on a device/emulator, or build a signed release via **Build > Generate Signed Bundle / APK**.

To build a debug APK from the command line instead:

```bash
cd android
./gradlew assembleDebug
# output: android/app/build/outputs/apk/debug/app-debug.apk
```

A GitHub Actions workflow (`.github/workflows/build-apk.yml`) also builds the debug APK on every push and publishes it as a downloadable GitHub Release.

## Regenerating icons/splash screens

```bash
npm install --no-save sharp @capacitor/assets
node scripts/gen-icon.js
npx capacitor-assets generate --android
```

## Native features wired up

- Hardware back button: closes whichever full-screen tool overlay is open (onboarding tour, Quick Type, Image Translator, SpeakEasy, Writing Practice); exits the app only from the home screen.
- Status bar color/style matches the app's light background.
- Layout: `android.adjustMarginsForEdgeToEdge: "force"` in `capacitor.config.json` makes Capacitor add margins to the WebView matching the system bars' insets, so content stays within the usable screen area on every Android version (targetSdk 35+ enforces edge-to-edge and Android 16 removes the manifest opt-out entirely, so this has to be handled via insets rather than opted out of).
- Speaker (word pronunciation) and mic (SpeakEasy voice input): Android's WebView implements neither `window.speechSynthesis` nor `window.SpeechRecognition`, so these are backed by the native `@capacitor-community/text-to-speech` and `@capacitor-community/speech-recognition` plugins. The main page uses them directly; the SpeakEasy/Writing Practice tools run in sandboxed `blob:` iframes and reach them via a small `postMessage` relay (see `scripts/sync-wordly.py`).

All four of these plugins (`App`, `StatusBar`, `TextToSpeech`, `SpeechRecognition`) are reached via `window.Capacitor.Plugins.<Name>`, which Capacitor Android auto-injects for every plugin registered in `capacitor.settings.gradle` — no extra `<script>` tags needed. (An earlier version of this bridge tried to load each plugin's own `dist/plugin.js` UMD build via `<script src>`; those reference a `capacitorExports` global that this runtime never defines, so every one of those loads silently threw and left all four plugins non-functional — including the back button and status bar, not just the two speech ones.)
