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
  "$ROOT/host-tests/ProjectImportServiceHostTest.java" \
  "$ROOT/host-tests/ProjectImportUnlimitedHostTest.java" \
  "$ROOT/host-tests/SafeZipCancellationHostTest.java" \
  "$ROOT/host-tests/ProjectImportStressHostTest.java" \
  "$ROOT/host-tests/BuildPlannerHostTest.java" \
  "$ROOT/host-tests/BuildTaskPolicyHostTest.java" \
  "$ROOT/host-tests/BuildOutputScannerHostTest.java" \
  "$ROOT/host-tests/ApkLocatorHostTest.java" \
  "$ROOT/host-tests/RuntimePackDownloaderCancellationHostTest.java" \
  "$ROOT/host-tests/EditorSessionHostTest.java" \
  "$ROOT/host-tests/EditorSettingsHostTest.java" \
  "$ROOT/host-tests/AppAppearanceSettingsHostTest.java" \
  "$ROOT/host-tests/EditorUndoHistoryHostTest.java" \
  "$ROOT/host-tests/TextSearchServiceHostTest.java" \
  "$ROOT/host-tests/ProjectSearchServiceHostTest.java" \
  "$ROOT/host-tests/BuildDiagnosticsParserHostTest.java" \
  "$ROOT/host-tests/ProcessEngineHostTest.java" \
  "$ROOT/host-tests/TerminalCommandPlannerHostTest.java" \
  "$ROOT/host-tests/GitServiceHostTest.java" \
  "$ROOT/host-tests/CapabilityRegistryHostTest.java" \
  "$ROOT/host-tests/ApkSignerServiceHostTest.java" \
  "$ROOT/host-tests/ApkSignerLocatorHostTest.java" \
  "$ROOT/host-tests/ResourceIndexServiceHostTest.java" \
  "$ROOT/host-tests/SqlQueryGuardHostTest.java" \
  "$ROOT/host-tests/BoundedLogBufferHostTest.java" \
  "$ROOT/host-tests/CacheMaintenanceServiceHostTest.java" \
  "$ROOT/host-tests/SyntaxLanguageServiceHostTest.java" \
  "$ROOT/host-tests/BasicCompletionServiceHostTest.java" \
  $CORE_SOURCES \
  "$ROOT/app/src/main/java/com/jepongdevxyz/idebuild/ApkLocator.java" \
  "$ROOT/app/src/main/java/com/jepongdevxyz/idebuild/BuildRunner.java"
for test in HostSelfTest HybridHostTest RuntimeCatalogHostTest ProjectPathHostTest WorkspacePathResolverHostTest ProjectDirectoryServiceHostTest ProjectFileServiceHostTest ProjectTemplateGeneratorHostTest ProjectArchiveServiceHostTest ProjectImportServiceHostTest ProjectImportUnlimitedHostTest SafeZipCancellationHostTest ProjectImportStressHostTest BuildPlannerHostTest BuildTaskPolicyHostTest BuildOutputScannerHostTest ApkLocatorHostTest RuntimePackDownloaderCancellationHostTest EditorSessionHostTest EditorSettingsHostTest AppAppearanceSettingsHostTest EditorUndoHistoryHostTest TextSearchServiceHostTest ProjectSearchServiceHostTest BuildDiagnosticsParserHostTest ProcessEngineHostTest TerminalCommandPlannerHostTest GitServiceHostTest CapabilityRegistryHostTest ApkSignerServiceHostTest ApkSignerLocatorHostTest ResourceIndexServiceHostTest SqlQueryGuardHostTest BoundedLogBufferHostTest CacheMaintenanceServiceHostTest SyntaxLanguageServiceHostTest BasicCompletionServiceHostTest; do
  java -cp "$OUT" "$test"
done
for test in source_contract_test.py import_ui_contract_test.py reference_redesign_contract_test.py editor_syntax_contract_test.py editor_actions_contract_test.py editor_gutter_contract_test.py editor_settings_contract_test.py project_wizard_contract_test.py recent_projects_contract_test.py developer_tools_contract_test.py project_settings_contract_test.py build_actions_contract_test.py terminal_log_contract_test.py git_ui_contract_test.py capability_registry_contract_test.py release_bundle_contract_test.py runtime_pack_tool_test.py bootstrap_stamp_test.py runtime_builder_contract_test.py project_flow_no_exit_contract_test.py reference_exact_ui_contract_test.py reference_exact_ui_landscape_contract_test.py reference_functional_surface_contract_test.py branding_splash_contract_test.py emulator_atomic_script_contract_test.py; do
  python3 "$ROOT/host-tests/$test"
done
rm -rf "$OUT"
