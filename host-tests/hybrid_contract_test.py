from pathlib import Path
root=Path(__file__).resolve().parents[1]
main=(root/'app/src/main/java/com/jepongdevxyz/idebuild/MainActivity.java').read_text()
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
buildrunner=(root/'app/src/main/java/com/jepongdevxyz/idebuild/BuildRunner.java').read_text()
planner=(root/'app/src/main/java/com/jepongdevxyz/idebuild/core/build/BuildPlanner.java').read_text()
assert '8L * 1024L * 1024L * 1024L' in main, 'large import limit is not 8 GiB'
assert 'ProjectRootDetector.findBestGradleRoot' in main, 'automatic nested project root detection missing'
assert 'buildButton.setEnabled(new File(root, "gradlew").isFile())' not in main, 'Build is still hidden when wrapper is absent'
assert 'android.permission.INTERNET' in manifest, 'online Maven requires INTERNET permission'
assert 'findGradleExecutable' in buildrunner, 'BuildRunner does not support internal Gradle fallback'
assert 'wrapperOrInternalGradleAvailable' in planner, 'BuildPlanner still hard-requires wrapper'
assert 'Maven dependency mode:' in buildrunner and 'ONLINE (download + persistent cache)' in buildrunner, 'online Maven status missing'
print('HYBRID CONTRACT TESTS PASSED')
