# Settle In UK — Mobile App

Settle In UK is packaged as a native Android app using [Capacitor](https://capacitorjs.com/), wrapping the existing web guide ([tarkeshstack.github.io/onboarding](https://tarkeshstack.github.io/onboarding/), `www/index.html`) in a native WebView shell.

## Project layout

- `www/index.html` — the web app (unchanged UI/logic, plus a small native bridge script at the bottom for the hardware back button and status bar theming)
- `capacitor.config.json` — Capacitor app config (app id `com.tarkeshstack.settleinuk`, app name `Settle In UK`)
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

A GitHub Actions workflow (`.github/workflows/build-apk.yml`) also builds the debug APK on every push and on demand, and uploads it as a downloadable artifact — handy in environments without a local Android SDK.

## Regenerating icons/splash screens

```bash
npm install --no-save sharp @capacitor/assets
node scripts/gen-icon.js
npx capacitor-assets generate --android
```

## Native features wired up

- Hardware back button: steps back through in-page history, and exits from the top of the stack (`www/index.html`, native bridge script).
- Status bar color/style matches the app's navy header.
