# DevxyzIDE Exact Reference UI Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the real DevxyzIDE Android app closely match the approved five-screen reference without regressing existing functionality.

**Architecture:** Preserve MainActivity and existing action handlers as the functional core. Rebuild the resource/layout presentation into compact workspace screens and add only small navigation/presentation coordinators where needed. Existing backend actions remain authoritative.

**Tech Stack:** Java 7, Android SDK/API 19 minimum, legacy Gradle 4.6/AGP project, Android XML resources, Python host contract tests.

**Spec:** `docs/superpowers/specs/2026-09-17-reference-exact-ui-rebuild-design.md`

## Global Constraints
- Preserve `minSdkVersion 19` and existing legacy build compatibility.
- No AndroidX/Material dependency requirement.
- Preserve import hardening, build, Git, terminal, signing and project behavior.
- Folder import remains API21-gated.
- Approved five-screen reference is the visual source of truth.

---

### Task 1: Reference UI contract
**Files:** Create `host-tests/reference_exact_ui_contract_test.py`; modify `host-tests/run.sh` only if test discovery requires it.
**Produces:** automated assertions for compact screen containers, navigation labels/IDs, cyan/navy tokens and removal of oversized legacy presentation.
- [ ] Write the contract against required resource IDs and style tokens.
- [ ] Run it against the current branch and confirm failure.
- [ ] Keep existing host contracts green except the new intentional failure.
- [ ] Commit the failing contract.

### Task 2: Visual resource system
**Files:** Modify `app/src/main/res/values/colors.xml`; create/update drawable XML resources for compact cards, selected navigation, tabs and cyan floating actions.
**Produces:** API19-compatible reusable visual tokens.
- [ ] Add dark/navy/cyan semantic colors without removing IDs used by existing code.
- [ ] Add compact shape/state drawables using platform XML only.
- [ ] Run the reference contract and resource-oriented host tests.
- [ ] Commit visual resources.

### Task 3: Project Explorer and Code Editor workspace
**Files:** Modify `app/src/main/res/layout/activity_main.xml`; modify landscape equivalent if present; minimally modify `MainActivity.java`/navigation helper only for visibility wiring.
**Consumes:** existing project tree/editor/action IDs and handlers.
**Produces:** compact reference-style Files and Code presentations.
- [ ] Replace oversized header/action presentation with compact toolbar/project explorer.
- [ ] Preserve authoritative project tree and create/open/import actions.
- [ ] Build editor tabs, gutter/editor body and Code/Terminal/Log/Problems navigation around existing editor widgets.
- [ ] Wire cyan floating actions to existing handlers.
- [ ] Run host contracts and compile verification.
- [ ] Commit Files/Code workspace.

### Task 4: Build & Run workspace
**Files:** Modify `activity_main.xml` and minimal navigation Java.
**Consumes:** existing BuildRunner/build-output/install handlers.
**Produces:** dedicated reference-style build console and artifact actions.
- [ ] Present real build logs in the compact build console.
- [ ] Surface artifact path/status from existing build state.
- [ ] Wire Install APK/Open Folder/available artifact actions to existing handlers.
- [ ] Run build-related host tests.
- [ ] Commit Build workspace.

### Task 5: Tools workspace
**Files:** Modify `activity_main.xml`; reuse `DeveloperToolsActivity`/existing buttons; add small presentation coordinator only if required.
**Produces:** grouped Project Tools, Utilities and Extras UI.
- [ ] Create compact grouped tool cards matching reference hierarchy.
- [ ] Wire every visible supported tool to its existing action.
- [ ] Disable or omit capabilities that registry marks unavailable rather than faking functionality.
- [ ] Run capability and host tests.
- [ ] Commit Tools workspace.

### Task 6: Settings workspace
**Files:** Modify `activity_main.xml`; reuse `EditorSettingsButton` and existing settings/preferences code.
**Produces:** compact Editor/Build/App settings presentation.
- [ ] Expose existing font size, word wrap, autosave/theme/build-related settings using compact rows.
- [ ] Keep unsupported reference-only settings out unless an existing backend supports them.
- [ ] Persist changes through existing preferences.
- [ ] Run settings and host tests.
- [ ] Commit Settings workspace.

### Task 7: Splash/icon and responsive polish
**Files:** Modify `activity_splash.xml`, relevant drawable/mipmap resources, portrait/landscape layout resources.
**Produces:** reference-consistent branding and responsive phone layout.
- [ ] Match approved DevxyzIDE dark/cyan splash composition using repository-safe assets/resources.
- [ ] Verify compact portrait sizing and usable landscape behavior.
- [ ] Verify no white landscape background regression.
- [ ] Run reference and orientation contracts.
- [ ] Commit branding/responsive polish.

### Task 8: Full verification and signed candidate
**Files:** workflow only if needed; no signing material committed.
**Produces:** CI-verified APK candidate.
- [ ] Run `sh host-tests/run.sh` in CI.
- [ ] Run Android build verification using known-good SDK28/build-tools28.0.3 + JDK8/Gradle4.6 path.
- [ ] Verify APK package/signature and installation workflow.
- [ ] Inspect failures and fix root causes rather than bypassing tests.
- [ ] After green verification, build a fresh signed release using GitHub Actions secrets.
- [ ] Package the exact release source with `git archive` and checksums.
