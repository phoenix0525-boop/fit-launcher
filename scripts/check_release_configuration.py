"""Static guardrails for the production variant; run before every CI build."""
from pathlib import Path

build = Path('app/build.gradle.kts').read_text()
props = Path('gradle.properties').read_text()
manifest = Path('app/src/main/AndroidManifest.xml').read_text()
receivers = Path('app/src/main/java/tw/fitcar/launcher/system/Receivers.kt').read_text()
workflow = Path('.github/workflows/build-apk.yml').read_text()
release_manifest = Path('app/src/release/AndroidManifest.xml')

checks = {
    'release minification': 'isMinifyEnabled = true' in build,
    'release resource shrinking': 'isShrinkResources = true' in build,
    'optimized default ProGuard rules': 'proguard-android-optimize.txt' in build,
    'release arm64-only ABI': 'abiFilters += listOf("arm64-v8a")' in build,
    'debug probes resource excluded': 'excludes += "DebugProbesKt.bin"' in build,
    'R8 full mode': 'android.enableR8.fullMode=true' in props,
    'RTL supported': 'android:supportsRtl="true"' in manifest,
    'boot receiver not exported': 'android:name=".system.BootReceiver"\n            android:exported="false"' in manifest,
    'boot action allowlist': 'ALLOWED_ACTIONS' in receivers and 'if (intent.action !in ALLOWED_ACTIONS) return' in receivers,
    'no unneeded foreground-service permission': 'android.permission.FOREGROUND_SERVICE' not in manifest,
    'release overlay exists': release_manifest.exists(),
    'release artifact audit in CI': 'check_release_artifact.py' in workflow,
    'AAB built in CI': 'bundleRelease' in workflow,
}
failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise AssertionError('Release configuration checks failed: ' + ', '.join(failed))
print('Release configuration passed: arm64-only, R8/resource shrink, RTL, receiver and artifact guardrails present.')
