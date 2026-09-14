# DevxyzIDE ARM Runtime Pack Builder Design

## Purpose

Build reproducible Android-hosted runtime packs for DevxyzIDE so modern Android projects can eventually build on-device with a compatible JDK, Gradle, Android SDK platform, and native `aapt2` without relying on desktop Linux binaries or unverified downloads.

This phase extends the existing modular runtime-pack architecture. It does not change the DevxyzIDE application identity, does not merge to `main`, and does not claim end-to-end DevxyzMusic APK production until an actual Android runtime build succeeds.

## Chosen approach

Use a dedicated GitHub Actions runtime-pack pipeline on `feature/modern-runtime-packs` that builds Android-native package payloads from pinned upstream package sources, assembles DevxyzIDE-specific archives, verifies them, emits SHA-256 metadata, and publishes CI artifacts for inspection. Release publication becomes enabled only after the build outputs have passed the required verification gates.

The pipeline is intentionally split into two layers:

1. **Native bootstrap layer** — Android/Termux-style runtime prefix built for DevxyzIDE's package identity, initially targeting `aarch64`, then `arm` once the ARM64 path is proven. The bootstrap must include or be able to install OpenJDK and Android-native build tools such as `aapt2`.
2. **Portable component layer** — Gradle distributions and Android SDK platform metadata packed separately under `files/toolchains/`, with exact size and SHA-256 descriptors compatible with the existing `RuntimePackDescriptor` and `RuntimePackDownloader` flow.

## Upstream baseline and pinning

The native package build must use a pinned upstream commit rather than an unbounded branch. The currently inspected Code On the Go / AndroidIDE package-builder baseline is `appdevforall/terminal-packages` commit `610af608b4a3b1127244e90edf8b7be2bf94fafa`, which already builds Termux-derived packages for Android application prefixes and supports `aarch64` and `arm`.

The workflow may patch configuration values only for DevxyzIDE-specific identity and output layout. It must not silently download opaque prebuilt executables from arbitrary third-party hosts.

Gradle distributions must come from official Gradle distribution endpoints. Android SDK platform artifacts must come from official Android/Google SDK repositories or be assembled from packages fetched by `sdkmanager` in CI. Every published pack must record its exact upstream version and SHA-256.

## Application identity and prefix

All Android-native payloads must be compatible with the DevxyzIDE app-private prefix associated with:

```text
applicationId=com.jepongdevxyz.idebuild
```

The accepted bootstrap continues to use the existing `devxyz-bootstrap.properties` contract and the existing safe installer. Absolute symlink targets and native package prefix paths must resolve only inside the DevxyzIDE private runtime tree.

No `com.termux` or `com.itsaky.androidide` absolute runtime prefix may be accepted as a production DevxyzIDE pack.

## Target runtime baseline

The first complete modern runtime target is the DevxyzMusic requirement set already detected by DevxyzIDE:

```text
JDK runtime: 17 or newer compatible runtime
Gradle: 8.9 or newer compatible version
Android SDK platform: 35
Android build tool: Android-host-compatible aapt2
ABI: arm64-v8a first
```

OpenJDK 21 is acceptable as the shipped Android-host JDK when the project only requires JDK 17, because DevxyzIDE's compatibility resolver already treats newer compatible JDKs as satisfying the minimum requirement. The project remains responsible for its own AGP and dependency declarations.

## Repository structure

Add a focused runtime builder area instead of mixing package-builder logic into Android application source:

```text
tools/runtime-builder/
  README.md
  build-bootstrap.sh
  assemble-component-pack.sh
  generate-manifest.py
  verify-pack.py
  patches/
  config/
    runtime-pack.properties

.github/workflows/
  android-runtime-packs.yml
```

The Android app continues to consume descriptors through the existing runtime-pack APIs. The builder owns only production and verification of payloads.

## Workflow triggering

The workflow must support both:

- `workflow_dispatch` for deliberate manual builds from GitHub UI.
- `push` on `feature/modern-runtime-packs` limited to runtime-builder files, so commits made through the connected GitHub integration can automatically start the pipeline even when no workflow-dispatch action is available to ChatGPT.

A source-only change outside runtime-builder paths must not rebuild heavyweight ARM toolchains unnecessarily.

## Build stages

### Stage 1: Source and configuration validation

The job checks out the DevxyzIDE branch and pinned package-builder source, verifies that the expected pinned commit is checked out, applies only reviewed DevxyzIDE prefix/config patches, and records upstream commit metadata.

### Stage 2: ARM64 native bootstrap build

Build the `aarch64` runtime using the package builder's supported container/build mechanism. The result must contain the expected Termux-style runtime tree and `SYMLINKS.txt` data.

