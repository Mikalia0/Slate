## Unreleased — Gallery improvements

- Organize photos, albums and favorites with filters, sorting, album sizes, hiding, selection actions, duplicate and similar-image scans.
- Add media renaming, album copy/move and creation on Android 10+, and direct folder navigation.
- Add non-destructive image editor, EXIF details, metadata-free image sharing, double-tap zoom, slideshow and wallpaper chooser.
- Cache adaptive thumbnails in memory and on disk, observe MediaStore changes and prefer capture dates.

## Unreleased — File browsing and storage connections

- Reuse bounded in-memory folder listings, folder counts and file catalogs, observe the active local folder and avoid full scans after read-only previews.
- Share file row and icon components with Documents Providers, add grid/list view, breadcrumb navigation, sorting, selection, metadata and capability-aware actions.
- Add a Connections section to the browser home and drawer for persistent Android folder grants, including compatible cloud providers and USB devices.

## Unreleased — APK signing

- Provide a local default signing key, scheme selection, V1 filename, managed imported keys and per-package signing preferences.
- Enable optional auto-signing by default for clone, provider injection, manifest editing and APK archive changes.

## Unreleased — First-run appearance

- Refine the first-run flow with a compact live preview, step progress and adaptive landscape layout.
- Follow system light/dark mode with AMOLED black in dark mode; use system wallpaper colors for new installations on Android 12+.

## Unreleased — Update delivery

- Check a lightweight public update manifest instead of the GitHub REST releases API.
- Cache update checks, preserve legacy release compatibility, and publish a SHA-256 manifest after each release.

# Changelog

Changes are listed newest first. Each signed development release also includes changes since the previous build.

## Unreleased — System app debloater

- Add a Debloater entry to Apps with UAD-NG descriptions and risk classifications, package search, batch review, current-user removal, disabling and restoration via Shizuku.
- Cache the community database after its first successful download; keep unknown and unsafe packages read-only.
- Fix APK clone menu icon compilation.

## Unreleased — Shizuku APK installation

- Choose between the Android package installer and authorized Shizuku shell installation, including Shizuku started with ADB.
- Stream the selected APK to the package manager for the current user, retain standard signature and version checks, and display the actual result.

## Unreleased — APK cloning and browser restoration

- Clone authorized APKs with a different manifest package name, optional local automatic signing and a separate output file.
- Preserve file list and grid positions and category navigation when returning from previews and APK tools.

## Unreleased — Persistent landscape navigation

- Keep navigation, Settings and About in a single shared header, with fixed ordering on every main screen.
- Use available dashboard height for both landscape panels, preserving scrolling in smaller windows.
- Keep file operations protected against switching main screens during a transfer.

## Unreleased — Display and landscape fixes

- Show screen resolution and current refresh rate on the dashboard; improve processor naming and dashboard text layout.
- Merge landscape gallery and monitor navigation into their actual headers and wrap monitor presets.
- Fix the landscape file drawer and give the file home a two-column layout.

## Unreleased — Integrated file tools

- Integrate advanced archive extraction, manifest metadata editing and hex inspection into Slate.apk.
- Show APK actions before opening the inspector, with installation and installed-app launch.
- Add ZIP-family file export, import, replacement, text editing, rename, delete and directory creation. APK contents edits create an unsigned copy.
- Add opt-in, read-only Documents Provider injection for authorized APKs (external files only; outputs an unsigned copy).
- Publish a single APK instead of an optional companion.

## Unreleased — Compact portrait and landscape layouts

- Move main navigation and file browser actions into the top header in landscape.
- Show storage, RAM, battery and the manufacturer icon with the detected processor in a compact overview.
- Use full available width and reduce file row height in landscape.
- Keep portrait navigation and file actions at the bottom.

## Unreleased — Adaptive layouts

- Add width-aware dashboard, navigation rail and tablet settings navigation.
- Add side-by-side analyzer summary, APK and device navigation, app master/detail, adaptive gallery grids, monitor preview/controls and a permanent file sidebar on expanded windows.
- Constrain secondary screens to readable widths while preserving compact phone navigation.
- Fix invalid Android string resource in the document browser.

## Unreleased — File manager navigation

- Replace the storage popup with a navigation drawer for home, storage, categories, bookmarks and recent folders.
- Restore tabs across app restarts and remember sort order for each directory.
- Add persistent Storage Access Framework connections for supported USB and document providers.
- Add a second local destination pane and show copy progress and transfer speed with cancellation for copies.
- Generate release notes from actual changes since the preceding published build.

## 0.1.0 — First public release

- Storage analysis, duplicate detection, cleanup and app management.
- Device diagnostics, sensors, performance monitoring and Material 3 themes.
- Tabbed file manager, media and document viewers, ZIP extraction and APK inspection and signing.
- Optional Slate Advanced companion for additional archive formats, manifest viewing and basic APK metadata editing.
- Unified updater supports numbered releases and development builds.

## 0.5.0-dev — Gallery date groups

- Select or deselect an entire date group from its heading and delete a group using its contextual trash button.
- Confirm the date and visible item count before deletion, including any unselected items; preserve selections in other groups.

## 0.5.0-dev — Gallery layout

- Refine the gallery with photo/album tabs, borderless album covers, search, adjustable grid density, date-group counts and clearer multi-selection and favorites.

## 0.5.0-dev — Advanced file manager

- Support explicit create, rename, recursive deletion, copy and move in Android/data and Android/obb using authorized Shizuku file operations, without overwriting existing files.
- Keep protected roots outside the mutation scope and never follow symbolic links.
- Refine the file manager with folder title, tab strip, compact breadcrumbs, sortable list header and flat rows.

## 0.5.0-dev — Shizuku protected storage

- Browse Android/data and Android/obb through a read-only Shizuku user service, including file sizes, directory traversal and opening files with explicit URI read grants.
- Include protected files in storage scans when Shizuku is authorized; do not persist privileged indexes to disk.
- Restrict the privileged backend to the current user's emulated protected directories and reject symbolic links and write operations.

## 0.5.0-dev — Classic performance monitor

- Add an independently implemented classic circular-gauge monitor inspired by Scene, with CPU/GPU/battery rings, CPU cluster frequencies and per-core utilization.
- Keep the existing panel, custom metrics, scale and opacity; tap the classic overlay to collapse or expand it.
- Read per-core CPU statistics via Shizuku only when the classic CPU monitor is enabled.

## 0.5.0-dev — FPS monitor recovery

- Re-discover changed game surfaces and periodically retry presentation timestamps after falling back to cumulative counters.
- Recover from missing focus, failed dumps and disconnected Shizuku services without closing the floating HUD.
- Reject stale timestamps and distinguish measured zero FPS from unavailable samples.

## 0.5.0-dev — App language

- Add a persistent per-app language selector with system default, English, Brazilian Portuguese, Spanish, French, Italian and Russian.
- Add translated UI resource catalogs for Spanish, French, Italian and Russian, including storage, permissions, HUD and cache cleanup.
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
