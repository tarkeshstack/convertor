# SpeakEasy — Speech Interpreter (native Android)

A native Android app (Kotlin + Jetpack Compose) that acts as a real-time speech
interpreter. Tap the mic, speak in one language, and the app shows and speaks back the
translation in another — covering English, Hindi, Tamil, Spanish, and French, with
auto-detect for the spoken language. No API key, no account, no server of ours —
translation runs entirely on-device via Google's ML Kit.

This is the native counterpart to the Expo/React Native version in the `spokenenglish`
repo — rebuilt as a plain Android Studio project here so it can be built straight from
source with the Android SDK (no Expo/EAS account, no JS toolchain), matching how other
apps in this repo (e.g. `smart-launcher/`) are built.

## How it works

1. **Pick languages** — "Speak in" (Auto-detect, or a specific one of the five
   languages) and "Translate to" (one of the five). Auto-detect uses the device's own
   default recognition language for speech-to-text (Android's on-device recognizer has
   no public API to identify a spoken language before transcribing it), and on-device
   language identification detects the actual language from the resulting text when
   translating.
2. **Tap the mic** — the app requests `RECORD_AUDIO` permission the first time, then
   starts listening via Android's system `SpeechRecognizer`, set to the chosen source
   language when one is picked. A waveform animates with the mic's input level.
3. **Translate** — the transcript is translated entirely on-device via ML Kit
   Translate. Each language pair's small model downloads once (needs a network
   connection that first time only) and is reused fully offline after that — no API
   key, no account, no per-request cost, and it works the same for one user or a
   million.
4. **Read back** — the translation is spoken aloud automatically via Android's system
   `TextToSpeech`, using a voice in the target language, and can be replayed on demand.
5. **History** — every turn (original text, detected source language, translation,
   target language) is saved on-device as a small JSON file in app-private storage,
   shown on the History tab, newest first. Entries can be replayed or deleted
   individually, or the whole history can be cleared.

## Project layout

```
app/src/main/java/com/tarkeshstack/speakeasy/
  MainActivity.kt                  Permission flow, voice controller wiring, Compose host
  MainViewModel.kt                 UiState (StateFlow), orchestrates mic -> translate -> speak -> save
  model/Models.kt                  Language, InterpretationResult, InterpretationEntry, ...
  voice/VoiceInputController.kt    System SpeechRecognizer wrapper (optional source-language tag)
  voice/VoiceOutputController.kt   System TextToSpeech wrapper (speaks in a given target language)
  interpret/TranslationService.kt  On-device ML Kit translation + language identification
  data/HistoryRepository.kt        JSON-file-backed interpretation history persistence
  ui/InterpreterScreen.kt          Language pickers, mic button, waveform, result card
  ui/HistoryScreen.kt              Interpretation history list
  ui/theme/                        Color / Theme / Type
```

## Building

Requires the Android SDK (via Android Studio) and JDK 17+.

```bash
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

Or open this folder in Android Studio and run on a device/emulator.

CI (`.github/workflows/speakeasy-build.yml`) builds a debug APK on every push to
`main`/`claude/**` that touches this folder, and commits it to
`speakeasy-dist/speakeasy-debug.apk` in the repo so it's directly downloadable without
digging through workflow run artifacts.

## Notes / limitations

- Speech recognition uses Android's system recognizer (the same one behind Google's
  voice typing) — no audio is recorded or uploaded by this app itself.
- "Auto-detect" for the spoken language is a best-effort UX simplification: the
  recognizer itself still transcribes using the device's default language, and
  on-device language identification detects the actual language from that text
  afterward. For guaranteed transcription accuracy in a specific language, pick it
  explicitly instead of Auto-detect.
- Translation itself needs a one-time internet connection per language pair, to
  download that pair's small offline model; after that, it works with no connection at
  all.
- Voice playback of the translation depends on the target language's voice data being
  installed on-device; if it isn't, the app shows the translated text with a note
  instead of silently failing.
