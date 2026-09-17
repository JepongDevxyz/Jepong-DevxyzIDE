# DevxyzIDE Phase 01 Verification Report

Date: 2026-09-16  
Application ID: `com.jepongdevxyz.idebuild`

## Verified automated release gate

The current release-candidate workflow verifies the exact branch commit before producing the verified-source artifact.

### 1. Host/runtime verification

`host-tests/run.sh` compiles and executes the portable Java host tests and Python source contracts. Coverage includes safe ZIP import, project paths and file operations, project templates, build planning, APK/AAB output scanning, runtime/toolchain selection, editor state/settings/search, diagnostics, process cancellation, terminal planning, real Git operations, APK signing helpers, cache maintenance, syntax helpers and completion helpers.

### 2. Real DevxyzIDE host APK build

GitHub Actions builds DevxyzIDE with the repository's AIDE-compatible host configuration:

- Android SDK 28
- Android Build Tools 28.0.3
- Gradle 4.6
- Android Gradle Plugin 3.2.1
- JDK 8 for the host build

The workflow then verifies:

- `app-debug.apk` exists and is structurally valid;
- package ID is `com.jepongdevxyz.idebuild`;
- a CI copy can be signed with the real Android SDK `apksigner`;
- the signed APK passes signature verification.

### 3. Generated-project end-to-end builds

The workflow generates Java and Kotlin sample Android projects using DevxyzIDE production template code, then builds both with Gradle 8.9 and Android SDK 35. It additionally verifies cached offline rebuilds, a deliberate missing-dependency failure in offline mode, navigable diagnostics from deliberately broken Java source, APK ZIP integrity and the generated package IDs.

### 4. Android emulator install and launch

After the host and generated-project jobs pass, the workflow boots an API 28 x86_64 Android emulator and verifies installation and launch of:

- DevxyzIDE;
- the generated Java sample;
- the generated Kotlin sample.

### 5. Verified-source packaging

Only after the emulator gate passes, CI exports the exact tested commit into `DevxyzIDE-verified-source.zip`, embeds verification metadata, runs a ZIP integrity test, generates a SHA-256 checksum and uploads the result as a workflow artifact.

## Current editor/runtime scope

The AIDE-compatible host intentionally uses the platform Android editor path rather than claiming a bundled Sora/LSP editor. Multi-tab editing, Save All, autosave, font size, word wrap, tab width, search/replace, project search, syntax helpers and completion helpers are implemented within the current host architecture.

Build, Terminal, Git and APK signing are capability-gated. They are enabled only when the required real project/runtime tools are available; missing components are reported instead of simulating success.

## Physical ARM64 boundary

The automated release gate proves the tested source, host APK build, generated Java/Kotlin project builds, offline cache behavior, APK signing/validation and API 28 emulator install/launch path.

The optional full on-phone JDK/Gradle/Android SDK/native runtime still requires an actual ARM64 Android device for final device-specific evidence. That separate physical test must cover runtime installation, project Gradle execution, dependency resolution, APK/AAB generation and install/launch of the project built inside DevxyzIDE. Arbitrary third-party projects can also fail for external reasons such as unavailable repositories, invalid credentials, unsupported plugins or incompatible ABIs.
