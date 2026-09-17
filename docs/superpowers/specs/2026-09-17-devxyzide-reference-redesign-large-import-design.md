# DevxyzIDE Reference Redesign + Large Import Hardening Design

Date: 2026-09-17
Branch: `feature/devxyzide-01-foundation`
App: DevxyzIDE
Application ID: `com.jepongdevxyz.idebuild`

## 1. Scope and intent

This change continues the existing DevxyzIDE implementation. It is not a rewrite and it must not replace real IDE behavior with a visual mockup.

Primary goals:

1. Redesign the existing DevxyzIDE UI to follow the supplied DevxyzIDE concept image: dark navy/near-black surfaces, cyan/electric-blue accent, compact IDE spacing, rounded/bordered panels, responsive portrait and landscape layouts, and persistent mobile navigation.
2. Preserve the existing editor, file/project services, build runner, diagnostics, Git, terminal, signing, toolchain, settings, cache, capability registry, tests, and release verification.
3. Harden project import so ZIP and supported folder imports use streaming I/O with no app-defined project/archive size ceiling. Practical limits are storage, provider/filesystem behavior, and device resources.
4. Keep import atomic, cancelable, secure against path traversal, and safe on low-memory phones.

Non-goals:

- Do not replace the app with Fragments/Compose solely for style.
- Do not add fake LSP or Sora Editor claims.
- Do not change the package/application ID.
- Do not change minSdk/targetSdk/build-system versions unless a verified compatibility issue requires it.
- Do not claim unsupported archive formats.
- Do not fake successful builds or generated artifacts.

## 2. Existing architecture to preserve

The current branch already has real implementations for project/file operations, multi-tab editor state, syntax styling, line-number gutter, current-line highlight, undo/redo, search, completion helpers, Gradle/build planning, diagnostics, artifact scanning, Git operations, terminal/runtime detection, APK signing, toolchain provisioning, recent projects, cache management, and capability gating.

The host remains the existing Java/AIDE-compatible Android application. `MainActivity` remains the top-level workspace orchestrator, but presentation logic added by this redesign should be split into focused helper/controller classes where that reduces further growth of `MainActivity`.

Existing services remain authoritative. UI controls call those services instead of duplicating backend behavior.

## 3. UI architecture

### 3.1 Visual system

Use the reference image as visual direction, not as a literal screenshot recreation.

Core palette:

- near-black/navy app background
- slightly lighter navy panel/card surfaces
- cyan/electric-blue primary accent
- white/high-contrast primary text
- blue-gray muted text
- subtle border/divider color
- success/warning/error colors only for status semantics

Effects:

- short, subtle state transitions only
- no continuous glow animation
- no heavy blur
- no expensive full-screen shadows
- rounded cards/pills where useful, but editor and explorer remain dense developer-tool surfaces

The app must not resemble a generic Android settings application. The code workspace is the visual priority.

### 3.2 Persistent primary navigation

Use five destinations:

- Files
- Search
- Git
- Build / Tools
- More

`Build / Tools` is one destination with internal tabs:

- Build
- Tools

The bottom navigation remains visible and readable on small screens, respects system navigation/insets, does not overlap editor content, and remains dark in landscape.

Because the host has legacy/API-19 compatibility constraints and intentionally avoids external UI dependencies, navigation/icons should use platform-compatible resources or lightweight custom-drawn views instead of introducing a modern UI library that would force a toolchain migration.

### 3.3 Screen switching

Keep one real application shell. Screen/destination switching changes which existing functional pane is visible; it does not create simulated duplicate features.

Recommended structure:

- `MainActivity`: lifecycle, project/editor/build state ownership and service orchestration
- focused UI helper/controller classes: destination selection, build presentation, tools presentation, import progress presentation
- XML layouts/resources: portrait/landscape/adaptive presentation
- existing backend classes remain unchanged unless the redesign/import work requires a focused extension

## 4. Files and project explorer

The Files destination is a professional project explorer.

Requirements:

- current project title/path context
- compact toolbar with project/file actions
- recognizable folder/file presentation
- selected-file highlight
- hierarchy indentation
- lazy expansion/enumeration
- project search entry point
- `+` action for existing file/folder creation behavior
- long-press/file actions continue to use existing real file services

Large project behavior:

- never build a full recursive project tree on the UI thread
- enumerate children only when a directory is opened/expanded
- batch UI refreshes
- do not pre-create one view/model node per file in the project
- preserve current root-containment/security rules

Portrait:

- Files uses the screen efficiently; opening a file transitions to the editor-focused workspace instead of permanently reserving half the display for the explorer.

Landscape:

- retain side-by-side explorer/editor behavior
- use a deliberate left explorer pane and right editor pane
- no white fallback panels or stretched portrait toolbar

## 5. Editor workspace

