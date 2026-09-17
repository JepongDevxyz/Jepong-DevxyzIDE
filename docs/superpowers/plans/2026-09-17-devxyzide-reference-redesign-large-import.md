# DevxyzIDE Reference Redesign + Large Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply the approved DevxyzIDE reference-image redesign to the real existing IDE while replacing the current fixed-size ZIP import path with cancelable, atomic, streaming import that has no app-defined project/archive byte ceiling.

**Architecture:** Keep the Java/AIDE-compatible host and all existing backend services. `MainActivity` remains the real workspace orchestrator, while focused helpers are added for destination navigation and import progress; import hardening extends `SafeZip`/`ProjectImportService` instead of creating a parallel fake import stack. Portrait and landscape use separate XML resources but share the same functional view IDs so existing features remain wired to real services.

**Tech Stack:** Java 7, Android platform APIs, XML resources, `ContentResolver`/SAF, `ZipInputStream`, existing Gradle/build/Git/terminal/signing/toolchain services, shell/Python/Java host tests, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-17-devxyzide-reference-redesign-large-import-design.md`

## Global Constraints

- App name remains `DevxyzIDE`.
- Application ID remains `com.jepongdevxyz.idebuild`.
- Continue from `feature/devxyzide-01-foundation`; do not rebuild the app from scratch.
- Preserve the current Java/AIDE-compatible host and existing Gradle/AGP/minSdk/targetSdk values unless a verified compatibility failure requires a change.
- No fake build success, fake Git/terminal/signing/toolchain state, or fake LSP claims.
- Project ZIP/folder import must use streaming I/O with bounded memory and no app-defined project/archive byte ceiling.
- Keep Zip Slip/path-containment protection, atomic staging, cancellation, clear errors, and lazy project-tree behavior.
- Keep `DevxyzIDE Full Android Verification` and `DevxyzIDE Device Install Verification` enabled and green before integration.

---

### Task 1: Add an unlimited streaming project-import core

**Files:**
- Modify: `app/src/main/java/com/jepongdevxyz/idebuild/core/SafeZip.java`
- Modify: `app/src/main/java/com/jepongdevxyz/idebuild/core/ProjectImportService.java`
- Create: `app/src/main/java/com/jepongdevxyz/idebuild/core/ProjectRootTracker.java`
- Modify: `host-tests/ProjectImportServiceHostTest.java`
- Modify: `host-tests/ProjectImportStressHostTest.java`
- Modify: `host-tests/SafeZipCancellationHostTest.java`

**Interfaces:**
- Produces: `SafeZip.extractProject(InputStream, File, CancellationSignal, ProjectEntryListener)` with no size/count limit arguments.
- Produces: `ProjectRootTracker.onEntry(String relativeName)` and `String getBestRootRelativePath()`.
- Produces: `ProjectImportService.importProject(InputStream, File, String, CancellationSignal, ProgressListener, StorageProbe)`.
- Existing limit-based overloads remain available for callers/tests that intentionally need a bounded extractor, but `MainActivity` project import must stop using them.

- [ ] **Step 1: Write failing tests for imports beyond the old policy limits and root tracking**

Add tests that generate ZIP entries in-memory/on temporary disk and assert the new overload does not consult `MAX_IMPORT_BYTES`/`MAX_IMPORT_ENTRIES`, tracks a deep `settings.gradle`, cleans staging on cancellation, and rejects traversal.

```java
ProjectImportService.ImportResult result = ProjectImportService.importProject(
        input, projects, "Huge.zip", ProjectImportService.NEVER_CANCELLED,
        ProjectImportService.NO_PROGRESS, ProjectImportService.NO_STORAGE_PROBE);
