# DevxyzIDE Release Verification Report

This document defines the evidence required before the Phase 01 source can be called a verified release candidate. The exact commit SHA, workflow run ID and timestamp are written into `VERIFICATION_METADATA.txt` by the final CI packaging job, so a packaged source ZIP cannot be produced from an unverified run.

## Automated release gate

The `DevxyzIDE Full Android Verification` workflow must complete all of these jobs successfully:

1. **Host/runtime verification and DevxyzIDE APK build**
   - compile and run the portable Java host/runtime tests and Python source contracts;
   - build the host with Android SDK 28, Gradle 4.6 and JDK 8;
   - verify the debug APK exists and is a valid ZIP;
   - verify application ID `com.jepongdevxyz.idebuild` using Android build tools;
   - sign a CI copy with the real Android SDK `apksigner` and verify its signatures.

2. **Generated project end-to-end verification**
   - generate the Java and Kotlin sample projects using production template code;
   - build both projects with Gradle 8.9 / Android SDK 35;
   - clean and rebuild both from the persistent dependency cache in offline mode;
   - prove an intentionally missing dependency fails clearly in offline mode;
   - prove deliberately broken Java source produces diagnostics that DevxyzIDE can navigate;
   - validate both generated APKs and their package IDs.

3. **Android emulator verification**
   - boot an API 28 x86_64 Android emulator;
   - install the DevxyzIDE host APK;
   - launch DevxyzIDE and verify its activity is running;
   - install and launch the generated Java sample APK;
   - install and launch the generated Kotlin sample APK.

4. **Verified-source packaging**
   - run only after every previous verification job is green;
   - export the exact tested commit into `DevxyzIDE-verified-source.zip`;
   - embed workflow metadata in the bundle;
   - run a ZIP integrity check;
   - produce a SHA-256 checksum;
   - upload the ZIP and checksum as a GitHub Actions artifact.

## Release-readiness checks covered by host tests

The portable host suite covers the project path/workspace model, safe ZIP handling, import cancellation/progress/stress behavior, project directory/file actions, templates, build planning and task policy, APK/AAB output scanning, APK location, runtime pack cancellation, editor session/settings/undo/search, build diagnostics, process execution/cancellation, terminal command planning, real Git operations, APK signing helpers, resources, cache maintenance and syntax/completion helpers. Source-contract tests additionally bind the Android UI to the tested services so the application cannot silently replace real operations with simulated output.

## Recent hardening included in this release candidate

- build-output reporting now exposes APK/AAB type, inferred variant, size, modification time and project-relative path after a successful Gradle invocation;
- build capability gating checks the complete project toolchain instead of enabling Build merely because a Gradle executable exists;
- Git integration has a dedicated real `fetch --prune` operation and host coverage for clone, fetch, fast-forward pull and push using a local bare remote;
- the existing capability registry remains the UI source of truth for Build, Terminal, Git and APK signing availability.

## Known boundaries

A green automated release gate is strong evidence for the tested host, templates, Gradle caches, APK validation/signing and emulator install/launch paths. It cannot guarantee arbitrary third-party Gradle projects, inaccessible Maven repositories, invalid credentials, desktop-only plugins, unsupported ABIs or unavailable external dependencies.

Physical Android ARM64 verification of the optional full on-phone JDK/Gradle/SDK/native runtime remains separate from the CI emulator gate. Do not present that physical-device runtime path as verified until an actual ARM64 device run records runtime installation, project build, generated APK/AAB and install/launch evidence.