Preserve `SyntaxEditText`, `EditorSession`, editor settings, search/replace, project search, autosave, Save, Save All, dirty state, undo/redo, syntax helpers, and completion helpers.

Do not market the current editor as LSP-backed.

Redesigned editor chrome:

- filename/title/action bar
- multi-file tab strip
- active-tab emphasis
- dirty indicator
- existing line-number gutter
- existing current-line highlight
- editor search/actions
- optional real build/run action where capability state allows it
- lower workspace strip: Code / Terminal / Log / Problems

The lower strip is navigation/presentation around real existing features. It must not contain placeholder terminals/logs/problems.

Editor rendering and responsiveness take precedence over decorative effects.

## 6. Search destination

The Search destination exposes the existing current-file/project search behavior in a clearer dedicated surface.

It must:

- keep project search cancelable
- show real results only
- preserve navigable result behavior
- avoid retaining huge result sets in memory beyond existing bounded result policies
- provide empty/error/canceled states without fabricating matches

## 7. Git destination

The Git destination is a real frontend for the existing Git backend and capability registry.

It may expose only operations implemented by the branch, including current init/status/stage/unstage/commit/diff/branch/checkout/clone/fetch/pull/push behavior.

Unavailable runtime/auth/network conditions must be shown as unavailable/needs-install/error states. No fake repository status or fake successful Git operation is allowed.

## 8. Build / Tools destination

### 8.1 Build tab

The Build tab presents information from the real build process.

Show when available:

- idle/running/success/failure state
- elapsed time
- live Gradle/process output
- warnings/errors
- Problems count/navigation
- discovered APK/AAB artifacts
- artifact type
- variant
- file size
- modified time
- project-relative/absolute output path as appropriate

Actions:

- real build action(s)
- Install APK only when a real APK exists and installer handoff is valid
- Open Folder/path action only when a real local destination can be opened/surfaced

Never pre-fill a fake `BUILD SUCCESSFUL` state.

### 8.2 Tools tab

Categorize real features only.

Project Tools:

- New Project
- Open/Recent Project
- Import Project
- Project Settings

Utilities:

- Terminal
- Git
- APK Signer
- APK/AAB/build actions
- Toolchain Manager

Extras are displayed only for implemented capabilities, for example existing developer/resource/database tools where present.

The capability registry remains authoritative for enabled, unavailable, and needs-install states.

## 9. More / Settings

More acts as a compact control center for:

- Settings
- backup/cache/storage actions
- recent projects
- app/project information
- About/branding

Settings groups:

Editor:

- Font Size
- Theme/appearance behavior already supported
- Word Wrap
- completion setting if backed by current behavior
- Tab Width
- Autosave

Build / Toolchain:

- detected Gradle/JDK/Android SDK/aapt2/toolchain status
- existing build options
- links/actions to real toolchain management

App:

- appearance
- backup projects
- cache/storage
- language only if there is real supported behavior
- About

Existing settings persistence remains the source of truth.

## 10. Branding

Brand remains:

- DevxyzIDE
- Code • Build • Create
- optional `Powered by Jepong Devxyz`

Splash/icon direction stays consistent with the supplied concept: dark navy/black, cyan/electric blue, DevxyzIDE identity.

Branding must not overpower the editor/workspace.

## 11. Project import architecture

### 11.1 Core rule: no app-defined import size ceiling

Project import must not reject a project merely because it crosses a fixed threshold such as 50 MB, 500 MB, 1 GB, 8 GB, or any other app-defined archive/project byte limit.

The existing project-import path currently has fixed byte/entry limits in the UI flow; the redesigned path must stop using those as project-size acceptance limits.

Limits for unrelated artifacts such as toolchain packs/backups are outside this requirement and should not be changed unless separately justified.

### 11.2 Streaming ZIP extraction

ZIP import remains incremental:

`ContentResolver/InputStream -> BufferedInputStream -> ZipInputStream -> reusable fixed-size buffer -> BufferedOutputStream -> staging file`

No `byte[]` sized to the source/archive/project is allowed.

A reusable buffer in the tens or hundreds of KB is appropriate. Buffer size never depends on archive size.

### 11.3 SAF and URI handling

ZIP sources continue to be opened through `ContentResolver` streams. Do not convert arbitrary `content://` URIs to `/storage/...` paths.

Supported providers may include Downloads, internal storage documents, SD/USB document providers, Files, and cloud/document providers that expose readable streams.

Folder import:

- on API levels supporting `ACTION_OPEN_DOCUMENT_TREE`, allow importing a provider-backed folder tree
- traverse provider children incrementally through platform `DocumentsContract`/`ContentResolver` APIs or another dependency-free platform-compatible method
- copy one file at a time through streaming I/O
- do not materialize the entire folder tree in memory
- on API levels where SAF tree picking is unavailable, expose the real limitation instead of pretending folder SAF import works

