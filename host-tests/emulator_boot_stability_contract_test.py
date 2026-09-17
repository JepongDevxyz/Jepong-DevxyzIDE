from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
workflow = (ROOT / '.github/workflows/android-full-verification.yml').read_text()
assert '-no-snapshot' in workflow
assert '-wipe-data' in workflow
assert '-no-window' in workflow
print('emulator boot stability contract: OK')
