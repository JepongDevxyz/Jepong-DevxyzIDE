from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
terminal = (ROOT/'app/src/main/java/com/jepongdevxyz/idebuild/TerminalButton.java').read_text()

assert 'BoundedLogBuffer' in terminal, 'Terminal output must use the tested bounded log buffer'
assert 'new BoundedLogBuffer(MAX_OUTPUT_CHARS' in terminal, 'Each terminal dialog must own a bounded output buffer'
assert 'log.appendLine' in terminal and 'log.snapshot()' in terminal, 'Terminal UI must render from bounded log state'
assert 'log.clear()' in terminal, 'Clear must reset bounded terminal state'
assert 'output.length() > MAX_OUTPUT_CHARS' not in terminal, 'Terminal must not use post-hoc TextView truncation'

print('TERMINAL LOG CONTRACT TESTS PASSED')
