# DevxyzIDE v0.5 Runtime Provisioning Design

## Goal

Make DevxyzIDE's on-device execution model compatible with Android's executable-file restrictions and add deterministic planning for the app-private JDK/Android SDK/aapt2 runtime required by each imported project.

## Verified 2026 constraints

- The IDE host app keeps a current compile SDK but targets API 28 so app-private Gradle/JDK/Termux-style executables can run on Android devices that enforce W^X for target 29+ apps.
- Imported projects keep their own compileSdk/targetSdk/AGP/Gradle versions; the host targetSdk does not rewrite them.
- The imported project's Gradle Wrapper remains authoritative.
- Maven repositories remain Gradle-native; DevxyzIDE does not inject arbitrary repositories.

## Architecture

1. `ExecutionPolicy` documents and tests the API-28 host execution constraint.
2. `ToolchainInventory` gains exact component query helpers.
3. `ToolchainProvisioningPlan` turns `ProjectRequirements` + inventory into an explicit set of missing runtime components.
4. `RuntimePackDescriptor` parses a verified HTTPS runtime-pack descriptor, including ABI, target path, size ceiling, and SHA-256.
5. `RuntimePackDownloader` downloads atomically with HTTPS-only policy and SHA-256 verification before the existing inner toolchain-pack verifier extracts it.
6. `BuildPlanner` keeps builds blocked until the provisioning plan has no required missing components.

## Compatibility policy

- Legacy AGP projects may use JDK 11 or a later compatible JDK selected by the existing compatibility engine.
- Current AGP 9.x projects prefer JDK 17 but may use compatible newer installed JDKs where Gradle supports them.
- Android projects require the detected compileSdk platform and Android-host-compatible aapt2.
- Native projects additionally require an NDK; CMake is required when a CMake project is detected.

## Verification

Host tests must prove the execution policy, provisioning plan, runtime descriptor validation, download checksum enforcement using local test transport, existing ZIP safety, project analysis, and build planning. Android-device execution remains a separate gate because this environment is Linux/x86_64 rather than Android/ARM.
