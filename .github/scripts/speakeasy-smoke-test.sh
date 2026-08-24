#!/usr/bin/env bash
# Installs the freshly built debug APK on the running emulator, launches
# MainActivity, grants the mic permission up front, taps the mic button
# (found via a UiAutomator dump so it works regardless of screen size),
# and fails (printing the logcat crash trace) if the app crashes at any
# point along the way. Run as a single script so shell state (variables,
# the if/fi control flow) persists — the emulator-runner action executes
# each line of an inline `script:` block as its own separate process.
set -euo pipefail

# The build now splits by ABI (see build.gradle.kts) — install the x86_64 variant to
# match this emulator's own architecture.
APK=$(find apk -name "*x86_64*.apk" | head -n1)
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
sleep 10
echo "== after launch =="
check_for_crash

# The UI (or the accessibility service backing UiAutomator) can take a beat
# to settle right after a cold start, so retry the dump+search a few times
# instead of treating one empty result as "no mic button".
TAP=""
for attempt in 1 2 3 4 5 6 7 8; do
  adb shell uiautomator dump /sdcard/window_dump.xml > /dev/null
  adb pull /sdcard/window_dump.xml window_dump.xml > /dev/null
  TAP=$(python3 -c "
import re
with open('window_dump.xml') as f:
    data = f.read()
m = re.search(r'content-desc=\"Speak\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', data)
if m:
    x1, y1, x2, y2 = map(int, m.groups())
    print(f'{(x1 + x2) // 2} {(y1 + y2) // 2}')
")
  if [ -n "$TAP" ]; then
    break
  fi

  # A system "isn't responding" dialog for an unrelated app (seen on freshly-booted
  # CI emulators once installing a larger APK does enough PackageManager/broadcast
  # work to briefly stall something else, e.g. the launcher) can sit in front of our
  # activity and block automation from ever finding it. Dismiss it via "Wait" so the
  # system gets a chance to recover, instead of just retrying against the same
  # blocking dialog every time.
  if grep -q "isn't responding" window_dump.xml; then
    echo "A system ANR dialog for another app is blocking the UI (attempt $attempt) — dismissing it and retrying..."
    WAIT_TAP=$(python3 -c "
import re
with open('window_dump.xml') as f:
    data = f.read()
m = re.search(r'resource-id=\"android:id/aerr_wait\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', data)
if m:
    x1, y1, x2, y2 = map(int, m.groups())
    print(f'{(x1 + x2) // 2} {(y1 + y2) // 2}')
")
    if [ -n "$WAIT_TAP" ]; then
      adb shell input tap $WAIT_TAP
    fi
  else
    echo "Mic button not found in UiAutomator dump on attempt $attempt, retrying..."
  fi
  sleep 5
done

if [ -z "$TAP" ]; then
  echo "::error::Could not find the mic button via UiAutomator dump after 5 attempts"
  echo "----- window_dump.xml -----"
  cat window_dump.xml
  exit 1
fi

echo "Tapping mic button at: $TAP"
adb shell input tap $TAP
sleep 5
echo "== after tapping the mic button (permission + recognizer start) =="
check_for_crash

echo "App launched, stayed running, and survived a mic tap — smoke test passed"
