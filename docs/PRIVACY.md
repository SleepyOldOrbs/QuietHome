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

Requests use HTTPS and a fixed QuietHome user-agent. Providers see the public IP address of the connection. Requests do not include a device serial, app list, account credentials or advertising identifier.

Feed fetches allow only these hosts, reject redirects, bound the response size to 256 KiB and apply connection/read timeouts. RSS parsing rejects document type/entity declarations. News text is displayed as text, not executed HTML.

The **Open source** action validates a BBC HTTPS link and sends it to an external browser. Browser and BBC page behaviour are outside QuietHome's feed-fetching boundary.

## Refreshing and caching

Weather and news default to off; existing explicit on/off choices are retained. Weather also requires a chosen town. Each widget can be hidden independently through Home options.

Successful data is refreshed about every 30 minutes while Home is active. Failures retry after five minutes. Manual refresh and town searches make additional requests. A request already running when Home is paused may finish, but its result is discarded. Subsequent feed requests and refresh timers do not start while paused.

Last successful results are stored in the app's preferences and survive process restarts. Ages are shown on screen. Failed or more-than-one-hour-old results are labelled cached. Modelled weather is not a reading from a sensor at the TV.

Use **Widgets > Clear feed data and turn feeds off** to remove cached feed bodies and disable both feeds. **Forget weather town** removes the town, coordinates and cached weather and disables weather. These controls do not erase previously exported or OS-managed backups. Pending responses cannot repopulate the cleared cache.

News summaries are the publisher's feed descriptions, not generated summaries.

## Local data and backups

Local preferences include favourites, tile size, selected app, wallpaper shuffle state, artwork delay, widget visibility, news edition, chosen town/coordinates and cached feed data.

User-triggered settings exports include configuration and gallery state, but not cached feed bodies, wallpaper pixels or credentials belonging to other apps. Android's `allowBackup` flag is enabled, so OS-managed app backup may also apply according to the device's backup configuration.

This public repository deliberately omits personal wallpapers, exported preferences, screenshots from private installations, device inventories and signing material.

QuietHome cannot control telemetry or network traffic from Android, the TV manufacturer's software, other apps or external browsers.
