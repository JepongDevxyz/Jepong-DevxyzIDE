# DevxyzIDE Release Readiness Design

Date: 2026-11-09

## Intent
Stabilize the current `main` implementation without replacing its newer Fragment-based UI. Make project creation and ZIP import safe and repeatable, validate imported Android build tools honestly, and ensure CI exercises the current application rather than an obsolete UI flow.

## Goals
- Preserve the existing Files/Search/Build/Tools/Settings UI and all current handlers.
- New-project creation produces a complete Gradle Android project and never overwrites an existing workspace.
- ZIP import rejects traversal, malformed/ambiguous projects, and excessive expanded archives; failed imports do not replace the selected project.
- Toolchain installation verifies executable binaries and restores the prior installation if activation or runtime validation fails.
- CI verifies lint, unit tests, APK package/signature, and a smoke test aligned with the current Fragment-based app.

## Non-goals
- Do not redesign or replace the current Fragment-based UI.
- Do not merge PR #8 or PR #9 wholesale: both are 12 commits behind `main`, and PR #9's MainActivity/layout changes conflict conceptually with the newer Fragment architecture.
- Do not publish a release or merge to `main` until verification is green and the user approves.
- Do not claim physical-device build support based solely on hosted CI.

## Constraints
- Package: `com.jepongdevxyz.idebuild`.
- minSdk 26, compileSdk/target SDK 35, Java 17, Gradle 8.10.2.
- Preserve existing project files and behavior; changes are additive or corrective.
- Work on isolated branch `fix/devxyzide-release-readiness-2026-11-09`.

## Approaches
1. Merge PR #8 and #9 directly: rejected because both branches are stale and PR #9's older single-activity UI would overwrite the newer Fragment-based UI in `main`.
2. Rebase/merge both branches and resolve conflicts: rejected because the conflict is architectural, not just textual, and would risk regressing the current UI.
3. Port only the validated capabilities into the current architecture: selected. Reuse safe importer/template/toolchain logic while adapting MainActivity entry points and tests to current APIs. This preserves current UI and minimizes regression risk.

## Implementation decisions
- Extract ZIP import and project-template generation into focused, unit-testable helpers.
- Use staging directories and atomic activation for both imported projects and toolchains.
- Test the exact filesystem contract used by MainActivity and the CI emulator.
- Update CI smoke testing to target current resource IDs / app-data layout, not PR #9's retired layout.
- Keep all work in a PR; do not change `main` directly.
