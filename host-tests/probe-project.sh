#!/bin/sh
set -eu
if [ "$#" -ne 1 ]; then
  echo "Usage: $0 <project-root>" >&2
  exit 2
fi
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd -P)
OUT="$ROOT/host-tests/probe-out"
rm -rf "$OUT"
mkdir -p "$OUT"
CORE_SOURCES=$(find "$ROOT/app/src/main/java/com/jepongdevxyz/idebuild/core" -name '*.java' -print)
javac -d "$OUT" $CORE_SOURCES "$ROOT/host-tests/ProjectProbe.java"
java -cp "$OUT" ProjectProbe "$1"
rm -rf "$OUT"
