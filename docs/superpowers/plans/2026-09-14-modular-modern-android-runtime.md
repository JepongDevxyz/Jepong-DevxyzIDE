# Modular Modern Android Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add modular on-device JDK 17, Gradle 8.9+, Android SDK 35, and build-tools/aapt2 support so DevxyzIDE can select a compatible runtime for modern Android projects while preserving legacy Gradle 4.6 / SDK 28 support.

**Architecture:** Extend the existing requirement-analysis and toolchain-layout code rather than replacing it. Add a focused runtime catalog/requirement resolver, teach `RuntimeLayout` to validate SDK/platform/build-tools availability, and make `BuildRunner` fail early with exact missing-runtime messages before launching Gradle.

**Tech Stack:** Java 7-compatible Android app source, Gradle/AGP project analysis, app-private filesystem toolchains, HTTPS/SHA-256 runtime packs, GitHub Actions Android SDK/emulator verification.

**Spec:** `docs/superpowers/specs/2026-09-14-modular-modern-android-runtime-design.md`

## Global Constraints

- Preserve package `com.jepongdevxyz.idebuild` and app name DevxyzIDE.
- Preserve legacy Gradle 4.6 / Android SDK 28 compatibility.
- Modern baseline: JDK 17, Gradle 8.9+, Android SDK platform 35, compatible build-tools/aapt2.
- Runtime pack URLs must use HTTPS and packs must be SHA-256 verified before install.
- No lambda expressions, method references, AndroidX, or Java source features that break the AIDE-compatible source path.
- Do not claim full DevxyzMusic on-device build verification without actual Android runtime evidence producing the DevxyzMusic APK.

---

### Task 1: Runtime requirement model and compatibility tests

**Files:**
- Modify: `app/src/main/java/com/jepongdevxyz/idebuild/core/build/ProjectRequirements.java`
- Modify: `app/src/main/java/com/jepongdevxyz/idebuild/core/build/ProjectAnalyzer.java`
- Modify: `tests/HybridHostTest.java`

**Interfaces:**
- Produces: `ProjectRequirements.getJavaMajor()`, `getMinimumGradleVersion()`, `getCompileSdk()`.
- Consumes: Gradle build script text and existing AGP compatibility rules.

- [ ] **Step 1: Add failing host assertions**

Add a modern fixture using AGP `8.7.3` and `compileSdk 35`, asserting Java 17, Gradle 8.9, and compile SDK 35.

- [ ] **Step 2: Run host tests and confirm the new assertions fail**

Run the existing hybrid host-test command used by the repository and confirm the modern fixture is not yet fully represented.

- [ ] **Step 3: Extend the requirement model minimally**

Add an integer compile SDK field/getter and populate it by recognizing `compileSdk 35`, `compileSdkVersion 35`, and equivalent numeric forms. Preserve current AGP-to-Java/Gradle mappings.

- [ ] **Step 4: Run host tests**

Expected: all existing tests plus the new AGP 8.7.3 / SDK 35 fixture pass.

- [ ] **Step 5: Commit**

`git commit -m "feat: detect modern Android SDK requirements"`

### Task 2: Installed runtime capability resolver

**Files:**
- Create: `app/src/main/java/com/jepongdevxyz/idebuild/core/toolchain/RuntimeCapabilities.java`
- Modify: `app/src/main/java/com/jepongdevxyz/idebuild/core/toolchain/RuntimeLayout.java`
- Modify: `tests/HybridHostTest.java`

**Interfaces:**
- Produces: `RuntimeCapabilities.inspect(File appFilesDir, ProjectRequirements requirements)` and getters for Java home, Gradle executable, SDK directory, aapt2, readiness, and missing-requirement message.
- Consumes: `ProjectRequirements`, `RuntimeLayout.findJavaHome`, `findGradleExecutable`, `findAndroidSdk`, `findAapt2`.

- [ ] **Step 1: Add failing tests for missing and complete modern runtime layouts**

Use temp directories to model `toolchains/jdk17/bin/java`, `toolchains/gradle-8.9/bin/gradle`, `toolchains/android-sdk/platforms/android-35/android.jar`, and `toolchains/android-sdk/build-tools/35.0.0/aapt2`.

- [ ] **Step 2: Run tests and confirm failure**

Expected: `RuntimeCapabilities` is absent.

- [ ] **Step 3: Implement focused capability inspection**

Check Java major, minimum Gradle version, required SDK platform directory/android.jar, and aapt2. Return deterministic messages such as `JDK 17 required`, `Gradle 8.9+ required`, `Android SDK platform 35 missing`, or `Android build-tools/aapt2 missing`.

