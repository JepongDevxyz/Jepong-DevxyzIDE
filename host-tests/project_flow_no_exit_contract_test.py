from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
main = (ROOT / 'app/src/main/java/com/jepongdevxyz/idebuild/MainActivity.java').read_text()
controller = (ROOT / 'app/src/main/java/com/jepongdevxyz/idebuild/ProjectImportController.java').read_text()

# Both flows must explicitly route recoverable failures back to the live MainActivity UI.
assert 'onCreateProjectError' in main, 'create-project failure callback is missing'
assert 'onImportError' in main, 'import failure callback is missing'
assert 'onImportCancelled' in main, 'import cancellation callback is missing'

# Error/cancel handling must never terminate the activity/process.
for forbidden in ('finish();', 'finishAffinity();', 'System.exit(', 'android.os.Process.killProcess('):
    assert forbidden not in main, 'MainActivity contains exit path: ' + forbidden
    assert forbidden not in controller, 'ProjectImportController contains exit path: ' + forbidden

assert 'createProjectButton.setEnabled(true)' in main
assert 'projectImportController.cancel()' in main
print('project flow no-exit contract: OK')
