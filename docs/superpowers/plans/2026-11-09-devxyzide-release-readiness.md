# DevxyzIDE Release Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stabilize project creation, ZIP import, toolchain installation, and CI verification without regressing the current Fragment-based DevxyzIDE UI.

**Architecture:** Keep `MainActivity` as the app coordinator and move filesystem work into focused helpers. Add regression tests before implementation, then update the Android CI workflow to validate the current app architecture.

**Tech Stack:** Kotlin, Android SDK 35, Java 17, Gradle 8.10.2, JUnit 4, GitHub Actions, Android emulator / adb.

**Spec:** `docs/superpowers/specs/2026-11-09-devxyzide-release-readiness-design.md`

## Global Constraints
- Package: `com.jepongdevxyz.idebuild`.
- minSdk 26, compileSdk/target SDK 35, Java 17, Gradle 8.10.2.
- Preserve the current Fragment-based UI and all existing handlers.
- Never overwrite a selected project during failed import or duplicate-name creation.
- Do not modify `main` directly; use the isolated repair branch and PR.

## Review Focus
- ZIP paths using backslashes, absolute paths, dot segments, or nested roots must not escape staging or silently select an ambiguous project.
- Large or highly nested archives must fail within explicit entry, depth, and expanded-byte limits.
- Existing project names and toolchain installations must remain intact after a failed operation.
- A toolchain executable that exists but cannot run must not be reported ready.
- CI smoke tests must target current Fragment resource IDs and actual app-private storage paths.

---

### Task 1: Safe, complete project-template generation

**Files:**
- Create: `app/src/main/java/com/jepongdevxyz/idebuild/ProjectTemplateWriter.kt`
- Create: `app/src/test/java/com/jepongdevxyz/idebuild/ProjectTemplateWriterTest.kt`
- Add: `app/src/main/assets/gradle-wrapper.jar` (reuse the known-valid wrapper JAR from PR #9)
- Modify: `app/src/main/java/com/jepongdevxyz/idebuild/MainActivity.kt`

**Interfaces:**
- Produces: `ProjectTemplateWriter.create(workspace: File, requestedName: String, wrapperJar: InputStream): File`
- MainActivity calls the helper and selects the returned root only after successful creation.

- [ ] Write tests for complete Gradle root/manifest/source/wrapper generation, name sanitization, duplicate-name rejection, and preservation of existing project contents.
- [ ] Run `gradle :app:testDebugUnitTest --tests '*ProjectTemplateWriterTest'`; confirm the new tests fail before implementation.
- [ ] Implement staged template generation with atomic rename and no-overwrite semantics.
- [ ] Update `MainActivity.createTemplate` to call the helper and preserve the existing selected project on failure.
- [ ] Run focused tests and full `gradle :app:testDebugUnitTest`.

### Task 2: Safe ZIP project import

**Files:**
- Create: `app/src/main/java/com/jepongdevxyz/idebuild/ProjectArchiveImporter.kt`
- Create: `app/src/test/java/com/jepongdevxyz/idebuild/ProjectArchiveImporterTest.kt`
- Modify: `app/src/main/java/com/jepongdevxyz/idebuild/MainActivity.kt`

**Interfaces:**
- Produces: `ProjectArchiveImporter.extract(input: InputStream, staging: File): File`
- The returned File is the unique Gradle root inside staging; MainActivity atomically moves it into workspace and calls `selectRoot` only after activation.

- [ ] Write tests for valid root ZIP, single nested Gradle root, traversal rejection, ambiguous roots, missing Gradle files, entry/depth/expanded-size limits, and cleanup on failure.
- [ ] Run focused importer tests and confirm expected RED failures.
- [ ] Implement bounded extraction, canonical-path validation, unique-root detection, and staging cleanup.
- [ ] Update `MainActivity.importProject` to use the helper and preserve the previously selected project on failure.
- [ ] Run focused tests and full `gradle :app:testDebugUnitTest`.

### Task 3: Transactional runtime-validated toolchain installation

**Files:**
- Modify: `app/src/main/java/com/jepongdevxyz/idebuild/ToolchainManager.kt`
- Modify: `app/src/test/java/com/jepongdevxyz/idebuild/ToolchainManagerTest.kt`

**Interfaces:**
- Preserve public `inspect(project: File?)`, `install(resolver: ContentResolver, uri: Uri)`, `environment()`, and `aapt2Path()` contracts.
- Runtime checks must use bounded process waits and return actionable diagnostics.

- [ ] Add tests for missing/non-executable tools and the validation helper's success, nonzero-exit, and timeout cases.
- [ ] Run focused tests and confirm RED.
- [ ] Implement executable checks and bounded `java -version` / `aapt2 version` probes.
- [ ] Make activation transactional: keep backup until validation succeeds; restore it on every failure path.
- [ ] Run focused tests and full unit suite.

### Task 4: CI and current-UI smoke verification

**Files:**
- Create: `scripts/smoke_devxyzide_emulator.py`
- Modify: `.github/workflows/android.yml`

- [ ] Add a smoke script that waits for emulator readiness, installs and launches the current package, confirms the foreground activity, navigates the current bottom tabs using current resource IDs, creates a uniquely named project, and verifies `workspace/<name>/settings.gradle.kts` through `run-as`.
- [ ] Run Python syntax checks and script-level tests locally/CI before wiring it into the workflow.
- [ ] Add the emulator job after lint, unit tests, APK assembly, package and signature checks; upload screenshots and diagnostics even on failure.
- [ ] Trigger the workflow on the repair branch and inspect every job/step/log. Fix only evidence-backed failures.
- [ ] Confirm APK artifact exists and package/signature checks pass; report hosted-emulator results separately from physical-device verification.

### Final review
- [ ] Review the full branch diff against current `main`, checking that no existing feature or Fragment UI was removed.
- [ ] Run the full CI workflow on the final branch head and inspect fresh results.
- [ ] Open a PR to `main`; do not merge or publish a release until the user approves after reviewing green CI.
