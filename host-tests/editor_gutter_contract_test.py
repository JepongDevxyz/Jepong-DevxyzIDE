from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
editor_path = ROOT/'app/src/main/java/com/jepongdevxyz/idebuild/SyntaxEditText.java'
text = editor_path.read_text()

assert 'onDraw(Canvas' in text, 'SyntaxEditText must render a visible editor gutter'
assert 'drawLineNumbers' in text, 'Editor gutter must render line numbers'
assert 'drawCurrentLineHighlight' in text, 'Editor must render the current-line highlight'
assert 'getLineForVertical' in text, 'Line numbers must be bounded to visible layout lines'
assert 'getLineStart' in text, 'Line-number rendering must respect wrapped visual lines'
assert 'MAX_GUTTER_SCAN_CHARS' in text, 'Line number derivation must have an explicit large-file bound'

print('EDITOR GUTTER CONTRACT TESTS PASSED')
