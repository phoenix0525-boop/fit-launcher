"""Static boundary check: test keys never enter production update trust."""
from pathlib import Path
s=Path('app/build.gradle.kts').read_text()
w=Path('.github/workflows/build-apk.yml').read_text()
for key in ('FIT_TEST_KEYSTORE_PATH','FIT_TEST_KEYSTORE_PASSWORD','FIT_TEST_KEY_ALIAS'):
    assert key in s
for key in ('FIT_TEST_KEYSTORE_BASE64','FIT_TEST_KEYSTORE_PASSWORD','FIT_TEST_KEY_ALIAS'):
    assert 'secrets.'+key in w
assert 'verify_test_certificate.py' in w
assert '未使用固定測試金鑰，無法覆蓋安裝' in w
release=s.split('release {',1)[1].split('compileOptions',1)[0]
assert 'fitTest' not in release
policy=Path('app/src/main/java/tw/fitcar/launcher/update/UpdatePolicy.kt').read_text()
assert '52EE06F203BEA1460317B66170B99D07214B366E9A3A1E6F30D873E42C3835C6' in policy
assert 'TEST' not in policy
assert not list(Path('.').rglob('*.p12')) and not list(Path('.').rglob('*.jks'))
print('Test signing boundaries passed')
