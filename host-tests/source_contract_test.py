from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
build = (ROOT/'app/build.gradle').read_text()
root_build = (ROOT/'build.gradle').read_text()
layout = (ROOT/'app/src/main/res/layout/activity_main.xml').read_text()
land_layout = (ROOT/'app/src/main/res/layout-land/activity_main.xml').read_text()
main = (ROOT/'app/src/main/java/com/jepongdevxyz/idebuild/MainActivity.java').read_text()
manifest = (ROOT/'app/src/main/AndroidManifest.xml').read_text()
script = ROOT/'runtime-builder/build-devxyz-terminal-runtime.sh'
settings_view_path = ROOT/'app/src/main/java/com/jepongdevxyz/idebuild/EditorSettingsButton.java'
terminal_view_path = ROOT/'app/src/main/java/com/jepongdevxyz/idebuild/TerminalButton.java'

# AIDE Test Edition intentionally uses only platform Android widgets/classes.
assert "dependencies {\n}" in build, "AIDE edition must not require external Maven UI/editor dependencies"
assert "sourceCompatibility JavaVersion.VERSION_1_7" in build
assert "compileSdkVersion 28" in build
assert "com.android.tools.build:gradle:3.2.1" in root_build
assert "<EditText" in layout and "io.github.rosemoe" not in layout
assert "EditText editor" in main and "io.github.rosemoe" not in main
assert "androidx." not in main
assert main.count('protected void onDestroy()') == 1
assert "applicationId 'com.jepongdevxyz.idebuild'" in build
assert ("targetSdk 28" in build or "targetSdkVersion 28" in build)
assert '.ApkFileProvider' in manifest and 'androidx.core.content.FileProvider' not in manifest
assert script.is_file(), "Devxyz runtime builder missing"
text = script.read_text()
assert "com.jepongdevxyz.idebuild" in text
assert "610af608b4a3b1127244e90edf8b7be2bf94fafa" in text
assert 'generate-bootstraps.sh' in text
assert 'stamp_bootstrap.py' in text
assert 'runtimeButton' in main
assert 'TerminalBootstrapInstaller.install' in main
assert '@+id/runtimeButton' in layout
assert '<EditText' in land_layout
assert '@+id/runtimeButton' in land_layout

# Project file open/save must resolve a ProjectPath against the trusted root each time.
assert 'WorkspacePathResolver' in main, "MainActivity must use root-contained workspace resolution"
assert 'ProjectPath currentPath' in main, "Editor must retain a project-relative path rather than a raw File"
assert 'new File(projectRoot, relative)' not in main, "Raw project-relative File construction bypasses containment checks"
assert 'currentFile' not in main, "Editor save state must not retain a raw File that can become a symlink escape"

# Project Explorer must list only the current directory instead of recursively scanning the full project.
assert 'ProjectDirectoryService' in main, "MainActivity must use the lazy directory service"
assert 'ProjectPath currentDirectory' in main, "Explorer must keep a project-relative current directory"
assert 'listDirectory(service, directory)' in main, "Explorer refresh must request a lazy immediate-child listing"
assert 'service.listChildren(directory, MAX_DIRECTORY_CHILDREN)' in main, "Lazy directory helper must request only immediate children"
assert 'ProjectFiles.listRelativeFiles' not in main, "Explorer must not rescan the full project tree on refresh"

# Real project file actions must be backed by ProjectFileService and destructive actions need confirmation.
assert 'ProjectFileService' in main, "Explorer mutations must use the safe project file service"
assert 'setOnItemLongClickListener' in main, "Explorer must expose rename/duplicate/delete actions"
assert 'new AlertDialog.Builder' in main, "Destructive explorer actions must use an explicit confirmation dialog"
assert '@+id/newFileButton' in layout and '@+id/newFolderButton' in layout
assert '@+id/newFileButton' in land_layout and '@+id/newFolderButton' in land_layout

# Users must be able to create a real Gradle project from the verified templates.
assert 'ProjectTemplateGenerator' in main, "MainActivity must expose real project creation"
assert 'promptCreateProject' in main, "Project creation must collect project metadata before generation"
assert '@+id/createProjectButton' in layout
assert '@+id/createProjectButton' in land_layout

# Loaded projects must be exportable as source-focused ZIP backups through Android SAF.
assert 'ProjectArchiveService' in main, "MainActivity must use the safe project archive service"
assert 'Intent.ACTION_CREATE_DOCUMENT' in main, "Backup export must use the Android Storage Access Framework"
assert 'ProjectArchiveService.writeSourceArchive' in main, "Backup export must archive the loaded project"
assert '@+id/backupButton' in layout
assert '@+id/backupButton' in land_layout

