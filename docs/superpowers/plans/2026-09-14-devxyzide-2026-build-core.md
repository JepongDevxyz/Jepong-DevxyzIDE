# DevxyzIDE 2026 Build Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden DevxyzIDE's project-aware Android build core so imported Gradle projects can use compatible app-private JDK/SDK/aapt2 toolchains and normal Maven resolution.

**Architecture:** Static project analysis feeds a compatibility-aware build planner. A toolchain inventory and verified ZIP installer manage app-private runtimes without rewriting imported projects.

**Tech Stack:** Java 17 source, AndroidX, Gradle Wrapper, Android Gradle Plugin, app-private Android SDK/JDK toolchains.

**Spec:** `docs/superpowers/specs/2026-09-14-devxyzide-2026-build-core-design.md`

## Global Constraints

- App name: DevxyzIDE
- Package/applicationId: `com.jepongdevxyz.idebuild`
- Imported project Gradle Wrapper remains authoritative.
- No completion claim without fresh host verification.
- Missing Android-device toolchains must be reported, not hidden.

---

### Task 1: Project analysis expansion
- [ ] Add failing tests for version catalogs, Compose/Kotlin/native indicators and repository URL redaction.
- [ ] Extend `ProjectRequirements` and `ProjectAnalyzer`.
- [ ] Run host tests.

### Task 2: Compatibility-aware JDK selection
- [ ] Add failing tests for compatible JDK fallback selection.
- [ ] Implement selection using installed app-private JDKs and Gradle/AGP constraints.
- [ ] Run host tests.

### Task 3: Toolchain inventory and verified pack install
- [ ] Add failing tests for inventory and safe toolchain pack installation.
- [ ] Implement manifest parsing, SHA-256 verification, path safety, and atomic install.
- [ ] Run host tests.

### Task 4: Generalized build plan
- [ ] Add failing tests for custom tasks and offline mode.
- [ ] Generalize build planning and runner entry points.
- [ ] Run host tests.

### Task 5: Package and verify
- [ ] Run full host suite.
- [ ] Parse Android XML.
- [ ] Scan source for wrong package id and secrets.
- [ ] Create source ZIP and SHA-256 file.
