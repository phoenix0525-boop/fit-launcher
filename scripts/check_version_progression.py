"""The candidate must not downgrade A's code 7 or reuse baseline B's 0.4.0 name."""
from pathlib import Path
import re
s = Path('app/build.gradle.kts').read_text()
code = int(re.search(r'versionCode\s*=\s*(\d+)', s)[1])
name = re.search(r'versionName\s*=\s*"([^"]+)"', s)[1]
assert code > 7, 'Candidate versionCode must be higher than A line code 7'
assert name != '0.4.0', 'AGAMA candidate must have a distinct versionName'
print(f'Version progression passed: {name} ({code}); signing compatibility still requires the same certificate.')