assertTrue(new File(result.getProjectRoot(), "settings.gradle").isFile());
```

- [ ] **Step 2: Run the focused import host tests and confirm RED**

Run:
```sh
./host-tests/run.sh
```
Expected: compilation/test failure because the new unlimited overload/root tracker does not exist yet.

- [ ] **Step 3: Implement `ProjectRootTracker`**

Use constant-sized state only. Normalize `/`, ignore directory entries, prefer the shallowest `settings.gradle`/`settings.gradle.kts`; fall back to the shallowest `build.gradle`/`build.gradle.kts` parent.

```java
public final class ProjectRootTracker {
    private String settingsRoot;
    private String buildRoot;
    public void onEntry(String relativeName) { /* normalize + choose best parent */ }
    public String getBestRootRelativePath() {
        return settingsRoot != null ? settingsRoot : buildRoot;
    }
}
```

- [ ] **Step 4: Add the no-project-size-ceiling SafeZip overload**

Keep the existing fixed 32 KiB reusable buffer or another fixed bounded buffer. Add per-entry callbacks containing name, files processed, and expanded bytes. Do not allocate based on source size. Reject absolute paths, canonical escapes, file/directory conflicts, and malformed names. Continue cancellation checks before entries and inside the copy loop.

- [ ] **Step 5: Update `ProjectImportService` to use staging + tracker + storage probe**

Create `.import-<name>-<nonce>`, stream directly into it, resolve the root from `ProjectRootTracker`, verify containment, and rename the staging directory only after validation. Replace recursive cleanup with an iterative stack/queue cleanup helper so very deep trees cannot overflow the Java stack.

```java
public interface StorageProbe {
    void beforeWrite(File destinationRoot, long copiedBytes) throws IOException;
}
```

`NO_STORAGE_PROBE` is a no-op; Android UI wiring can provide a real usable-space probe.

- [ ] **Step 6: Run import host tests and the full host suite**

Run:
```sh
./host-tests/run.sh
```
Expected: all current host tests plus the new import cases PASS.

- [ ] **Step 7: Commit**

```sh
git add app/src/main/java/com/jepongdevxyz/idebuild/core/SafeZip.java \
        app/src/main/java/com/jepongdevxyz/idebuild/core/ProjectImportService.java \
        app/src/main/java/com/jepongdevxyz/idebuild/core/ProjectRootTracker.java \
        host-tests/ProjectImportServiceHostTest.java \
        host-tests/ProjectImportStressHostTest.java \
        host-tests/SafeZipCancellationHostTest.java
git commit -m "feat: harden unlimited streaming project import"
```

### Task 2: Wire Android SAF ZIP/folder import and responsive progress

**Files:**
- Create: `app/src/main/java/com/jepongdevxyz/idebuild/ProjectImportController.java`
- Create: `app/src/main/java/com/jepongdevxyz/idebuild/SafProjectTreeCopier.java`
- Modify: `app/src/main/java/com/jepongdevxyz/idebuild/MainActivity.java:1-430`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `host-tests/import_ui_contract_test.py`
- Modify: `host-tests/run.sh`

**Interfaces:**
- `ProjectImportController.importZip(Uri)` opens the URI with `ContentResolver`, invokes the new atomic import service off the UI thread, and reports throttled progress.
- `ProjectImportController.cancel()` flips one cancellation token shared by ZIP and folder copy.
- `SafProjectTreeCopier.copyTree(ContentResolver, Uri, File, CancellationSignal, ProgressListener)` streams one document at a time and is used only on API 21+.

- [ ] **Step 1: Write source-contract tests**

Assert `MainActivity` no longer defines/uses `MAX_IMPORT_BYTES` or `MAX_IMPORT_ENTRIES` for project import, `ACTION_OPEN_DOCUMENT_TREE` is guarded by API 21+, and `content://` URIs are consumed through streams rather than path conversion.

- [ ] **Step 2: Run contract test and confirm RED**

Run:
```sh
python3 host-tests/import_ui_contract_test.py
```
Expected: FAIL until controller/tree-copy wiring exists.

- [ ] **Step 3: Implement `SafProjectTreeCopier`**

Use `DocumentsContract.buildChildDocumentsUriUsingTree`, query child document IDs/names/MIME types with a `Cursor`, recurse iteratively using a small queue of directory work items, and copy each file using `BufferedInputStream` -> fixed byte buffer -> `BufferedOutputStream`. Close cursor and stream immediately after each item.

- [ ] **Step 4: Implement `ProjectImportController`**

Own the cancellation token and an `AlertDialog`/progress view state. Throttle visible updates to roughly 100–200 ms, while internal counters update on every file/buffer. Show file count, copied bytes, current relative path, and indeterminate progress when total is unknown. Map `ENOSPC`/usable-space failures to `Not enough free storage` without inventing required bytes.

- [ ] **Step 5: Replace `MainActivity.importZip()` and add folder picker**

Use request codes for ZIP and folder. `Import Project` offers ZIP or Folder where folder SAF is supported; API 19 shows the truthful ZIP-only limitation for folder picking. Successful imports call `loadProject()` only after final activation.

- [ ] **Step 6: Run host/source contracts**

Run:
```sh
./host-tests/run.sh
```
Expected: PASS.

