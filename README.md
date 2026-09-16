# DevxyzIDE v0.6 AIDE-Compatible Test Source

**App name:** DevxyzIDE  
**Application ID:** `com.jepongdevxyz.idebuild`

DevxyzIDE is an Android-on-Android IDE project. This branch is the AIDE-compatible Phase 01 adaptation: it preserves the existing on-device runtime/build architecture while hardening workspace paths, project/file operations, responsive layouts, and editor settings. It does not replace real build/runtime behavior with simulated output.

## Host application configuration

The DevxyzIDE host APK in this branch currently uses:

- `compileSdkVersion 28`
- `minSdkVersion 21`
- `targetSdkVersion 28`
- Java source/target 7
- Android Gradle Plugin 3.2.1
- no external UI/editor Maven dependency in the host app

GitHub Actions builds the host APK with Gradle 4.6 and JDK 8 after running the portable host/runtime tests under JDK 17. These host settings do **not** rewrite imported projects. Imported Android projects keep their own Gradle scripts, repositories, dependencies, SDK requirements, and Gradle Wrapper where supported.

## Implemented in this source snapshot

- Jepong Devxyz launcher icon and splash screen
- secure ZIP project import with ZIP-slip/path containment protection
- source-focused ZIP backup through Android Storage Access Framework
- project-relative path model and root-contained workspace resolution
- lazy current-directory project explorer rather than full recursive refresh scans
- real new-file/new-folder, rename, duplicate, and confirmed delete actions
- classic Java and AndroidX Java project templates
- multi-tab editor session model with dirty state and Save All
- literal current-file search/replace
- cancelable project-wide text search
- build output parsing into navigable Problems
- portrait stacked workspace and landscape side-by-side explorer/editor workspace
- persistent editor font-size and word-wrap settings
- project Gradle Wrapper detection
- Gradle / AGP / SDK requirement analysis
- Groovy and Kotlin DSL detection
- version catalog detection
- AndroidX / Kotlin / Compose / native-build indicators
- repository discovery and credential redaction in diagnostics
- project-aware JDK / Gradle / Android SDK capability selection
- dual runtime layout support:
  - Devxyz toolchain packs under `files/toolchains/`
  - Termux-style runtime under `files/usr` + `files/home`
- persistent Gradle cache support
- Android SDK and Android-host `aapt2` discovery
- offline/custom Gradle task support in the build layer
- verified runtime/toolchain pack installation with size + SHA-256 checks
- package-specific terminal bootstrap installer with path-traversal and applicationId validation
- runtime bootstrap import action
- APK discovery and Android package-installer handoff

## Editor status

The AIDE-compatible host currently uses the platform Android `EditText` editor path. It does **not** currently ship Sora Editor in this branch. The current editor milestone provides open/save, multi-tab state, Save All, search/replace, project search, persisted font size, and word wrap. Rich language-server-grade completion, line-number rendering, deeper syntax services, and other advanced editor features remain later milestones and must not be treated as already implemented.

## Runtime layouts

DevxyzIDE recognizes both a Termux-style app-private runtime and component packs:

```text
files/
├── usr/
│   ├── bin/
│   └── lib/jvm/
├── home/
│   ├── .gradle/
│   └── android-sdk/
│       ├── platforms/
│       ├── build-tools/
│       ├── ndk/
│       └── cmake/
└── toolchains/
    ├── jdk11/
    ├── jdk17/
    ├── jdk21/
    ├── android-sdk/
    └── aapt2/
```

The build planner analyzes project requirements and selects compatible installed components when available. A project's own valid Gradle Wrapper remains authoritative where supported.

## DevxyzIDE-prefixed terminal runtime

Do **not** install a Termux/IDE bootstrap compiled for another Android application ID. The app data prefix is part of the native runtime build.

`runtime-builder/build-devxyz-terminal-runtime.sh` pins the maintained `appdevforall/terminal-packages` source revision used by this project and invokes its package-specific build path for:

```text
com.jepongdevxyz.idebuild
```

Plan the runtime build without compiling it:

```sh
./runtime-builder/build-devxyz-terminal-runtime.sh plan
```

A real native runtime build requires its documented Linux dependencies and network/source inputs. Bootstrap archives are stamped by `tools/runtime/stamp_bootstrap.py`, and DevxyzIDE rejects a stamped bootstrap whose `applicationId` does not exactly match the app.

## Verified component packs

`tools/runtime/make_toolchain_pack.py` converts an already prepared JDK / SDK / Android-native tool directory into the verified pack format consumed by `ToolchainPackInstaller`.

Example:

```sh
python3 tools/runtime/make_toolchain_pack.py \
  --input ./jdk17-android-arm64 \
  --target toolchains/jdk17 \
  --output ./jdk17-arm64.devxyz-toolchain.zip
```

The pack installer validates declared size/hashes before accepting executable/toolchain content.

## Maven and imported-project behavior

DevxyzIDE does not fake a library whitelist. Imported projects retain their declared Gradle repositories, version catalogs, plugin repositories, dependency coordinates, and credentials configuration. Gradle performs dependency resolution when a compatible runtime is actually available.

No IDE can make an unavailable repository, invalid credentials, corrupt artifact, desktop-only plugin, incompatible toolchain combination, or unsupported device ABI work. DevxyzIDE should report those limitations instead of fabricating a successful sync/build.

## Current automated verification

The repository workflow currently verifies the host application with:

1. portable host/runtime/source-contract tests;
2. a clean real debug APK build;
3. APK existence, ZIP integrity, and package badging checks;
4. uploaded APK artifact;
5. API 28 Android emulator installation and launcher/activity startup.

Those checks verify the **DevxyzIDE host APK**. They do not prove every imported Android project or the full ARM64 on-phone toolchain. The remaining hard gate for a complete AndroidIDE-class claim is physical Android ARM64 evidence covering runtime installation, project Gradle execution, dependency resolution, Android-native build tools, generated APK/AAB, and install/run of the project produced inside DevxyzIDE.
