# ARM Runtime Pack Builder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a reproducible GitHub Actions pipeline that produces structurally verified DevxyzIDE ARM64 runtime artifacts: a DevxyzIDE-stamped Android-native bootstrap, Gradle 8.9 component pack, Android SDK 35 platform pack, and machine-readable SHA-256/size manifest.

**Architecture:** Keep runtime production isolated under `tools/runtime-builder/` and trigger a dedicated workflow only for runtime-builder changes or manual dispatch. Use the pinned `appdevforall/terminal-packages` baseline for Android-native packages, official Gradle and Android SDK sources for portable components, then validate every final archive before uploading CI artifacts. The Android application remains unchanged except for later catalog consumption after immutable release URLs exist.

**Tech Stack:** Bash, Python 3, GitHub Actions, pinned Termux-derived package builder, official Gradle distributions, Android `sdkmanager`, ZIP archives, SHA-256 verification.

**Spec:** `docs/superpowers/specs/2026-09-15-arm-runtime-pack-builder-design.md`

## Global Constraints

- Work only on `feature/modern-runtime-packs`; do not merge to `main` during this plan.
- Preserve `applicationId=com.jepongdevxyz.idebuild`.
- First native ABI is `aarch64`; `arm` is deferred until the ARM64 path succeeds.
- Pinned native package-builder baseline: `appdevforall/terminal-packages@610af608b4a3b1127244e90edf8b7be2bf94fafa`.
- Modern runtime target: JDK 17+ compatible runtime, Gradle 8.9+, Android SDK platform 35, Android-native `aapt2`.
- Runtime pack URLs/descriptors must never use insecure HTTP, placeholder URLs, zero hashes, or `example.invalid` in production metadata.
- Every final archive must have exact byte size and SHA-256 generated from the final bytes.
- Do not claim ARM64 on-device execution or DevxyzMusic end-to-end build until actual Android ARM64 runtime evidence exists.
- Existing DevxyzIDE APK build/emulator verification must remain green.

---

### Task 1: Runtime builder configuration and validation contract

**Files:**
- Create: `tools/runtime-builder/config/runtime-pack.properties`
- Create: `tools/runtime-builder/validate-config.py`
- Create: `tools/runtime-builder/test_validate_config.py`
- Create: `tools/runtime-builder/README.md`

**Interfaces:**
- Produces: validated key/value configuration consumed by all later scripts.
- Required keys: `applicationId`, `nativeBuilderRepo`, `nativeBuilderCommit`, `nativeAbi`, `gradleVersion`, `androidApi`.

- [ ] **Step 1: Write failing configuration tests**

Create `test_validate_config.py` with tests that reject wrong application IDs, unpinned/invalid commit SHAs, unsupported ABIs, Gradle versions below `8.9`, and Android API values below `35`.

```python
import unittest
from validate_config import validate

VALID = {
    "applicationId": "com.jepongdevxyz.idebuild",
    "nativeBuilderRepo": "https://github.com/appdevforall/terminal-packages.git",
    "nativeBuilderCommit": "610af608b4a3b1127244e90edf8b7be2bf94fafa",
    "nativeAbi": "aarch64",
    "gradleVersion": "8.9",
    "androidApi": "35",
}

class ConfigValidationTest(unittest.TestCase):
    def test_valid_config(self):
        validate(dict(VALID))

    def test_wrong_application_id_rejected(self):
        cfg = dict(VALID); cfg["applicationId"] = "com.termux"
        with self.assertRaises(ValueError): validate(cfg)

    def test_unpinned_commit_rejected(self):
        cfg = dict(VALID); cfg["nativeBuilderCommit"] = "main"
        with self.assertRaises(ValueError): validate(cfg)

    def test_unsupported_abi_rejected(self):
        cfg = dict(VALID); cfg["nativeAbi"] = "x86_64"
        with self.assertRaises(ValueError): validate(cfg)
```

- [ ] **Step 2: Run the test and confirm RED**

Run:

```bash
cd tools/runtime-builder
python3 -m unittest -v test_validate_config.py
```

Expected: FAIL because `validate_config.py` does not exist yet.

- [ ] **Step 3: Implement configuration parser/validator**

`validate-config.py` must expose `load_properties(path)` and `validate(config)`, require a 40-character lowercase hex commit SHA, allow only `aarch64` for this milestone, require the exact DevxyzIDE package ID, require Gradle `>=8.9`, and Android API `>=35`.

Create `runtime-pack.properties` with:

