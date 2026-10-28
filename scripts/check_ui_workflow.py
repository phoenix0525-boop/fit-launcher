"""Ensure the active UX branch actually runs the required car-screen regression."""
from pathlib import Path

workflow = Path('.github/workflows/ui-layout.yml').read_text()
tests = Path('app/src/androidTest/java/tw/fitcar/launcher/ui/LargeFontLayoutTest.kt').read_text()
# 0.4.19 起以萬用規則涵蓋所有開發分支，不再逐一列名
assert "'codex/**'" in workflow and "'claude/**'" in workflow, 'UI workflow must run on all codex/** and claude/** branches'
assert 'cancel-in-progress: true' in workflow, 'UI workflow should cancel superseded runs'
for required in ('api-level: 33', 'wm size 2000x1200', 'wm density 240', 'font_scale 1.25',
                 'connectedDebugAndroidTest', 'ui-screenshots/'):
    assert required in workflow, f'UI workflow missing required configuration: {required}'
assert 'fun drivingModeAt125Percent()' in tests
assert 'screenshot("home-driving-125")' in tests
print('UI workflow: UX branch, Android 13 car screen and driving-font regression configured.')
