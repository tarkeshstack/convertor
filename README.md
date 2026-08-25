# Writing Practice — Mobile App

Writing Practice is packaged as a native Android app using [Capacitor](https://capacitorjs.com/), wrapping the app's web export (`www/`) in a native WebView shell.

The app itself is an Expo/React Native app (built with a Skia-based writing pad, handwriting-shape matching, and per-language scoring) that also builds a static web bundle via `npx expo export --platform web`; that bundle is what's wrapped here for Android via Capacitor.

## Project layout

- `www/` — the exported web build of the Writing Practice app (unchanged UI/logic)
- `capacitor.config.json` — Capacitor app config (app id `com.writingpractice.app`, app name `Writing Practice`)
- `android/` — generated native Android Studio project
- `resources/`, `assets-src/` — source icon/splash images used to generate the Android launcher icons and splash screens
- `scripts/gen-icon.js` — regenerates the source icon/splash PNGs from SVG (uses `sharp`, install it as a dev dependency if you need to re-run this)

## Updating the wrapped web build

The `www/` folder is a build artifact, not something to hand-edit. To pull in the latest app changes:

```bash
# from the app's own repo (e.g. spokenenglish)
npx expo export --platform web

# copy the export into this repo, then re-sync
rm -rf www && cp -r <app-repo>/dist www
# the writing pad renders via Skia's web/WASM backend (CanvasKit); the wasm
# binary isn't copied by Metro automatically, so copy it in manually:
cp node_modules/@shopify/react-native-skia/node_modules/canvaskit-wasm/bin/full/canvaskit.wasm \
   www/_expo/static/js/web/canvaskit.wasm
npx cap sync android
```

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

## Regenerating icons/splash screens

```bash
npm install --no-save sharp @capacitor/assets
node scripts/gen-icon.js
npx capacitor-assets generate --android
```

## Known limitation

The writing pad's canvas rendering uses Skia's web backend (CanvasKit/WASM) inside the WebView, rather than a truly native Skia binding. It was verified end-to-end in a desktop browser (draw → visual ink → shape match → score), but hasn't been verified inside the actual Android WebView on a device/emulator - if drawing doesn't render there, it's almost certainly the same "wasm file needs to sit next to the JS bundle with the right extension" concern described above, now inside `android/app/src/main/assets/public/`.
