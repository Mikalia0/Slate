# Changelog

Changes are listed newest first. Each signed development release also includes changes since the previous build.

## 0.5.0-dev — App language

- Add a persistent per-app language selector with system default, English, Brazilian Portuguese, Spanish and French.
- Integrate Android per-app languages and backward-compatible locale handling.

## 0.5.0-dev — Complete themes

- Replace accent swatches with four complete Material 3 themes (Slate, Ocean, Aurora and Graphite) and Android wallpaper colors.
- Use coherent primary, secondary, tertiary, neutral and surface roles across all screens, with faithful miniature previews and separate display mode controls.
- Migrate existing accent preferences and preserve light, dark, system and AMOLED modes.

## 0.5.0-dev — Optional advanced access setup

- Offer Root, Shizuku or None in onboarding and Settings, with explicit authorization and no automatic permission prompts.
- Keep shared storage permission separate and hide /data unless root access is verified.

## 0.5.0-dev — Root storage analysis

- Add a read-only root scan of /data to the storage volume overview.
- Rank up to 2,000 largest regular files across private app and system data, with progress, cancellation, search and size ordering.
- Request superuser access explicitly from Settings and hide /data until root is verified. Never delete private files from this view.

## 0.5.0-dev — Customizable performance HUD

- Replace the plain overlay with a dark compact panel, colored metric rows, fine separators, RAM usage bar and measured frame interval graph.
- Add FPS-only, compact, detailed and all-metrics presets, independent toggles, opacity and scale controls.
- Read CPU counters through Shizuku when permitted and use KGSL fallback for GPU load. Show missing measurements as unavailable rather than simulated.

## 0.5.0-dev — Appearance and GPU monitoring

- Refine the three-step onboarding with a compact layout, clearer permission actions and appearance previews.
- Add eight persistent accent palettes independent of light, dark and AMOLED modes.
- Add optional Neon Edge styling to the FPS/package overlay.
- Add the Ludashi KGSL memstore fallback for Adreno busy estimates when sysfs is unavailable.

## 0.5.0-dev — Adreno GPU monitoring

- Add the Ludashi KGSL memstore fallback for whole-GPU busy estimates when sysfs is unavailable, with idle shutdown and retry backoff.
- Preserve native source attribution and report unavailable metrics when unsupported.

## 0.5.0-dev — Storage volumes and About screen

- Add a dedicated mounted-volume overview with capacity, free space and direct folder analysis.
- Keep the analyzer focused on files and folders.
- Replace the About dialog with a full Material 3 screen showing installed version, project links, credits and offline license.

## 0.5.0-dev — Storage analyzer layout

- Redesign the analyzer as a compact continuous list with proportional size backgrounds and thin separators.
- Remove repeated size summary, large shortcut cards and fixed folder/largest-file filters.
- Show the current folder path in the header and move secondary analysis actions into the overflow menu.
- Preserve search, sorting, grid view, selection, and incomplete-size warnings.

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
