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
  if timeout 120 adb install -r "$apk"; then
    return 0
  fi
  echo "Initial $label install timed out/failed; recovering ADB and retrying once" >&2
  adb kill-server || true
  adb start-server
  adb wait-for-device
  timeout 120 adb install -r "$apk"
}

install_apk "$DEVXYZ_APK" "DevxyzIDE"
adb shell pm list packages | grep -F "package:com.jepongdevxyz.idebuild"
adb shell monkey -p com.jepongdevxyz.idebuild -c android.intent.category.LAUNCHER 1
sleep 3
adb shell dumpsys activity activities | grep -F "com.jepongdevxyz.idebuild"
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
