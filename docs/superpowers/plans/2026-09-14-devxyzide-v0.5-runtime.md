# DevxyzIDE v0.5 Runtime Provisioning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the host execution policy and deterministic on-device runtime provisioning foundation needed for real Gradle builds.

**Architecture:** Project analysis feeds a runtime provisioning plan. Verified runtime-pack descriptors/downloads feed the existing safe toolchain-pack installer, while targetSdk 28 preserves execution of app-private binaries.

**Tech Stack:** Java 17, AndroidX, Gradle Wrapper, app-private JDK/Android SDK/aapt2 packs.

**Spec:** `docs/superpowers/specs/2026-09-14-devxyzide-v0.5-runtime-design.md`

## Global Constraints

- App: DevxyzIDE
- Package: `com.jepongdevxyz.idebuild`
- Host targetSdk: 28 for on-device executable compatibility.
- Project Gradle Wrapper remains authoritative.
- Downloads must be HTTPS in production and SHA-256 verified before installation.
- No Android-device build-success claim without Android/ARM evidence.

---

### Task 1: Host execution policy
- [ ] Add failing test for API 28/29 policy boundary.
- [ ] Add `ExecutionPolicy` and set host targetSdk 28.
- [ ] Run host tests.

### Task 2: Runtime provisioning plan
- [ ] Add failing tests for legacy, modern, and native project missing-component plans.
- [ ] Add inventory query helpers and `ToolchainProvisioningPlan`.
- [ ] Run host tests.

### Task 3: Runtime pack descriptor and verified downloader
- [ ] Add failing descriptor/checksum tests.
- [ ] Implement descriptor parser and atomic downloader.
- [ ] Run host tests.

### Task 4: Integrate build diagnostics
- [ ] Make build preflight include provisioning-plan diagnostics without rewriting the project.
- [ ] Run host tests and project probe against Zen Injector source.

### Task 5: Package and verify
- [ ] Run full host suite.
- [ ] Parse all Android XML.
- [ ] Scan for wrong package id and obvious secrets.
- [ ] Create source ZIP and SHA-256.