- [ ] **Step 7: Commit**

```sh
git add app/src/main/java/com/jepongdevxyz/idebuild/ProjectImportController.java \
        app/src/main/java/com/jepongdevxyz/idebuild/SafProjectTreeCopier.java \
        app/src/main/java/com/jepongdevxyz/idebuild/MainActivity.java \
        app/src/main/res/values/strings.xml host-tests/import_ui_contract_test.py host-tests/run.sh
git commit -m "feat: add SAF project import progress and cancellation"
```

### Task 3: Add the DevxyzIDE visual system and primary navigation shell

**Files:**
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values-night/colors.xml`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/res/values-night/themes.xml`
- Create: `app/src/main/res/drawable/devxyz_panel.xml`
- Create: `app/src/main/res/drawable/devxyz_panel_active.xml`
- Create: `app/src/main/res/drawable/devxyz_nav_item_active.xml`
- Create: `app/src/main/java/com/jepongdevxyz/idebuild/MainDestinationController.java`
- Modify: `app/src/main/java/com/jepongdevxyz/idebuild/MainActivity.java:100-220`
- Create: `host-tests/navigation_ui_contract_test.py`
- Modify: `host-tests/run.sh`

**Interfaces:**
- `MainDestinationController` constants: `FILES`, `SEARCH`, `GIT`, `BUILD_TOOLS`, `MORE`.
- `select(int destination)` toggles real destination containers and active navigation state.

- [ ] **Step 1: Write the navigation contract test**

Check exactly five destination IDs/labels, presence of Build/Tools internal tabs, dark navigation resources, and no external Material/Compose dependency.

- [ ] **Step 2: Run and confirm RED**

Run:
```sh
python3 host-tests/navigation_ui_contract_test.py
```
Expected: FAIL.

- [ ] **Step 3: Implement the palette/drawables**

Dark mode stays the reference-first appearance (`AppAppearanceSettings.defaults()` already returns dark). Keep light mode supported with corresponding surfaces; both palettes must use the same semantic resource names so landscape cannot fall back to platform white.

- [ ] **Step 4: Implement `MainDestinationController`**

Use platform `View`/`TextView`/`LinearLayout` APIs only. Preserve existing button/view IDs for real feature wiring.

- [ ] **Step 5: Wire navigation in `MainActivity`**

Default to Files. Search opens the dedicated search surface; Git contains the existing real `GitButton`; Build/Tools selects its destination; More exposes real settings/maintenance actions.

- [ ] **Step 6: Run host/source contracts and commit**

Run:
```sh
./host-tests/run.sh
```
Expected: PASS.

Commit:
```sh
git add app/src/main/res app/src/main/java/com/jepongdevxyz/idebuild/MainDestinationController.java \
        app/src/main/java/com/jepongdevxyz/idebuild/MainActivity.java \
        host-tests/navigation_ui_contract_test.py host-tests/run.sh
git commit -m "feat: add DevxyzIDE reference navigation shell"
```

### Task 4: Redesign portrait Files + Editor workspace without replacing editor logic

**Files:**
- Replace: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/java/com/jepongdevxyz/idebuild/MainActivity.java:100-750`
- Modify: `app/src/main/java/com/jepongdevxyz/idebuild/SyntaxEditText.java`
- Create: `host-tests/reference_layout_contract_test.py`
- Modify: `host-tests/run.sh`

**Interfaces:**
- Existing IDs `fileList`, `editor`, `tabBar`, `projectPath`, save/search/build buttons remain functional.
- Add containers `filesDestination`, `searchDestination`, `gitDestination`, `buildToolsDestination`, `moreDestination`, plus bottom-nav IDs.

- [ ] **Step 1: Write layout contract tests**

Assert the portrait XML has persistent five-item navigation, modern project header/actions, tab strip, editor, `Code/Terminal/Log/Problems` actions bound to real feature IDs, and no giant horizontal toolbar containing every IDE action.

- [ ] **Step 2: Run and confirm RED**

Run:
```sh
python3 host-tests/reference_layout_contract_test.py
```
Expected: FAIL.

- [ ] **Step 3: Replace portrait layout**

Build the layout with navy panels, cyan active state, compact spacing, bordered/rounded action groups, and weight-based/adaptive sizing. Place existing custom buttons (`TerminalButton`, `GitButton`, `BuildActionsButton`, `DeveloperToolsButton`, `EditorSettingsButton`, etc.) inside the destination where they belong instead of duplicating functionality.

- [ ] **Step 4: Improve explorer row rendering**

Reuse the existing adapter/backing services, but render folder/file prefixes/icons, selected file highlight, and indentation/path context without recursively materializing the entire project. Keep `ProjectDirectoryService.listChildren()` as the lazy enumeration authority.

- [ ] **Step 5: Preserve `SyntaxEditText` behavior**

Only make visual refinements required by the reference (gutter/current-line colors/padding). Do not replace its syntax, undo, line-number, or text state logic.

- [ ] **Step 6: Run full host suite and commit**

Run:
```sh
./host-tests/run.sh
```
Expected: PASS.

Commit:
```sh
git add app/src/main/res/layout/activity_main.xml \
        app/src/main/java/com/jepongdevxyz/idebuild/MainActivity.java \
        app/src/main/java/com/jepongdevxyz/idebuild/SyntaxEditText.java \
        host-tests/reference_layout_contract_test.py host-tests/run.sh
