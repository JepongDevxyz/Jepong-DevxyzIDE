# DevxyzIDE Capability Matrix

This matrix describes the capabilities implemented in the `feature/devxyzide-01-foundation` release candidate. Statuses are intentionally conservative: `Available` means the feature is implemented in the host source; `Conditional` means it additionally depends on project/toolchain inputs; `Experimental` means a fallback exists but is not equivalent to the full packaged runtime.

| Area | Capability | Status | Verification / limits |
| --- | --- | --- | --- |
| Workspace | Open/import Gradle projects | Available | Root detection, contained paths, staging import, ZIP safety, cancellation, progress and stress tests are in the host suite. |
| Workspace | Recent projects | Available | Persisted recent-project state is source-contract tested. |
| Workspace | File/folder create, rename, duplicate, delete | Available | Backed by root-contained project services; destructive delete requires confirmation. |
| Workspace | Source ZIP backup | Available | Uses Android Storage Access Framework and source-focused archive service. |
| Editor | Multi-tab editing, dirty state, Save All | Available | Host-tested session model. |
| Editor | Autosave on tab switch/close | Available | Controlled by persisted editor settings; close/close-others/close-all use the real session model. |
| Editor | Font size, word wrap, tab width, autosave settings | Available | Persisted and host/source-contract tested. |
| Editor | Current-file search/replace and project search | Available | Project search is cancelable and results are navigable. |
| Editor | Advanced LSP/Sora-class editor | Not claimed | This AIDE-compatible host uses the platform `EditText` path; advanced language-server behavior is outside this release claim. |
| Build | Project requirement analysis | Available | Detects Gradle/AGP, compile/min/target SDK, Java requirement, AndroidX, Kotlin, Compose, native build, Kotlin DSL and version catalogs. |
| Build | Gradle build actions | Conditional | Requires a complete compatible project wrapper or internal Gradle plus required JDK/Android SDK/aapt2 components. Capability UI reports `NEEDS_INSTALL` when the project toolchain is incomplete. |
| Build | Online and offline Gradle mode | Conditional | Uses a persistent Gradle cache. Offline builds correctly fail when required dependencies are not cached. |
| Build | APK/AAB output discovery | Available | Scans real `*/build/outputs` artifacts and reports type, variant, size, modification time and relative path. APK success still requires structural APK validation. |
| Build | Build diagnostics / Problems | Available | Real compiler/Gradle diagnostic lines are parsed into navigable Problems. |
| Templates | Classic Java Android project | Available | Generated with production template code; CI builds the generated APK. |
| Templates | AndroidX/Kotlin project | Available | Generated with production template code; CI builds the generated APK. |
| Toolchain | JDK / Gradle / SDK / aapt2 discovery | Conditional | Supports Devxyz component packs and the app-private Termux-style layout. Missing required components are surfaced before build. |
| Toolchain | Native NDK/CMake projects | Conditional | NDK is required when native build use is detected; CMake is reported when relevant. Device ABI/tool availability still determines whether a specific project can build. |
| Runtime | Verified runtime/toolchain packs | Available | Installer validates declared sizes and SHA-256 entries before accepting pack content. |
| Terminal | App-private runtime shell | Conditional | Available when packaged runtime shell exists. |
| Terminal | Android `/system/bin/sh` fallback | Experimental | Useful fallback, but not equivalent to the full Devxyz terminal runtime. |
| Git | Init/status/stage/unstage/commit/diff/branch/checkout | Conditional | Runs the real installed `git` executable through the shared process engine. |
| Git | Clone/fetch/pull/push | Conditional | Real local-remote host tests cover clone, fetch, fast-forward pull and push. Network/authentication depend on the runtime and repository credentials. |
| Signing | APK signing and verification | Conditional | Requires a real app-private `apksigner`, an APK and a user-selected compatible keystore. Secrets are collected at action time and are not stored in preferences. |
| Install | APK package-installer handoff | Available | Verified APK can be handed to Android's package installer. |
| CI | Host application APK build | Verified gate | Android SDK 28 + Gradle 4.6 + JDK 8 build, APK ZIP/badging checks and real `apksigner` verification. |
| CI | Generated Java/Kotlin project E2E | Verified gate | Real Gradle 8.9/API 35 builds, offline rebuild, missing-dependency failure and broken-source diagnostics are checked. |
| CI | Emulator install/launch | Verified gate | API 28 emulator installs and launches DevxyzIDE plus generated Java and Kotlin sample APKs before a verified-source bundle is produced. |

## Release-claim boundary

The automated release gate proves the repository source, host APK build, generated sample projects, signing checks and API 28 emulator install/launch path for the tested CI environments. It does **not** prove that every arbitrary imported project will build, nor does it replace physical ARM64 Android evidence for the optional full on-phone JDK/Gradle/SDK/native runtime stack.
