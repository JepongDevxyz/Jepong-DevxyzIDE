#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CONFIG="$SCRIPT_DIR/config/runtime-pack.properties"
API="$(awk -F= '$1=="androidApi"{print $2}' "$CONFIG")"
: "${ANDROID_SDK_ROOT:?ANDROID_SDK_ROOT must be set}"
SDKMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
[ -x "$SDKMANAGER" ] || { echo "sdkmanager not found at $SDKMANAGER" >&2; exit 1; }

"$SDKMANAGER" "platforms;android-${API}" >/dev/null
SRC="$ANDROID_SDK_ROOT/platforms/android-${API}"
[ -f "$SRC/android.jar" ] || { echo "android.jar missing" >&2; exit 1; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
STAGE="$WORK/stage"
DEST="$STAGE/toolchains/android-sdk/platforms/android-${API}"
mkdir -p "$DEST" "$SCRIPT_DIR/out/android-sdk"
cp "$SRC/android.jar" "$DEST/android.jar"
[ ! -f "$SRC/package.xml" ] || cp "$SRC/package.xml" "$DEST/package.xml"
[ ! -f "$SRC/source.properties" ] || cp "$SRC/source.properties" "$DEST/source.properties"
python3 "$SCRIPT_DIR/generate-toolchain-properties.py" --root "$STAGE" --component android-sdk-platform --version "$API" --abi all
(
  cd "$STAGE"
  zip -qr "$SCRIPT_DIR/out/android-sdk/android-sdk-${API}.devxyz-toolchain.zip" .
)
unzip -t "$SCRIPT_DIR/out/android-sdk/android-sdk-${API}.devxyz-toolchain.zip" >/dev/null
printf '%s\n' "$SCRIPT_DIR/out/android-sdk/android-sdk-${API}.devxyz-toolchain.zip"