git commit -m "feat: redesign DevxyzIDE portrait workspace"
```

### Task 5: Redesign landscape deliberately and prevent white-panel regressions

**Files:**
- Replace: `app/src/main/res/layout-land/activity_main.xml`
- Modify: `host-tests/reference_layout_contract_test.py`

**Interfaces:**
- Same functional IDs as portrait.
- Files destination uses side-by-side explorer/editor; other destinations remain full-height usable panes.

- [ ] **Step 1: Extend contract test for landscape**

Assert landscape has side-by-side explorer/editor, all root/panel backgrounds use Devxyz semantic colors, bottom navigation remains present, and editor/console do not overlap it.

- [ ] **Step 2: Run and confirm RED**

Run:
```sh
python3 host-tests/reference_layout_contract_test.py
```
Expected: FAIL on landscape checks.

- [ ] **Step 3: Implement landscape XML**

Use horizontal weights for explorer/editor only where appropriate. Build/Tools/Search/Git/More use the wider viewport rather than stretched portrait cards. Avoid hard-coded white/platform backgrounds.

- [ ] **Step 4: Run contracts and commit**

Run:
```sh
./host-tests/run.sh
```
Expected: PASS.

Commit:
```sh
git add app/src/main/res/layout-land/activity_main.xml host-tests/reference_layout_contract_test.py
git commit -m "feat: add intentional DevxyzIDE landscape workspace"
```

### Task 6: Integrate real Build / Tools presentation

**Files:**
- Create: `app/src/main/java/com/jepongdevxyz/idebuild/BuildPresentationController.java`
- Modify: `app/src/main/java/com/jepongdevxyz/idebuild/MainActivity.java`
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/res/layout-land/activity_main.xml`
- Create: `host-tests/build_presentation_contract_test.py`
- Modify: `host-tests/run.sh`

**Interfaces:**
- `onBuildStarted(long startedAtMs)`
- `onBuildLog(String line)`
- `onBuildFinished(boolean success, long elapsedMs, List<BuildArtifact> artifacts)`
- Build artifact data continues to come from the existing real output scanner/locator path.

- [ ] **Step 1: Add failing contract tests**

Assert no hard-coded `BUILD SUCCESSFUL` in a default UI state, build log text is sourced from real console/build callbacks, artifact fields exist, Install APK is enabled only after a real APK file is selected, and Tools embeds existing capability-backed controls.

- [ ] **Step 2: Run and confirm RED**

Run:
```sh
python3 host-tests/build_presentation_contract_test.py
```
Expected: FAIL.

- [ ] **Step 3: Implement `BuildPresentationController` and wire existing build flow**

Track start time/state, reuse `BuildDiagnosticsParser`/Problems, expose real logs, and format type/variant/size/path for artifacts returned by the existing scanner. Do not synthesize an artifact when none exists.

- [ ] **Step 4: Populate Tools with real actions only**

Group New/Open/Import/Project Settings, Terminal/Git/Signer/Toolchain/Build Actions, and implemented Extras. Keep custom capability-aware button classes in charge of availability.

- [ ] **Step 5: Run full host suite and commit**

Run:
```sh
./host-tests/run.sh
```
Expected: PASS.

Commit:
```sh
git add app/src/main/java/com/jepongdevxyz/idebuild/BuildPresentationController.java \
        app/src/main/java/com/jepongdevxyz/idebuild/MainActivity.java app/src/main/res/layout \
        app/src/main/res/layout-land host-tests/build_presentation_contract_test.py host-tests/run.sh
git commit -m "feat: integrate real Build and Tools destination"
```

