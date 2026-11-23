# Slate

Android storage manager and cleaner. GPL-3.0-only.

## Current milestone
Read-only storage analyzer for mounted shared internal and removable storage. Browse directories using an interactive treemap or list, sort by size or name, filter the current listing, inspect file details, and show up to 100 largest files across a scanned subtree. See logical file sizes and counts; cancel or refresh scans. Android restrictions can hide app-private files; inaccessible paths are reported when detectable. No files are deleted.

## Device information
Independent, read-only device dashboard with hardware, Android system, battery, storage, network, sensor and camera metadata tabs. A redesigned overview shows RAM/storage gauges, per-core frequency relative to the reported maximum, GPU clock/utilization when exposed by the kernel, battery and charging metrics, thermal status, readable thermal sensors, display mode and network summary. Live measurements refresh only while the screen is visible. No overlay, background monitoring, internet permission or camera capture. Restricted metrics are marked unavailable; full Vulkan extension enumeration, CPU utilization and privileged metrics remain future work.

## Build
JDK 17, Android SDK 35 and Gradle 8.9:
```sh
gradle :app:assembleDebug :app:testDebugUnitTest
```
APK: app/build/outputs/apk/debug/app-debug.apk. The Android build workflow runs tests and assembles a debug APK published as a prerelease asset in this private repository.

## Roadmap
Storage analyzer improvements (root and Shizuku, richer storage statistics and cached browsing); app cleaner; residual files; system cleaner; duplicates; app manager; media compression; media review; scheduling; custom exclusions; accessibility-based compatibility; history and localization.

See CREDITS.md for upstream attribution. No SD Maid code is included in this milestone.
