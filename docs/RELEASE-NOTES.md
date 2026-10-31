# Release notes

## 1.1.0-rc3 — TV casting development
- Add a dedicated Cast to TV screen from the player.
- Add Google Cast via the public default receiver and standards-based DLNA discovery/AVTransport controls.
- Add TV-browser playback with a short LAN address and a single-use, two-minute pairing code, limited to five failed attempts.
- Relay the selected HLS/MP4 stream without transcoding or changing upstream authorization. The relay registers playlist resources behind temporary opaque URLs, forwards range requests and retains origin-scoped headers.
- Keep casting active in a foreground service while the screen is off; stop on explicit stop, task removal, process death, network loss, ten idle minutes or twelve hours.
- Universal receiver compatibility is not claimed: browser codecs, Google Play services, DLNA capabilities and device support vary. See CASTING.md. Offline casting, DASH, remote watch-history synchronization and receiver caption controls are not implemented.

## 1.1.0-rc2 — queue and Android platform features
- Add a persisted sequential queue with pause/resume/retry/reordering and safe fresh-stream resolution. Retain partial data when the stream resource identity is unchanged.
- Add metadata-based search filters and explicit genre/history recommendation preferences.
- Add resume widget and privacy control, Continue Watching shortcut, Android TV launcher/focus support and wide-window title columns.
- Add followed-series checks/notifications and conditional trailer previews from explicit catalog metadata.
- Add external SRT/WebVTT timing, additional caption styles, session audio effects/night mode, offline-resource checks and active watch-time statistics.
- Keep same native runtime/package/signing identity and preserve completed downloads. See ANDROID-EXPANSION.md for limitations and pending receiver/device work.


## 1.1.0-rc1 — Android feature development
- Add local metadata profiles while preserving the original profile's library and keeping playback pinned to its starting profile.
- Add collections, ratings, notes, watched controls, explicit discovery feedback and per-profile Home shelf visibility/order. Include personal records in validated metadata backups.
- Add richer update prompts, reminder snoozing, a What's new page, storage breakdown, Wi-Fi/download-budget preferences, private download notices and history privacy.
- Add gesture seeking/brightness/volume, touch lock, a sleep timer, caption appearance controls, cancellable episode countdown and direct playback recovery.
- Add debounced search results, offline startup routing, launcher shortcuts, viewing-progress overview and reviewed feedback sharing.
- Retain automatic-backup status, and bind scheduled backups to the profile chosen during folder setup.

84 automated tests pass; Android debug lint has no errors. This is a debug-signed development build. Device playback, Wi-Fi changes, visual/accessibility acceptance and live provider behavior are not verified for this increment. The download queue, advanced recommendations/filters, TV/casting, widgets, trailers, reminders, subtitle timing and audio enhancements remain on the roadmap. See ANDROID-EXPANSION.md.

## 1.0.2
- Accept numbered release tags such as 1.0.2-3 and branded version tags, with release-title fallback. Keep signing and checksum verification.
- Show a brief Velixio launch overlay while the catalog loads concurrently.
- Dismiss successful settings notices after 2.2 seconds and explicitly clear finished download notifications.
- Remove failed/cancelled partial media and update APK cache files older than one day. Completed downloads are retained.


## 1.0.1-rc2
- Add Sunset (aubergine and peach) and Midnight gold (ink blue and champagne).
- Preserve existing theme selections and include both new palettes in backup and restore.
- Increase version code to 18 for in-app update installation.


## 1.0.1-rc1
- Rename the app, repository, resources and backup exports to Velixio; retain installation identity, stored collections and earlier backup imports.
- Introduce a generated V/play logo with an adaptive launcher icon and a wordmark sized for the longer name.
- Add automatic daily release checks and verified in-app update downloads with Android-confirmed installation.
- Add daily folder backups with seven-copy retention, read-back validation and failure recovery.
- Expand to eight themes, coordinate player and browsing colors, and smooth download progress updates.
- Preserve GPL v3 and original-author attribution.


## 1.0.0-rc1
- Restore portrait cards for Continue Watching on Home. Show the complete poster without cropping it into a landscape frame.
- Add a dedicated resume control, progress bar and remaining-time label.
- Reserve enough shelf height for controls and text in comfortable and compact poster layouts.
- Preserve player, downloads, streaming configuration, backups and installation identity.

58 automated tests pass; lint reports zero errors and 39 warnings. Packaged signing/native checks pass. This remains a test-signed release candidate; physical-device and real-service offline acceptance are pending before 1.0.0.

## 0.12.0
- Replace the stock video controller with a custom seek/playback layout and direct version, quality, sizing, captions and episode actions. Remove the duplicate stock settings/audio menu.
- Select individual episodes inside playback while retaining the current stream if resolution fails.
- Add a Downloads navigation destination with Ready/Transfers views, artwork saved from the browsing cache, progress, storage totals, offline play and deletion.
- Introduce wide discovery cards, numbered highly rated cards and artwork-based Continue Watching cards. Keep excluded titles out even when the remaining discovery set is empty.
- Hide browsing chrome on title pages; add recent searches and visual theme previews.
- Preserve streaming inputs, normal authorization, cache-only offline playback and backup behavior.

57 automated tests pass. Lint reports zero errors and 39 warnings. Live offline playback, physical-device appearance/performance and emulator native playback still require acceptance.

## 0.11.0
- Replace the compact side-by-side feature with large cinematic artwork, gradient-backed titles and integrated actions.
- Rebuild title-page artwork into a full-width composition.
- Introduce a generated ribbon-Z logo in the launcher, header and library empty state.
- Add a midnight/coral visual identity, quiet category tabs, a rounded navigation dock and revised poster spacing.
- Restyle settings navigation and sheet controls.
- Preserve playback transport, downloads, library and backup behavior.

53 automated tests pass; lint has zero errors and 24 warnings. Physical-device visual acceptance remains outstanding.

## 0.10.0
- Redesign the wordmark, launcher icon and Cinema, Pacific and Golden hour themes.
- Add three-line navigation and styled action sheets, contained synopsis expansion and responsive episode cards.
- Separate Continue watching and My list navigation; add recommendation hiding and startup placeholders.
- Keep connection/version switching inside the player; add explicit rotate and picture-in-picture icons, Fit/Fill/Stretch and delayed buffering feedback.
- Add foreground downloads and cache-only offline playback with progress, cancellation and deletion.
- Adapt grids and reading widths to phone, tablet and wide windows.

This is a review build before 1.0.0. Automated checks pass; real-title offline playback and device/emulator acceptance remain outstanding. Downloads do not yet resume automatically after interruption or acquire offline DRM licenses.

## 0.9.0
- Prepare the final review milestone before 1.0.0.
- Reuse poster memory across screen recreation and avoid clearing unchanged artwork.
- Bound queued artwork requests to prioritize visible browsing work.
- Reuse Home shelf adapters and preserve horizontal positions when recycled.
- Reopen recent catalog views from a bounded cache while preserving incomplete-result notices.
- Update project naming and documentation for Velixio; remove obsolete development narrative.
- Preserve existing installation identity, playback configuration, backups and settings.

The 1.0.0 milestone follows device acceptance for playback, accessibility, browsing performance and backup/restore. This repository does not bundle service credentials or external runtime binaries.
