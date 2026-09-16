from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
main = (ROOT / "app/src/main/java/com/jepongdevxyz/idebuild/MainActivity.java").read_text()
portrait = (ROOT / "app/src/main/res/layout/activity_main.xml").read_text()
landscape = (ROOT / "app/src/main/res/layout-land/activity_main.xml").read_text()
strings = (ROOT / "app/src/main/res/values/strings.xml").read_text()
run_script = (ROOT / "host-tests/run.sh").read_text()

assert "recentProjectsButton" in portrait, "Portrait UI must expose Recent Projects"
assert "recentProjectsButton" in landscape, "Landscape UI must expose Recent Projects"
assert "recent_projects" in strings, "Recent Projects label must be a string resource"

required_main_tokens = [
    "KEY_RECENT_PROJECTS",
    "MAX_RECENT_PROJECTS",
    "recordRecentProject",
    "loadRecentProjects",
    "showRecentProjects",
    "canonicalFile",
    "isDirectory()",
    "loadProject(recent)",
]

for token in required_main_tokens:
    assert token in main, f"MainActivity must implement recent project behavior via {token}"

assert "recent_projects_contract_test.py" in run_script, "Host run script must include recent projects contract"

print("RECENT PROJECTS CONTRACT TESTS PASSED")
