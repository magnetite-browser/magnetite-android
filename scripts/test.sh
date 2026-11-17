#!/usr/bin/env bash
# Run the UI tests on the emulator or device adb sees, with an app without
# data, and fetch the pictures they took. Usage: scripts/test.sh [directory for the pictures]
# The demo sync server must run on this machine (see README).
set -euo pipefail
cd "$(dirname "$0")/.."
APP=org.magnetitebrowser.android
SHOTS="${1:-build/shots}"

./gradlew -q installDebug installDebugAndroidTest
adb shell pm clear "$APP" > /dev/null
# The core only talks to a server without https on localhost.
adb reverse tcp:8787 tcp:8787 > /dev/null
out="$(adb shell am instrument -w "$APP.test/androidx.test.runner.AndroidJUnitRunner")"
echo "$out"

mkdir -p "$SHOTS"
for shot in $(adb shell run-as "$APP" ls files/shots 2> /dev/null | tr -d '\r'); do
  adb exec-out run-as "$APP" cat "files/shots/$shot" > "$SHOTS/$shot"
done
# `am instrument` does not fail when a test does.
grep -q '^OK (' <<< "$out"
