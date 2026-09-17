# DevxyzIDE Reference Visual + Functional Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the rejected legacy-looking UI with the approved reference-style DevxyzIDE workspace and wire every visible control to real production behavior.

**Architecture:** Preserve existing Java/API19-compatible backends while rebuilding the authoritative portrait/landscape presentation. Remove the hidden-control compatibility pattern, style all visible controls with API19-safe resources, and bind visible controls directly to existing handlers/controllers. Verification becomes visual-structure plus functional-flow oriented before release signing.

**Tech Stack:** Android SDK 19–29, Java 7, XML layouts/drawables, legacy Gradle 4.6/AGP, Python host contract tests, GitHub Actions API 28 emulator verification.

**Spec:** `docs/superpowers/specs/2026-09-17-reference-visual-functional-rebuild-design.md`

## Global Constraints
- package `com.jepongdevxyz.idebuild`
- minSdkVersion 19; targetSdkVersion 29; compileSdk 29
- Java 7 compatibility
- no AndroidX/Material dependency requirement
- user image reference is the visual source of truth; rejected APK is not a baseline
- no hidden 1dp authoritative controls and no default gray Android button chrome
- no release until host, build, signing, emulator launch and representative interaction checks pass

---

### Task 1: Lock rejected-UI regressions
**Files:** Modify `host-tests/reference_exact_ui_contract_test.py`; modify `host-tests/reference_exact_ui_landscape_contract_test.py`; create `host-tests/reference_functional_surface_contract_test.py`.
**Interfaces:** Consumes portrait/landscape XML and MainActivity listener bindings. Produces contracts that reject hidden authoritative IDs, duplicate controls, default button chrome, and inert visible actions.
- [ ] Add failing assertions for no 1dp hidden compatibility holder containing actionable IDs.
- [ ] Add failing assertions requiring themed backgrounds/text styling on visible secondary controls.
- [ ] Add failing assertions requiring New File/New Folder/Search/Git/Build/More and build/tool/settings actions to have real visible bindings.
- [ ] Run host tests and confirm RED on current rejected UI.
- [ ] Commit tests.

### Task 2: Rebuild shared reference visual system
**Files:** Modify `res/values/colors.xml`; create/modify API19-safe `res/drawable/devxyz_*` selectors/shapes; modify reusable navigation/tool button classes only where XML styling cannot cover state.
**Interfaces:** Produces reusable panel/card/row/active/inactive/primary/secondary styling used by both orientations.
- [ ] Implement compact navy/cyan states without vector/Material dependencies.
- [ ] Remove platform-default button appearance from authoritative controls.
- [ ] Run host resource contracts.
- [ ] Commit visual system.

### Task 3: Reconstruct portrait workspace
**Files:** Replace `res/layout/activity_main.xml`; targeted MainActivity binding edits only.
**Interfaces:** Produces reference-style Project Explorer/editor workspace, bottom nav, Build & Run, Tools and Settings using real IDs once each.
- [ ] Replace hidden compatibility controls with real visible controls or direct handlers.
- [ ] Match reference hierarchy/spacing/compact sizing.
- [ ] Make New File/New Folder actionable when a project is active.
- [ ] Make Search/Git/Build/More navigation switch real surfaces.
- [ ] Run portrait + functional contracts to GREEN.
- [ ] Commit portrait rebuild.

### Task 4: Reconstruct landscape workspace
**Files:** Replace `res/layout-land/activity_main.xml`; targeted binding edits if orientation assumptions exist.
**Interfaces:** Same authoritative control IDs and handlers as portrait, arranged as reference-style split explorer/editor workspace.
- [ ] Match compact split-pane reference composition.
- [ ] Preserve functional surfaces and controls without duplicate/hidden IDs.
- [ ] Run landscape + functional contracts to GREEN.
- [ ] Commit landscape rebuild.

### Task 5: Fix real feature flows
**Files:** Target only controllers/classes implicated by failing interaction tests, including `MainActivity.java`, `ProjectImportController.java`, build/install/search/git/terminal/settings helpers.
**Interfaces:** Visible controls call real existing production operations and report explicit errors/needs-install states.
- [ ] Verify Create and Import never finish/close MainActivity on normal errors/cancel.
- [ ] Verify file create/folder create/open/edit/save/search flows.
- [ ] Verify build starts real build session and exposes log/artifact state.
- [ ] Verify install is enabled only for a discovered APK and invokes install flow.
- [ ] Verify Terminal/Git/APK Signer/Developer Tools/Settings open real surfaces/actions.
- [ ] Add focused regression test before each production bug fix; run GREEN; commit by flow.

### Task 6: Rebuild icon and splash
**Files:** Modify `activity_splash.xml`, manifest icon references and API19-safe launcher/logo resources; modify branding contract.
**Interfaces:** Produces branded DevxyzIDE launch experience consistent with the reference palette.
- [ ] Write branding assertions first and confirm RED.
- [ ] Implement custom logo/icon and compact splash without API21-only base-layout attributes.
- [ ] Run branding/resource tests GREEN.
- [ ] Commit branding.

### Task 7: Full verification and release
**Files:** Modify emulator verification script/workflow only if needed to exercise real UI actions; no weakening assertions to make failures disappear.
**Interfaces:** Produces verified APK/source artifacts from exact tested commit.
- [ ] Run all host contracts.
- [ ] Run full Android SDK 28 build/signature validation.
- [ ] Install/launch on API 28 emulator and exercise representative Create/Import/navigation/build/settings flows.
- [ ] Fix any real failure with test-first commits and rerun until GREEN.
- [ ] Merge only the verified rebuild commit/PR into release branch.
- [ ] Run Official Signed Release and verify artifact/signature/source bundle before delivery.
