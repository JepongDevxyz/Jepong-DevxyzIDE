#!/bin/sh
set -eu
if ! command -v gradle >/dev/null 2>&1; then
  echo "Trusted Gradle installation not found. Install/use Gradle 9.6.0, then rerun this script." >&2
  exit 1
fi
gradle wrapper --gradle-version 9.6.0 --distribution-type bin
echo "Official Gradle wrapper generated."
