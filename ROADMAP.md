# Slate roadmap

Goal: a fast, focused Material 3 Android utility that combines the strongest storage and cleanup workflows of SD Maid SE, app inspection capabilities of App Manager and useful device diagnostics without clutter. Prioritize correctness and real-device reliability over feature count.

Status: implemented features still need device validation. CI verifies compilation, unit tests and signing, not Android/OEM behavior. Items below are future work or validation gates, not claims of completion.

## P0 — Cleanup safety and reliability

- [ ] Validate cache-only cleaning with Shizuku ADB and root on Android 13–16, including Motorola, Samsung and Xiaomi.
- [ ] Investigate a safe package-manager backend for older Android builds; never fall back to clearing app data.
- [ ] Per-app cache cleanup in app details; optional filters for user/system apps in the review.
- [ ] Add cache cleanup history with selected packages, estimated vs measured space and failures.
- [ ] Add a shared cleanup review/result flow for duplicate files, temporary files, empty folders and remnants.
- [ ] Improve remnant detection and exclusions; verify paths, symlinks and protected storage behavior.
- [ ] Add cancellation, lifecycle resilience, large-app-list performance and device test coverage.

## P1 — Storage analyzer and app management

- [ ] Fast incremental scanning with benchmarks for 10k/100k files, memory use and repeated scans.
- [ ] Improve folder drill-down, visual size distribution, breadcrumbs and sorting; preserve scroll position.
- [ ] Better category accounting and adopted/removable storage support; avoid double-counting app and shared storage.
- [ ] Storage growth history, largest files, duplicate groups, and clear before/after comparisons.
- [ ] App manager: permissions, installer, target SDK, split APKs, signing details, last update and install date.
- [ ] Export installed APKs and split sets, batch uninstall through Android or explicitly authorized Shizuku, with review.
- [ ] Inspect app components, trackers and libraries using locally available metadata; explain incomplete results.

## P2 — Device diagnostics

- [ ] Validate redesigned RAM breakdown (available, cached, swap/zRAM where exposed) and per-volume storage usage on real devices; add filesystem details where exposed.
- [ ] Improve CPU cluster topology, frequencies, GPU identification, Vulkan/OpenGL capabilities and thermal accuracy.
- [ ] Battery health/capacity, charging power and estimated time with source/availability indicators.
- [ ] Faster Device tabs, responsive live readings and optional hardware tests; avoid polling off-screen.
- [ ] Export a privacy-reviewed device report; no identifiers by default.

## P3 — Interface, automation and release quality

- [ ] Keep Home minimal: storage summary, existing graph, relevant actions and optional insights; no redundant quick folders.
- [ ] Validate redesigned Material 3 hardware cards and expandable technical details; polish typography, accessibility, landscape and smooth navigation.
- [ ] Improve permission onboarding, optional Shizuku setup and clear unsupported-state explanations.
- [ ] Scheduled scans with review notifications; destructive actions remain explicit.
- [ ] Localization review (English/PT-BR), actionable changelogs, update history and reproducible release checks.
- [ ] Establish a stable-release checklist: CI, device matrix, migration/signature checks, cleanup safety, performance and regressions.

## Progress tracking

Track each item as planned, implemented, CI-verified or device-verified in GitHub issues. Only count an item complete after the applicable acceptance tests pass. Keep scope lean: no ads, analytics, RAM boosters, artificial speed claims or automatic deletion of personal files.
