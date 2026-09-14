# DevxyzIDE app-private toolchains

DevxyzIDE resolves build runtimes from its private files directory. The imported project's Gradle Wrapper remains authoritative.

```text
files/
├── gradle-home/                       # Gradle wrapper distributions + dependency cache
└── toolchains/
    ├── jdk11/
    │   └── bin/java
    ├── jdk17/
    │   └── bin/java
    ├── jdk21/
    │   └── bin/java
    ├── jdk25/
    │   └── bin/java
    ├── jdk26/
    │   └── bin/java
    ├── android-sdk/
    │   ├── platforms/android-<API>/android.jar
    │   ├── build-tools/<version>/...
    │   ├── ndk/<version>/...          # optional
    │   └── cmake/<version>/...        # optional
    └── aapt2/
        └── aapt2                      # Android/ARM-compatible binary
```

For Android projects DevxyzIDE sets `ANDROID_HOME` and `ANDROID_SDK_ROOT`, shares `GRADLE_USER_HOME`, and passes `-Pandroid.aapt2FromMavenOverride=<app-private-aapt2>`.

## Toolchain-pack format

Example `devxyz-toolchain.properties` inside a pack:

```properties
format=1
target=toolchains/jdk17
file.bin/java=<64-character SHA-256>
file.bin/javac=<64-character SHA-256>
file.lib/modules=<64-character SHA-256>
```

Every non-directory payload entry must be listed with a SHA-256. Targets must remain below `toolchains/`. Unexpected files, missing files, bad hashes and path traversal are rejected.
