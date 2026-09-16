# DevxyzIDE Known Limitations

DevxyzIDE is designed to report real runtime limitations instead of simulating success. The following constraints remain relevant even when the release verification workflow is green.

## Android-on-Android toolchain

The repository can verify the host APK and representative generated projects on GitHub-hosted Linux runners and an Android emulator. That is not identical to every physical Android ARM64 device. On-device JDK, Gradle, Android SDK, build-tools, native binaries, filesystem behavior, OEM restrictions, and available RAM/storage can differ.

A project whose required JDK, Gradle, Android SDK platform, build-tools/aapt2, NDK, CMake, repository artifact, or compatible device ABI is absent must be reported as not ready. DevxyzIDE does not fabricate a successful build.

## Imported projects

Imported projects keep their own Gradle scripts, repositories, dependency versions, plugin requirements, and wrapper configuration. Projects may still fail when they require unavailable/private repositories, invalid credentials, unsupported desktop-only tooling, incompatible plugins, unsupported native ABIs, or a toolchain combination not present on the device.

## Network and offline builds

Online Gradle builds depend on the configured repositories and network availability. Offline mode only works for dependencies and plugins already present in the persistent Gradle cache. The verification workflow explicitly checks both a cached offline rebuild and the expected failure of a deliberately missing offline dependency.

## Git credentials

Core Git operations are implemented with real Git commands. The application does not embed repository access tokens in source or UI fields. Authentication for private remotes depends on a compatible runtime credential mechanism supplied outside the repository.

## Terminal

The Android system shell can be used as a limited fallback and is intentionally reported as experimental. Full command availability depends on the installed DevxyzIDE terminal/runtime pack.

## Editor scope

The current AIDE-compatible host uses Android platform editor components plus DevxyzIDE syntax/completion services. It is not a full desktop IDE language-server environment and does not claim universal semantic completion/refactoring support.

## Release meaning

A green release workflow proves the repository commit passed its automated host tests, real host APK build, APK validation/signing, representative generated-project builds, offline/error-path checks, and emulator install/launch gates. It does not prove that every third-party Android project will build on every physical phone.
