"""Run only AFTER verifying the signed APK against the pinned production certificate."""
from pathlib import Path
import hashlib
import json
import re
import shutil
import subprocess
import os
import glob

CERT = '52EE06F203BEA1460317B66170B99D07214B366E9A3A1E6F30D873E42C3835C6'
apk = Path('app/build/outputs/apk/release/app-release.apk')
# Verify the exact file again here, not a stale sidecar or filename assertion.
subprocess.run(['python', 'scripts/verify_release_certificate.py', str(apk)], check=True)
build = Path('app/build.gradle.kts').read_text()
version = re.search(r'versionName\s*=\s*"([\d.]+)"', build)[1]
code = int(re.search(r'versionCode\s*=\s*(\d+)', build)[1])
sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
aapts = glob.glob(str(Path(sdk or '') / 'build-tools' / '*' / 'aapt'))
if not aapts:
    raise SystemExit('aapt unavailable')
aapt = max(aapts, key=lambda p: tuple(int(x) for x in re.findall(r'\d+', Path(p).parent.name)))
badging = subprocess.check_output([aapt, 'dump', 'badging', str(apk)], text=True)
assert re.search(r"package: name='tw.fitcar.launcher' versionCode='" + str(code) + r"' versionName='" + re.escape(version) + "'", badging), 'APK identity differs from release configuration'
name = f'vehicle-launcher-v{version}.apk'
out = Path('update-distribution')
out.mkdir(exist_ok=True)
shutil.copy2(apk, out / name)
manifest = {
    'packageName': 'tw.fitcar.launcher', 'versionName': version, 'versionCode': code,
    'apkUrl': f'https://github.com/phoenix0525-boop/fit-launcher/releases/download/v{version}/{name}',
    'size': apk.stat().st_size, 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
    'certificateSha256': CERT, 'notes': Path('docs/release-0.4.23.md').read_text(),
}
(out / 'update.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2))
