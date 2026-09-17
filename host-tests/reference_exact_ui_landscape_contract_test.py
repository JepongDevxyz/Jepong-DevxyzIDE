from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
layout = (ROOT / "app/src/main/res/layout-land/activity_main.xml").read_text(encoding="utf-8")


def require(value, message):
    if not value:
        raise AssertionError(message)


for token in (
    'android:id="@+id/workspaceBody"',
    'android:orientation="horizontal"',
    'PROJECT EXPLORER',
    'Code', 'Terminal', 'Log', 'Problems',
    'BUILD &amp; RUN',
    'BUILT-IN TOOLS',
    'SETTINGS',
    'android:id="@+id/workspace_search"',
    'android:id="@+id/workspace_git"',
    'android:id="@+id/buildButton"',
    'android:id="@+id/settingsButton"',
    'android:id="@+id/developerToolsButton"',
    'android:id="@+id/projectSettingsButton"',
    'android:id="@+id/terminalButton"',
    'android:id="@+id/gitButton"',
    'android:id="@+id/bottomNavigation"',
    'android:layout_height="52dp"',
):
    require(token in layout, "Landscape reference UI missing: %s" % token)

for legacy in ('BUILD &amp; TOOLS', 'MORE / SETTINGS'):
    require(legacy not in layout, "Landscape still exposes legacy UI: %s" % legacy)

# Save/Save All/Complete/Actions are intentional real editor controls in the approved UI.
for label in ('android:text="Save"', 'android:text="Save All"', 'android:text="Complete"', 'android:text="Actions"'):
    require(label in layout, "Landscape editor toolbar missing: %s" % label)

hidden_marker = '<LinearLayout android:layout_width="1dp" android:layout_height="1dp" android:visibility="gone">'
require(hidden_marker not in layout, "Landscape must not keep a hidden compatibility holder")

for control in ('createProjectButton', 'newFileButton', 'newFolderButton', 'recentProjectsButton', 'importButton',
                'searchButton', 'projectSearchButton', 'saveButton', 'saveAllButton', 'completionButton',
                'editorActionsButton', 'problemsButton', 'buildButton', 'installButton', 'buildActionsButton',
                'terminalButton', 'gitButton', 'signApkButton', 'developerToolsButton', 'projectSettingsButton',
                'settingsButton', 'backupButton', 'toolchainButton', 'sdkManagerButton', 'runtimeButton'):
    token = 'android:id="@+id/%s"' % control
    require(layout.count(token) == 1, "%s must exist exactly once as an authoritative landscape control" % control)

print("REFERENCE EXACT LANDSCAPE UI CONTRACT PASSED")