### Task 7: Finish Search, Git, More/Settings and branding surfaces

**Files:**
- Modify: `app/src/main/java/com/jepongdevxyz/idebuild/MainActivity.java`
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/res/layout-land/activity_main.xml`
- Modify: `app/src/main/res/layout/activity_splash.xml`
- Modify: `app/src/main/res/mipmap/ic_launcher.xml`
- Modify: `app/src/main/res/mipmap/ic_launcher_round.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `host-tests/redesign_feature_contract_test.py`
- Modify: `host-tests/run.sh`

**Interfaces:**
- Search calls existing current-file/project-search code.
- Git embeds/invokes the existing `GitButton`/Git backend.
- More invokes existing editor settings, project settings, cache/backup/toolchain/about behavior.

- [ ] **Step 1: Write the redesign feature contract test**

Assert `DevxyzIDE`, `Code • Build • Create`, optional `Powered by Jepong Devxyz`, five destinations, real Search/Git/Settings action IDs, and absence of labels claiming LSP/Sora if not implemented.

- [ ] **Step 2: Run and confirm RED**

Run:
```sh
python3 host-tests/redesign_feature_contract_test.py
```
Expected: FAIL.

- [ ] **Step 3: Wire Search/Git/More and branding**

Reuse existing dialogs/activities/button classes. Appearance settings remain persisted. Splash/icon stay dark/cyan and consistent with the supplied concept without introducing raster-heavy effects.

- [ ] **Step 4: Run full host suite and commit**

Run:
```sh
./host-tests/run.sh
```
Expected: PASS.

Commit:
```sh
git add app/src/main/java/com/jepongdevxyz/idebuild/MainActivity.java app/src/main/res \
        host-tests/redesign_feature_contract_test.py host-tests/run.sh
git commit -m "feat: finish DevxyzIDE reference redesign surfaces"
```

### Task 8: Strengthen stress/CI verification and release evidence

**Files:**
- Modify: `.github/workflows/large-import-stress.yml`
- Modify: `.github/workflows/android-full-verification.yml`
- Modify: `.github/workflows/device-install-verification.yml` only if a new explicit UI/install assertion is required; do not weaken existing steps.
- Modify: `docs/VERIFICATION_REPORT.md`
- Modify: `docs/CAPABILITY_MATRIX.md`
- Modify: `README.md`

**Interfaces:**
- CI creates synthetic stress inputs at run time rather than committing huge fixtures.

- [ ] **Step 1: Add/extend stress verification**

Generate deep trees, thousands of entries, and a large sparse/streamed payload in CI. Verify cancellation/cleanup and Zip Slip tests. Keep resource usage reasonable for hosted runners; the test proves streaming behavior and absence of the old policy limit, not an arbitrary multi-gigabyte cloud-runner benchmark.

- [ ] **Step 2: Run source/host verification locally where available**

Run:
```sh
./host-tests/run.sh
```
Expected: PASS.

- [ ] **Step 3: Update docs conservatively**

Document that project import has no application-defined byte ceiling, but physical/provider/filesystem/storage limits still apply. Keep LSP/on-device runtime claims conservative.

- [ ] **Step 4: Commit**

```sh
git add .github/workflows docs README.md
git commit -m "test: verify DevxyzIDE redesign and large imports"
```

### Task 9: Final release-gate verification and integration

**Files:**
- No feature files should be changed unless a real verification failure identifies a root cause.

**Interfaces:**
- Acceptance depends on repository tests and GitHub Actions, not visual claims alone.

- [ ] **Step 1: Run complete host suite**

Run:
```sh
./host-tests/run.sh
```
Expected: PASS with no disabled tests.

- [ ] **Step 2: Build host APK using the repository-supported CI/toolchain path**

Use the same Gradle/JDK/SDK combination documented by the current workflows. Do not alter versions just to make a local environment pass.

- [ ] **Step 3: Push the implementation branch and wait for both release gates**

Required green workflows:
- `DevxyzIDE Full Android Verification`
- `DevxyzIDE Device Install Verification`

Also require the large-import stress workflow to pass.

- [ ] **Step 4: Inspect failed job logs if anything is red**

Fix the real cause, rerun focused tests, then the full gates. Never disable a test/workflow to obtain green status.

- [ ] **Step 5: Fast-forward the approved development branch only after verification**

Integrate the verified implementation back into `feature/devxyzide-01-foundation` without rewriting history.
