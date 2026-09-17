#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
workflow = (root / '.github/workflows/android-full-verification.yml').read_text()
script = root / '.github/scripts/verify-emulator.sh'

assert 'script: sh .github/scripts/verify-emulator.sh' in workflow, 'emulator runner must invoke one atomic shell script'
assert script.is_file(), 'atomic emulator verification script is missing'
text = script.read_text()
for required in [
    'set -eu',
    'DEVXYZ_APK=',
    'JAVA_APK=',
    'KOTLIN_APK=',
    'install_apk()',
    'adb install -r',
    'com.jepongdevxyz.idebuild',
    'com.jepongdevxyz.devxyzsamplejava',
    'com.jepongdevxyz.devxyzsamplekotlin',
]:
    assert required in text, 'missing emulator verification contract: ' + required

print('emulator atomic script contract: PASS')
