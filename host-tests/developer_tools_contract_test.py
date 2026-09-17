from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
portrait = (ROOT/'app/src/main/res/layout/activity_main.xml').read_text()
landscape = (ROOT/'app/src/main/res/layout-land/activity_main.xml').read_text()
manifest = (ROOT/'app/src/main/AndroidManifest.xml').read_text()
button_path = ROOT/'app/src/main/java/com/jepongdevxyz/idebuild/DeveloperToolsButton.java'
activity_path = ROOT/'app/src/main/java/com/jepongdevxyz/idebuild/DeveloperToolsActivity.java'

assert '@+id/developerToolsButton' in portrait and '@+id/developerToolsButton' in landscape
assert button_path.is_file(), 'Developer Tools launcher is missing'
assert activity_path.is_file(), 'Developer Tools activity is missing'
assert '.DeveloperToolsActivity' in manifest

button = button_path.read_text()
activity = activity_path.read_text()
assert 'DeveloperToolsActivity' in button
assert 'R.id.projectPath' in button
assert 'ResourceIndexService.scan' in activity, 'Resource Manager must read real project resources'
assert 'ClipboardManager' in activity, 'Resource Manager/Color Picker must support copying references/snippets'
assert 'SQLiteDatabase.OPEN_READONLY' in activity, 'Database viewer must default to read-only SQLite'
assert 'SqlQueryGuard.isReadOnly' in activity, 'Database query console must enforce read-only queries by default'
assert 'Intent.ACTION_OPEN_DOCUMENT' in activity, 'Database viewer must use SAF for external database selection'
assert 'WebView' in activity and 'setAllowUniversalAccessFromFileURLs(false)' in activity, 'HTML preview must restrict local file privileges'
assert 'shouldOverrideUrlLoading' in activity, 'HTML preview must block unexpected remote navigation'
assert 'Basic XML layout preview' in activity, 'Layout preview limitations must be explicit'
assert 'Color Picker' in activity and '#AARRGGBB' in activity, 'Color picker must expose Android-compatible formats'

print('DEVELOPER TOOLS CONTRACT TESTS PASSED')
