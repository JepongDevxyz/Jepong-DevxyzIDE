# DevxyzIDE v0.6 AIDE Test Edition — Hybrid Mobile IDE Update

Fresh host verification completed for this source tree.

Implemented in this update:
- Streaming project ZIP extraction with an 8 GiB expanded-size safety ceiling and 100,000-entry ceiling.
- 64-bit/long extraction counters suitable for archives larger than 2 GiB.
- Bounded nested Gradle project-root auto-detection.
- Build button remains available even without a project `gradlew`; preflight reports the missing launcher instead of hiding Build.
- Internal Gradle fallback detection (`files/usr/bin/gradle`, packaged Gradle toolchains).
- Online Gradle/Maven resolution mode with persistent `GRADLE_USER_HOME` cache.
- INTERNET permission for online dependency resolution.
- Existing project-declared Maven repositories remain handled by Gradle; DevxyzIDE does not replace them.

Fresh checks:
- Existing source/runtime/bootstrap/AIDE compatibility regression checks: PASS
- Host self-tests: 23/23 PASS
- Hybrid host tests: 4/4 PASS
- Core + BuildRunner host compilation: PASS under Java 8 syntax compilation
- Android XML parsing: PASS

Important verification boundary:
The current execution container does not provide an Android SDK/Gradle Android build environment, so an actual APK compile/install of DevxyzIDE itself was not performed here. AIDE-device build remains the final runtime verification step.