### 11.4 Atomic staging

All imported content is written under the projects directory into a hidden/staging directory, for example:

`.import-MyProject-<nonce>`

Flow:

1. create staging directory
2. stream/copy/extract source into staging
3. validate source/project structure while importing
4. determine the project root
5. ensure final project name does not conflict unexpectedly
6. finalize with same-filesystem rename/activation
7. only then expose/load the project

On failure/cancellation, the final project must not appear as a completed import.

### 11.5 Root detection without giant post-import scans

The current root detector performs a bounded recursive/BFS directory scan. Large-import hardening should avoid relying on fixed max-depth/max-directory acceptance limits.

Preferred redesign:

- track likely Gradle root candidates while streaming ZIP entries or copying provider files
- recognize `settings.gradle` / `settings.gradle.kts` first, with `build.gradle` / `build.gradle.kts` as fallback
- store only the best relative candidate path, not all paths
- select the shallowest/most appropriate candidate deterministically

This avoids a second full-tree traversal and prevents large/deep imports from failing merely because project-root detection crossed an arbitrary scan count/depth.

If post-copy fallback detection is still needed, it must remain bounded-memory and must not impose an arbitrary project-size limit.

### 11.6 ZIP security

Keep and strengthen existing path-containment checks.

Reject/handle safely:

- `../` traversal
- canonical/resolved output outside staging
- absolute paths
- malformed names
- conflicting file/directory paths
- corrupted ZIP data
- output-parent creation failure

The normalized/canonical target of every extracted entry must remain inside staging.

Security must not be weakened to achieve large-file support.

### 11.7 Duplicate/conflicting entries

ZIP entries that conflict as file-vs-directory or create unsafe duplicate semantics must fail clearly rather than silently overwriting an unrelated path.

Existing source remains untouched on any failure.

### 11.8 Disk-space handling

Do not require a full pre-scan solely to calculate an exact expanded size.

When an entry/source size is cheaply known, compare it with available workspace storage before writing.

During streaming, monitor usable storage so an import that exhausts storage terminates safely.

If exact required size is unknown, the UI must not invent one. Report the available space and that required size was unknown.

Map storage exhaustion to a clear `Not enough free storage` import error where possible.

### 11.9 Cancellation

Use a cancellation signal shared by ZIP and folder import.

Cancellation checks occur:

- before each entry/document
- repeatedly while copying file contents
- before project validation/finalization

Cancel must:

- stop promptly
- close current streams
- prevent final activation
- clean staging data when safe
- leave original source untouched

Cleanup of very deep trees must avoid recursive-stack failure; use an iterative/bounded approach where needed.

### 11.10 Progress

Import work stays off the main UI thread.

A modern progress surface shows what is actually known:

- Importing Project
- files processed
- bytes copied/expanded
- current relative path when available
- determinate progress only when a trustworthy total is cheaply available
- otherwise indeterminate progress
- Cancel

Do not scan a huge source twice solely to produce a percentage.

Throttle/batch UI progress updates so thousands of files do not generate one main-thread redraw per buffer/entry.

### 11.11 Low-memory behavior

Import must not:

- read complete archives/files into memory
- build a full project path list
- pre-index the whole project during copy
- recursively render the entire tree after import
- generate previews/bitmaps for every file

Release streams/cursors promptly. Keep progress/root-detection state small and constant/bounded relative to project size.

## 12. Import error model

Present specific, understandable errors for:

- canceled import
- invalid/corrupted ZIP
- unsafe ZIP path
- conflicting archive entry
- permission denied
- source/provider removed or unreadable
- unsupported provider/tree behavior
- duplicate final project
- invalid project name
- storage full
- destination creation/finalization failure
- inaccessible document/file
- no importable project root where applicable

Never turn a failed/canceled import into a success state.

## 13. Interruption and recovery

Correctness is higher priority than complex resume support.

Minimum behavior:

- staging directories are identifiable as incomplete
- incomplete staging is never shown as a completed project
- stale staging can be cleaned safely on a later app run or through cache/storage maintenance
- if recovery is added, it must validate the source/staging state before resuming

No requirement to implement resumable ZIP decompression if it would make correctness worse.

## 14. Responsive behavior

### Portrait

- persistent bottom navigation
- editor gets maximum useful height
- Files can transition into editor focus rather than fixed split
- cards and dialogs fit small phones
- no toolbar overflow that requires a giant horizontal row of all actions

### Landscape

- intentionally separate layout/resource behavior
- explorer/editor side-by-side where appropriate
- bottom navigation remains dark and non-overlapping
- build/tools/settings surfaces use width efficiently
- dialogs fit without clipped actions
- no white panels/system-bar regressions

