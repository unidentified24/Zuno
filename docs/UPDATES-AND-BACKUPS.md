# Updates and automatic backups

## For viewers
Open Settings → App updates. Automatic checks are on by default, once daily when the app is used. Tap Download update, then Install update. There is no need to find an APK in a browser for each update. Android may ask you to allow installations from Velixio and always controls installation; Play Protect remains enabled. The app does not silently replace executable code.

Open Settings → Your data → Automatic backups and choose a folder once. Velixio immediately saves a copy, then schedules a daily backup and checks for overdue backups when opened. It retains seven successfully written copies. Android battery restrictions can delay jobs. Cloud syncing depends on the chosen document provider. Turning backups off keeps existing files.

Backups contain saved titles, viewing history, playback positions and preferences. Videos, credentials and streaming URLs are excluded. New files use `.velixiodata`; earlier version-1 metadata backups remain importable.

## Publishing an update
1. Increase both `versionCode` and `versionName` in `app/build.gradle`. This build is code 19, version 1.0.2. Next use code 20 or higher and version 1.0.3, or a numbered revision such as 1.0.2-1.
2. Build the complete APK using the same installation package, signing key and required local integration inputs. Keep the signing key and private inputs out of Git. Attach source for the matching version alongside distribution documentation.
3. Publish a GitHub Release in `unidentified24/Velixio`, tagged `v1.0.1-rc2`, `v1.0.1`, or another newer semantic version. Attach the signed APK as a release asset, not as a source file. GitHub must expose its `sha256:` asset digest. Mark candidates as prereleases; stable installations ignore candidates.
4. Test Check for updates from an older installation, complete the Android install confirmation, and verify the version and retained library.

No separate update server or manually edited JSON feed is needed. Release drafts are ignored. The newest suitable version is selected by version, not upload date. Download URLs are restricted to this repository and HTTPS GitHub asset hosts. Downloads are bounded to 128 MiB, checksum-verified, and rejected unless the package, signing certificate and newer version code match. APKs remain in private app cache and are shared only with Android's installer.

This build is non-debuggable but retains the existing development signing identity for upgrade and streaming compatibility. It is not a migration to Play App Signing. Never regenerate that key for an update.

## Verification limits
Automated tests cover release selection, hash requirements, backup formats, scheduling, retention and failed writes. Actual installation of a future update and real document-provider behavior still need phone testing. No release is uploaded automatically by this project.

Play Protect acceptance cannot be guaranteed. This app does not request SMS access, accessibility-service access or notification-listener access. A block can still require Google review of the complete APK and its bundled runtime. See Google's [developer warning guidance](https://developers.google.com/android/play-protect/warning-dev-guidance).

## Version order
Tags may be plain (1.0.2), prefixed (v1.0.2 or VelixioStream1.0.2), or numbered revisions (1.0.2-1, 1.0.2-2, 1.0.2-3). The order is 1.0.2 < 1.0.2-1 < 1.0.2-2 < 1.0.3. Earlier rc versions remain supported and sort before their final version. If a tag has no version, the release title is checked. Keep the tag aligned with the APK version; Android still requires an increased versionCode.

Older installed updaters still need plain semantic tags such as v1.0.2 to receive this fix.

## Android expansion build
The development branch currently builds 1.1.0-rc3, versionCode 22. The next candidate must use a higher code and a newer version (for example 1.1.0-rc4/code 23). Stable installations ignore release candidates. No release asset is published by this change. Download jobs and their private URLs/headers are not backup data.
