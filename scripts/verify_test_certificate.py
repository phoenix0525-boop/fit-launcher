"""Verify the public test fingerprint after apksigner verifies the complete APK."""
import re, subprocess, sys
from pathlib import Path
expected=Path('docs/test-certificate.sha256').read_text().strip().replace(':','').upper()
assert re.fullmatch('[0-9A-F]{64}',expected), 'Set and review the public test fingerprint first'
assert expected!='274D805BDD01F01D0F8A711D9EE77B593E161BCAEA1897AC7A487327AA389AFF', 'Test and production certificates must differ'
tool=sorted((Path(__import__('os').environ['ANDROID_HOME'])/'build-tools').glob('*/apksigner'))[-1]
r=subprocess.run([str(tool),'verify','--verbose','--print-certs',sys.argv[1]],capture_output=True,text=True,check=True)
actual=re.findall(r'certificate SHA-256 digest:\s*([a-fA-F0-9:]+)',r.stdout)
assert len(actual)==1 and actual[0].replace(':','').upper()==expected, 'Test certificate mismatch'
print('Verified test certificate SHA-256: '+expected)
