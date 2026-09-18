from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
main = (ROOT / 'app/src/main/java/com/jepongdevxyz/idebuild/MainActivity.java').read_text()
controller = (ROOT / 'app/src/main/java/com/jepongdevxyz/idebuild/ProjectImportController.java').read_text()

# Create Project must recover in-place: report the failure and re-enable its action.
assert 'CREATE PROJECT ERROR:' in main, 'create-project errors are not surfaced in the live UI'
assert 'createProjectButton.setEnabled(true)' in main, 'create-project action is not restored after failure'
assert 'finally { runOnUiThread' in main, 'create-project recovery must return to the UI thread'

# Import Project must recover in-place for failure and cancellation, clean busy state,
# and keep the selected project inactive when a partial import is canceled.
assert 'finishFailure(e)' in controller, 'import exceptions are not routed to recovery'
assert 'clearBusyState();' in controller, 'import busy state is not cleared after completion/failure'
assert 'IMPORT CANCELED' in controller, 'import cancellation is not surfaced'
assert 'IMPORT ERROR:' in controller, 'import failures are not surfaced'
assert 'The partial import was not activated as a project.' in controller
assert 'public void cancel()' in controller

# Import completion callbacks must be lifecycle-safe. A long ZIP can outlive an
# Activity recreation/teardown; touching a dead Activity is a real crash/exit path.
assert 'isActivityAlive()' in controller, 'import controller lacks lifecycle guard'
assert 'postToLiveActivity(' in controller, 'import callbacks must be posted only to a live Activity'
assert 'activity.runOnUiThread(new Runnable()' not in controller, 'raw Activity UI posting can target a destroyed Activity'

# MainActivity owns the shared single-thread executor used by imports. Lifecycle teardown
# must interrupt queued/running project work rather than finish/kill the Activity process.
assert 'io.shutdownNow()' in main, 'activity lifecycle must stop active background project work safely'

# Recoverable project operations must never terminate the activity/process.
for forbidden in ('finish();', 'finishAffinity();', 'System.exit(', 'android.os.Process.killProcess('):
    assert forbidden not in main, 'MainActivity contains exit path: ' + forbidden
    assert forbidden not in controller, 'ProjectImportController contains exit path: ' + forbidden

print('project flow no-exit contract: OK')
