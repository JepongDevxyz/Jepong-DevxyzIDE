from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
splash = (ROOT / 'app/src/main/res/layout/activity_splash.xml').read_text()
manifest = (ROOT / 'app/src/main/AndroidManifest.xml').read_text()

for text in ('DevxyzIDE', 'CODE  •  BUILD  •  CREATE', 'Initializing your creative environment', 'Powered by Jepong Devxyz'):
    assert text in splash, 'missing splash branding: ' + text
assert '@mipmap/ic_launcher' in splash
assert '@mipmap/ic_launcher' in manifest
print('branding splash contract: OK')
