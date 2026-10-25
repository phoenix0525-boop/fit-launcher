# FIT Launcher engineering rules

## Stack and target

- Kotlin and Jetpack Compose Android launcher.
- Package: `tw.fitcar.launcher`.
- Minimum Android 12 (API 31); compile/target API 36.
- Java 17 and Gradle Wrapper only.

## Commands

- Configuration check: `python scripts/verify_release_config.py`
- Unit tests: `./gradlew testDebugUnitTest`
- Debug APK: `./gradlew assembleDebug`
- Release APK: `./gradlew assembleRelease`
- Release bundle: `./gradlew bundleRelease`

## Boundaries

- Never commit signing keys, Base64 key material, passwords, API keys, local properties, APKs or AABs.
- Never copy or modify third-party APK code, assets or certificates.
- Do not use root, FYT/MCU private APIs, firmware flashing, hidden Android APIs, `QUERY_ALL_PACKAGES`, or `REQUEST_INSTALL_PACKAGES`.
- All navigation, music and speed-camera apps are user-bound from installed launchable activities; do not guess package names.
- External traffic and speed-camera data is untrusted: validate schema, size, coordinate range, timeout and source.
- Sensitive permissions are optional and requested only when the corresponding feature is enabled.
- Do not claim vehicle compatibility until tested on the AKZ ZX09.

## Delivery discipline

- Write a failing test or configuration check before behavior changes.
- Keep each increment buildable and commit it separately after verification.
- Preserve factory-launcher return paths and existing reverse-camera safety assumptions.