Insets/system bars remain dark and compatible with the branch's supported APIs.

## 15. Performance boundaries

UI:

- avoid deep unnecessary layout nesting
- avoid animated blur/glow
- reuse/adapt rows where practical
- throttle frequent log/progress refreshes
- editor/file explorer performance wins over decoration

Import:

- fixed reusable buffers
- one file/entry streamed at a time
- small root/progress state
- no giant recursive file model

Project tree:

- lazy child enumeration
- no immediate full-tree rendering after import

## 16. Compatibility constraints

Preserve current application identity and AIDE-compatible architecture.

Do not change Gradle/AGP/minSdk/targetSdk merely to obtain modern widgets. The visual design must be implemented using resources/custom platform-compatible code that works with the current host toolchain.

Where a platform feature is not available on the minimum API, gate it by real API/capability state instead of changing the minimum API without need.

## 17. Testing strategy

Keep all existing tests and release workflows enabled.

### 17.1 Import tests

Maintain/add synthetic tests for:

- small ZIP project
- single large entry streamed through bounded buffer behavior
- thousands of files
- deep directory/project-root path
- large ZIP extraction generated during test, not stored in Git
- cancellation during an entry
- cancellation between entries
- malformed/corrupted ZIP
- Zip Slip attempt
- absolute/unsafe path handling
- conflicting duplicate/file-directory entries
- atomic staging/finalization
- failure cleanup
- stale staging detection/cleanup where implemented
- duplicate project behavior
- disk-full/space-check behavior where testable
- provider/content-stream abstraction behavior where host-testable
- project-root detection without fixed depth/directory acceptance limits
- lazy directory/project tree behavior

Stress tests generate files/ZIPs programmatically and must not commit multi-gigabyte fixtures.

### 17.2 UI/source contracts

Add/maintain checks covering:

- five primary destinations
- Build / Tools internal tabs
- portrait dark layout
- landscape dark layout
- no white/default unintended surfaces
- persistent bottom navigation IDs/resources
- editor tab/gutter wiring preserved
- capability-gated real tool actions
- import progress/cancel UI wiring

Where screenshot instrumentation is impractical under the legacy gate, source/layout contracts supplement emulator launch verification; they do not replace real APK build/install/launch checks.

### 17.3 Release verification

The following remain required and must not be disabled:

- host tests
- Python/source contract tests
- clean DevxyzIDE APK build
- signing verification
- generated Java/Kotlin sample project builds
- offline rebuild checks
- broken-source diagnostics check
- emulator install and launch
- `DevxyzIDE Full Android Verification`
- `DevxyzIDE Device Install Verification`
- large-import stress workflow

Do not claim completion until the latest commit under test has passing required gates.

## 18. Acceptance criteria

The work is accepted only when:

1. UI follows the supplied DevxyzIDE visual direction while remaining a real IDE frontend.
2. Files, Search, Git, Build / Tools, and More are real persistent primary destinations.
3. Build / Tools contains real Build and Tools tabs.
4. Existing real functionality is preserved.
5. Portrait and landscape are intentionally designed and remain dark.
6. Explorer remains lazy/responsive for large projects.
7. Existing editor behavior remains functional and is not misrepresented as LSP.
8. Build screen reflects real process/artifact state.
9. Tools/settings expose real capabilities only.
10. Project import no longer uses an app-defined project/archive byte ceiling.
11. Import does not use an arbitrary entry-count or root-scan count/depth limit as a project-size acceptance policy.
12. ZIP/folder copy paths are streaming and bounded-memory.
13. SAF/content URI behavior does not require real filesystem path conversion.
14. Cancellation is safe and prompt.
15. Partial imports are never loaded as completed projects.
16. ZIP path traversal protection remains enforced.
17. Storage exhaustion is handled safely and reported accurately.
18. Progress UI remains responsive without expensive pre-scans.
19. Existing host tests pass.
20. Host APK builds, signs, installs, and launches in the established verification gates.
21. Existing Full Android Verification and Device Install Verification remain green on the final tested commit.

## 19. Implementation sequencing recommendation

1. Add/update tests that capture the import-limit, root-detection, cancellation, atomicity, and UI-layout contracts.
2. Harden import core and connect the real MainActivity import flow to the atomic streaming service.
3. Add SAF folder-import path where platform-supported.
4. Introduce shared visual resources and navigation shell.
5. Redesign Files/editor workspace while preserving existing editor/file services.
6. Add Search and Git destination presentation around existing services.
7. Add Build / Tools destination presentation and real build/artifact state wiring.
8. Add More/Settings presentation and branding polish.
9. Verify portrait/landscape, dark surfaces, source contracts, host tests, APK build/sign/install/launch, and GitHub Actions gates.

This order hardens correctness first, then applies the larger UI change over a safer import foundation.