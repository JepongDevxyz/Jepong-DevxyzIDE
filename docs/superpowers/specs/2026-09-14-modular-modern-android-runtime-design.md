# DevxyzIDE Modular Modern Android Runtime Design

## Goal
Enable DevxyzIDE to build modern Android projects on-device by provisioning and selecting compatible app-private toolchains, initially targeting JDK 17, Gradle 8.9, Android SDK 35, and Android build-tools/aapt2.

## Approved approach
Use modular runtime packs rather than one monolithic bundle. JDK, Gradle, Android SDK/platform/build-tools, and aapt2 are independently installable, versioned, HTTPS-downloaded, SHA-256 verified, and reusable across projects.

## Architecture

### Runtime requirement detection
Project analysis produces requirements including Java major version, minimum Gradle version, and Android compile SDK requirement. DevxyzIDE compares those requirements with installed toolchains before starting a build.

For an AGP 8.7.x project such as DevxyzMusic, the expected baseline is Java 17 and Gradle 8.9 or newer. If compileSdk 35 is requested, SDK platform 35 and compatible build-tools/aapt2 must exist.

### Modular toolchain layout
App-private runtime components are installed beneath `files/toolchains/`:

- `toolchains/jdk17/`
- `toolchains/gradle-8.9/`
- `toolchains/android-sdk/platforms/android-35/`
- `toolchains/android-sdk/build-tools/<version>/`
- optional explicit `toolchains/aapt2/aapt2`

Existing legacy locations remain supported for backward compatibility.

### Runtime catalog
A small catalog maps a requirement to one or more runtime-pack descriptors. Each descriptor contains component, version, ABI, HTTPS URL, expected size, and SHA-256. The catalog must not silently accept HTTP or unverified payloads.

### Download and installation
`RuntimePackDownloader` remains responsible for HTTPS streaming download, declared-size enforcement, SHA-256 verification, and atomic finalization. `ToolchainPackInstaller` remains responsible for safe extraction and path traversal protection.

Runtime packs are installed independently so updating Gradle does not require redownloading JDK or Android SDK.

### Build selection
`BuildPlanner` resolves the project requirements. `RuntimeLayout` resolves matching installed components. `BuildRunner` exports:

- `JAVA_HOME`
- `ANDROID_HOME`
- `ANDROID_SDK_ROOT`
- persistent `GRADLE_USER_HOME`
- Android-compatible `aapt2` override only when needed

Wrapper builds remain preferred when the wrapper satisfies project requirements. If no wrapper exists, DevxyzIDE selects the newest compatible internal Gradle.

### User experience
When a required runtime is missing, DevxyzIDE should report the exact missing requirement instead of producing a generic build failure, e.g. `Gradle 8.9+ required` or `Android SDK platform 35 missing`.

Runtime installation remains explicit. DevxyzIDE must not execute an unverified downloaded archive.

### Compatibility
Legacy Android/AIDE-compatible projects continue to use older installed JDK/Gradle/SDK combinations. The modern runtime path is additive and must not remove Gradle 4.6 or SDK 28 compatibility.

## Error handling
Downloads fail closed on HTTPS, size, or digest mismatch. Runtime selection fails before Gradle execution if required JDK, Gradle, SDK platform, or build-tools are missing. Existing valid runtime components are not deleted when a new component download fails.

## Verification
Verification has four layers:

1. Host tests for requirement detection and runtime selection.
2. Android app `assembleDebug` in GitHub Actions.
3. APK integrity/package verification and install/launch on API 28 emulator.
4. A modern-project fixture representing DevxyzMusic requirements, proving JDK 17 / Gradle 8.9 / SDK 35 selection logic. Full on-device DevxyzMusic build is only claimed after an Android runtime test actually executes the modern toolchain and produces its APK.
