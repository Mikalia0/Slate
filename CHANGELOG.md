# Changelog

Changes are listed newest first. Each signed development release also includes changes since the previous build.

## 0.5.0-dev — File manager and gallery expansion

- Add folder tabs, persistent bookmarks, breadcrumbs, list/grid views and batch file operations.
- Add gallery media filters, favorites, selection, batch sharing and confirmed deletion.
- Add fullscreen swipeable photo viewer, pinch zoom and built-in video playback.
- Link README download buttons to the latest signed development release.

## 0.5.0-dev — Direct cache cleanup

- Use Android Package Manager freeStorageAndNotify with the full reserved-cache allocation flag for global cleanup.
- Use the per-package cache deletion API for selective cleanup when permitted.
- Preserve the cache estimate, explicit confirmation, and actual completion callback.

## 0.5.0-dev — Shizuku cache compatibility

- Detect actual remote cache permissions instead of requiring per-package cache clearing.
- Support Android's global cache trim with a reviewed estimate and explicit confirmation when selective cleanup is unavailable.
- Keep selective cleanup where supported; never clear application data.

## 0.5.0-dev — FPS monitor and in-app update notes

- Replace hardware charts and overlay with a minimal FPS counter and foreground package.
- Read presented game frames through Shizuku and SurfaceFlinger; use TimeStats when timestamp history is unavailable.
- Show update changelog inside Slate before downloading and installing.

## 0.5.0-dev — New tools navigation

- Add bottom navigation for Home, Files, Gallery and Monitors.
- Browse shared storage directly, search folders, switch volumes and manage files with explicit operations.
- Browse local photos and videos by date or album with lazy thumbnails and external viewing/sharing.
- Add live performance charts and a user-started draggable floating hardware monitor. Game FPS is not implemented yet.

## 0.5.0-dev — Device information redesign

- Redesign CPU, GPU, memory, display, thermal, storage and network sections around visual summaries and compact metrics.
- Move verbose driver, system and sensor details behind expandable sections and hide unavailable readings.
- Remove duplicated detail panels from device overview while preserving live usage indicators.
- Show cached/free RAM, swap and zRAM when the OS exposes them; show storage usage per volume and named HDR formats.

## 0.5.0-dev — Cache review and update notes

- Analyze app caches and review each app, estimated size and selection before cleaning.
- Show unmeasured apps, require a separate confirmation and report cleanup progress/results.
- Remove fixed 1/2/5 GB cache trimming controls.
- Include app storage on adopted volumes when Android exposes statistics.
- Show release changelogs in the in-app updater and publish notes with every new signed APK.
- Refresh the development roadmap with priorities for cleanup, app management, storage and diagnostics.

## 0.5.0-dev — Shizuku access

- Add optional Shizuku setup to onboarding and Settings.
- Add app-cache cleanup using Android cache-only operations with Shizuku.
- Improve the Home dashboard and storage/device navigation.
