from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PORTRAIT = (ROOT / "app/src/main/res/layout/activity_main.xml").read_text()
LAND = (ROOT / "app/src/main/res/layout-land/activity_main.xml").read_text()
COLORS = (ROOT / "app/src/main/res/values/colors.xml").read_text()
MAIN = (ROOT / "app/src/main/java/com/jepongdevxyz/idebuild/MainActivity.java").read_text()

# The reference redesign must be structural, not just a recolor.
for view_id in (
    "nav_files", "nav_search", "nav_git", "nav_build_tools", "nav_more",
    "workspace_files", "workspace_editor", "workspace_build_tools", "workspace_more",
):
    assert ("@+id/" + view_id) in PORTRAIT or ("@id/" + view_id) in PORTRAIT, view_id

for label in ("Files", "Search", "Git", "Build", "More"):
    assert label in PORTRAIT

assert "#050A12" in COLORS or "#060B14" in COLORS
assert "#00B8FF" in COLORS or "#00C8FF" in COLORS or "#149CFF" in COLORS

# Landscape remains deliberately dark and retains the real explorer/editor split.
assert "project_pane" in LAND
assert "editor_pane" in LAND
assert "@color/devxyz_bg" in LAND or "@color/devxyz_surface" in LAND

# Import must remain API-safe and must not finish the Activity on picker/import errors.
assert "Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP" in MAIN
assert "ProjectImportController" in MAIN
assert "finish();" not in MAIN[MAIN.find("onActivityResult"):MAIN.find("onDestroy")]

print("reference redesign contract: OK")
