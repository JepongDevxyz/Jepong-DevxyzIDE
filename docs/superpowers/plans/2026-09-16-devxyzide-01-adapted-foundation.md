# DevxyzIDE Phase 01 Adaptation Note

This branch preserves the verified v0.6 Android-on-Android runtime/build architecture instead of replacing it with a new Compose host.

## Preserved verified constraints

- Application ID remains `com.jepongdevxyz.idebuild`.
- Existing Gradle wrapper, runtime-pack, terminal-bootstrap, build planning, Sora Editor, ZIP import, and APK install flows are preserved.
- Host `targetSdk 28` remains unchanged during this phase because the app-private executable/toolchain model depends on the current verified behavior.
- No existing real build/runtime capability is replaced by a simulated implementation.

## Phase 01 implementation focus

1. Add safe project-relative path models and validation around existing file operations.
2. Add focused regression tests before changing production behavior.
3. Refactor project explorer/workspace logic behind small interfaces without rewriting the verified build/toolchain path.
4. Improve responsive shell/settings incrementally after core file safety tests are green.
5. Re-run the existing verification workflow after each checkpoint.

The modern Kotlin/Compose multi-module architecture from the approved design remains the long-term direction, but migration is incremental and must preserve verified runtime capability at every checkpoint.
