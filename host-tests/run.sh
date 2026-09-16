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
  "$ROOT/host-tests/ProjectPathHostTest.java" \
  "$ROOT/host-tests/WorkspacePathResolverHostTest.java" \
  "$ROOT/host-tests/ProjectDirectoryServiceHostTest.java" \
  "$ROOT/host-tests/ProjectFileServiceHostTest.java" \
  "$ROOT/host-tests/ProjectTemplateGeneratorHostTest.java" \
  "$ROOT/host-tests/ProjectArchiveServiceHostTest.java" \
  "$ROOT/host-tests/EditorSessionHostTest.java" \
  "$ROOT/host-tests/EditorSettingsHostTest.java" \
  "$ROOT/host-tests/TextSearchServiceHostTest.java" \
  "$ROOT/host-tests/ProjectSearchServiceHostTest.java" \
  "$ROOT/host-tests/BuildDiagnosticsParserHostTest.java" \
  "$ROOT/host-tests/ProcessEngineHostTest.java" \
  "$ROOT/host-tests/TerminalCommandPlannerHostTest.java" \
  "$ROOT/host-tests/GitServiceHostTest.java" \
  "$ROOT/host-tests/ApkSignerServiceHostTest.java" \
  "$ROOT/host-tests/ApkSignerLocatorHostTest.java" \
  "$ROOT/host-tests/ResourceIndexServiceHostTest.java" \
  "$ROOT/host-tests/SqlQueryGuardHostTest.java" \
  "$ROOT/host-tests/BoundedLogBufferHostTest.java" \
  "$ROOT/host-tests/CacheMaintenanceServiceHostTest.java" \
  $CORE_SOURCES \
  "$ROOT/app/src/main/java/com/jepongdevxyz/idebuild/ApkLocator.java" \
  "$ROOT/app/src/main/java/com/jepongdevxyz/idebuild/BuildRunner.java"
java -cp "$OUT" HostSelfTest
java -cp "$OUT" HybridHostTest
java -cp "$OUT" RuntimeCatalogHostTest
java -cp "$OUT" ProjectPathHostTest
java -cp "$OUT" WorkspacePathResolverHostTest
java -cp "$OUT" ProjectDirectoryServiceHostTest
java -cp "$OUT" ProjectFileServiceHostTest
java -cp "$OUT" ProjectTemplateGeneratorHostTest
java -cp "$OUT" ProjectArchiveServiceHostTest
java -cp "$OUT" EditorSessionHostTest
java -cp "$OUT" EditorSettingsHostTest
java -cp "$OUT" TextSearchServiceHostTest
java -cp "$OUT" ProjectSearchServiceHostTest
java -cp "$OUT" BuildDiagnosticsParserHostTest
java -cp "$OUT" ProcessEngineHostTest
java -cp "$OUT" TerminalCommandPlannerHostTest
java -cp "$OUT" GitServiceHostTest
java -cp "$OUT" ApkSignerServiceHostTest
java -cp "$OUT" ApkSignerLocatorHostTest
java -cp "$OUT" ResourceIndexServiceHostTest
java -cp "$OUT" SqlQueryGuardHostTest
java -cp "$OUT" BoundedLogBufferHostTest
java -cp "$OUT" CacheMaintenanceServiceHostTest
python3 "$ROOT/host-tests/source_contract_test.py"
python3 "$ROOT/host-tests/developer_tools_contract_test.py"
python3 "$ROOT/host-tests/terminal_log_contract_test.py"
python3 "$ROOT/host-tests/runtime_pack_tool_test.py"
python3 "$ROOT/host-tests/bootstrap_stamp_test.py"
python3 "$ROOT/host-tests/runtime_builder_contract_test.py"
rm -rf "$OUT"
