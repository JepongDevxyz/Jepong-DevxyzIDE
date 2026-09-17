# DevxyzIDE Exact Reference UI Rebuild Design

## Goal
Rebuild the Android UI to closely match the approved DevxyzIDE reference while preserving the existing working project/import/editor/build/signing/tool backends.

## Approved visual target
The supplied five-screen reference is the source of truth: compact dark navy/cyan mobile IDE with dedicated Project Explorer, Code Editor, Build & Run, Built-in Tools, and Settings presentations. The current oversized gray controls and large empty regions are not acceptable.

## Architecture
Keep MainActivity and existing backend handlers authoritative. Replace the presentation with focused workspace containers and compact navigation. Existing import, project, editor, Git, terminal, build, APK install/signing and settings actions are reused rather than reimplemented. New UI classes may coordinate visibility/navigation but must not duplicate backend behavior.

## Screens
1. Project Explorer: compact header, project tree, search/menu affordances, cyan floating create action, Files/Search/Git/Build/More bottom nav.
2. Code Editor: file title and tabs, line-number gutter, editor body, cyan run action, Code/Terminal/Log/Problems bottom nav.
3. Build & Run: real build output, success/error state, artifact path, Install APK/Open Folder actions, Build-selected bottom nav.
4. Built-in Tools: grouped Project Tools, Utilities and Extras cards wired to existing tools; Tools-selected bottom nav.
5. Settings: compact editor/build/app setting rows and switches wired to existing preferences; Settings-selected bottom nav.

## Visual system
Dark near-black/navy background, layered navy surfaces, thin blue separators, cyan/blue selected state, white primary text, muted blue-gray secondary text. Controls must be compact and phone-scaled; no giant gray rectangular buttons. Use platform-compatible resources and existing API19/Java7 constraints; no AndroidX/Material dependency requirement.

## Compatibility
Preserve minSdk 19 and existing legacy Gradle/AGP build compatibility. Folder picker remains API21-gated. Existing project import hardening and build/signing logic must remain intact.

## Verification
Add reference-layout contract tests for required screen containers/navigation/style tokens and preservation of authoritative existing IDs/actions. Run host-tests/run.sh and the full Android verification workflow. Only produce a replacement signed release APK after tests and Android build/signature verification pass.
