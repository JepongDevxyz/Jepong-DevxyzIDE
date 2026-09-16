# DevxyzIDE v0.6 AIDE-Compatible Release Candidate

**App name:** DevxyzIDE  
**Application ID:** `com.jepongdevxyz.idebuild`

DevxyzIDE is an Android-on-Android IDE project. This branch is the AIDE-compatible Phase 01 adaptation: it preserves the existing on-device runtime/build architecture while hardening workspace paths, project/file operations, responsive layouts, editor state, build/runtime capability detection, Git, signing, and release verification. It does not replace real build/runtime behavior with simulated output.

For the exact feature boundaries and verification gates, see [docs/CAPABILITY_MATRIX.md](docs/CAPABILITY_MATRIX.md) and [docs/VERIFICATION_REPORT.md](docs/VERIFICATION_REPORT.md).

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
- cancelable/progress-aware project import plus large-import stress coverage
- source-focused ZIP backup through Android Storage Access Framework
- project-relative path model and root-contained workspace resolution
- lazy current-directory project explorer rather than full recursive refresh scans
- real new-file/new-folder, rename, duplicate, and confirmed delete actions
- persisted Recent Projects
- production Java and Kotlin Android project templates used by CI end-to-end builds
- multi-tab editor session model with dirty state, Save All, tab close, close others, and close all
- real autosave on configured tab switch/close paths
- persistent editor font size, word wrap, tab width, and autosave settings
- literal current-file search/replace
- cancelable project-wide text search
- build output parsing into navigable Problems
- portrait stacked workspace and landscape side-by-side explorer/editor workspace
- project Gradle Wrapper detection
- Gradle / AGP / SDK / JDK requirement analysis
- Groovy and Kotlin DSL detection
- version catalog detection
- AndroidX / Kotlin / Compose / native-build indicators
- repository discovery and credential redaction in diagnostics
- centralized capability registry for Build, Terminal, Git, and APK signing UI states
- project-aware JDK / Gradle / Android SDK / `aapt2` capability selection
- build actions gated on the complete detected project toolchain rather than Gradle alone
- dual runtime layout support:
  - Devxyz toolchain packs under `files/toolchains/`
  - Termux-style runtime under `files/usr` + `files/home`
- persistent Gradle cache and explicit offline build support
- Android SDK and Android-host `aapt2` discovery
- verified runtime/toolchain pack installation with size + SHA-256 checks
- package-specific terminal bootstrap installer with path-traversal and applicationId validation
- runtime bootstrap import action
- real Git init/status/stage/unstage/commit/diff/branch/checkout/clone/fetch/pull/push backend operations
- real APK signing/verification workflow using an app-private `apksigner`
- APK/AAB build-output scanning with type, variant, size, modification time, and project-relative path reporting
- APK discovery and Android package-installer handoff

## Editor status

The AIDE-compatible host currently uses the platform Android `EditText` editor path. It does **not** currently ship Sora Editor in this branch. The current editor milestone provides open/save, multi-tab state, autosave settings, tab close actions, Save All, search/replace, project search, persisted font size, word wrap, and tab width. Rich language-server-grade completion and a Sora-class editor are outside this release claim and must not be represented as already implemented.

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

The build planner analyzes project requirements and selects compatible installed components when available. A project's own valid Gradle Wrapper remains authoritative where supported. Missing project-required runtime components are surfaced as install-needed states instead of enabling a build that is already known to be impossible.

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

No IDE can make an unavailable repository, invalid credentials, corrupt artifact, desktop-only plugin, incompatible toolchain combination, unsupported device ABI, or missing external dependency work. DevxyzIDE reports those limitations instead of fabricating a successful sync/build.

## Automated release verification

The full GitHub Actions release gate verifies:

1. portable Java host/runtime tests and Python source contracts;
2. a clean real DevxyzIDE debug APK build with Android SDK 28, Gradle 4.6, and JDK 8;
3. APK ZIP integrity, package badging, real Android SDK `apksigner` signing, and signature verification;
4. production-template Java and Kotlin sample generation followed by real Gradle 8.9 / Android SDK 35 APK builds;
5. cached offline clean rebuilds for both generated projects;
6. expected failure for an intentionally unavailable offline dependency;
7. navigable diagnostics from deliberately broken Java source;
8. API 28 emulator installation and launch of DevxyzIDE plus both generated sample APKs;
9. generation of `DevxyzIDE-verified-source.zip` only after the emulator gate passes, including exact workflow/commit metadata, ZIP integrity validation, and a SHA-256 checksum.

The automated gate proves the tested repository source, host APK, generated sample projects, build-cache behavior, signing path, and API 28 emulator install/launch path. It does **not** prove every arbitrary imported Gradle project.

Physical Android ARM64 verification of the optional full on-phone JDK/Gradle/SDK/native runtime remains a separate evidence boundary. That stronger on-device runtime claim requires an actual ARM64 Android run covering runtime installation, project Gradle execution, dependency resolution, Android-native build tools, generated APK/AAB, and install/launch of the project built inside DevxyzIDE.
