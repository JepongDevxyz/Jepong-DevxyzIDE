# DevxyzIDE 2026 Build Core Design

## Goal

DevxyzIDE is a clean-room Android-on-Android IDE with AndroidIDE-class project compatibility while keeping the imported project's own Gradle configuration authoritative. The IDE must support both legacy AndroidX projects and current 2026 Android projects without rewriting them to DevxyzIDE's own AGP/Gradle versions.

## Scope of this milestone

This milestone hardens the build core and toolchain management. It does not claim feature parity with Android Studio or the archived AndroidIDE UI yet.

## Architecture

1. **Project analyzer** reads Gradle Wrapper, AGP, SDK levels, Kotlin/Compose/native-build indicators, version catalogs, repository declarations, and wrapper completeness without executing project code.
2. **Compatibility engine** chooses a compatible installed JDK from app-private toolchains based on both AGP and Gradle runtime constraints.
3. **Toolchain inventory** scans app-private JDKs, Android SDK platforms/build-tools, Android-host aapt2, NDK/CMake, and optional command-line tools.
4. **Toolchain pack installer** accepts a DevxyzIDE toolchain ZIP with a strict manifest and SHA-256 checks, rejects path traversal, then atomically installs it under the app-private toolchains directory.
5. **Build planner** constructs Gradle Wrapper arguments/environment, including persistent GRADLE_USER_HOME, Android SDK variables, Android-host aapt2 override, offline mode, and caller-selected Gradle task.
6. **Build runner** executes only the imported project's Gradle Wrapper and never injects arbitrary Maven repositories into the project. Repository/auth behavior remains Gradle-native.

## Compatibility policy

- Project Gradle Wrapper is authoritative.
- DevxyzIDE selects a compatible installed JDK rather than forcing one global JDK.
- `google()`, `mavenCentral()`, `mavenLocal()`, Gradle Plugin Portal, custom HTTPS Maven repositories, version catalogs, and project-defined credentials remain normal Gradle behavior.
- DevxyzIDE reports repository URLs with credentials redacted.
- Android build requires an Android-host-compatible aapt2; desktop Linux binaries are not assumed to execute on Android.
- Missing or incompatible components produce explicit preflight blockers rather than false success.

## Security

- ZIP imports and toolchain-pack imports reject path traversal.
- Toolchain packs have a file-count and expanded-size limit.
- Toolchain pack files can be SHA-256 verified from their manifest.
- URL credentials are never shown in project diagnostics.
- DevxyzIDE does not silently rewrite Gradle repository definitions or inject credentials.

## Verification

Host tests cover project analysis, Maven repository detection/redaction, version-catalog parsing, JDK selection, toolchain inventory, toolchain-pack validation/install, build planning, ZIP safety, and APK location. Android-device end-to-end verification remains a separate gate because this execution environment has no Android SDK/device runtime.
