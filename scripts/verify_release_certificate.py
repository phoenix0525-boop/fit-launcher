"""Verify a produced release; outputs public certificate digest only, never signing inputs."""
import glob
import os
from pathlib import Path
import re
import subprocess
import sys

EXPECTED = 'B693B2503C22ADE93323CDEA35483B5084784581BB730D393D332D6D008E4DD8'

def certificate_matches(output: str) -> bool:
    digests = re.findall(r'certificate SHA-256 digest:\s*([0-9a-fA-F:]+)', output)
    return len(digests) == 1 and digests[0].replace(':', '').upper() == EXPECTED

if __name__ == '__main__':
    apk = sys.argv[1]
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    candidates = glob.glob(str(Path(sdk or '') / 'build-tools' / '*' / 'apksigner'))
    if not candidates:
        raise SystemExit('apksigner unavailable')
    signer = max(candidates, key=lambda p: tuple(int(v) for v in re.findall(r'\d+', Path(p).parent.name)))
    result = subprocess.run([signer, 'verify', '--verbose', '--print-certs', apk], capture_output=True, text=True)
    if result.returncode or not certificate_matches(result.stdout):
        raise SystemExit('Release signature verification failed or certificate fingerprint did not match FIT')
    fingerprint = ':'.join(EXPECTED[i:i+2] for i in range(0, 64, 2))
    print('Verified FIT release certificate SHA-256: ' + fingerprint)
    Path(apk).with_suffix('.certificate.txt').write_text('SHA-256: ' + fingerprint + '\n')
