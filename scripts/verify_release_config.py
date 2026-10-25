#!/usr/bin/env python3
"""Fail fast when the checked-in Android release baseline drifts."""

from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def require(pattern: str, text: str, message: str, errors: list[str]) -> None:
    if re.search(pattern, text, flags=re.MULTILINE) is None:
        errors.append(message)


def version_tuple(value: str) -> tuple[int, ...]:
    return tuple(int(part) for part in value.split("."))


def main() -> int:
    errors: list[str] = []
    app_gradle = read("app/build.gradle.kts")
    root_gradle = read("build.gradle.kts")
    wrapper = read("gradle/wrapper/gradle-wrapper.properties")
    manifest = read("app/src/main/AndroidManifest.xml")
    workflow = read(".github/workflows/build-apk.yml")
    workflow_backup = read("build-apk.github-workflow.yml")
    gitignore = read(".gitignore")
    gradle_properties = read("gradle.properties")
    windows_build = read("scripts/build_local_windows.ps1")

    expected = {
        r"\bcompileSdk\s*=\s*36\b": "compileSdk must be 36",
        r"\bminSdk\s*=\s*31\b": "minSdk must be 31 (Android 12)",
        r"\btargetSdk\s*=\s*36\b": "targetSdk must be 36",
        r"\bversionCode\s*=\s*4\b": "versionCode must be 4",
        r'\bversionName\s*=\s*"0\.2\.1"': "versionName must be 0.2.1",
    }
    for pattern, message in expected.items():
        require(pattern, app_gradle, message, errors)

    agp = re.search(r'com\.android\.application"\) version "([0-9.]+)"', root_gradle)
    if agp is None or version_tuple(agp.group(1)) < version_tuple("8.9.1"):
        errors.append("Android Gradle Plugin must be at least 8.9.1 for API 36")

    gradle = re.search(r"gradle-([0-9.]+)-bin\.zip", wrapper)
    if gradle is None or version_tuple(gradle.group(1)) < version_tuple("8.11.1"):
        errors.append("Gradle wrapper must be at least 8.11.1")

    for forbidden in (
        "android.permission.QUERY_ALL_PACKAGES",
        "android.permission.REQUEST_INSTALL_PACKAGES",
    ):
        if forbidden in manifest:
            errors.append(f"Manifest must not request {forbidden}")

    if 'signingConfigs.getByName("debug")' in app_gradle:
        errors.append("Release build must never fall back to the debug signing key")
    require(r'FIT_KEYSTORE_PATH', app_gradle, "Release signing must use FIT_KEYSTORE_PATH", errors)

    for task in ("testDebugUnitTest", "assembleDebug", "assembleRelease", "bundleRelease"):
        if task not in workflow:
            errors.append(f"GitHub Actions must run {task}")
    if "SDKMANAGER=" not in workflow:
        errors.append("GitHub Actions must resolve sdkmanager from ANDROID_HOME explicitly")
    if workflow_backup != workflow:
        errors.append("Visible workflow backup must exactly match .github/workflows/build-apk.yml")

    require(
        r"^android\.overridePathCheck=true$",
        gradle_properties,
        "Gradle must allow the canonical non-ASCII Windows workspace path",
        errors,
    )
    require(
        r"\bsubst\b",
        windows_build,
        "Windows local build must map non-ASCII workspace paths",
        errors,
    )

    for ignored in ("*.p12", "*.jks", "*.keystore", "*.base64.txt", "secrets.properties"):
        if ignored not in gitignore:
            errors.append(f".gitignore must contain {ignored}")

    if errors:
        print("Release configuration check FAILED:")
        for error in errors:
            print(f"- {error}")
        return 1

    print("Release configuration check passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
