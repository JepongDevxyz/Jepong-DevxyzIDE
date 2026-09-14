#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
CONFIG="$SCRIPT_DIR/config/runtime-pack.properties"
get_prop(){ awk -F= -v k="$1" '$1==k{print substr($0,index($0,"=")+1)}' "$CONFIG"; }
APP_ID="$(get_prop applicationId)"
UPSTREAM="$(get_prop nativeBuilderRepo)"
COMMIT="$(get_prop nativeBuilderCommit)"
ABI="$(get_prop nativeAbi)"
[ "$ABI" = "aarch64" ] || { echo "unsupported ABI: $ABI" >&2; exit 1; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
SRC="$WORK/terminal-packages"
git clone --recurse-submodules "$UPSTREAM" "$SRC"
git -C "$SRC" checkout --detach "$COMMIT"
git -C "$SRC" submodule update --init --recursive
[ "$(git -C "$SRC" rev-parse HEAD)" = "$COMMIT" ] || { echo "upstream commit mismatch" >&2; exit 1; }
git -C "$SRC" apply "$SCRIPT_DIR/patches/devxyz-prefix.patch"
grep -q 'COTG_PACKAGE_NAME="com.jepongdevxyz.idebuild"' "$SRC/common.sh"
grep -q '"aapt"' "$SRC/packages.sh"

# Build the pinned Android-native package set for aarch64. This includes
# OpenJDK 21 and the aapt package, whose Termux subpackage provides aapt2.
(
  cd "$SRC"
  ./build.sh -a aarch64
  ./generate-apt-repo.sh
)

# Generate only the requested architecture instead of the upstream helper's
# aarch64+arm loop.
# shellcheck disable=SC1090
. "$SRC/common.sh"
OUT_ARCH="$SRC/output/aarch64"
mkdir -p "$OUT_ARCH"
PACKAGES="$(IFS=,; echo "${COTG_PACKAGES__BASE[*]} ${COTG_PACKAGES__DEBUG[*]}" | tr ' ' ',')"
PACKAGES="${PACKAGES//,,/,}"
(
  cd "$OUT_ARCH"
  "$SRC/termux-packages/scripts/generate-bootstraps.sh" \
    --architectures aarch64 \
    --repository "file://$SRC/output/repo" \
    --add "$PACKAGES"
)

RAW="$OUT_ARCH/bootstrap-aarch64.zip"
[ -f "$RAW" ] || { echo "bootstrap output missing" >&2; exit 1; }
OUT="$SCRIPT_DIR/out/bootstrap/devxyz-bootstrap-aarch64.zip"
mkdir -p "$(dirname "$OUT")"
python3 "$REPO_ROOT/tools/runtime/stamp_bootstrap.py" \
  --input "$RAW" \
  --output "$OUT" \
  --application-id "$APP_ID" \
  --arch aarch64 \
  --upstream-commit "$COMMIT"
python3 "$SCRIPT_DIR/test_bootstrap_contract.py" "$OUT"
printf '%s\n' "$OUT"
