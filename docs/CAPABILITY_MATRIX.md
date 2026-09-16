# DevxyzIDE Capability Matrix

This matrix documents the release-readiness contract for the current DevxyzIDE source branch. A capability is considered implemented only when the repository contains a real code path and an automated verification path; runtime-dependent capabilities also report `NEEDS_INSTALL`, `UNAVAILABLE`, `UNSUPPORTED`, or `EXPERIMENTAL` instead of fabricating success.

| Capability | Implementation state | Verification path | Notes |
| --- | --- | --- | --- |
| Project open/import | Implemented | Host tests + ZIP safety/cancellation tests | Imported paths are containment-checked and staged imports are cleaned on failure/cancel. |
| Recent Projects | Implemented | Source contract test | Persists canonical project paths and filters missing directories. |
| Multi-tab editor | Implemented | Host/session + source contract tests | Dirty state, save-all, close tab, close others, close all. |
| Editor settings | Implemented | Host + source contract tests | Font size, word wrap, tab width, autosave. |
| Autosave | Implemented | Host/source tests | Saves on configured editor transitions such as tab switch/close. |
| Project search | Implemented | Host tests | Cancelable project-wide text search. |
| Build diagnostics | Implemented | Host tests + generated broken-project CI | Parses real compiler output into navigable project-relative diagnostics. |
| Gradle build actions | Implemented with runtime prerequisites | Host tests + real Android CI | Preflight uses project requirements and installed JDK/SDK/Gradle/build-tools. |
| APK/AAB artifact scan | Implemented | Host tests + build-session contract | Reports artifact type, variant, size, modified time, and relative path. |
| APK signing | Implemented with runtime prerequisites | Host tests + Android SDK `apksigner` CI | No synthetic signing success. |
| Terminal | Implemented | Process/terminal host tests | Uses real shell/process execution; system shell is reported as experimental. |
| Git init/status/stage/unstage/commit/diff/branch/checkout | Implemented | Real Git host tests | Commands execute through the shared process engine. |
| Git clone/fetch/pull/push | Implemented | Real local-bare-remote host tests | Network credentials are not embedded in source/UI. |
| Classic Java template | Implemented | CI generation, build, APK validation, emulator install/launch | AGP 3.2.1 / Gradle 4.6 / Android SDK 28 profile. |
| AndroidX Java template | Implemented | CI generation, build, offline rebuild, APK validation, emulator install/launch | Modern AGP/Gradle/JDK/SDK profile. |
| AndroidX Kotlin template | Implemented | CI generation, build, offline rebuild, APK validation, emulator install/launch | Kotlin + modern Android toolchain profile. |
| Offline dependency behavior | Implemented | CI cached rebuild + deliberate missing dependency failure | Offline mode must use cache and fail clearly when an artifact is absent. |
| Host APK build | Implemented | CI clean build + ZIP/aapt validation | Package must be `com.jepongdevxyz.idebuild`. |
| Host APK emulator launch | Implemented | API 28 emulator gate | Install, launcher start, and activity presence are checked. |
| Verified source ZIP | Release-gated | Package job after build/templates/emulator | Produced only after preceding verification jobs succeed. |

## Capability-state rule

DevxyzIDE must not present a tool as ready merely because a button or source module exists. Build, signing, terminal, and Git readiness is derived from runtime detection and project requirements. Missing components are surfaced as explicit capability states with a user-facing reason.
