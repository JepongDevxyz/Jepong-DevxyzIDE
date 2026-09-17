from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

settings_model = (ROOT / "app/src/main/java/com/jepongdevxyz/idebuild/core/settings/EditorSettings.java").read_text()
settings_button = (ROOT / "app/src/main/java/com/jepongdevxyz/idebuild/EditorSettingsButton.java").read_text()
main_activity = (ROOT / "app/src/main/java/com/jepongdevxyz/idebuild/MainActivity.java").read_text()
run_script = (ROOT / "host-tests/run.sh").read_text()

required_model_tokens = [
    "DEFAULT_TAB_WIDTH",
    "DEFAULT_AUTOSAVE",
    "MIN_TAB_WIDTH",
    "MAX_TAB_WIDTH",
    "getTabWidth()",
    "isAutosaveEnabled()",
    "withTabWidth",
    "withAutosaveEnabled",
    "clampTabWidth",
]

for token in required_model_tokens:
    assert token in settings_model, f"EditorSettings must expose persistent {token} support"

required_button_tokens = [
    "KEY_TAB_WIDTH",
    "KEY_AUTOSAVE",
    "getTabWidth()",
    "isAutosaveEnabled()",
    "Tab width",
    "Autosave",
]

for token in required_button_tokens:
    assert token in settings_button, f"Editor settings dialog must persist/expose {token}"

required_main_tokens = [
    "isAutosaveEnabled()",
    "autosaveDocumentThenSwitch",
    "EditorSettingsButton.loadEditorSettings",
    "Autosaved",
]

for token in required_main_tokens:
    assert token in main_activity, f"MainActivity must wire autosave behavior via {token}"

assert "editor_settings_contract_test.py" in run_script, "Host run script must include editor settings contract"

print("EDITOR SETTINGS CONTRACT TESTS PASSED")
