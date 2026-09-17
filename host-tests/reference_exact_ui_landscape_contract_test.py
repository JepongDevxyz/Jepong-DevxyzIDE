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

# Authoritative controls must be visible in their actual surfaces, not buried in the hidden compatibility holder.
hidden_at = layout.find('android:visibility="gone"')
for control in ('buildButton', 'settingsButton', 'developerToolsButton', 'projectSettingsButton'):
    pos = layout.find('android:id="@+id/%s"' % control)
    require(pos >= 0 and (hidden_at < 0 or pos < hidden_at), "%s must be visible before hidden compatibility controls" % control)

print("REFERENCE EXACT LANDSCAPE UI CONTRACT PASSED")