```properties
applicationId=com.jepongdevxyz.idebuild
nativeBuilderRepo=https://github.com/appdevforall/terminal-packages.git
nativeBuilderCommit=610af608b4a3b1127244e90edf8b7be2bf94fafa
nativeAbi=aarch64
gradleVersion=8.9
androidApi=35
```

- [ ] **Step 4: Run tests GREEN**

```bash
cd tools/runtime-builder
python3 -m unittest -v test_validate_config.py
python3 validate-config.py config/runtime-pack.properties
```

Expected: all tests pass and validator exits `0`.

- [ ] **Step 5: Commit**

```bash
git add tools/runtime-builder
git commit -m "feat: add runtime builder configuration contract"
```

---

### Task 2: Gradle 8.9 component pack assembler

**Files:**
- Create: `tools/runtime-builder/assemble-gradle-pack.sh`
- Create: `tools/runtime-builder/generate-toolchain-properties.py`
- Create: `tools/runtime-builder/test_toolchain_properties.py`

**Interfaces:**
- Consumes: `gradleVersion` from runtime configuration.
- Produces: `out/gradle/gradle-8.9.devxyz-toolchain.zip` with `toolchains/gradle-8.9/bin/gradle` and `devxyz-toolchain.properties` containing per-file SHA-256 entries.

- [ ] **Step 1: Write failing manifest-generation tests**

Test that generated metadata contains `component=gradle`, `version=8.9`, `abi=all`, and a `sha256.<relative-path>=<64 lowercase hex>` entry for every payload file.

- [ ] **Step 2: Run RED**

```bash
cd tools/runtime-builder
python3 -m unittest -v test_toolchain_properties.py
```

Expected: FAIL because generator is absent.

- [ ] **Step 3: Implement metadata generator and Gradle pack script**

`assemble-gradle-pack.sh` must:

1. download only from `https://services.gradle.org/distributions/gradle-8.9-bin.zip`;
2. download official checksum from `https://services.gradle.org/distributions/gradle-8.9-bin.zip.sha256`;
3. verify checksum before extraction;
4. stage as `toolchains/gradle-8.9/...`;
5. generate `devxyz-toolchain.properties` from final staged files;
6. create the final ZIP under `out/gradle/`.

No alternate mirror fallback is allowed.

- [ ] **Step 4: Run unit test and structural pack check**

```bash
cd tools/runtime-builder
python3 -m unittest -v test_toolchain_properties.py
bash assemble-gradle-pack.sh
unzip -t out/gradle/gradle-8.9.devxyz-toolchain.zip
unzip -l out/gradle/gradle-8.9.devxyz-toolchain.zip | grep 'toolchains/gradle-8.9/bin/gradle'
```

Expected: unit tests PASS, ZIP integrity PASS, Gradle launcher present.

- [ ] **Step 5: Commit**

```bash
git add tools/runtime-builder
git commit -m "feat: assemble verified Gradle 8.9 runtime pack"
```

---

### Task 3: Android SDK 35 platform component pack

**Files:**
- Create: `tools/runtime-builder/assemble-android-sdk-pack.sh`
- Modify: `tools/runtime-builder/generate-toolchain-properties.py`
- Modify: `tools/runtime-builder/test_toolchain_properties.py`

**Interfaces:**
- Consumes: Android SDK command-line tools supplied by CI and `androidApi=35`.
- Produces: `out/android-sdk/android-sdk-35.devxyz-toolchain.zip` containing `toolchains/android-sdk/platforms/android-35/android.jar` plus only required platform metadata.

- [ ] **Step 1: Extend tests for Android SDK metadata**

Add assertions for `component=android-sdk-platform`, `version=35`, `abi=all`, and per-file hashes.

- [ ] **Step 2: Run RED before implementing the pack script**

```bash
cd tools/runtime-builder
python3 -m unittest -v test_toolchain_properties.py
```

Expected: new Android-specific fixture fails until generator supports the component.

- [ ] **Step 3: Implement SDK pack assembler**

The script must require `ANDROID_SDK_ROOT`, run:

```bash
yes | "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" "platforms;android-35"
```

then copy `platforms/android-35/android.jar`, `package.xml`, and `source.properties` if present into `toolchains/android-sdk/platforms/android-35/`. It must not copy desktop `build-tools/*/aapt2` into the Android-device pack.

- [ ] **Step 4: Verify pack**

```bash
bash tools/runtime-builder/assemble-android-sdk-pack.sh
unzip -t tools/runtime-builder/out/android-sdk/android-sdk-35.devxyz-toolchain.zip
unzip -l tools/runtime-builder/out/android-sdk/android-sdk-35.devxyz-toolchain.zip | grep 'platforms/android-35/android.jar'
```

Expected: archive integrity succeeds and `android.jar` exists.

