# DevxyzIDE runtime provisioning

DevxyzIDE separates imported-project requirements from the IDE runtime. Imported projects own their Gradle Wrapper, AGP, repository declarations, dependencies and SDK requirements.

## Supported runtime layouts

The resolver recognizes a Termux-style app-private environment:

```text
files/usr/
files/home/.gradle/
files/home/android-sdk/
```

It also recognizes DevxyzIDE component packs under `files/toolchains/` for separately provisioned JDKs, Android SDK, aapt2, NDK and CMake.

Known JDK lookup locations include:

```text
files/toolchains/jdk17/
files/usr/lib/jvm/java-17-openjdk/
files/usr/opt/openjdk-17.0/
files/usr/opt/openjdk-17/
```

Equivalent locations are checked for the supported JDK majors.

## Terminal bootstrap identity

A bootstrap accepted by `TerminalBootstrapInstaller` must contain:

```properties
format=1
applicationId=com.jepongdevxyz.idebuild
arch=aarch64
```

in `devxyz-bootstrap.properties` plus the normal Termux `SYMLINKS.txt` data. The installer validates archive paths, validates absolute symlink targets against the DevxyzIDE private prefix, stages extraction, recreates symlinks, and activates the prefix only after validation.

Use `tools/runtime/stamp_bootstrap.py` after building the runtime with the correct package prefix.

## Component runtime descriptors

Downloaded standalone component packs can use a descriptor:

```properties
format=1
id=jdk17-arm64
component=jdk
version=17.0.x
abi=arm64-v8a
url=https://example.invalid/jdk17.devxyz-toolchain.zip
sha256=<64 lowercase hex characters>
size=<exact bytes>
```

Production descriptors are HTTPS-only. The outer archive must match exact size and SHA-256. Its inner `devxyz-toolchain.properties` then provides a SHA-256 for every payload file before atomic installation.

## Project-aware provisioning

For each project, `ToolchainProvisioningPlan` reports missing components such as `jdk11-compatible`, `android-platform-34`, `android-aapt2`, `android-ndk`, and `cmake`. DevxyzIDE does not silently rewrite the imported project to make a failed build appear compatible.
