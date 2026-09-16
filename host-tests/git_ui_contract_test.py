from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
button = (ROOT/'app/src/main/java/com/jepongdevxyz/idebuild/GitButton.java').read_text()

assert 'Clone Repository' in button, 'Git UI must expose repository cloning when no project is loaded'
assert 'GitService.cloneRepository' in button, 'Clone action must call the real Git service'
assert 'Unstage All' in button, 'Git UI must expose unstaging staged changes'
assert 'GitService.unstage' in button, 'Unstage action must call the real Git service'
assert 'Fetch' in button, 'Git UI must expose fetching remote refs without merging'
assert 'GitService.fetch' in button, 'Fetch action must call the real Git service'
assert 'getExternalFilesDir(null)' in button, 'Clone destination must use app-owned project storage'
assert 'getCanonicalFile()' in button and 'startsWith' in button, 'Clone destination must be containment-checked'
assert 'token' not in button.lower(), 'Git UI must not embed or log credential token fields'

print('GIT UI CONTRACT TESTS PASSED')
