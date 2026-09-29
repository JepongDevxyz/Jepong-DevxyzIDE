from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1] / "app/src/main/res"
attr = "android:windowLightNavigationBar"

base = ET.parse(root / "values/styles.xml").getroot()
styles = root / "values-v27/styles.xml"
base_theme = next(s for s in base.findall("style") if s.attrib.get("name") == "Theme.DevxyzIDE")
assert not any(item.attrib.get("name") == attr for item in base_theme.findall("item")), (
    "windowLightNavigationBar is API 27+ and must not be placed in unqualified values/"
)
assert styles.is_file(), "API 27+ navigation-bar theme override is missing"
v27 = ET.parse(styles).getroot()
v27_theme = next(s for s in v27.findall("style") if s.attrib.get("name") == "Theme.DevxyzIDE")
assert any(item.attrib.get("name") == attr for item in v27_theme.findall("item")), (
    "the API 27+ theme must define the navigation-bar icon appearance"
)
print("Theme API qualifiers are compatible with minSdk 26")
