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
    main_activity = read("app/src/main/java/tw/fitcar/launcher/MainActivity.kt")
    workflow = read(".github/workflows/build-apk.yml")
    workflow_backup = read("build-apk.github-workflow.yml")
    gitignore = read(".gitignore")
    gradle_properties = read("gradle.properties")
    windows_build = read("scripts/build_local_windows.ps1")

    expected = {
        r"\bcompileSdk\s*=\s*36\b": "compileSdk must be 36",
        r"\bminSdk\s*=\s*31\b": "minSdk must be 31 (Android 12)",
        r"\btargetSdk\s*=\s*36\b": "targetSdk must be 36",
        r"\bversionCode\s*=\s*6\b": "versionCode must be 6",
        r'\bversionName\s*=\s*"0\.3\.1"': "versionName must be 0.3.1",
        r"\bisMinifyEnabled\s*=\s*true\b": "Release must enable R8 minification (reference APK is 2.4 MB)",
        r"\bisShrinkResources\s*=\s*true\b": "Release must shrink unused resources",
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

    if "override fun dispatchKeyEvent" in main_activity:
        errors.append("MainActivity must use public key callbacks instead of restricted dispatchKeyEvent")
    require(
        r"override fun onKeyDown\(",
        main_activity,
        "MainActivity must preserve hardware-key diagnostics through onKeyDown",
        errors,
    )

    # Optional hardware must not filter installs on head units (same as reference APK).
    for feature in ("android.hardware.location.gps", "android.hardware.location", "android.hardware.touchscreen"):
        if re.search(r'<uses-feature\s+android:name="' + re.escape(feature) + r'"\s+android:required="false"', manifest) is None:
            errors.append(f"Manifest must declare {feature} as required=false")

    if "upload-artifact" not in workflow or "fit-launcher-debug" not in workflow:
        errors.append("GitHub Actions must always upload the debug APK, even without signing secrets")
    if "v0.2.1" in workflow:
        errors.append("Workflow must derive the version from Gradle instead of hard-coding it")

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
