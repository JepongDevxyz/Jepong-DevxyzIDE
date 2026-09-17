from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PORTRAIT = (ROOT / "app/src/main/res/layout/activity_main.xml").read_text()
LAND = (ROOT / "app/src/main/res/layout-land/activity_main.xml").read_text()
COLORS = (ROOT / "app/src/main/res/values/colors.xml").read_text()
MAIN = (ROOT / "app/src/main/java/com/jepongdevxyz/idebuild/MainActivity.java").read_text()
SPLASH = (ROOT / "app/src/main/res/layout/activity_splash.xml").read_text()

for view_id in (
    "nav_files", "nav_search", "nav_git", "nav_build_tools", "nav_more",
    "workspace_files", "workspace_editor", "workspace_build_tools", "workspace_more",
):
    assert ("@+id/" + view_id) in PORTRAIT or ("@id/" + view_id) in PORTRAIT, view_id

for label in ("Files", "Search", "Git", "Build", "More"):
    assert label in PORTRAIT

# Final shell is dark navy/cyan, not a light recolor.
assert "#050A12" in COLORS or "#060B14" in COLORS
assert "#00B8FF" in COLORS or "#00C8FF" in COLORS or "#149CFF" in COLORS
assert "#F5F8FC" not in COLORS
assert "#FFFFFF</color>" not in COLORS

assert "projectPane" in LAND
assert "editorPane" in LAND
assert "workspace_editor" in LAND
assert "@color/devxyz_bg" in LAND or "@color/devxyz_surface" in LAND

# Bottom navigation must be wired to real existing destinations/actions.
for nav_id in ("nav_files", "nav_search", "nav_git", "nav_build_tools", "nav_more"):
    assert ("R.id." + nav_id) in MAIN, nav_id
assert "setupWorkspaceNavigation" in MAIN
assert "showWorkspace" in MAIN
assert "gitButton.performClick()" in MAIN
assert "searchButton.performClick()" in MAIN

# Branding stays aligned with the supplied reference visual language.
assert "DevxyzIDE" in SPLASH
assert "CODE  •  BUILD  •  CREATE" in SPLASH
assert "Jepong Devxyz" in SPLASH

assert "Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP" in MAIN
assert "ProjectImportController" in MAIN
assert "finish();" not in MAIN[MAIN.find("onActivityResult"):MAIN.find("onDestroy")]

print("reference redesign contract: OK")
