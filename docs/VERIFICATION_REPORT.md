# DevxyzIDE Release Verification Report

This document defines the evidence required before a DevxyzIDE source commit is treated as release-ready. The authoritative result for a specific commit is the `DevxyzIDE Full Android Verification` workflow attached to that commit.

## Required automated gates

1. **Portable host/runtime tests** — compile and run the Java host-test suite plus source-contract tests under JDK 17.
2. **Host APK build** — build DevxyzIDE with its pinned legacy host profile (JDK 8, Gradle 4.6, AGP 3.2.1, Android SDK 28).
3. **Host APK validation** — require a non-empty APK, valid ZIP structure, and expected package badging for `com.jepongdevxyz.idebuild`.
4. **APK signing verification** — sign a real host APK with Android SDK `apksigner`, verify the signature, and recheck ZIP integrity.
5. **Classic Java template E2E** — generate the production Classic Java template, build it with its pinned profile, validate its package/APK, and install/launch it on the emulator.
6. **AndroidX Java template E2E** — generate from production template code, build, validate APK/package, rebuild from cache in offline mode, install, and launch.
7. **AndroidX Kotlin template E2E** — generate from production template code, build, validate APK/package, rebuild from cache in offline mode, install, and launch.
8. **Broken-project diagnostics** — deliberately introduce a Java compilation error and verify DevxyzIDE's production diagnostics parser resolves the real compiler output to the expected source file.
9. **Offline dependency failure** — inject a dependency that cannot exist in cache and verify Gradle fails clearly while offline.
10. **Git backend tests** — execute real init/status/stage/unstage/commit/diff/branch/checkout plus clone/fetch/pull/push against a local bare remote.
11. **ZIP/import safety** — verify containment, malicious ZIP rejection, cancellation cleanup, progress reporting, and streaming import behavior.
12. **Emulator gate** — boot API 28, install the host APK and generated application APKs, launch each package, and verify the activity is present before force-stopping it.
13. **Verified source package** — only after the preceding build/template/emulator jobs succeed, archive the exact Git commit as `DevxyzIDE-verified-source.zip`, generate SHA-256 checksums, and upload the package as the final workflow artifact.

## Release rule

A commit is not considered fully verified merely because source-contract tests pass. The final source package job is dependency-gated on the real build, generated-template, and emulator jobs. If any required gate fails or is skipped unexpectedly, no final verified source package should be produced for that commit.

## Physical-device note

The automated gates provide strong reproducible evidence for the repository and emulator paths. Physical Android ARM64 testing remains valuable because OEM Android builds and phone-local toolchain binaries can behave differently from GitHub-hosted Linux and x86_64 Android emulator environments. Device-specific failures must be reported as real limitations rather than hidden or converted into synthetic success.
