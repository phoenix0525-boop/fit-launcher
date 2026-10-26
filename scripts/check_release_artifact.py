"""Inspect the actual release APK/AAB, rather than trusting build configuration alone."""
from __future__ import annotations
import argparse, json, os, shutil, subprocess, zipfile
from pathlib import Path

PROHIBITED_COMPONENTS = (
    'androidx.compose.ui.tooling.PreviewActivity',
    'androidx.activity.ComponentActivity',
)
PROHIBITED_FILES = ('DebugProbesKt.bin',)
EXPECTED_ABIS = {'arm64-v8a'}


def find_apkanalyzer() -> str:
    direct = shutil.which('apkanalyzer')
    if direct:
        return direct
    root = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or '')
    candidates = sorted(root.glob('cmdline-tools/*/bin/apkanalyzer'), reverse=True)
    if not candidates:
        raise RuntimeError('apkanalyzer not found in PATH or Android SDK')
    return str(candidates[0])


def zip_facts(path: Path, aab: bool = False) -> dict:
    with zipfile.ZipFile(path) as z:
        bad_crc = z.testzip()
        if bad_crc:
            raise AssertionError(f'{path}: corrupt ZIP member {bad_crc}')
        names = z.namelist()
        prohibited = [n for n in names if any(n.endswith(x) for x in PROHIBITED_FILES)]
        if prohibited:
            raise AssertionError(f'{path}: prohibited debug files: {prohibited}')
        prefix = 'base/lib/' if aab else 'lib/'
        abis = {n[len(prefix):].split('/', 1)[0] for n in names if n.startswith(prefix) and '/' in n[len(prefix):]}
        if abis != EXPECTED_ABIS:
            raise AssertionError(f'{path}: expected ABI {sorted(EXPECTED_ABIS)}, got {sorted(abis)}')
        dex_prefix = 'base/dex/' if aab else ''
        dex = [n for n in names if n.startswith(dex_prefix) and Path(n).name.startswith('classes') and n.endswith('.dex')]
        native_bytes = sum(i.file_size for i in z.infolist() if i.filename.startswith(prefix))
    return {'file': path.name, 'bytes': path.stat().st_size, 'abis': sorted(abis), 'dex_files': len(dex), 'native_uncompressed_bytes': native_bytes}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--apk', required=True, type=Path)
    ap.add_argument('--aab', type=Path)
    ap.add_argument('--report', type=Path)
    args = ap.parse_args()
    manifest = subprocess.check_output([find_apkanalyzer(), 'manifest', 'print', str(args.apk)], text=True)
    if 'android:debuggable="true"' in manifest:
        raise AssertionError('Release manifest is debuggable')
    if 'android:supportsRtl="true"' not in manifest:
        raise AssertionError('Release manifest must support RTL')
    for name in PROHIBITED_COMPONENTS:
        if name in manifest:
            raise AssertionError(f'Prohibited release activity: {name}')
    boot = manifest.split('tw.fitcar.launcher.system.BootReceiver', 1)
    if len(boot) != 2 or 'android:exported="false"' not in boot[1].split('</receiver>', 1)[0]:
        raise AssertionError('BootReceiver must be exported=false in release')
    facts = {'apk': zip_facts(args.apk)}
    if args.aab:
        facts['aab'] = zip_facts(args.aab, aab=True)
    text = json.dumps(facts, ensure_ascii=False, indent=2)
    print(text)
    if args.report:
        args.report.write_text(text + '\n')

if __name__ == '__main__':
    main()
