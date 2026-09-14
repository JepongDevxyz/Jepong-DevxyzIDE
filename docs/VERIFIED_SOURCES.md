# Verified upstream baseline (2026-09-14)

- Android Gradle Plugin 9.4.0 compatibility:
  https://developer.android.com/build/releases/agp-9-4-0-release-notes
  - baseline Gradle 9.6.0
  - JDK 17
  - SDK Build Tools 36.0.0
  - maximum API 37
- Gradle Wrapper (project-declared Gradle version is the recommended execution path):
  https://docs.gradle.org/current/userguide/gradle_wrapper.html
- Gradle Java runtime compatibility matrix:
  https://docs.gradle.org/current/userguide/compatibility.html
- Android remote repositories (`google()`, `mavenCentral()`, `mavenLocal()`, custom Maven/Ivy):
  https://developer.android.com/build/remote-repositories
- AndroidIDE historical Android-host aapt2 override reference:
  https://github.com/AndroidIDEOfficial/AndroidIDE/wiki/Getting-started
- AndroidX versions:
  https://developer.android.com/jetpack/androidx/versions
- AppCompat 1.8.0:
  https://developer.android.com/jetpack/androidx/releases/appcompat
- Material Components 1.14.0:
  https://github.com/material-components/material-components-android/releases/tag/1.14.0

These sources justify the project-aware wrapper/JDK/repository/aapt2 architecture. They do not prove that an Android ARM64 JDK/SDK/aapt2 payload is already bundled in the source archive; that requires a separate physical-device/toolchain provisioning milestone.
