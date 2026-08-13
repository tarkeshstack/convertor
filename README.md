# Wordly — Mobile App

Wordly is packaged as a native Android app using [Capacitor](https://capacitorjs.com/), wrapping the existing web app (`www/index.html`) in a native WebView shell.

## Project layout

- `www/index.html` — the web app (unchanged UI/logic, plus a small native bridge script at the bottom for the hardware back button and status bar theming)
- `capacitor.config.json` — Capacitor app config (app id `com.wordly.app`, app name `Wordly`)
- `android/` — generated native Android Studio project
- `resources/`, `assets-src/` — source icon/splash images used to generate the Android launcher icons and splash screens
- `scripts/gen-icon.js` — regenerates the source icon/splash PNGs from SVG (uses `sharp`, install it as a dev dependency if you need to re-run this)

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

- Hardware back button: exits the app (Wordly is a single-screen app, so there's no in-app navigation stack to unwind).
- Status bar color/style matches the app's light background.
