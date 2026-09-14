# DevxyzIDE v0.6 AIDE Test Edition

This is a separate compatibility snapshot for building DevxyzIDE in AIDE. The main AndroidX source is unchanged.

Compatibility-only build changes:

- legacy `buildscript` / `apply plugin` syntax for AIDE parsing
- Android Gradle Plugin 4.2.2
- Gradle Wrapper 6.7.1
- Java 8 source/target bytecode
- compileSdk 34, while preserving minSdk 28 and targetSdk 28
- AIDE-friendly `packagingOptions` syntax
- AndroidX remains enabled with Jetifier
- AndroidX dependency pins compatible with this build profile
- Sora Editor and Sora Java language remain at 0.24.6

Identity preserved:

- App name: DevxyzIDE
- Application ID/package: `com.jepongdevxyz.idebuild`

This edition is only for AIDE build/install testing. Continue feature development from the main AndroidX source, not from this compatibility snapshot.
