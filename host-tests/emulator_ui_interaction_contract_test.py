from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
script = (ROOT / '.github/scripts/verify-emulator.sh').read_text()

for token in (
    'uiautomator dump',
    'DevxyzIDE',
    'Files',
    'Search',
    'Git',
    'Build',
    'More',
    'PROJECT EXPLORER',
    'SOURCE CONTROL',
    'BUILD & RUN',
    'SETTINGS & RUNTIME',
    'screencap -p',
):
    assert token in script, 'emulator verification missing real UI check: ' + token

assert 'input tap' in script, 'emulator verification must interact with navigation, not launch only'
print('emulator UI interaction contract: OK')
