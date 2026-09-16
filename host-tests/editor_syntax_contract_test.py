from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
main = (ROOT/'app/src/main/java/com/jepongdevxyz/idebuild/MainActivity.java').read_text()
styler_path = ROOT/'app/src/main/java/com/jepongdevxyz/idebuild/EditorSyntaxStyler.java'

assert styler_path.is_file(), 'EditorSyntaxStyler must exist outside host-only core sources'
styler = styler_path.read_text()
assert 'SyntaxLanguageService.scan' in styler, 'Real editor highlighting must use the tested lexer'
assert 'MAX_SYNTAX_CHARS' in styler, 'Editor highlighting must have a bounded character window'
assert 'SyntaxColorSpan.class' in styler, 'Styler must remove only its own syntax spans'
assert 'editable.subSequence' in styler, 'Styler must not copy the entire large editor document for a local refresh'
assert 'EditorSyntaxStyler.apply' in main, 'MainActivity must apply syntax colors to the actual editor'
assert 'postDelayed' in main, 'Keystroke syntax refresh must be debounced instead of rescanning synchronously on every edit'
assert 'removeCallbacks' in main, 'Pending syntax refreshes must be coalesced'

print('EDITOR SYNTAX CONTRACT TESTS PASSED')
