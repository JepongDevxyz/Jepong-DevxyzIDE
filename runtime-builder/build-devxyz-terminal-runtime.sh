#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
DEVXYZ_APPLICATION_ID="com.jepongdevxyz.idebuild"
UPSTREAM_REPO="https://github.com/appdevforall/terminal-packages.git"
UPSTREAM_COMMIT="610af608b4a3b1127244e90edf8b7be2bf94fafa"
ARCH="${DEVXYZ_ARCH:-aarch64}"
WORK_DIR="${DEVXYZ_RUNTIME_WORK:-$PWD/.devxyz-runtime-build}"
PUBLISH_REPO="${DEVXYZ_APT_REPO_URL:-https://example.invalid/devxyzide/apt/termux-main}"
GPG_KEY="${DEVXYZ_GPG_PUBLIC_KEY:-}"

usage() {
  cat <<EOF
Build a Termux-compatible bootstrap whose compiled prefix matches DevxyzIDE.

Required for a real build:
  DEVXYZ_GPG_PUBLIC_KEY=/absolute/path/to/public-key.gpg $0 build

Optional:
  DEVXYZ_ARCH=aarch64|arm
  DEVXYZ_RUNTIME_WORK=/path/to/work
  DEVXYZ_APT_REPO_URL=https://your-host/apt/termux-main

Commands:
  plan   Print the pinned upstream/build parameters only.
  build  Clone pinned upstream, build packages, APT repo and one bootstrap archive.
EOF
}

print_plan() {
  printf 'applicationId=%s\nupstream=%s\ncommit=%s\narch=%s\nrepo=%s\n' \
    "$DEVXYZ_APPLICATION_ID" "$UPSTREAM_REPO" "$UPSTREAM_COMMIT" "$ARCH" "$PUBLISH_REPO"
}

case "${1:-}" in
  plan) print_plan; exit 0 ;;
  build) ;;
  -h|--help|help|'') usage; exit 0 ;;
  *) echo "Unknown command: $1" >&2; usage >&2; exit 2 ;;
esac

case "$ARCH" in aarch64|arm) ;; *) echo "DEVXYZ_ARCH must be aarch64 or arm" >&2; exit 2 ;; esac
if [[ "$PUBLISH_REPO" != https://* ]]; then
  echo "DEVXYZ_APT_REPO_URL must use HTTPS" >&2
  exit 2
fi
if [[ -z "$GPG_KEY" || ! -f "$GPG_KEY" ]]; then
  echo "DEVXYZ_GPG_PUBLIC_KEY must point to an existing exported public GPG key" >&2
  exit 2
fi
for cmd in git bash python3; do
  command -v "$cmd" >/dev/null || { echo "Missing required command: $cmd" >&2; exit 2; }
done

mkdir -p "$WORK_DIR"
SRC="$WORK_DIR/terminal-packages"
if [[ ! -d "$SRC/.git" ]]; then
  git clone --recurse-submodules "$UPSTREAM_REPO" "$SRC"
fi
git -C "$SRC" fetch --tags --prune origin
git -C "$SRC" checkout --detach "$UPSTREAM_COMMIT"
git -C "$SRC" submodule update --init --recursive

# Build the normal Code On the Go package set plus Android's native aapt package.
# The pinned Termux aapt recipe emits an aapt2 subpackage (bin/aapt2), and using -p
# rebuilds every native binary for DevxyzIDE's own app-private prefix rather than
# mixing binaries compiled for com.termux.
"$SRC/build.sh" -a "$ARCH" -p "$DEVXYZ_APPLICATION_ID" -r "$PUBLISH_REPO" -s "$GPG_KEY" "aapt"
"$SRC/generate-apt-repo.sh"

# Generate only the requested architecture instead of upstream's convenience wrapper that loops both ABIs.
# shellcheck source=/dev/null
source "$SRC/packages.sh"
# Include native aapt2 explicitly. openjdk-21 already comes from the pinned base package set.
BOOTSTRAP_PACKAGES=("${COTG_PACKAGES__BASE[@]}" "${COTG_PACKAGES__DEBUG[@]}" "aapt2")
PACKAGE_CSV=$(IFS=,; echo "${BOOTSTRAP_PACKAGES[*]}")
ARCH_OUT="$SRC/output/$ARCH"
pushd "$ARCH_OUT" >/dev/null
"$SRC/termux-packages/scripts/generate-bootstraps.sh" \
  --architectures "$ARCH" \
  --repository "$PUBLISH_REPO" \
  --add "$PACKAGE_CSV"
popd >/dev/null

RAW_BOOTSTRAP="$ARCH_OUT/bootstrap-$ARCH.zip"
if [[ ! -f "$RAW_BOOTSTRAP" ]]; then
  echo "Expected bootstrap was not generated: $RAW_BOOTSTRAP" >&2
  exit 1
fi
ARTIFACT_DIR="$WORK_DIR/artifacts"
mkdir -p "$ARTIFACT_DIR"
STAMPED="$ARTIFACT_DIR/DevxyzIDE-bootstrap-debug-$ARCH.zip"
python3 "$SCRIPT_DIR/../tools/runtime/stamp_bootstrap.py" \
  --input "$RAW_BOOTSTRAP" \
  --output "$STAMPED" \
  --application-id "$DEVXYZ_APPLICATION_ID" \
  --arch "$ARCH" \
  --upstream-commit "$UPSTREAM_COMMIT"
sha256sum "$STAMPED" > "$STAMPED.sha256"

cat <<EOF
Runtime build complete.
Bootstrap: $STAMPED
Checksum:  $STAMPED.sha256
APT repo:  $SRC/output/repo

Before publishing the APT repository, sign its Release metadata with the private key that corresponds to the public key used for the package build. Never commit the private key.
EOF
