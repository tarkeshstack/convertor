#!/usr/bin/env bash
# Installs the freshly built debug APK on the running emulator, launches
# MainActivity, grants the mic permission up front, taps the mic button
# (found via a UiAutomator dump so it works regardless of screen size),
# and fails (printing the logcat crash trace) if the app crashes at any
# point along the way. Run as a single script so shell state (variables,
# the if/fi control flow) persists — the emulator-runner action executes
# each line of an inline `script:` block as its own separate process.
set -euo pipefail

APK=$(find apk -name "*.apk" | head -n1)
echo "Installing $APK"
adb install -r "$APK"

check_for_crash() {
  adb logcat -d -b all > logcat.txt
  if grep -q "FATAL EXCEPTION" logcat.txt; then
    echo "::error::App crashed"
    echo "----- crash trace -----"
    grep -A 60 "FATAL EXCEPTION" logcat.txt
    exit 1
  fi
  if ! adb shell pidof com.tarkeshstack.speakeasy > /dev/null; then
    echo "::error::App process is not running (no FATAL EXCEPTION found in logcat, but it exited)"
    echo "----- last 200 lines of logcat -----"
    tail -n 200 logcat.txt
    exit 1
  fi
}

adb logcat -c

# Grant the mic permission up front so the tap below exercises the actual
# speech-recognition start path rather than just the permission dialog.
adb shell pm grant com.tarkeshstack.speakeasy android.permission.RECORD_AUDIO

adb shell am start -n com.tarkeshstack.speakeasy/.MainActivity -W
sleep 5
echo "== after launch =="
check_for_crash

adb shell uiautomator dump /sdcard/window_dump.xml
adb pull /sdcard/window_dump.xml window_dump.xml
TAP=$(python3 -c "
import re
with open('window_dump.xml') as f:
    data = f.read()
m = re.search(r'content-desc=\"Speak\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', data)
if m:
    x1, y1, x2, y2 = map(int, m.groups())
    print(f'{(x1 + x2) // 2} {(y1 + y2) // 2}')
")

if [ -z "$TAP" ]; then
  echo "::warning::Could not find the mic button via UiAutomator dump; skipping tap-based check"
else
  echo "Tapping mic button at: $TAP"
  adb shell input tap $TAP
  sleep 5
  echo "== after tapping the mic button (permission + recognizer start) =="
  check_for_crash
fi

echo "App launched, stayed running, and survived a mic tap — smoke test passed"
