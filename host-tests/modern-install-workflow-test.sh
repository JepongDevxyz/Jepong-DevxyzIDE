#!/bin/sh
set -eu
WORKFLOW=.github/workflows/android-full-verification.yml
grep -F 'apksigner" verify --verbose --print-certs' "$WORKFLOW"
grep -F 'api-level: 35' "$WORKFLOW"
grep -F 'No native libraries: APK is architecture-neutral' "$WORKFLOW"
grep -F 'adb install -r "$APK"' "$WORKFLOW"
echo 'Modern install workflow coverage: PASS'
