# DevxyzIDE v0.3 Build Engine Design

DevxyzIDE v0.3 keeps the existing secure project importer/editor and replaces the single-JDK build assumption with a project-aware build engine.

The engine analyzes each imported project before execution. It reads the project's Gradle Wrapper version, Android Gradle Plugin version, SDK levels, Groovy/Kotlin DSL usage, AndroidX usage, and declared repositories. Gradle Wrapper remains authoritative for the Gradle version; DevxyzIDE does not rewrite imported wrapper files.

Runtime selection is isolated from project parsing. DevxyzIDE chooses an app-private JDK based on project requirements, exposes a shared persistent GRADLE_USER_HOME for downloaded Gradle distributions and Maven dependencies, exposes an app-private Android SDK through ANDROID_HOME/ANDROID_SDK_ROOT, and injects `android.aapt2FromMavenOverride` when an Android-compatible aapt2 executable is installed. Missing components are reported as preflight blockers instead of starting a knowingly broken build.

Repository resolution is delegated to Gradle. google(), mavenCentral(), mavenLocal(), JitPack/custom `maven { url ... }`, plugin repositories and authenticated repositories remain project configuration. DevxyzIDE must never print repository credentials or environment secrets in its diagnostics.

v0.3 does not claim that arbitrary Gradle plugins or native binaries are universally compatible with Android/ARM64. It provides deterministic detection, wrapper-aware execution and toolchain boundaries so additional JDK/SDK/aapt2/NDK packages can be installed without rewriting the editor or build runner.

The uploaded Zen Injector project is the regression fixture for legacy AndroidX compatibility. Its expected detection is Gradle 7.4.2, AGP 7.2.1, compileSdk 34, minSdk 21, targetSdk 34, AndroidX enabled, and custom Maven repositories present.
