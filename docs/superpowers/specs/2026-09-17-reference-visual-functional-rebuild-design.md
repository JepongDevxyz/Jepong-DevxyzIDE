# DevxyzIDE Reference Visual + Functional Rebuild Design

## Goal
Rebuild the installed DevxyzIDE UI so the real app matches the user's approved image reference rather than merely satisfying XML/ID contracts, while making every visible navigation/action control execute its real feature.

## Visual source of truth
The user's approved DevxyzIDE image reference is authoritative for composition and visual hierarchy. The previous signed APK is explicitly rejected as a visual baseline.

Required visual language:
- near-black/navy IDE workspace, compact edge-to-edge composition
- cyan/blue active accents; muted blue-gray inactive text
- compact toolbars, tabs, cards, rows and bottom navigation
- Project Explorer + code editor are the primary workspace, not large generic Android buttons
- editor has file tabs, code-oriented typography/line treatment, and compact Code / Terminal / Log / Problems controls
- Build & Run is a real IDE surface with build status/log/artifact actions
- Built-in Tools are compact grouped tool cards/rows
- Settings uses compact IDE setting rows/toggles/dropdowns
- custom DevxyzIDE icon/splash consistent with the reference theme
- no default Holo/Android gray button chrome anywhere in authoritative visible UI
- portrait and landscape both preserve the reference design language

## Functional source of truth
No visible control may be decorative or backed by a hidden 1dp compatibility control. Visible controls are authoritative and wired to existing production handlers/controllers.

Flows that must work without closing the app:
- Create Project
- Import Project and cancel/failure recovery
- project/file browsing
- New File / New Folder
- open/edit/save files
- Search
- Git surface/actions supported by the backend
- Build Project / build log / artifact discovery
- Install generated APK when available
- Terminal
- APK Signer
- Developer Tools
- Editor Settings
- Project Settings
- bottom workspace navigation

Unsupported capabilities must show a clear disabled/needs-install state rather than silently doing nothing.

## Architecture
Keep existing Java/API19-compatible production backends and controllers where they are valid, but replace the presentation shell and remove the hidden-control compatibility pattern. MainActivity remains the integration owner; visible views bind directly to existing listeners/controllers. Split reusable visual state into API19-safe drawables/styles and focused custom views instead of adding AndroidX/Material dependencies.

## Verification
The old presence-only tests are insufficient. New regression contracts must reject default gray button styling, hidden authoritative controls, duplicate IDs and unwired visible actions. Existing host tests remain. Full Android verification must compile with the legacy toolchain, validate APK signing, install and launch on API 28, and exercise representative UI flows. A new official signed release is produced only after these checks are green.

## Constraints
- package: com.jepongdevxyz.idebuild
- minSdkVersion 19
- targetSdkVersion 29
- compileSdk 29
- Java 7 source compatibility
- no AndroidX/Material dependency requirement
- do not commit signing secrets or keystores
- do not call a build final solely because it launches
