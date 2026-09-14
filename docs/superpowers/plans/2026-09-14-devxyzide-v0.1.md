# DevxyzIDE v0.1 Implementation Plan

> **For agentic workers:** implement and verify the project-import/editor shell before integrating the native build toolchain.

**Goal:** Produce a testable DevxyzIDE Android source baseline capable of safely importing and editing Android project ZIPs with a clean boundary for Gradle builds.

**Architecture:** AndroidX/Material app shell; pure-Java ZIP/file utilities; app-scoped workspace; process-based build runner isolated from the UI. The on-device JDK/aapt2 runtime remains a separate milestone.

**Tech Stack:** Java 17, AGP 9.4.0, Gradle 9.6.0 baseline, AndroidX AppCompat 1.8.0, Material 1.14.0.

**Spec:** `docs/superpowers/specs/2026-09-14-devxyzide-v0.1-design.md`

## Global Constraints
- applicationId/namespace: `com.jepongdevxyz.idebuild`
- minSdk: 23
- compileSdk/targetSdk: 37
- no MANAGE_EXTERNAL_STORAGE permission
- ZIP imports must reject path traversal and enforce extraction limits
- do not claim on-device Android builds work until the ARM64 toolchain is physically verified

### Task 1: Safe project core
- Verify ZIP extraction, text classification and project file listing with host tests.

### Task 2: Android IDE shell
- Add splash/icon branding, project explorer, editor, save and console UI.

### Task 3: Build boundary
- Add Gradle runner, missing-toolchain detection, APK locator and installer handoff.

### Task 4: Verification and source archive
- Validate XML/package identifiers and produce the source ZIP with the exact verification report.
