#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd -P)
OUT="$ROOT/host-tests/out"
rm -rf "$OUT"
mkdir -p "$OUT"
CORE_SOURCES=$(find "$ROOT/app/src/main/java/com/jepongdevxyz/idebuild/core" -name '*.java' -print)
javac -d "$OUT" \
  "$ROOT/host-tests/HostSelfTest.java" \
  "$ROOT/host-tests/HybridHostTest.java" \
  "$ROOT/host-tests/RuntimeCatalogHostTest.java" \
  $CORE_SOURCES \
  "$ROOT/app/src/main/java/com/jepongdevxyz/idebuild/ApkLocator.java" \
  "$ROOT/app/src/main/java/com/jepongdevxyz/idebuild/BuildRunner.java"
java -cp "$OUT" HostSelfTest
java -cp "$OUT" HybridHostTest
java -cp "$OUT" RuntimeCatalogHostTest
python3 "$ROOT/host-tests/source_contract_test.py"
python3 "$ROOT/host-tests/runtime_pack_tool_test.py"
python3 "$ROOT/host-tests/bootstrap_stamp_test.py"
python3 "$ROOT/host-tests/runtime_builder_contract_test.py"
sh "$ROOT/host-tests/modern-install-workflow-test.sh"
rm -rf "$OUT"
