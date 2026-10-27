"""Check that all user-provided theme assets remain byte-for-byte intact."""
from pathlib import Path
import hashlib
import sys

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('.')
expected = {
    'pompompurin.png': '387008c779913a234345d907486636d5d854e7f20e04da824c319c6c4b1046d7',
    'pompompurin_cheer.png': '2c48bcbf93353a779eb13870508d32cf5725910eb14188c8724c1e3f643ded2f',
    'pompompurin_sleep.png': '307bd4ff75600676a149545ec78c23d4a4816583a6cace597528db1c80465c48',
    'pompompurin_thanks.png': '9fecb58828245457f1240f6e719dd35632942e58e8aecb793db6e6cf72ac7a60',
    'spiderman_crouch.png': '46b26d6bb48dab971a5f93cbf7d7be6e43b0a88ee2c5b091033f48d70e0e6601',
    'spiderman_swing.png': '738bbf92b84adb1843d834b1c7569127527871e2a54ebf406fbb550e5975f9e3',
    'spiderman_perch.png': 'f68de9ef35cd711febb189b0dc51c0524fa50d43abe72c3577537e9d47157fe8',
    'spiderman_reach.png': '0e8a79636fd83b21ee0c02a81f604c97c81fdc8cf0141cc3a886587917d3a74d',
}
for name, sha in expected.items():
    path = root / 'app/src/main/res/drawable-nodpi' / name
    assert hashlib.sha256(path.read_bytes()).hexdigest() == sha, f'Theme image damaged or changed: {name}'
print('All Pompompurin and Spider-Man assets match the user-provided originals.')
