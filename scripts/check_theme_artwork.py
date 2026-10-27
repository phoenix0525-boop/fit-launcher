"""Check that all four user-provided theme assets remain byte-for-byte intact."""
from pathlib import Path
import hashlib
import sys

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('.')
expected = {
    'pompompurin.png': '387008c779913a234345d907486636d5d854e7f20e04da824c319c6c4b1046d7',
    'pompompurin_cheer.png': '2c48bcbf93353a779eb13870508d32cf5725910eb14188c8724c1e3f643ded2f',
    'pompompurin_sleep.png': '307bd4ff75600676a149545ec78c23d4a4816583a6cace597528db1c80465c48',
    'pompompurin_thanks.png': '9fecb58828245457f1240f6e719dd35632942e58e8aecb793db6e6cf72ac7a60',
}
for name, sha in expected.items():
    path = root / 'app/src/main/res/drawable-nodpi' / name
    assert hashlib.sha256(path.read_bytes()).hexdigest() == sha, f'Theme image damaged or changed: {name}'
print('All four Pompompurin source images match the user-provided originals.')