- [ ] **Step 4: Run host tests**

Expected: complete layout reports ready; each missing component reports the precise requirement.

- [ ] **Step 5: Commit**

`git commit -m "feat: resolve installed modern Android runtime"`

### Task 3: Runtime pack catalog metadata

**Files:**
- Create: `app/src/main/java/com/jepongdevxyz/idebuild/core/toolchain/RuntimePackCatalog.java`
- Modify: `app/src/main/java/com/jepongdevxyz/idebuild/core/toolchain/RuntimePackDescriptor.java`
- Modify: `tests/HostSelfTest.java`

**Interfaces:**
- Produces: catalog lookup by component/version/ABI returning one or more `RuntimePackDescriptor` objects.
- Consumes: existing descriptor validation rules.

- [ ] **Step 1: Add failing validation tests**

Assert the catalog rejects unsupported ABI, insecure HTTP descriptors, invalid hashes, and unknown components.

- [ ] **Step 2: Run host tests and confirm failure**

- [ ] **Step 3: Implement catalog parsing without hard-coded unverified binaries**

Catalog entries should be properties-backed metadata that can later point to published verified packs. Code must support `jdk`, `gradle`, `android-sdk-platform`, and `android-build-tools` component names.

- [ ] **Step 4: Run host tests**

Expected: all catalog and descriptor safety tests pass.

- [ ] **Step 5: Commit**

`git commit -m "feat: add modular runtime pack catalog"`

### Task 4: Build preflight and environment selection

**Files:**
- Modify: `app/src/main/java/com/jepongdevxyz/idebuild/BuildRunner.java`
- Modify: `app/src/main/java/com/jepongdevxyz/idebuild/core/build/BuildPlanner.java`
- Modify: `tests/HybridHostTest.java`

**Interfaces:**
- Consumes: `RuntimeCapabilities.inspect(...)`.
- Produces: build command/environment using matching `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `GRADLE_USER_HOME`, compatible Gradle executable, and optional aapt2 override.

- [ ] **Step 1: Add failing planner tests**

Assert a modern project with incomplete runtime is rejected before process launch with the exact missing-runtime message; assert a complete modern runtime chooses Gradle 8.9 and JDK 17; assert legacy project behavior remains valid.

- [ ] **Step 2: Run tests and confirm failure**

- [ ] **Step 3: Implement preflight selection**

Prefer project wrapper when valid. Otherwise select internal Gradle meeting the minimum. Export Android SDK variables and Java home from the resolved capability object. Keep persistent Gradle cache behavior.

- [ ] **Step 4: Run host tests**

Expected: modern and legacy paths pass.

- [ ] **Step 5: Commit**

`git commit -m "feat: preflight modern runtime before Gradle build"`

### Task 5: Runtime UI status and install guidance

**Files:**
- Modify: `app/src/main/java/com/jepongdevxyz/idebuild/MainActivity.java`
- Modify: `app/src/main/res/layout/activity_main.xml` only if an existing runtime-status view cannot be reused.

**Interfaces:**
- Consumes: missing requirement string from `RuntimeCapabilities` / planner.
- Produces: user-visible exact runtime status and install/download guidance.

- [ ] **Step 1: Add source-contract assertions for required messages**

Add repository tests checking that missing JDK, Gradle, SDK platform, and build-tools messages remain exposed to users.

- [ ] **Step 2: Confirm the contract test fails if the UI path is absent**

- [ ] **Step 3: Wire exact preflight messages into the existing Build/Runtime console flow**

Do not silently start a knowingly incompatible build. Preserve existing Toolchains/Runtime buttons and legacy behavior.

- [ ] **Step 4: Run host/source-contract tests**

- [ ] **Step 5: Commit**

`git commit -m "feat: show exact missing runtime requirements"`

### Task 6: CI verification of full DevxyzIDE app

**Files:**
- Modify: `.github/workflows/android-full-verification.yml` only if additional host-test steps are needed.

**Interfaces:**
- Consumes: all previous implementation tasks.
- Produces: fresh build, APK validation, API 28 emulator install/launch evidence.

- [ ] **Step 1: Run all host/source-contract tests**

Expected: all pass.

- [ ] **Step 2: Push the implementation commit(s)**

- [ ] **Step 3: Wait for fresh GitHub Actions run**

Required successful jobs: `Build APK with Android SDK 28` and `Install and launch on Android emulator`.

- [ ] **Step 4: Inspect failed logs if any step is red and fix root cause before rerunning**

- [ ] **Step 5: Record verification result**

Report the exact run ID/commit and explicitly distinguish full DevxyzIDE app verification from future on-device DevxyzMusic APK production.
