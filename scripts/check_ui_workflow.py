"""Ensure the active UX branch actually runs the required car-screen regression."""
from pathlib import Path

workflow = Path('.github/workflows/ui-layout.yml').read_text()
tests = Path('app/src/androidTest/java/tw/fitcar/launcher/ui/LargeFontLayoutTest.kt').read_text()
assert 'claude/ux-v047' in workflow, 'UX branch is missing from UI workflow push triggers'
assert 'claude/review-fixes-v0416' in workflow, '0.4.16 review branch is missing from UI workflow push triggers'
assert 'codex/split-guide-home-v0417' in workflow, 'Split guide branch missing UI trigger'
for required in ('api-level: 33', 'wm size 2000x1200', 'wm density 240', 'font_scale 1.25',
                 'connectedDebugAndroidTest', 'ui-screenshots/'):
    assert required in workflow, f'UI workflow missing required configuration: {required}'
assert 'fun drivingModeAt125Percent()' in tests
assert 'screenshot("home-driving-125")' in tests
print('UI workflow: UX branch, Android 13 car screen and driving-font regression configured.')
