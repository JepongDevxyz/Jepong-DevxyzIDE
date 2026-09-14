from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
root = (ROOT/'build.gradle').read_text()
app = (ROOT/'app/build.gradle').read_text()
props = (ROOT/'gradle.properties').read_text()
wrapper = (ROOT/'gradle/wrapper/gradle-wrapper.properties').read_text()
manifest = (ROOT/'app/src/main/AndroidManifest.xml').read_text()
strings = (ROOT/'app/src/main/res/values/strings.xml').read_text()

assert "classpath 'com.android.tools.build:gradle:3.2.1'" in root
assert "apply plugin: 'com.android.application'" in app
assert 'compileSdkVersion 28' in app
assert "applicationId 'com.jepongdevxyz.idebuild'" in app
assert 'minSdkVersion 21' in app
assert 'targetSdkVersion 28' in app
assert 'JavaVersion.VERSION_1_7' in app
assert 'gradle-4.6-all.zip' in wrapper
assert 'io.github.rosemoe:' not in app
assert 'android:label="DevxyzIDE"' in manifest
assert 'DevxyzIDE' in strings
assert 'namespace ' not in app
assert 'plugins {' not in root
print('AIDE COMPATIBILITY CONTRACT TESTS PASSED')
