# TV casting

## Use

Open a playing online title, reveal its controls and choose **Cast to TV**. The phone pauses; select a receiver on the dedicated casting screen. Keep the phone and TV on the same trusted home Wi-Fi/Ethernet network. A foreground notification opens the controls and has a Stop action. Home/locking the phone retains the relay. **Stop & return to player** ends casting and returns to paused local playback.

| Receiver | Available path | Limits |
| --- | --- | --- |
| Chromecast / Google TV / TV with Google Cast | Google Cast button and public Default Media Receiver | Sender needs working Google Play services; receiver must decode the supplied stream. |
| Compatible Samsung, LG and other DLNA media renderers | SSDP discovery and UPnP AVTransport | Only actual advertised AVTransport services appear. Model names alone do not establish support. Pause/seek and HLS support vary by receiver. |
| TV with a compatible browser, including some Fire TV browsers | Open the displayed short LAN address, enter the six-digit pairing code, then use TV video controls | Native browser HLS/MP4 support required. No downloaded JavaScript player or external web hosting. Browser casting has no phone playback controls. |
| Roku / unsupported receivers | Android's screen-mirroring controls, if the phone and receiver support them | No direct Roku adapter. This does not guarantee that every Roku/phone pair can mirror. |

TV-browser codes last two minutes, work once, and lock after five failed guesses. Opening the browser-pairing panel on the phone creates a new code. Once paired, the receiver gets a longer session capability; stopping casting invalidates it. The copied address contains no media capability or upstream URL.

Google's sender SDK handles Google Cast discovery/session setup. DLNA discovery only requests MediaRenderer devices and uses an advertised AVTransport service; there are no guessed vendor endpoints, logins or hidden receiver installations. Amazon Fling is retired; Matter Casting and a dedicated Fire TV receiver are not integrated. Roku's published ECP rules disallow third-party mobile ECP commands, so Velixio does not issue them.

## Stream and network handling

- Selects one RFC1918 IPv4 address on Wi-Fi/Ethernet and binds the media server to that address, never all interfaces. IPv6-only, VPN-only, cellular-only and cross-subnet receiver use are not supported.
- Relays the selected online source using the existing streaming authorization and original available renditions. No transcoding, alternate content source, purchase claim or credit manipulation is performed. The receiver chooses a rendition it supports; the phone's local quality cap does not configure the TV.
- Supports HLS and MP4. HLS variant/audio/key/map/segment references become registered opaque URLs. Query bytes remain in private memory. Clients cannot provide arbitrary destination URLs. Playlists/resources are bounded; variable expansion, content steering and DASH fail instead of exposing unsupported references.
- Upstream headers stay scoped to their originating server. HTTPS uses system certificate/hostname checks, including redirects. Private upstream destinations are blocked except media paths on the active on-device proxy. Native control endpoints are not relayed. General app cleartext policy remains disabled; the explicit LAN receiver/relay transport is HTTP and is **not end-to-end encrypted**. Use a trusted LAN.
- The DLNA HTTP client accepts only literal private IPv4 addresses on the selected subnet; description/control URLs must stay on the responding device. Responses are bounded and XML document types/external entities are disabled.
- Range/HEAD/OPTIONS requests support receiver buffering/seeking. Session URLs never enter backup files or persistent preferences. The foreground notice hides title details.
- Service/network checks stop the bridge after network loss, ten idle minutes, twelve hours, task removal or explicit Stop. Process death does not restart it. A wake lock supports relaying while the screen is off and is released on stop.
- Current targetSdk is 35. Android's documented legacy INTERNET grant supplies LAN access; no new broad nearby-device runtime permission is requested. Before raising targetSdk to 37, adopt the platform's current local-network permission/picker requirements.

## Scope and acceptance

Offline casting, DASH rewriting, remote progress/watch-time synchronization, receiver subtitle selection/timing, remote episode/version changes, Matter Casting and a dedicated Roku receiver are not implemented. Choose the desired episode/version in the phone player before starting a new cast session. Embedded tracks may be handled by the receiver; separate caption tracks and phone audio effects are not transferred. Browser autoplay/fullscreen restrictions remain under TV/browser control.

Automated coverage exercises HLS rewriting with signed references, unchanged segment/range bytes, capability enforcement/revocation, pairing lockout, private-address restrictions, same-device DLNA endpoints and XML entity rejection. It does not prove real Google Cast discovery, TV decoding, DLNA actions, screen-off service lifetime or mirroring. No TV or attached Android device was used for this checkpoint. Verify a full title, pause/seek, phone lock, network loss and Stop on each supported receiver family before a stable release.

## Primary protocol references

- [Google Cast Android integration](https://developers.google.com/cast/docs/android_sender/integrate)
- [UPnP AVTransport specification](https://openconnectivity.org/wp-content/uploads/2015/11/UPnP-av-AVTransport-Service.pdf)
- [Roku ECP rules](https://developer.roku.com/dev/docs/external-control-api)
- [Amazon Fling support status](https://www.developer.amazon.com/docs/fling/integrating-amazon-fling-into-your-fire-tv-app.html)
- [Android local-network permission](https://developer.android.com/privacy-and-security/local-network-permission)
