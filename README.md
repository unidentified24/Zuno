# Velixio

<img src="app/src/main/res/drawable-nodpi/velixio_logo.webp" width="96" alt="Velixio logo" />

An Android streaming app with artwork-led discovery, a fullscreen player and a library you can take with you.

## Android expansion (development)
The 1.1.0-rc3 development build adds Google Cast, compatible DLNA receivers and one-time TV-browser pairing, alongside a durable download queue, metadata filters and genre preferences, a resume widget, TV focus controls, wide title pages, series following, external-caption timing, optional audio effects and active watch-time tracking. It retains the profiles, collections and player tools from rc1. See [TV casting compatibility](docs/CASTING.md) and the [implementation status and remaining work](docs/ANDROID-EXPANSION.md); online accounts, sync and the iPhone website are deferred.

## Downloads
[Download APKs from GitHub Releases](https://github.com/unidentified24/Velixio/releases).

Choose the newest release candidate for testing; older APKs are retained as historical builds.

## Features
- Browse films, series, animation and shorts, with search across configured catalogs.
- Discover rotating featured picks and rating- and release-year-based collections.
- Save titles to My list and resume from Continue watching.
- Watch with quality, source, caption and picture-size controls, episode autoplay and picture-in-picture.
- Choose from ten themes, including Amethyst, Forest, Rosewood and Glacier, with comfortable or compact poster layouts.
- Export and restore saved titles, viewing history and preferences using `.velixiodata` backups.
- Manage appearance, playback and data through dedicated Settings pages.
- Download eligible streams and play completed copies offline; see [offline downloads and compatibility](docs/OFFLINE.md).

- Check for updates automatically and download verified packages inside the app. Android confirms installation.
- Schedule daily backups to a folder you choose, retaining seven copies.

## Release track
1.0.2 introduces the Velixio identity, in-app updates and automatic backups. Device acceptance is pending.

## Build
Use JDK 17, Android SDK 35 and Gradle 8.9:

```sh
./gradlew :app:assembleDebug
```

The source builds without service credentials. Live streaming requires local connection profiles in `private/assets/` and runtime libraries in `private/engines/jniLibs/`. These directories are ignored by Git. See the [connection contract](docs/BACKEND.md) for configuration and packaging requirements.

## Verification
```sh
./gradlew :app:lintDebug :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

See [verification](docs/VERIFICATION.md) for current results and device checks. The Android workflow can be run manually from GitHub Actions.

## Documentation
- [Interface behavior](docs/UX-REVIEW.md)
- [Updates and automatic backups](docs/UPDATES-AND-BACKUPS.md)
- [Data backups](docs/VELIXIO-DATA.md)
- [Security](docs/SECURITY.md)
- [Release checklist](docs/ROADMAP.md)
- [Third-party notices](THIRD_PARTY_NOTICES.md)

## License and Attribution

Velixio is an open-source Android application developed by [unidentified24](https://github.com/unidentified24).

Copyright © 2026 unidentified24.

Licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE) for details.

You may fork, modify, and redistribute Velixio under the terms of GPL v3.

When redistributing Velixio, you must preserve applicable copyright and license notices. Modified versions must also identify their changes as required by GPL v3.

Do not falsely claim authorship of the original Velixio source code.

Original project: https://github.com/unidentified24/Velixio
