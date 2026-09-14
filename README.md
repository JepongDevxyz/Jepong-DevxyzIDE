# DevxyzIDE v0.6 Runtime + Editor Source

**App name:** DevxyzIDE  
**Application ID:** `com.jepongdevxyz.idebuild`

DevxyzIDE is an Android-on-Android IDE project. v0.6 moves the project from the early EditText prototype toward an AndroidIDE-class architecture: project-owned Gradle wrappers, project-aware JDK/SDK selection, verified runtime provisioning, a package-specific terminal bootstrap path, and Sora Editor 0.24.6.

## Host application configuration

DevxyzIDE itself intentionally uses:

- `compileSdk 37`
- `minSdk 28`
- `targetSdk 28`
- Java source/target 17
- Android Gradle Plugin 9.4.0
- Gradle distribution target 9.6.0

The host `targetSdk 28` is deliberate for the app-private executable/toolchain model. It does **not** rewrite imported projects. A project opened in DevxyzIDE keeps its own `compileSdk`, `minSdk`, `targetSdk`, AGP, Gradle Wrapper, repositories, dependencies, and build scripts.

## Implemented in this source snapshot

- Jepong Devxyz launcher icon and splash screen
- secure ZIP project import with ZIP-slip protection
- generated/cache filtering (`build/`, `.gradle/`, machine-local `local.properties`)
- project explorer
- Sora Editor 0.24.6 with built-in line numbers and Java language highlighting
- portrait and landscape CodeEditor layouts
- open/save text and source files
- project Gradle Wrapper detection
- Gradle / AGP / SDK requirement analysis
- Groovy and Kotlin DSL detection
- version catalog detection
- AndroidX / Kotlin / Compose / native-build indicators
- Maven repository discovery without rewriting repository declarations
- repository credential redaction in diagnostics
- JDK compatibility selection for older and current Gradle/AGP lines
- dual runtime layout support:
  - Devxyz toolchain packs under `files/toolchains/`
  - Termux-style runtime under `files/usr` + `files/home`
- persistent Gradle dependency/wrapper cache
- Android SDK and Android-host `aapt2` discovery
- custom Gradle tasks and offline mode
- NDK/CMake detection
- verified runtime/toolchain packs with size + SHA-256 validation
- package-specific terminal bootstrap installer with path-traversal and applicationId checks
- runtime bootstrap UI import action
- APK discovery and Android package-installer handoff

## Modern editor

The source pins:

```gradle
implementation 'io.github.rosemoe:editor:0.24.6'
implementation 'io.github.rosemoe:language-java:0.24.6'
```

The old separate line-number `EditText` implementation has been removed from both portrait and landscape layouts.

## Runtime layouts

DevxyzIDE supports both its component-pack layout and the Termux-style layout used by maintained Android-on-Android IDE architectures.

```text
files/
├── usr/
│   ├── bin/
│   └── lib/jvm/java-17-openjdk/   # or other installed JDK
├── home/
│   ├── .gradle/
│   └── android-sdk/
│       ├── platforms/
│       ├── build-tools/
│       ├── ndk/
│       └── cmake/
└── toolchains/                    # component-pack fallback
    ├── jdk11/
    ├── jdk17/
    ├── jdk21/
    ├── android-sdk/
    └── aapt2/
```

The build planner picks a compatible installed JDK and keeps the imported project's own Gradle Wrapper authoritative.

## Building a DevxyzIDE-prefixed terminal runtime

Do **not** install a Termux/IDE bootstrap that was compiled for another Android application id. The fixed Android app data prefix is part of the runtime package build.

`runtime-builder/build-devxyz-terminal-runtime.sh` pins the maintained `appdevforall/terminal-packages` source revision used for this snapshot and invokes its supported `-p` option with:

```text
com.jepongdevxyz.idebuild
```

Run its `plan` command without doing a build:

```sh
./runtime-builder/build-devxyz-terminal-runtime.sh plan
```

A real runtime build requires Linux build dependencies, network access, and an exported **public** GPG key. The script does not ask for or store a private signing key.

Generated bootstrap archives are stamped by `tools/runtime/stamp_bootstrap.py`; DevxyzIDE refuses a stamped bootstrap whose `applicationId` does not exactly match the app.

## Creating a verified component pack

`tools/runtime/make_toolchain_pack.py` converts an already prepared JDK / SDK / aapt2 / NDK / CMake directory into the double-verified pack format used by `ToolchainPackInstaller`.

Example:

```sh
python3 tools/runtime/make_toolchain_pack.py \
  --input ./jdk17-android-arm64 \
  --target toolchains/jdk17 \
  --output ./jdk17-arm64.devxyz-toolchain.zip
```

## Maven behavior

DevxyzIDE does not maintain a fake whitelist of Maven libraries. The imported Gradle project keeps `google()`, `mavenCentral()`, `mavenLocal()`, custom Maven repositories, version catalogs, plugin repositories, authentication declarations, and dependency coordinates. Gradle performs normal dependency resolution using DevxyzIDE's persistent `GRADLE_USER_HOME`.

No IDE can guarantee that a dead repository, invalid credentials, corrupt artifact, desktop-only plugin, incompatible AGP/JDK combination, or unsupported native ABI will work. DevxyzIDE's goal is compatibility with valid Android/Gradle projects supported by their own declared toolchains.

## Verification boundary

The portable Java/core logic and source contracts are tested in this workspace. This environment does not provide an Android ARM device/runtime or a full Android SDK installation, so it cannot prove the final on-phone chain here:

```text
DevxyzIDE APK
→ install DevxyzIDE-prefixed ARM runtime
→ install Android SDK / Android-host aapt2
→ run imported project's Gradle Wrapper
→ resolve Maven dependencies
→ assembleDebug / bundle task
→ produce APK/AAB
→ verify/install/run
```

That physical-device test is the next hard gate before calling DevxyzIDE a complete AndroidIDE replacement.
