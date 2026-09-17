from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
styler_path = ROOT/'app/src/main/java/com/jepongdevxyz/idebuild/EditorSyntaxStyler.java'
editor_path = ROOT/'app/src/main/java/com/jepongdevxyz/idebuild/SyntaxEditText.java'
portrait_path = ROOT/'app/src/main/res/layout/activity_main.xml'
landscape_path = ROOT/'app/src/main/res/layout-land/activity_main.xml'

assert styler_path.is_file(), 'EditorSyntaxStyler must exist outside host-only core sources'
assert editor_path.is_file(), 'SyntaxEditText must own Android editor refresh scheduling'

styler = styler_path.read_text()
editor = editor_path.read_text()
portrait = portrait_path.read_text()
landscape = landscape_path.read_text()

assert 'SyntaxLanguageService.scan' in styler, 'Real editor highlighting must use the tested lexer'
assert 'MAX_SYNTAX_CHARS' in styler, 'Editor highlighting must have a bounded character window'
assert 'SyntaxColorSpan.class' in styler, 'Styler must remove only its own syntax spans'
assert 'editable.subSequence' in styler, 'Styler must not copy the entire large editor document for a local refresh'
assert 'EditorSyntaxStyler.apply' in editor, 'Actual editor widget must apply syntax colors'
assert 'postDelayed' in editor, 'Keystroke syntax refresh must be debounced instead of rescanning synchronously on every edit'
assert 'removeCallbacks' in editor, 'Pending syntax refreshes must be coalesced'
assert 'com.jepongdevxyz.idebuild.SyntaxEditText' in portrait, 'Portrait workspace must use the syntax-aware editor widget'
assert 'com.jepongdevxyz.idebuild.SyntaxEditText' in landscape, 'Landscape workspace must use the syntax-aware editor widget'

print('EDITOR SYNTAX CONTRACT TESTS PASSED')
