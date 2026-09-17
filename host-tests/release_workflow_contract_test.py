from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
workflow = ROOT / '.github/workflows/official-release.yml'
assert workflow.exists(), 'official release workflow is missing'
text = workflow.read_text()
for secret in ('APK_KEYSTORE_BASE64', 'RELEASE_STORE_PASSWORD', 'RELEASE_KEY_ALIAS', 'RELEASE_KEY_PASSWORD'):
    assert secret in text, secret
assert 'assembleRelease' in text
assert 'apksigner verify' in text
assert 'upload-artifact' in text
assert 'jepong-release.jks' in text
assert 'rm -f' in text
print('official release workflow contract: OK')
