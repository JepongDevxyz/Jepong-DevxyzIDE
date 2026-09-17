from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
portrait = (ROOT / 'app/src/main/res/layout/activity_main.xml').read_text()
colors = (ROOT / 'app/src/main/res/values/colors.xml').read_text()

for token in ('#050A12', '#091321', '#00C8FF'):
    assert token in colors
for view_id in ('projectPane', 'editorPane', 'workspace_build_tools', 'workspace_more', 'bottomNavigation'):
    assert ('@+id/' + view_id) in portrait
for label in ('PROJECT EXPLORER', 'Code', 'Terminal', 'Log', 'Problems', 'BUILD &amp; RUN', 'BUILT-IN TOOLS', 'SETTINGS'):
    assert label in portrait, 'missing reference label: ' + label
assert 'android:text="+ File"' not in portrait, 'oversized legacy + File button still visible'
assert 'android:text="+ Folder"' not in portrait, 'oversized legacy + Folder button still visible'
assert 'android:layout_height="52dp"' in portrait, 'compact reference bottom navigation height missing'
assert '@+id/buildSurfaceButton' not in portrait, 'inert buildSurfaceButton must not replace authoritative buildButton'

# Core Build/Tools/Settings actions must exist before the final hidden compatibility holder.
hidden_marker = '<LinearLayout android:layout_width="1dp" android:layout_height="1dp" android:visibility="gone">'
hidden_at = portrait.rfind(hidden_marker)
assert hidden_at >= 0, 'hidden compatibility holder missing'
for control in ('buildButton', 'installButton', 'buildActionsButton', 'terminalButton', 'gitButton',
                'signApkButton', 'developerToolsButton', 'projectSettingsButton', 'settingsButton'):
    token = 'android:id="@+id/%s"' % control
    pos = portrait.find(token)
    assert pos >= 0 and pos < hidden_at, '%s must be a visible authoritative control' % control
    assert portrait.find(token, pos + 1) < 0, '%s must only exist once' % control

print('reference exact UI contract: OK')
