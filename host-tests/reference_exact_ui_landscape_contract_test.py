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

for legacy in ('android:text="Save"', 'android:text="Save All"', 'android:text="Complete"', 'android:text="Actions"', 'BUILD &amp; TOOLS', 'MORE / SETTINGS'):
    require(legacy not in layout, "Landscape still exposes legacy UI: %s" % legacy)

# Workspace surfaces intentionally start GONE and are shown by navigation. Only the final 1dp
# compatibility holder is permanently hidden, so authoritative controls must occur before it.
hidden_marker = '<LinearLayout android:layout_width="1dp" android:layout_height="1dp" android:visibility="gone">'
hidden_at = layout.rfind(hidden_marker)
require(hidden_at >= 0, "Landscape hidden compatibility holder missing")
for control in ('buildButton', 'settingsButton', 'developerToolsButton', 'projectSettingsButton'):
    token = 'android:id="@+id/%s"' % control
    pos = layout.find(token)
    require(pos >= 0 and pos < hidden_at, "%s must be a visible authoritative control" % control)
    require(layout.find(token, pos + 1) < 0, "%s must only exist once" % control)

print("REFERENCE EXACT LANDSCAPE UI CONTRACT PASSED")
