# DevxyzIDE v0.3 Build Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make DevxyzIDE project-aware so imported AndroidX projects use their own Gradle Wrapper, compatible app-private JDK/SDK/aapt2 paths, persistent dependency caches, and preflight diagnostics.

**Architecture:** Pure-Java project analysis and compatibility planning feed the Android process runner. Gradle remains responsible for Maven/plugin resolution; DevxyzIDE supplies the execution environment and refuses builds only for concrete missing local toolchain requirements.

**Tech Stack:** Java 17 source, AndroidX app shell, Gradle Wrapper, Android SDK environment variables, Android-compatible aapt2 override.

**Spec:** `docs/superpowers/specs/2026-09-14-devxyzide-v0.3-build-engine-design.md`

## Global Constraints
- applicationId/namespace: `com.jepongdevxyz.idebuild`
- do not rewrite imported Gradle Wrapper versions
- never log repository credentials or secret environment values
- persistent Gradle/Maven cache lives under app-private storage
- no universal-build claim until physical Android ARM64 toolchains are verified

---

### Task 1: Project analyzer
**Files:** create `core/build/ProjectRequirements.java`, `core/build/ProjectAnalyzer.java`; extend host tests.
**Produces:** deterministic Gradle/AGP/SDK/repository detection.

### Task 2: Compatibility and toolchain planner
**Files:** create `core/build/JvmCompatibility.java`, `core/build/BuildPlan.java`, `core/build/BuildPlanner.java`; extend host tests.
**Produces:** selected JDK, SDK/aapt2 checks, Gradle args and safe diagnostics.

### Task 3: Build runner integration
**Files:** modify `BuildRunner.java`, `MainActivity.java`.
**Produces:** automatic preflight report and wrapper-aware environment before `assembleDebug`.

### Task 4: Regression verification
**Files:** update host test script/report/readme.
**Produces:** host tests plus Zen Injector requirement-detection evidence and a source archive.
