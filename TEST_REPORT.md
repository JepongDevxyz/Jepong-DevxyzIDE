# DevxyzIDE v0.6 Verification Report

Date: 2026-09-14  
Application ID: `com.jepongdevxyz.idebuild`

## Host verification suite

Command:

```sh
./host-tests/run.sh
```

Expected fresh result for this source snapshot:

```text
HOST SELF-TESTS PASSED: 23/23
SOURCE CONTRACT TESTS PASSED
RUNTIME PACK TOOL TESTS PASSED
BOOTSTRAP STAMP TESTS PASSED
```

Core coverage includes:

- safe project ZIP extraction and ZIP-slip rejection
- generated/cache filtering
- project file listing
- Gradle/AGP/SDK analysis
- AndroidX/Kotlin/Compose/native indicators
- custom Maven URL discovery with credential redaction
- Gradle/JDK compatibility selection
- wrapper/cache/aapt2 build planning
- missing-toolchain blockers
- version catalog analysis
- component toolchain inventory
- verified inner toolchain pack installation
- HTTPS outer runtime descriptor / checksum validation
- atomic runtime pack download/install pipeline
- host targetSdk execution-policy boundary
- Termux-style `files/usr` + `files/home/android-sdk` runtime discovery
- package-specific terminal bootstrap install + symlink recreation

Source-contract coverage includes Sora Editor 0.24.6, portrait/landscape CodeEditor layouts, runtime bootstrap UI wiring, final applicationId, targetSdk 28, and the pinned runtime-builder source revision.

## Zen Injector regression probe

The cleaned test project is recognized as:

```text
Gradle=7.4.2
AGP=7.2.1
compileSdk=34
minSdk=21
targetSdk=34
AndroidX=true
Kotlin=false
Compose=true
Native=false
KotlinDSL=false
VersionCatalog=false
wrapperComplete=true
recommendedJdk=11
```

Its repository list includes Google, Maven Central, Maven Local, JitPack, and its custom Maven URLs. DevxyzIDE preserves these and leaves dependency resolution to Gradle.

## Not verified in this environment

- compiling the DevxyzIDE Android APK with a full Android SDK
- installing it on an ARM Android phone
- executing the custom-prefix terminal/JDK runtime on-device
- running Zen Injector's actual Gradle Wrapper on the phone
- downloading its live Maven dependencies
- producing/signing/installing its final APK

Those items require the physical Android/ARM test gate and must not be reported as passing until observed there.
