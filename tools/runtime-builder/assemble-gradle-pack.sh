#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CONFIG="$SCRIPT_DIR/config/runtime-pack.properties"
VERSION="$(awk -F= '$1=="gradleVersion"{print $2}' "$CONFIG")"
BASE="https://services.gradle.org/distributions/gradle-${VERSION}-bin.zip"
OUT="$SCRIPT_DIR/out/gradle"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$OUT"

curl -fL "$BASE" -o "$WORK/gradle.zip"
curl -fL "$BASE.sha256" -o "$WORK/gradle.zip.sha256"
EXPECTED="$(tr -d '[:space:]' < "$WORK/gradle.zip.sha256")"
ACTUAL="$(sha256sum "$WORK/gradle.zip" | awk '{print $1}')"
[ "$EXPECTED" = "$ACTUAL" ] || { echo "Gradle checksum mismatch" >&2; exit 1; }

unzip -q "$WORK/gradle.zip" -d "$WORK/extracted"
STAGE="$WORK/stage"
mkdir -p "$STAGE/toolchains/gradle-${VERSION}"
cp -a "$WORK/extracted/gradle-${VERSION}/." "$STAGE/toolchains/gradle-${VERSION}/"
python3 "$SCRIPT_DIR/generate-toolchain-properties.py" --root "$STAGE" --component gradle --version "$VERSION" --abi all
(
  cd "$STAGE"
  zip -qr "$OUT/gradle-${VERSION}.devxyz-toolchain.zip" .
)
unzip -t "$OUT/gradle-${VERSION}.devxyz-toolchain.zip" >/dev/null
printf '%s\n' "$OUT/gradle-${VERSION}.devxyz-toolchain.zip"
