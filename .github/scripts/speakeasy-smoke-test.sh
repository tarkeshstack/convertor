#!/usr/bin/env bash
# Installs the freshly built debug APK on the running emulator, launches
# MainActivity, and fails (printing the logcat crash trace) if the app
# doesn't stay running. Run as a single script so shell state (variables,
# the if/fi control flow) persists — the emulator-runner action executes
# each line of an inline `script:` block as its own separate process.
set -euo pipefail

APK=$(find apk -name "*.apk" | head -n1)
echo "Installing $APK"
adb install -r "$APK"

adb logcat -c
adb shell am start -n com.tarkeshstack.speakeasy/.MainActivity -W
sleep 10

adb logcat -d > logcat.txt
echo "----- last 400 lines of logcat -----"
tail -n 400 logcat.txt

if grep -q "FATAL EXCEPTION" logcat.txt; then
  echo "::error::App crashed on launch"
  grep -A 60 "FATAL EXCEPTION" logcat.txt
  exit 1
fi

if ! adb shell pidof com.tarkeshstack.speakeasy; then
  echo "::error::App process is not running after launch (no FATAL EXCEPTION found, but it exited)"
  exit 1
fi

echo "App launched and stayed running — smoke test passed"
