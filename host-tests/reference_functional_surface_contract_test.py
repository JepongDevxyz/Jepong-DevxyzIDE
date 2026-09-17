from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
portrait = (ROOT / 'app/src/main/res/layout/activity_main.xml').read_text()
landscape = (ROOT / 'app/src/main/res/layout-land/activity_main.xml').read_text()

ACTION_IDS = (
    'createProjectButton', 'newFileButton', 'newFolderButton',
    'buildButton', 'installButton', 'buildActionsButton',
    'terminalButton', 'gitButton', 'signApkButton', 'developerToolsButton',
    'settingsButton', 'projectSettingsButton',
    'nav_files', 'nav_search', 'nav_git', 'nav_build_tools', 'nav_more'
)

for name, layout in (('portrait', portrait), ('landscape', landscape)):
    assert 'android:layout_width="1dp" android:layout_height="1dp" android:visibility="gone"' not in layout, \
        '%s must not keep actionable compatibility controls in a hidden 1dp holder' % name

    for control in ACTION_IDS:
        token = 'android:id="@+id/%s"' % control
        assert layout.count(token) == 1, '%s must contain exactly one authoritative %s' % (name, control)

    assert 'android:id="@+id/installButton"' in layout
    assert 'android:id="@+id/buildActionsButton"' in layout
    assert 'android:id="@+id/terminalButton"' in layout
    assert 'android:id="@+id/gitButton"' in layout
    assert 'android:id="@+id/settingsButton"' in layout

    # Reference UI uses explicit DevxyzIDE surfaces rather than platform-default gray chrome.
    for control in ('installButton', 'buildActionsButton', 'terminalButton', 'gitButton',
                    'signApkButton', 'developerToolsButton', 'settingsButton', 'projectSettingsButton'):
        pos = layout.find('android:id="@+id/%s"' % control)
        assert pos >= 0
        tag_end = layout.find('/>', pos)
        tag = layout[pos:tag_end]
        assert 'android:background="@drawable/devxyz_' in tag, \
            '%s %s must use explicit DevxyzIDE styling' % (name, control)

print('reference functional surface contract: OK')
