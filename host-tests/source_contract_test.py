from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
build = (ROOT/'app/build.gradle').read_text()
root_build = (ROOT/'build.gradle').read_text()
layout = (ROOT/'app/src/main/res/layout/activity_main.xml').read_text()
land_layout = (ROOT/'app/src/main/res/layout-land/activity_main.xml').read_text()
main = (ROOT/'app/src/main/java/com/jepongdevxyz/idebuild/MainActivity.java').read_text()
manifest = (ROOT/'app/src/main/AndroidManifest.xml').read_text()
script = ROOT/'runtime-builder/build-devxyz-terminal-runtime.sh'

# AIDE Test Edition intentionally uses only platform Android widgets/classes.
assert "dependencies {\n}" in build, "AIDE edition must not require external Maven UI/editor dependencies"
assert "sourceCompatibility JavaVersion.VERSION_1_7" in build
assert "compileSdkVersion 28" in build
assert "com.android.tools.build:gradle:3.2.1" in root_build
assert "<EditText" in layout and "io.github.rosemoe" not in layout
assert "EditText editor" in main and "io.github.rosemoe" not in main
assert "androidx." not in main
assert main.count('protected void onDestroy()') == 1
assert "applicationId 'com.jepongdevxyz.idebuild'" in build
assert ("targetSdk 28" in build or "targetSdkVersion 28" in build)
assert '.ApkFileProvider' in manifest and 'androidx.core.content.FileProvider' not in manifest
assert script.is_file(), "Devxyz runtime builder missing"
text = script.read_text()
assert "com.jepongdevxyz.idebuild" in text
assert "610af608b4a3b1127244e90edf8b7be2bf94fafa" in text
assert 'generate-bootstraps.sh' in text
assert 'stamp_bootstrap.py' in text
assert 'runtimeButton' in main
assert 'TerminalBootstrapInstaller.install' in main
assert '@+id/runtimeButton' in layout
assert '<EditText' in land_layout
assert '@+id/runtimeButton' in land_layout

# Project file open/save must resolve a ProjectPath against the trusted root each time.
assert 'WorkspacePathResolver' in main, "MainActivity must use root-contained workspace resolution"
assert 'ProjectPath currentPath' in main, "Editor must retain a project-relative path rather than a raw File"
assert 'new File(projectRoot, relative)' not in main, "Raw project-relative File construction bypasses containment checks"
assert 'currentFile' not in main, "Editor save state must not retain a raw File that can become a symlink escape"

print("SOURCE CONTRACT TESTS PASSED (AIDE TEST EDITION)")
