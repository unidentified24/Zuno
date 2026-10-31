# Third-party software

Velixio is licensed under the **GNU General Public License version 3.0 (GPL-3.0)**.

See the `LICENSE` file in the repository for the full license text.

## Dependencies

- **AndroidX Media3** (ExoPlayer, HLS, DASH and UI): Apache License 2.0. Source: https://github.com/androidx/media
- **Android Gradle Plugin and Android SDK tooling:** Retain their applicable upstream notices.
- **Gradle wrapper/tooling:** Apache License 2.0. Source: https://github.com/gradle/gradle
- **Media3 OkHttp datasource, OkHttp and Okio:** Retain their upstream Apache-2.0 licenses.
- **AndroidX RecyclerView 1.4.0:** Apache License 2.0. Source: https://android.googlesource.com/platform/frameworks/support/+/androidx-main/recyclerview/

- **AndroidX AppCompat, Fragment and MediaRouter:** Apache License 2.0; transitive Google Cast UI dependencies.
- **Google Play services Cast framework 22.3.1:** Google SDK/API terms and applicable packaged notices. Source licensing of Velixio does not relicense this SDK. Verify distribution and GPL compatibility along with the external runtime before public distribution.

## Testing dependencies

The following dependencies are used for development and testing:

- **JUnit 4:** EPL-1.0.
- **JSON-java:** Subject to its applicable upstream license.
- **Robolectric 4.16:** MIT License. Source: https://github.com/robolectric/robolectric/blob/master/LICENSE
- **AndroidX Test Core, Runner, Ext JUnit and UI Automator:** Apache License 2.0.

Robolectric and the listed testing dependencies are not intended to be packaged in the released application.

Device tests reference a public Media3 Big Buck Bunny fixture. Neither the clip nor an alternative catalog is bundled with Velixio.

## External streaming runtime

Some Velixio builds use an external ARM streaming runtime.

Its binaries are excluded from this Git repository, and redistribution is subject to its applicable license.

Velixio's GPL-3.0 license does not independently grant redistribution rights for that external runtime.

The small `com.pp.hls` Java declaration is an ABI interface and does not contain native implementation code.

Compatibility between the external runtime's license and GPL-3.0 must be verified before distributing builds that combine them.

## Distribution requirements

Before distributing Velixio binaries:

1. Generate an inventory of resolved direct and transitive dependencies.
2. Include all required third-party copyright notices and license texts.
3. Comply with GPL-3.0 source-code distribution requirements.
4. Verify that external runtime components can legally be redistributed and combined with GPL-covered code.

This file is a development inventory, not a completed distribution audit.
