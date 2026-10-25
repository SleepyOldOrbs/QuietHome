# Privacy and data flows

## Permission and execution

QuietHome requests only `android.permission.INTERNET`. There is no location, microphone, contacts or shared-storage permission, advertising identifier access, analytics SDK, WebView or background service.

The clock and wallpaper rotation are local. Images are read from the app's bundled assets. Widget network requests run on a worker separate from image decoding.

## Network endpoints

| Host | Purpose | Request data |
|---|---|---|
| `api.open-meteo.com` | Weather forecast/current modelled conditions | Chosen town coordinates and weather fields |
| `geocoding-api.open-meteo.com` | Explicit town search | The search text entered by the user |
| `feeds.bbci.co.uk` | Public RSS | Selected UK, World or Technology feed |
| `www.goodnewsnetwork.org` | Public RSS | Good news edition; exact path `/category/news/feed/` only |

Public-feed requests use HTTPS and a fixed QuietHome user-agent. Providers see the public IP address of the connection. Requests do not include a device serial, app list, account credentials or advertising identifier.

Feed fetches allow only these hosts, reject redirects, bound the response size to 256 KiB and apply connection/read timeouts. RSS parsing rejects document type/entity declarations. News text is displayed as text, not executed HTML.

The **Open source** action validates an HTTPS link belonging to the selected publisher (BBC or Good News Network) and sends it to an external browser. Browser and publisher-page behaviour are outside QuietHome's feed-fetching boundary.

Good news strips feed HTML without loading embedded images and excludes common distressing words in titles and summaries locally. It does not send text to an AI service, rewrite stories or silently fall back to an unfiltered news edition. A word filter cannot guarantee tone. If every story is excluded, the card shows a quiet empty state. Existing news-source preferences are retained until the user changes them.

## Refreshing and caching

Weather and news default to off; existing explicit on/off choices are retained. Weather also requires a chosen town. Each widget can be hidden independently through Home options.

Clock-only layout suppresses both feeds and their scheduled requests without forgetting the selected town, news edition or individual visibility preferences. Returning to standard or compact layout resumes the enabled feeds. A request already in progress may complete, but an obsolete response is discarded.

Successful data is refreshed about every 30 minutes while Home is active. Failures retry after five minutes. Manual refresh and town searches make additional requests. A request already running when Home is paused may finish, but its result is discarded. Subsequent feed requests and refresh timers do not start while paused.

Last successful results are stored in the app's preferences and survive process restarts. Ages are shown on screen. Failed or more-than-one-hour-old results are labelled cached. Modelled weather is not a reading from a sensor at the TV.

Use **Widgets > Clear feed data and turn feeds off** to remove cached feed bodies and disable both feeds. **Forget weather town** removes the town, coordinates and cached weather and disables weather. These controls do not erase previously exported or OS-managed backups. Pending responses cannot repopulate the cleared cache.

News summaries are the publisher's feed descriptions, not generated summaries.

## Local data and backups

Power buttons use Sony's local service at `http://127.0.0.1/sony/system`. This is the only HTTP exception; LAN and internet HTTP remain disallowed. Commands never leave the TV. There is no PC dependency, shell command, ADB key, root access, accessibility service or new Android permission. An explicit connection check uses Sony's authenticated, read-only `getSystemInformation` method; the returned device information is not displayed, logged, uploaded or saved.

Sony must already permit password-authenticated IP control. QuietHome accepts that separate TV control password through a password field and verifies it before saving. It is stored in Android's app-private `noBackupFilesDir`, excluded from both QuietHome settings exports and Android's automatic backup. It is not logged or included in public code/builds. **Power/Restart → Connection → Forget connection** erases the local copy. Sony's control password can authorise capabilities beyond the two commands QuietHome exposes, so keep it private. QuietHome never changes Sony's authentication mode or password itself. The TV owner must authorise those settings separately; retain an existing password if other controllers use it.

Each command requires a confirmation, runs on a worker with connection/read timeouts, and is sent once without automatic retries. A missing reply can mean the TV is already shutting down; the error message explicitly asks the user to check before retrying. No power polling or scheduled requests are added.

Local preferences include favourites, tile size, selected app, wallpaper shuffle state, artwork delay, widget visibility, news edition, chosen town/coordinates and cached feed data.

Theme, widget layout and card positions are also stored locally and included in settings exports. Frosted Glass is generated on the TV from bundled wallpaper pixels; it adds no network endpoint or permission. Image decoding uses a worker and a three-entry cache with a 14 MiB retained-cache budget; displayed images and temporary decoding allocations are additional memory. Cached frost images have a maximum dimension of 160 pixels. There is no continuous blur or wallpaper upload.

User-triggered settings exports include configuration and gallery state, but not cached feed bodies, wallpaper pixels or credentials belonging to other apps. Android's `allowBackup` flag is enabled, so OS-managed app backup may also apply according to the device's backup configuration.

This public repository deliberately omits personal wallpapers, exported preferences, screenshots from private installations, device inventories and signing material.

QuietHome cannot control telemetry or network traffic from Android, the TV manufacturer's software, other apps or external browsers.

## Colour and nature themes (1.8)

Appearance settings are local and included in settings exports. Botanical/seasonal borders are original vector drawings recorded when size or style changes. They introduce no downloads, tracking, permissions, bitmap collection or animation timer. Art mode hides them. Colour and transparency previews are not saved until Save; leaving with Cancel or Back discards the preview. Version 1.8 did not include video playback.

## Local video (1.9)

Video is read only from the app-specific local videos directory. Media validation runs on a worker; preparation is asynchronous. Playback is muted and released when Home is paused. No audio focus, wake lock, new permission, media URL, analytics or upload is introduced. Settings exports contain mode and filename only; keep a separate copy of the video files. Cached still-picture frost is bypassed while video is visible; panels use their existing translucent tint. See [video backgrounds](VIDEO-BACKGROUNDS.md).

## Summertime artwork (1.10)

Summertime bundles one generated PNG with real alpha transparency. Generation happened during development; the TV does not call an image-generation service or download decorative assets. A worker decodes one sampled bitmap (approximately 1.5 MiB on the tested TV), cached for the process lifetime; this is additional to the wallpaper cache. Static corner drawings have no animation timer. Theme/border-density preferences use the existing local settings and backups. Home-screen-only video mode releases its player in Art mode.

## Seasonal artwork and panel decorations (1.11)

All six botanical/seasonal themes use bundled generated PNGs, plus matching transparent sprigs for the dock and widget rims. Generation occurs only during development. A single worker decodes the requested theme; the cache retains one pair of sampled bitmaps, approximately 1.9 MiB on the tested TV. Old drawing resources and in-flight decoding can temporarily add memory until released by Android. Alpha-bound analysis also runs on the worker. There are no additional providers, permissions, timers or saved-setting fields.

Decorations use actual visible panel bounds, follow widget layout/visibility changes, and stay fixed while app tiles scroll. The overlay cannot receive focus or touch. Text and controls are clipped out of its drawing area. Off/Light/Lush and Art mode apply to both the screen borders and panel artwork.

## Larger panel garlands (1.11 preview 2)

Six replacement alpha images create continuous top-and-side drapes at the existing panel bounds. Screen-corner images are unchanged. Both frame and garland are sampled at half size by the existing worker, with only one theme pair retained. This increases the theme bitmap allocation to approximately 3 MiB, excluding decoding temporaries and other caches. Panel interiors and controls remain protected, and Art mode hides the whole overlay. No permission, network provider, timer or settings-schema change is introduced.