- [ ] **Step 5: Commit**

```bash
git add tools/runtime-builder
git commit -m "feat: assemble Android SDK 35 platform pack"
```

---

### Task 4: DevxyzIDE ARM64 bootstrap builder wrapper

**Files:**
- Create: `tools/runtime-builder/build-bootstrap.sh`
- Create: `tools/runtime-builder/patches/devxyz-prefix.patch`
- Create: `tools/runtime-builder/test_bootstrap_contract.py`
- Reuse: `tools/runtime/stamp_bootstrap.py`

**Interfaces:**
- Consumes: pinned native package-builder repo/commit and `applicationId=com.jepongdevxyz.idebuild`.
- Produces: `out/bootstrap/devxyz-bootstrap-aarch64.zip` containing Termux-style runtime files, `SYMLINKS.txt`, `devxyz-bootstrap.properties`, Android-native JDK, and native `aapt2`.

- [ ] **Step 1: Add failing structural contract test**

The test opens a fixture bootstrap ZIP and requires:

```properties
format=1
applicationId=com.jepongdevxyz.idebuild
arch=aarch64
```

It must also reject ZIP entries or symlink targets containing `/data/data/com.termux/` or `/data/data/com.itsaky.androidide/`, and require paths equivalent to `usr/bin/aapt2` plus a supported JDK location such as `usr/lib/jvm/java-21-openjdk/bin/java`.

- [ ] **Step 2: Run RED**

```bash
cd tools/runtime-builder
python3 -m unittest -v test_bootstrap_contract.py
```

Expected: FAIL until builder/stamped fixture logic exists.

- [ ] **Step 3: Implement pinned source checkout and build wrapper**

`build-bootstrap.sh` must:

1. clone `https://github.com/appdevforall/terminal-packages.git` into a temporary directory;
2. checkout exactly `610af608b4a3b1127244e90edf8b7be2bf94fafa`;
3. verify `git rev-parse HEAD` equals the configured commit;
4. apply only `patches/devxyz-prefix.patch`;
5. invoke the upstream supported `aarch64` build path for the JDK and Android build-tool packages needed by DevxyzIDE;
6. generate a bootstrap archive containing OpenJDK and `aapt2`;
7. run `tools/runtime/stamp_bootstrap.py` with the DevxyzIDE application ID and `aarch64`.

The script must `set -euo pipefail` and abort on any commit mismatch or missing output.

- [ ] **Step 4: Run structural verification when native build output exists**

```bash
python3 tools/runtime-builder/test_bootstrap_contract.py
python3 tools/runtime/stamp_bootstrap.py --help
```

In GitHub Actions, the full build step must additionally run the contract test against the generated bootstrap and fail if JDK or `aapt2` is absent.

- [ ] **Step 5: Commit**

```bash
git add tools/runtime-builder
git commit -m "feat: add pinned ARM64 bootstrap builder"
```

---

### Task 5: Final artifact verifier and runtime manifest generator

**Files:**
- Create: `tools/runtime-builder/verify-pack.py`
- Create: `tools/runtime-builder/generate-manifest.py`
- Create: `tools/runtime-builder/test_manifest.py`

**Interfaces:**
- Consumes: final bootstrap, Gradle pack, SDK 35 pack.
- Produces: `out/runtime-manifest.properties` and `out/SHA256SUMS` containing component, version, ABI, filename, exact byte size, SHA-256, and provenance fields.

- [ ] **Step 1: Write failing manifest safety tests**

Tests must reject entries containing `http://`, `example.invalid`, a 64-zero hash, missing source provenance, or incorrect file size/hash.

- [ ] **Step 2: Run RED**

```bash
cd tools/runtime-builder
python3 -m unittest -v test_manifest.py
```

Expected: FAIL because generator/verifier do not exist.

- [ ] **Step 3: Implement final-byte verification**

`verify-pack.py` accepts `--file`, `--sha256`, and `--size`; it exits nonzero on any mismatch. `generate-manifest.py` computes metadata directly from final archives and writes deterministic sorted entries.

Example generated fields:

```properties
artifact.gradle.component=gradle
artifact.gradle.version=8.9
artifact.gradle.abi=all
artifact.gradle.filename=gradle-8.9.devxyz-toolchain.zip
artifact.gradle.size=123456
artifact.gradle.sha256=<64 lowercase hex>
artifact.gradle.source=https://services.gradle.org/distributions/gradle-8.9-bin.zip
```

- [ ] **Step 4: Run GREEN**

