from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
script = (ROOT / '.github/scripts/verify-emulator.sh').read_text()

assert 'while [ "$i" -lt 10 ]' in script
assert 'dump_ui "devxyz-${label}.xml" || true' in script
assert 'grep -F "$expected" "devxyz-${label}.xml"' in script
assert 'sleep 1' in script
print('emulator navigation retry contract: OK')
