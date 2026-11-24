# Slate development tracker

Scope: 36 requested features, grouped into four workstreams. A feature is complete only when the implementation is verified by CI and the applicable runtime behavior is tested; partial and pending features do not count toward the completion percentage. The following is a living checklist, not a promise of platform capabilities. Android restrictions may require explicit user approval, Shizuku or root; do not silently bypass them.

## Storage analyzer (12)
- [ ] Grid view (implemented; verify)
- [ ] Real app icons (implemented; verify)
- [ ] File type icons (implemented; verify)
- [ ] Open/share files (primary shared storage implemented; verify other volumes)
- [ ] Clickable breadcrumbs (implemented; verify)
- [ ] Modification date sorting (implemented; verify)
- [ ] Custom exclusions (implemented; verify)
- [ ] Full scan history (30 scans and folder details; verify)
- [ ] Detailed session comparison (implemented; verify)
- [ ] Persistent index (implemented; verify)
- [ ] Incremental updates
- [ ] Accurate user/app/system separation (partial; Android limits)

## Cleanup tools (9)
- [ ] Duplicate finder (implemented; verify)
- [ ] Uninstalled app remnants
- [ ] Cache cleanup (app settings only)
- [ ] Temporary files (implemented; verify)
- [ ] App manager (open, version, install date, settings, system uninstall flow; verify)
- [ ] Empty folders (implemented; verify)
- [ ] Scheduled scans/cleanup
- [ ] Shizuku/root
- [ ] Exclusion management (implemented; verify)

## Device information (7)
- [ ] More manufacturer logos
- [ ] Detailed GPU information (GL limits, Vulkan version and GPU activity; verify)
- [ ] Live sensors (implemented; verify)
- [ ] Charging speed and remaining time (system estimate and watts implemented; verify)
- [ ] Battery capacity and wear (reported sysfs values and approximate capacity, if exposed; verify)
- [ ] Detailed Wi-Fi/mobile network (signal, frequency, link speeds and available cellular info; verify)
- [ ] Display/touch/sensor tests (implemented; verify)

## Home and UI (8)
- [ ] Smarter recommendations (partial)
- [ ] Automatic folder-growth detection (automatic full scan; verify, incremental pending)
- [ ] Custom dashboard cards (visibility controls implemented; verify)
- [ ] Storage category shortcuts (partial)
- [ ] Scroll restoration (analyzer implemented; verify other screens)
- [ ] Landscape layouts
- [ ] Smooth transitions
- [ ] Brazilian Portuguese localization
