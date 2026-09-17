from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
portrait = (ROOT/'app/src/main/res/layout/activity_main.xml').read_text()
landscape = (ROOT/'app/src/main/res/layout-land/activity_main.xml').read_text()
button = ROOT/'app/src/main/java/com/jepongdevxyz/idebuild/BuildActionsButton.java'

assert button.is_file(), 'Advanced build actions must be implemented in a modular control'
text = button.read_text()
assert 'BuildRunner.runBuild' in text
assert '"clean"' in text
assert '"assembleDebug"' in text
assert '"assembleRelease"' in text
assert 'offline' in text.lower()
assert 'Rebuild Debug' in text
assert 'BuildRunner.BuildHandle' in text and '.cancel()' in text
assert 'saveAllButton' in text, 'Artifact builds must not silently ignore dirty editor tabs'
assert '@+id/buildActionsButton' in portrait
assert '@+id/buildActionsButton' in landscape

print('BUILD ACTIONS CONTRACT TESTS PASSED')
