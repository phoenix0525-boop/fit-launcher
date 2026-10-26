"""Reject a release that silently uses debug signing or lacks CI fingerprint validation."""
from pathlib import Path
import re
build = Path('app/build.gradle.kts').read_text()
release = build.split('release {', 1)[1].split('compileOptions', 1)[0]
assert 'getByName("debug")' not in release, 'Release must never fall back to the debug certificate'
workflow = Path('.github/workflows/build-apk.yml').read_text()
for name in ('FIT_KEYSTORE_BASE64', 'FIT_KEYSTORE_PASSWORD', 'FIT_KEY_ALIAS'):
    assert 'secrets.' + name in workflow, 'Missing conditional signing secret: ' + name
assert 'verify_release_certificate.py' in workflow, 'CI must verify the exact release fingerprint'
print('Signing policy passed: no debug fallback; conditional secrets and certificate verification present.')
