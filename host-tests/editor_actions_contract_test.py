from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
button = ROOT/'app/src/main/java/com/jepongdevxyz/idebuild/EditorActionsButton.java'
editor = ROOT/'app/src/main/java/com/jepongdevxyz/idebuild/SyntaxEditText.java'
main = ROOT/'app/src/main/java/com/jepongdevxyz/idebuild/MainActivity.java'
portrait = (ROOT/'app/src/main/res/layout/activity_main.xml').read_text()
landscape = (ROOT/'app/src/main/res/layout-land/activity_main.xml').read_text()

assert button.is_file(), 'EditorActionsButton must expose mobile undo/redo/go-to-line actions'
text = button.read_text()
editor_text = editor.read_text()
main_text = main.read_text()
assert 'undoEdit()' in text and 'redoEdit()' in text
assert 'goToLine' in text
assert 'Close Tab' in text
assert 'Close Others' in text
assert 'Close All' in text
assert 'requestCloseActiveTab' in main_text
assert 'requestCloseOtherTabs' in main_text
assert 'requestCloseAllTabs' in main_text
assert 'closeOthers' in main_text
assert 'closeAll' in main_text
assert 'EditorUndoHistory' in editor_text
assert '@+id/editorActionsButton' in portrait
assert '@+id/editorActionsButton' in landscape

print('EDITOR ACTIONS CONTRACT TESTS PASSED')
