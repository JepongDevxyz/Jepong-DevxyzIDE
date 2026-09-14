# DevxyzIDE v0.6 Editor + Runtime Buildability Plan

> **For agentic workers:** Continue with test-first changes and verification gates.

**Goal:** Replace the prototype EditText editor with the current Sora Editor line and add reproducible tooling for building DevxyzIDE-prefixed Android terminal/runtime artifacts.

**Architecture:** Keep project analysis/build planning independent from Android UI. The Android UI embeds Sora Editor for large-file-friendly code editing. Runtime build scripts wrap the maintained Code On The Go terminal-packages build with DevxyzIDE's applicationId rather than consuming binaries built for a different app package.

**Tech Stack:** Java 17, AndroidX, Sora Editor 0.24.6, Gradle Wrapper, Android/Termux-compatible runtime build tooling.

## Global Constraints

- App/applicationId: `com.jepongdevxyz.idebuild`.
- Host targetSdk stays 28 for app-private executable toolchains.
- Imported project SDK/Gradle/Maven declarations remain authoritative.
- Runtime binaries must be built for the DevxyzIDE applicationId; no package-prefix binary patching at install time.
- Do not claim physical Android build success without device evidence.

### Task 1: Source contract test
- Add a failing source-contract test for Sora 0.24.6, CodeEditor UI, applicationId, targetSdk 28, and runtime-builder wrapper.
- Run it and observe failure before implementation.

### Task 2: Sora Editor integration
- Add Sora editor + Java language artifacts at 0.24.6.
- Replace manual line-number EditText layout with `CodeEditor`.
- Update MainActivity file open/save flows and Java highlighting.

### Task 3: Runtime package builder
- Add a pinned upstream wrapper around `appdevforall/terminal-packages`.
- Force the upstream build `-p` option to `com.jepongdevxyz.idebuild`.
- Add deterministic toolchain-pack generator and tests.

### Task 4: Verify
- Run Java host tests, source-contract tests, runtime-pack tests, XML parse, identity/secret scans, and Zen Injector regression probe.
