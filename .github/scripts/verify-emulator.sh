#!/bin/sh
set -eu

DEVXYZ_APK="$(find verified-artifact -type f -name 'app-debug.apk' -print -quit)"
JAVA_APK="$(find generated-samples -type f -path '*DevxyzJavaSample*' -name 'app-debug.apk' -print -quit)"
KOTLIN_APK="$(find generated-samples -type f -path '*DevxyzKotlinSample*' -name 'app-debug.apk' -print -quit)"

test -n "$DEVXYZ_APK" && test -s "$DEVXYZ_APK"
test -n "$JAVA_APK" && test -s "$JAVA_APK"
test -n "$KOTLIN_APK" && test -s "$KOTLIN_APK"

adb wait-for-device

install_apk() {
  apk="$1"
  label="$2"
  echo "Installing $label"
  if timeout 120 adb install -r "$apk"; then return 0; fi
  echo "Initial $label install timed out/failed; recovering ADB and retrying once" >&2
  adb kill-server || true
  adb start-server
  adb wait-for-device
  timeout 120 adb install -r "$apk"
}

dump_ui() {
  adb shell uiautomator dump /sdcard/devxyz-ui.xml >/dev/null
  adb pull /sdcard/devxyz-ui.xml "$1" >/dev/null
}

require_ui() {
  file="$1"
  text="$2"
  grep -F "$text" "$file" >/dev/null || { echo "Missing UI text: $text" >&2; cat "$file" >&2; exit 1; }
}

tap_nav() {
  x="$1"
  label="$2"
  expected="$3"
  adb shell input tap "$x" 1745
  # UiAutomator can briefly return a stale/null hierarchy while the surface
  # changes. Poll the real hierarchy until the requested surface is visible.
  i=0
  while [ "$i" -lt 10 ]; do
    dump_ui "devxyz-${label}.xml" || true
    if grep -F "$expected" "devxyz-${label}.xml" >/dev/null 2>&1; then return 0; fi
    i=$((i + 1))
    sleep 1
  done
  require_ui "devxyz-${label}.xml" "$expected"
}

install_apk "$DEVXYZ_APK" "DevxyzIDE"
adb shell pm list packages | grep -F "package:com.jepongdevxyz.idebuild"
adb shell monkey -p com.jepongdevxyz.idebuild -c android.intent.category.LAUNCHER 1
sleep 3
adb shell dumpsys activity activities | grep -F "com.jepongdevxyz.idebuild"

dump_ui devxyz-files.xml
require_ui devxyz-files.xml "DevxyzIDE"
require_ui devxyz-files.xml "Files"
require_ui devxyz-files.xml "Search"
require_ui devxyz-files.xml "Git"
require_ui devxyz-files.xml "Build"
require_ui devxyz-files.xml "More"
require_ui devxyz-files.xml "PROJECT EXPLORER"

# API 28 CI emulator is configured at 1080x1920; tap the five bottom navigation cells.
tap_nav 324 search "SEARCH"
tap_nav 540 git "SOURCE CONTROL"
# Expected surface: BUILD & RUN. UiAutomator XML escapes the ampersand in this heading.
tap_nav 756 build "BUILD &amp; RUN"
# Expected surface: SETTINGS & RUNTIME (rendered heading is SETTINGS).
tap_nav 972 more "SETTINGS"
tap_nav 108 files "PROJECT EXPLORER"

adb exec-out screencap -p > devxyz-reference-ui.png
test -s devxyz-reference-ui.png
adb shell am force-stop com.jepongdevxyz.idebuild

install_apk "$JAVA_APK" "generated Java sample"
adb shell pm list packages | grep -F "package:com.jepongdevxyz.devxyzsamplejava"
adb shell monkey -p com.jepongdevxyz.devxyzsamplejava -c android.intent.category.LAUNCHER 1
sleep 3
adb shell dumpsys activity activities | grep -F "com.jepongdevxyz.devxyzsamplejava"
adb shell am force-stop com.jepongdevxyz.devxyzsamplejava

install_apk "$KOTLIN_APK" "generated Kotlin sample"
adb shell pm list packages | grep -F "package:com.jepongdevxyz.devxyzsamplekotlin"
adb shell monkey -p com.jepongdevxyz.devxyzsamplekotlin -c android.intent.category.LAUNCHER 1
sleep 3
adb shell dumpsys activity activities | grep -F "com.jepongdevxyz.devxyzsamplekotlin"
adb shell am force-stop com.jepongdevxyz.devxyzsamplekotlin