# Editor UX must expose real multi-tab state, Save All, and literal search/replace.
assert 'EditorSession' in main, "MainActivity must use the multi-tab editor session model"
assert 'TextSearchService' in main, "MainActivity must use literal text search/replace"
assert 'TextWatcher' in main, "Editor changes must update dirty-tab state"
assert 'renderEditorTabs' in main, "Editor tabs must be rendered from the session model"
assert 'saveAllOpenDocuments' in main, "Editor must support Save All"
assert 'showSearchDialog' in main, "Editor must expose search/replace UI"
assert '@+id/tabBar' in layout and '@+id/tabBar' in land_layout
assert '@+id/searchButton' in layout and '@+id/searchButton' in land_layout
assert '@+id/saveAllButton' in layout and '@+id/saveAllButton' in land_layout

# Project search must be streamed/cancelable and build diagnostics must be navigable.
assert 'ProjectSearchService' in main, "MainActivity must expose project-wide search"
assert 'activeProjectSearch' in main, "Project-wide search must support cancellation"
assert 'showProjectSearchResults' in main, "Project-wide search results must be navigable"
assert 'BuildDiagnosticsParser' in main and 'BuildProblem' in main, "Build output must feed Problems"
assert 'recordBuildProblem' in main, "Build lines must be parsed into Problems"
assert 'showProblemsDialog' in main, "Problems must be visible and navigable"
assert '@+id/projectSearchButton' in layout and '@+id/projectSearchButton' in land_layout
assert '@+id/problemsButton' in layout and '@+id/problemsButton' in land_layout

# Portrait keeps a stacked workspace while landscape must use a real side-by-side workspace.
assert '@+id/workspaceBody' in layout and '@+id/workspaceBody' in land_layout, "Workspace body must have a stable responsive-shell id"
assert '@+id/projectPane' in layout and '@+id/projectPane' in land_layout, "Project pane must be addressable in both orientations"
assert '@+id/editorPane' in layout and '@+id/editorPane' in land_layout, "Editor pane must be addressable in both orientations"
portrait_body = layout.split('android:id="@+id/workspaceBody"', 1)[1].split('>', 1)[0]
landscape_body = land_layout.split('android:id="@+id/workspaceBody"', 1)[1].split('>', 1)[0]
assert 'android:orientation="vertical"' in portrait_body, "Portrait workspace must remain stacked"
assert 'android:orientation="horizontal"' in landscape_body, "Landscape workspace must be side-by-side"

# Editor settings must be functional, persistent, modular, and available in both orientations.
assert '@+id/settingsButton' in layout and '@+id/settingsButton' in land_layout, "Settings action must exist in portrait and landscape"
assert settings_view_path.is_file(), "Editor settings must live outside MainActivity"
settings_view = settings_view_path.read_text()
assert 'SharedPreferences' in settings_view, "Editor settings must persist across app restarts"
assert 'EditorSettings' in settings_view, "Settings UI must apply the validated settings model"
assert 'showSettingsDialog' in settings_view, "Settings action must open a functional editor settings dialog"
assert 'setHorizontallyScrolling(!settings.isWordWrap())' in settings_view, "Word wrap must change actual editor behavior"
assert 'editor.setTextSize(settings.getFontSizeSp())' in settings_view, "Font size preference must change actual editor text size"

# Switching projects must never silently discard dirty editor tabs.
assert 'editorSession.hasDirtyDocuments()' in main, "Project switching must detect unsaved editor tabs"
assert 'confirmProjectSwitch' in main, "Dirty project switches must require an explicit user decision"
assert 'saveAllBeforeProjectSwitch' in main, "Users must be able to save all dirty tabs before switching projects"
assert 'loadProjectNow' in main, "Confirmed project switching must be separated from the dirty-state guard"
assert 'Discard & Switch' in main and 'Save All & Switch' in main, "Project switch dialog must expose safe save/discard choices"

# Terminal UI must execute real device commands through the shared process engine.
assert '@+id/terminalButton' in layout and '@+id/terminalButton' in land_layout, "Terminal action must exist in portrait and landscape"
assert terminal_view_path.is_file(), "Terminal UI must live outside MainActivity"
terminal_view = terminal_view_path.read_text()
assert 'TerminalCommandPlanner.plan' in terminal_view, "Terminal UI must use the tested shell planner"
assert 'ProcessEngine.start' in terminal_view, "Terminal UI must execute a real process"
assert 'onStdout' in terminal_view and 'onStderr' in terminal_view, "Terminal UI must stream real stdout and stderr"
assert '.cancel()' in terminal_view, "Terminal UI must support stopping a running process"
assert 'setWorkingDirectory' in terminal_view, "Terminal UI must accept the loaded project as cwd"
assert 'terminalButton.setWorkingDirectory(projectRoot)' in main, "Loaded project must update terminal working directory"

print("SOURCE CONTRACT TESTS PASSED (AIDE TEST EDITION)")