```bash
cd tools/runtime-builder
python3 -m unittest -v test_manifest.py
python3 generate-manifest.py --out out/runtime-manifest.properties out/bootstrap/*.zip out/gradle/*.zip out/android-sdk/*.zip
sha256sum -c out/SHA256SUMS
```

Expected: tests PASS and all checksums verify.

- [ ] **Step 5: Commit**

```bash
git add tools/runtime-builder
git commit -m "feat: verify runtime packs and generate checksums"
```

---

### Task 6: Dedicated GitHub Actions runtime-pack workflow

**Files:**
- Create: `.github/workflows/android-runtime-packs.yml`

**Interfaces:**
- Consumes: Tasks 1-5 scripts.
- Produces: GitHub Actions artifacts `devxyz-runtime-bootstrap-aarch64`, `devxyz-gradle-8.9-pack`, `devxyz-android-sdk-35-pack`, `devxyz-runtime-manifest`, plus retained failure logs.

- [ ] **Step 1: Add workflow with narrow triggers**

Use:

```yaml
on:
  workflow_dispatch:
  push:
    branches:
      - feature/modern-runtime-packs
    paths:
      - 'tools/runtime-builder/**'
      - 'tools/runtime/stamp_bootstrap.py'
      - '.github/workflows/android-runtime-packs.yml'
```

- [ ] **Step 2: Add lightweight validation job first**

The first job runs Python unit tests and configuration validation before any heavyweight native build.

- [ ] **Step 3: Add portable pack jobs**

Use `actions/setup-python`, `android-actions/setup-android@v3`, official SDK manager, and the Task 2/3 scripts. Upload Gradle and SDK artifacts separately.

- [ ] **Step 4: Add ARM64 native bootstrap job**

Run `build-bootstrap.sh` in the upstream-supported Linux/container environment. Upload build logs with `if: always()` and upload the bootstrap only after contract verification succeeds.

- [ ] **Step 5: Add manifest aggregation job**

Download all successful pack artifacts, run `generate-manifest.py`, `verify-pack.py`, and `sha256sum -c`, then upload manifest/checksum artifacts.

- [ ] **Step 6: Commit and let branch push auto-trigger the workflow**

```bash
git add .github/workflows/android-runtime-packs.yml
git commit -m "ci: build verified DevxyzIDE ARM runtime packs"
```

Expected: the push on `feature/modern-runtime-packs` automatically starts the runtime-pack workflow because the workflow file itself is included in the `paths` trigger.

---

### Task 7: Inspect runtime-pack CI and fix root causes until structurally GREEN

**Files:**
- Modify only files implicated by concrete CI failures from Tasks 1-6.

**Interfaces:**
- Consumes: fresh GitHub Actions logs.
- Produces: one fresh runtime-pack workflow run where validation, portable packs, ARM64 bootstrap, and manifest jobs all conclude `success`.

- [ ] **Step 1: Inspect the fresh run**

Record exact workflow run ID and job IDs.

- [ ] **Step 2: On failure, fetch failed-job logs and identify the first root-cause error**

Do not patch later symptoms before the first causal failure.

- [ ] **Step 3: Write/extend a regression test for the discovered failure when feasible**

Examples: config validation, ZIP layout, prefix rejection, missing JDK/aapt2, checksum mismatch.

- [ ] **Step 4: Apply the minimal fix and push**

Each fix must stay on `feature/modern-runtime-packs` and cause a fresh workflow run.

- [ ] **Step 5: Repeat until structurally GREEN or a genuine upstream/environment blocker is proven**

If blocked by an upstream package-builder incompatibility, stop and report the exact failing command/log rather than substituting desktop Linux binaries.

---

### Task 8: Re-run existing DevxyzIDE application verification

**Files:**
- No source changes expected; use existing `.github/workflows/android-full-verification.yml`.

**Interfaces:**
- Consumes: final branch state after runtime-builder changes.
- Produces: fresh evidence that DevxyzIDE itself still builds, validates, installs, and launches on the API 28 emulator.

- [ ] **Step 1: Trigger existing app verification through a branch change if necessary**

Use a no-op documentation update only if the workflow trigger requires a relevant changed path; do not alter application behavior merely to trigger CI.

- [ ] **Step 2: Verify both jobs**

Required conclusions:

```text
Build APK with Android SDK 28 = success
Install and launch on Android emulator = success
```

- [ ] **Step 3: Record exact run ID, commit SHA, runtime artifact names, hashes, and sizes**

Final report must say **built and structurally verified** for ARM64 packs, not **on-device execution verified**.

- [ ] **Step 4: Do not merge to `main` yet**

Keep PR #1/feature branch open for the later physical ARM64/DevxyzMusic execution milestone.
