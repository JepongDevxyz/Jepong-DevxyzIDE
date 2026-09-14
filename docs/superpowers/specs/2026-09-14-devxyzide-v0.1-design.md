# DevxyzIDE v0.1 Design

DevxyzIDE uses package `com.jepongdevxyz.idebuild`, minSdk 23, compile/target API 37 and a modern dark Material UI. v0.1 provides secure ZIP import, a file explorer, text editing with line numbers, save, a Gradle process runner boundary, console output, APK discovery and install handoff.

The Android build toolchain is isolated behind `BuildRunner`. v0.1 does not pretend a JDK or Android ARM64 build tools are present: it detects the missing app-private JDK and reports the blocker. A later toolchain milestone will populate `files/toolchains/jdk17` and Android-specific build binaries without changing the editor/import interfaces.

ZIP extraction rejects canonical paths outside the workspace and enforces entry/expanded-size limits. Imported projects live only in the app-scoped external files directory, so broad storage permission is unnecessary.
