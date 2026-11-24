<div align="center">
  <img src="docs/slate.svg" width="104" height="104" alt="Slate app icon">

  <h1>Slate</h1>

  <p><strong>Storage management, cleanup and device diagnostics for Android.</strong></p>

  <p>
    <a href="https://github.com/stevenmx2/Slate/releases"><img alt="Download APK" src="https://img.shields.io/badge/Download-APK-A7E8CF?style=flat-square&amp;logo=android&amp;logoColor=15191D"></a>
    <a href="LICENSE"><img alt="GPL-3.0-only" src="https://img.shields.io/badge/License-GPL--3.0--only-586069?style=flat-square"></a>
    <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-586069?style=flat-square">
    <a href="https://github.com/stevenmx2/Slate/actions/workflows/android.yml"><img alt="Build status" src="https://github.com/stevenmx2/Slate/actions/workflows/android.yml/badge.svg?branch=main"></a>
  </p>

  <p><a href="#features">Features</a> · <a href="#installation">Installation</a> · <a href="#privacy-and-permissions">Privacy</a> · <a href="README.pt-BR.md">Português (Brasil)</a></p>
</div>

Slate brings storage analysis, app management and device information together in a single Material 3 interface. Find what's taking up space, review unnecessary files and inspect your hardware without switching between multiple utilities.

## Features

| Storage analyzer | Cleanup |
| :--- | :--- |
| Browse internal and removable storage | Find duplicate files by content |
| List and grid views, search and sorting | Review temporary files and empty folders |
| File categories and largest-file lists | Find possible remnants of uninstalled apps |
| Persistent indexes and scan history | Review and selectively clear app caches with Shizuku |
| Compare folder growth between sessions | Scheduled reviews and custom exclusions |

| App management | Device information |
| :--- | :--- |
| Installed app sizes, data and cache | CPU frequencies, GPU capabilities and RAM |
| User/system app filters and cache sorting | Battery temperature, charging power and capacity |
| App details and accessible storage folders | Android, display, network and thermal information |
| Launch apps and open Android app settings | Live sensors, display and touchscreen tests |
| Uninstall user apps through Android | Manufacturer identification |

The home dashboard highlights storage usage, device status, app cache and relevant recommendations. Slate supports light, dark and AMOLED appearances, dynamic colors and English/Brazilian Portuguese.

## Additional tools

Slate also includes a local file manager, offline photo/video gallery, and live hardware charts with an optional floating monitor. Game FPS is planned, not yet supported.

## Installation

**[Get Slate.apk from GitHub Releases](https://github.com/stevenmx2/Slate/releases)**

1. Open the releases page and select the newest development build.
2. Download `Slate.apk` from its assets and open it on your Android device.
3. Allow installation from the source you're using if Android asks.

Slate requires **Android 8.0 (API 26) or newer**. Root and Shizuku are optional; most features work with ordinary Android permissions.

> Slate is in active development. Releases are signed development builds, and some features may behave differently depending on the device or Android version.

The built-in updater checks GitHub Releases and verifies the APK's package, version and signing certificate before opening Android's installer.

## Privacy and permissions

- Storage scans, file indexes, app statistics and device readings are processed locally.
- Storage access and usage access are requested for their respective features.
- Internet access is used for update checks and downloads.
- Cleanup candidates are reviewed before deletion. Scheduled scans do not automatically delete personal files.
- Shizuku/root features require explicit authorization. Protected app data and system files remain subject to Android and backend restrictions.

Storage measurements may be incomplete on restricted directories. App statistics and shared-file totals can overlap; they should not be added together as a complete filesystem accounting. Hardware readings are shown only when the device exposes them.

## Development

Built with Kotlin, Jetpack Compose and Material 3.

<details>
<summary>Build from source</summary>

Requirements: JDK 17, Android SDK 35 and Gradle 8.9.

```sh
gradle :app:testReleaseUnitTest :app:assembleRelease
```

APK output: `app/build/outputs/apk/release/`.

To sign a local build, set `SLATE_KEYSTORE_PATH` and `SLATE_KEYSTORE_PASSWORD`. Without them, the release build is unsigned.

For repository maintainers, `bash scripts/setup-signing.sh` configures the persistent signing identity and GitHub Actions secrets. Keep the existing signing key backed up and private; rotating it prevents normal in-place updates. CI intentionally fails if signing secrets are missing.

GitHub Actions runs release unit tests, verifies the signed APK and publishes development prereleases.

</details>

Report reproducible bugs or suggest improvements through [GitHub Issues](https://github.com/stevenmx2/Slate/issues). See [release changes](CHANGELOG.md) and the [development tracker](ROADMAP.md) for implementation and validation status.

## License and acknowledgements

Slate is free and open-source software licensed under [GPL-3.0-only](LICENSE).

[SD Maid SE](https://github.com/d4rken-org/sdmaid-se) is a feature and compatibility reference. Slate uses the [Shizuku API](https://github.com/RikkaApps/Shizuku-API), AndroidX and other open-source libraries. Manufacturer marks are used for device identification. See [CREDITS.md](CREDITS.md) for attribution and licensing details.
