from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
main = (ROOT/'app/src/main/java/com/jepongdevxyz/idebuild/MainActivity.java').read_text()

assert 'Modern AndroidX Kotlin' in main, 'New Project wizard must expose the existing Kotlin template'
assert 'ProjectTemplateGenerator.Template.MODERN_ANDROIDX_KOTLIN' in main, 'Kotlin wizard row must map to the real Kotlin generator'
assert 'New AndroidX Kotlin project' in main, 'Project details dialog must identify Kotlin projects accurately'

assert 'No Activity' in main, 'New Project wizard must expose the no-activity template'
assert 'ProjectTemplateGenerator.Template.NO_ACTIVITY_JAVA' in main, 'No Activity row must map to the real generator'
assert 'WebView App' in main, 'New Project wizard must expose the WebView template'
assert 'ProjectTemplateGenerator.Template.WEBVIEW_JAVA' in main, 'WebView row must map to the real generator'
assert 'Library Module' in main, 'New Project wizard must expose the library template'
assert 'ProjectTemplateGenerator.Template.LIBRARY_JAVA' in main, 'Library row must map to the real generator'

print('PROJECT WIZARD CONTRACT TESTS PASSED')