The production bootstrap must contain or provide Android-native executables needed by the build path, including `aapt2`. A JDK package satisfying the modern baseline must be present in the generated runtime or in a separately generated native JDK component pack.

### Stage 3: DevxyzIDE stamping

Run the existing DevxyzIDE bootstrap stamping/validation logic so the archive contains:

```properties
format=1
applicationId=com.jepongdevxyz.idebuild
arch=aarch64
```

The stamped archive must satisfy `TerminalBootstrapInstaller` path and symlink rules.

### Stage 4: Portable Gradle 8.9 pack

Fetch Gradle 8.9 from the official distribution source, verify the upstream checksum where available, and repack it in DevxyzIDE's component layout:

```text
toolchains/gradle-8.9/bin/gradle
```

The pack includes `devxyz-toolchain.properties` with per-file SHA-256 entries.

### Stage 5: Android SDK 35 component pack

Use official Android SDK tooling in CI to install the minimum required SDK platform/build metadata for API 35. Do not treat desktop host binaries such as Linux `aapt2` as usable on Android. Native `aapt2` comes from the Android-native bootstrap/toolchain path.

The SDK component pack is laid out under:

```text
toolchains/android-sdk/platforms/android-35/android.jar
```

Additional files are included only when required by Gradle/AGP at runtime.

### Stage 6: Manifest generation

Generate a machine-readable catalog/manifest containing for each artifact:

```text
component
version
abi
filename
exact byte size
sha256
source URL or upstream provenance
source commit/version
```

No production descriptor may contain a placeholder URL, zero hash, `example.invalid`, or insecure HTTP URL.

## Verification gates

A runtime pack is considered CI-verified only if all relevant gates pass:

1. Archive integrity check succeeds.
2. Exact SHA-256 and byte-size metadata are generated from the final bytes.
3. Bootstrap identity is `com.jepongdevxyz.idebuild`.
4. Bootstrap architecture matches its artifact (`aarch64` initially).
5. No absolute runtime path targets another app package prefix.
6. Expected JDK executable exists in a supported `RuntimeLayout` location.
7. Expected Gradle executable exists in `toolchains/gradle-8.9/bin/gradle`.
8. `platforms/android-35/android.jar` exists in the SDK pack.
9. Android-native `aapt2` exists in a path already recognized by `RuntimeLayout`.
10. Existing DevxyzIDE host/source-contract tests remain green.
11. Existing DevxyzIDE APK build and API 28 emulator install/launch workflow remains green after catalog integration.

A GitHub-hosted x86_64 runner cannot prove that ARM64 native executables launch on a real Android ARM64 device. Therefore the first CI artifact is labeled **built and structurally verified**, not **on-device execution verified**.

## Publishing model

### CI artifact phase

Initial successful runtime builds are uploaded as GitHub Actions artifacts with their manifest and checksum files. This allows inspection without immediately wiring an unproven payload into production downloads.

### Release phase

After the ARM64 artifacts pass structural verification, a release-publish job may create/update a clearly marked prerelease runtime bundle. DevxyzIDE's production runtime catalog is updated only with immutable HTTPS release-asset URLs and exact SHA-256/size values produced by the same build.

The app must never auto-install a runtime artifact solely because a CI build exists.

## Error handling

- Upstream commit mismatch: fail immediately.
- Native package build failure: retain logs as CI artifacts; do not generate production descriptors.
- Missing JDK/aapt2/SDK file: fail verification.
- Hash or size mismatch: fail verification.
- Prefix mismatch: fail verification.
- Unsupported ABI: do not publish descriptor.
- Release upload failure: keep verified CI artifacts but leave production catalog unchanged.

## Compatibility and scope

Legacy Gradle 4.6 / SDK 28 behavior remains unchanged. Existing manually imported toolchain/runtime packs remain supported.

This phase does not add Kotlin/Compose-specific logic, NDK/CMake packs, x86 Android-device packs, or arbitrary package-manager UI. Those are separate milestones if future projects require them.

## Success criteria

This phase is complete when:

1. A fresh GitHub Actions runtime-pack run produces an `aarch64` DevxyzIDE bootstrap plus Gradle 8.9 and Android SDK 35 component artifacts.
2. The run emits exact SHA-256 and byte-size metadata and all structural/prefix checks pass.
3. The existing DevxyzIDE app verification remains green.
4. The generated catalog can be consumed by the current runtime-pack downloader without placeholders or insecure URLs once release publication is enabled.
5. The result is still described as **not yet DevxyzMusic end-to-end verified** until an actual Android ARM64 runtime launches the toolchain and produces the DevxyzMusic APK.
