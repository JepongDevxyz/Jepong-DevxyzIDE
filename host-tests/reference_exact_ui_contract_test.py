from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
portrait = (ROOT / 'app/src/main/res/layout/activity_main.xml').read_text()
colors = (ROOT / 'app/src/main/res/values/colors.xml').read_text()

for token in ('#050A12', '#091321', '#00C8FF'):
    assert token in colors
for view_id in ('projectPane', 'editorPane', 'workspace_search', 'workspace_git', 'workspace_build_tools', 'workspace_more', 'bottomNavigation'):
    assert ('@+id/' + view_id) in portrait
for label in ('PROJECT EXPLORER', 'Code', 'Terminal', 'Log', 'Problems', 'BUILD &amp; RUN', 'BUILT-IN TOOLS', 'SETTINGS'):
    assert label in portrait, 'missing reference label: ' + label
assert 'android:text="+ File"' not in portrait, 'oversized legacy + File button still visible'
assert 'android:text="+ Folder"' not in portrait, 'oversized legacy + Folder button still visible'
assert 'android:layout_height="52dp"' in portrait, 'compact reference bottom navigation height missing'
assert '@+id/buildSurfaceButton' not in portrait, 'inert buildSurfaceButton must not replace authoritative buildButton'
assert '<LinearLayout android:layout_width="1dp" android:layout_height="1dp" android:visibility="gone">' not in portrait, 'hidden compatibility holder must not exist'

for control in ('createProjectButton', 'newFileButton', 'newFolderButton', 'recentProjectsButton', 'importButton',
                'searchButton', 'projectSearchButton', 'saveButton', 'saveAllButton', 'completionButton',
                'editorActionsButton', 'problemsButton', 'buildButton', 'installButton', 'buildActionsButton',
                'terminalButton', 'gitButton', 'signApkButton', 'developerToolsButton', 'projectSettingsButton',
                'settingsButton', 'backupButton', 'toolchainButton', 'sdkManagerButton', 'runtimeButton'):
    token = 'android:id="@+id/%s"' % control
    assert portrait.count(token) == 1, '%s must exist exactly once as an authoritative control' % control

print('reference exact UI contract: OK')
