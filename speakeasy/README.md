# SpeakEasy — Spoken English Practice (native Android)

A native Android app (Kotlin + Jetpack Compose) for practicing spoken
English. Tap the mic, say a sentence, and the app transcribes it, checks
your grammar, suggests a simpler way to phrase it, and reads the corrected
sentence back to you. Every practice turn is saved to a local history.

This is the native counterpart to the Expo/React Native version in the
`spokenenglish` repo — rebuilt as a plain Android Studio project here so it
can be built straight from source with the Android SDK (no Expo/EAS
account, no JS toolchain), matching how other apps in this repo (e.g.
`smart-launcher/`) are built.

## How it works

1. **Tap the mic** — the app requests `RECORD_AUDIO` permission the first
   time, then starts listening via Android's system `SpeechRecognizer`. A
   waveform animates with the mic's input level (`onRmsChanged`) and the
   live partial transcript appears as you speak.
2. **Stop talking (or tap again)** — recognition finalizes the transcript.
3. **Analyze** — the transcript is sent to the free
   [LanguageTool](https://languagetool.org) API for grammar/style checking.
   Filler words ("um", "uh", "you know", …) are stripped locally first. A
   local "simplify" pass also swaps wordy phrases ("in order to" → "to",
   "utilize" → "use", …) and splits very long run-on sentences.
4. **Read back** — the corrected sentence is spoken aloud automatically via
   Android's system `TextToSpeech`, and can be replayed on demand. The
   simplified version can be replayed separately too.
5. **History** — every turn (original, corrected, simplified, number of
   fixes) is saved on-device as a small JSON file in app-private storage,
   shown on the History tab, newest first. Entries can be replayed or
   deleted individually, or the whole history can be cleared.

If the device is offline, the LanguageTool call fails gracefully and the
app falls back to the local filler-word cleanup and simplify pass only,
with a note shown in the UI.

## Project layout

```
app/src/main/java/com/tarkeshstack/speakeasy/
  MainActivity.kt              Permission flow, voice controller wiring, Compose host
  MainViewModel.kt             UiState (StateFlow), orchestrates mic -> analyze -> speak -> save
  model/Models.kt               AnalysisResult, GrammarIssue, ConversationEntry, ...
  voice/VoiceInputController.kt System SpeechRecognizer wrapper (partial results + RMS metering)
  voice/VoiceOutputController.kt System TextToSpeech wrapper
  grammar/GrammarService.kt     Filler-word cleanup + LanguageTool call + simplify pass
  data/HistoryRepository.kt     JSON-file-backed conversation history persistence
  ui/PracticeScreen.kt          Mic button, waveform, live transcript, feedback card
  ui/HistoryScreen.kt           Practice history list
  ui/theme/                     Color / Theme / Type
```

## Building

Requires the Android SDK (via Android Studio) and JDK 17+.

```bash
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

Or open this folder in Android Studio and run on a device/emulator.

CI (`.github/workflows/speakeasy-build.yml`) builds a debug APK on every
push to `main`/`claude/**` that touches this folder, and commits it to
`speakeasy-dist/speakeasy-debug.apk` in the repo so it's directly
downloadable without digging through workflow run artifacts.

## Notes / limitations

- Grammar correction quality depends on the LanguageTool free tier, which
  is rate-limited for heavy use.
- The "simplify" step is a lightweight rule-based pass, not an AI rewrite.
- Speech recognition uses Android's system recognizer (the same one behind
  Google's voice typing) — no audio is recorded or uploaded by this app
  itself.
