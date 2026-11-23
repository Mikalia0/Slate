# Slate

Android storage manager and cleaner. GPL-3.0-only.

## Current milestone
Read-only storage analyzer for mounted shared internal and removable storage. Browse directories using an interactive treemap or list, sort by size or name, filter the current listing, inspect file details, and show up to 100 largest files across a scanned subtree. See logical file sizes and counts; cancel or refresh scans. Android restrictions can hide app-private files; inaccessible paths are reported when detectable. No files are deleted.

## Device information
Independent, read-only device dashboard with hardware, Android system, battery, storage, network, sensor and camera metadata tabs. A redesigned overview shows RAM/storage gauges, per-core frequency relative to the reported maximum, GPU clock/utilization when exposed by the kernel, battery and charging metrics, thermal status, readable thermal sensors, display mode and network summary. Live measurements refresh only while the screen is visible. No overlay, background monitoring, internet permission or camera capture. Restricted metrics are marked unavailable; full Vulkan extension enumeration, CPU utilization and privileged metrics remain future work.

## Build
JDK 17, Android SDK 35 and Gradle 8.9:
```sh
gradle :app:assembleRelease :app:testReleaseUnitTest
```
The CI builds a signed, non-debuggable release APK, verifies its signature, runs release unit tests and publishes `Slate.apk` to the private repository's GitHub prereleases. The package ID is unchanged; CI version codes increase with the workflow run number.

One-time signing setup (run locally with JDK and GitHub CLI authenticated for the repository):

```sh
bash scripts/setup-signing.sh
```

The script creates a local PKCS12 signing identity (or reuses the existing backup), uploads the key and password directly to Actions secrets and dispatches the build. Keep the backup directory private and backed up offline. Do not rotate the key for routine updates. CI deliberately fails if signing secrets are missing. Old debug APKs signed with a different certificate must be uninstalled once before installing the signed release. For local signed builds, set SLATE_KEYSTORE_PATH and SLATE_KEYSTORE_PASSWORD; otherwise assembleRelease produces an unsigned APK.

## Roadmap
Storage analyzer improvements (root and Shizuku, richer storage statistics and cached browsing); app cleaner; residual files; system cleaner; duplicates; app manager; media compression; media review; scheduling; custom exclusions; accessibility-based compatibility; history and localization.

See CREDITS.md for upstream attribution. No SD Maid code is included in this milestone.
