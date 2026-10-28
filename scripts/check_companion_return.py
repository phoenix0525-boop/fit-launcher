"""The in-app return must not resolve through an OEM-controlled HOME handler."""
from pathlib import Path
s=Path('app/src/main/java/tw/fitcar/launcher/CompanionActivity.kt').read_text()
body=s.split('private fun goHome()',1)[1].split('@androidx.compose.runtime.Composable',1)[0]
assert 'CATEGORY_HOME' not in body, 'Companion return still resolves through the system HOME handler'
assert 'DesktopReturn.intent(this)' in body, 'Companion must explicitly target our MainActivity'
print('Companion return explicitly targets vehicle desktop; hardware Home remains system-owned.')
