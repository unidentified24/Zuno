# Android expansion — 1.1.0-rc3

This development branch now contains two implementation increments. Device acceptance is still pending. Online accounts, cross-device sync and the iPhone website remain deferred at the owner's request. Releases are uploaded by the owner; this work does not publish a GitHub Release.

## Implemented behavior

| Area | Current behavior |
| --- | --- |
| Updates | Version, bounded release notes and download size, Update / Remind me tomorrow; daily automatic checks while used; verified package/signature/checksum installation flow. |
| What's new | Dedicated page shown once per installed version and available in Settings. |
| Storage | Breakdown for downloads, app files, cache, runtime and updater packages; cleanup restricted to expired updater APKs. Runtime files and completed downloads are protected. |
| Download policy | Optional Wi-Fi requirement, total storage budget and per-title safety limit. Blocked jobs wait until resumed under eligible conditions. |
| Download queue | Up to 50 pending jobs, sequential transfers, pause/resume/retry, earlier/later ordering, cancellation and deletion. Queue metadata survives process restart. Old interrupted jobs become paused. |
| Resuming media | Catalog jobs resolve normal signed streams from title/episode/version identity. Partial cache is retained and reused when resource URLs are unchanged. If a server token, proxy port or resource URL changes, the partial cache is safely discarded and that transfer restarts. This avoids treating temporary proxy URLs as permanent identities. |
| Backups | Existing status/failure page retained. Automatic jobs use the profile selected during folder setup; manual exports use the current profile. Collections, reviews, watched/hidden flags, followed-series metadata and tracked watch time are allowlisted. Imports retain a newer watch-time total. |
| Local profiles | Separate saved titles, history, progress, searches, collections, reviews, recommendations and Home layout. Default profile keeps original data. Playback stays pinned to its starting profile. Appearance and downloaded videos remain shared. Profiles are not authenticated accounts or privacy locks. |
| Recommendations | Explicit genre preferences, saved-title genres and viewing-history avoidance influence ordering where metadata exists. Not interested applies to known editions of the selected grouped title. No fabricated popularity or AI-learning claim. |
| Collections and reviews | Named collections, title opening/removal, personal ratings and notes, watched/unwatched controls and individual Continue Watching removal. |
| Player | Gestures, touch lock, sleep timer, verified next-episode countdown with Play now / Cancel, direct retry/version recovery, existing PiP and Fit/Fill/Stretch controls. |
| Captions | Size and contrast/style/font options. External SRT/WebVTT timing can move by 500 ms up to ±10 seconds. Embedded and TTML timestamp adjustment is not supported. Timing transforms only caption resources, not video bytes. |
| Home | Per-profile shelf visibility/order and featured visibility. |
| Notifications/privacy | Update prompts, followed-series notices, title hiding for downloads/episode notices/widget, Android notification settings and private viewing. Foreground download notification remains required by Android. |
| Accessibility | Larger text, higher contrast, reduced launch motion, existing font scaling and labels. Physical TalkBack/large-font acceptance remains necessary. |
| Offline startup | Opens completed downloads when offline. Playback uses a cache-only source with no network fallback. |
| Search | Debounced results, recent submitted searches, year/rating/genre/language and Movies & series/Shorts filters. Filtering applies to each supplied results page; missing metadata cannot match an active filter. Pure film-versus-series filtering is available through the existing browse sections, not inferred from missing search metadata. |
| Series following | Follow multi-episode titles, inspect/check/unfollow them, and receive optional notices when the selected version's listed episode count increases. Android schedules checks roughly every six hours; up to 20 titles are checked per run, rotating larger libraries. Failures retain previous counts. Not a server push service or a guarantee of release timing. |
| Trailers | An in-app preview action appears only for a valid explicit HTTPS `trailer_url` supplied by the catalog. There is no separately configured trailer provider; the catalog may supply none. |
| Casting | Google Cast default receiver, DLNA AVTransport discovery and controls, and single-use TV-browser pairing. HLS/MP4 only, on one private IPv4 LAN. Google Play services are needed only for Google Cast. See CASTING.md; hardware acceptance is pending. |
| TV | Optional Android TV launcher entry/banner, focus outlines, larger cards, D-pad control reveal and wide layouts. Runtime support still requires a compatible ARM device or working translation. Physical TV/remote playback remains unverified. |
| Tablets/foldables | Existing responsive browsing plus artwork/details columns on wide windows. Narrow windows return to a single column. No hinge-specific posture integration is claimed. |
| Shortcuts/widget | Search, Downloads and Continue Watching launcher shortcuts, plus a resume widget with profile/title and optional title privacy. Widget refresh is throttled and also follows profile changes. |
| Viewing statistics | Active playback time from this version onward, excluding pauses/buffering/trailers/private viewing, plus saved progress, completed/manual-watched counts and collections. Historical watch time is not fabricated from seek positions. |
| Travel check | Re-walks a completed download's resource graph with no network upstream and reports missing resources. This does not validate codecs, content checksums or future decoder compatibility; try airplane-mode playback before travel. |
| Audio | Original sound, dialogue EQ, gentle boost and Android 9+ night-mode dynamic-range compression where the device supports session effects. Default is original sound. This is not a full LUFS normalization pipeline. |
| Feedback | Reviewable text report with optional displayed device details, then Android's chooser. No background upload. |

## Remaining requirements and limitations

- Direct Google Cast, compatible DLNA and browser receiver paths are implemented in rc3 through a session-only LAN relay. Universal TV support, Roku direct integration, Matter Casting/Fire TV receiver integration, offline casting, DASH relay, receiver captions and remote progress/history synchronization remain outside this implementation. See CASTING.md for capability and security limits.
- Hinge-aware foldable postures, more complete tablet/TV navigation, remote/device acceptance and true loudness normalization need additional work.
- All-profile backup bundles and export of Home/search/discovery/caption-timing preferences are not included. Current backup scope is documented in VELIXIO-DATA.md.
- Trailer coverage and episode-reminder reliability depend on actual catalog metadata. No unsupported content or release dates are invented.
- Cross-device sync, accounts and the iPhone website are deferred.

## Acceptance

Automated checks and packaged native identity/library integrity are recorded in VERIFICATION.md. Live playback, pause/resume across changing signed streams, Wi-Fi transitions, notifications, widgets, caption sync, audio output, Android TV, folding screens and accessibility need physical-device checks. No Play Protect verdict is established.
