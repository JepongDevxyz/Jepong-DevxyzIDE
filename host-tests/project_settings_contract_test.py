from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
portrait = (ROOT/'app/src/main/res/layout/activity_main.xml').read_text()
landscape = (ROOT/'app/src/main/res/layout-land/activity_main.xml').read_text()
button_path = ROOT/'app/src/main/java/com/jepongdevxyz/idebuild/ProjectSettingsButton.java'

assert button_path.is_file(), 'Project Settings must have a dedicated modular control'
text = button_path.read_text()
assert 'ProjectAnalyzer.analyze' in text, 'Project Settings must derive values from the real project analyzer'
assert 'ProjectRequirements' in text
assert 'getGradleVersion()' in text and 'getAgpVersion()' in text
assert 'getCompileSdk()' in text and 'getMinSdk()' in text and 'getTargetSdk()' in text
assert 'getJavaMajor()' in text
assert 'usesAndroidX()' in text and 'usesKotlin()' in text and 'usesCompose()' in text
assert 'isWrapperComplete()' in text
assert 'Detected from project files' in text
assert '@+id/projectSettingsButton' in portrait
assert '@+id/projectSettingsButton' in landscape

print('PROJECT SETTINGS CONTRACT TESTS PASSED')
