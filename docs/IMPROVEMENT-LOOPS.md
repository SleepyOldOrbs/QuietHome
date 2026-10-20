# Eight improvement loops

These previews evolve the public source. They are not automatically installed on the original household TV. Hardware playback, HDMI/CEC, audio and a physical remote still need real-device checks before promotion to a stable release.

The [AI-assisted setup guide](AI-ASSISTED-SETUP.md) was published separately before these loops.

## 1. Discard obsolete widget responses

Research: Android's [activity lifecycle](https://developer.android.com/guide/components/activities/activity-lifecycle) and [Java worker-thread guidance](https://developer.android.com/develop/background-work/background-tasks/asynchronous/java-threads).

Finding: a previous town request could overwrite a newly selected location with the same name. Paused or reconfigured widgets could also accept old replies. Feed and town-search generations now invalidate pending results on pause, visibility/source/location changes and restoration. The worker remains bounded to one executor; an obsolete completion triggers a fresh request only if Home is active.

Validation: offline regression scenarios cover same-label changes, switching back, pausing and clearing. Host tests and the Android APK build passed. Physical network races on the household TV were not exercised.

## 2. Opt-in feeds and privacy controls

Research: Android's [permission minimisation guidance](https://developer.android.com/privacy-and-security/minimize-permission-requests), including user-entered locations instead of GPS.

Weather/news now default off; explicit prior choices survive upgrades. The widget chooser identifies providers. New actions clear feed caches and turn feeds off, or forget the town/coordinates and disable weather. Existing exported/OS backups are explicitly outside these controls. Queued town searches also check lifecycle state before contacting the provider.

Validation: host tests and APK build passed. Installed on an isolated Android TV API 31 emulator; inspected fresh-home and widget-choice screenshots. Enabled BBC News, used Clear and turn off, then reopened the chooser and verified both online widgets off. A message/list conflict found in the first UI check was corrected before publication. This is not a network packet-capture audit.

## 3. Celsius and Fahrenheit

Research: [Open-Meteo forecast documentation](https://open-meteo.com/en/docs) specifies Celsius as the default temperature unit. Keep one Celsius cache and convert locally instead of fetching the same forecast again.

Added a Temperature units chooser; current/high/low values and details use it. Preserve provider precision until display rounding. The optional backup field defaults to Celsius for older backups and rejects non-boolean values.

Validation: host tests cover freezing/boiling points, negative temperatures, convert-before-rounding and invalid values. Android parser/security/settings tests and all three live provider checks passed on the API 31 TV emulator. APK built and installed; inspected the unit picker screenshot and verified Fahrenheit remained selected on reopening.

## 4. Clock format follows the viewer

Research: Android [DateFormat](https://developer.android.com/reference/android/text/format/DateFormat) provides the system 24-hour preference and locale-appropriate patterns.

Added Home options > Clock format with system/12-hour/24-hour choices. The clock uses the current time zone and locale, and changes immediately. Backups include the optional clock mode; older backups follow the TV setting.

Validation: host preference-resolution tests; Android tests for midnight, noon and 23:59; parser/live-feed tests and APK build passed. Installed on the API 31 TV emulator and inspected the home screenshot showing the chosen 12-hour clock. No TV system clock setting is modified.

## 5. Wallpaper timing and pause

Research: Android [SystemClock](https://developer.android.com/reference/android/os/SystemClock) documents monotonic elapsed time; [Handler](https://developer.android.com/reference/android/os/Handler) supports removing scheduled callbacks. Wallpaper time should count foreground viewing, independently of wall-clock changes.

Added manual-only/1/5/15-minute rotation, saved in preferences and settings exports. A small tested timer preserves the remaining interval while Home is paused. Empty and single-image collections avoid redundant automatic decodes. Manual advance remains available.

Validation: host tests simulate interval boundaries, an hour away, repeated lifecycle events, paused rotation and invalid intervals; host suite and APK build passed. Installed on the TV emulator, inspected the timing picker and verified the paused selection persists on reopening. Long-duration visual rotation is checked with synthetic images in the next loop.

## 6. Fit or fill your pictures

Research: Android [ImageView scale types](https://developer.android.com/reference/android/widget/ImageView.ScaleType) distinguish aspect-preserving fit from centre crop; [bitmap guidance](https://developer.android.com/topic/performance/graphics/load-bitmap) reinforces retaining bounded background decoding.

Added Picture framing under Wallpaper settings: Fill screen or Fit whole picture. It updates the existing bitmap without another decode and is included in backups. Removed filename-based artist guesses from captions; naming a file forest or lake does not establish authorship.

Validation: host tests and APK build passed. Used three synthetic square test images, excluded from Git, in the API 31 TV emulator. Inspected screenshots showing crop versus full borders. Automatic one-minute advance, a paused image held over a minute, and manual advance while paused passed. No personal wallpaper collection was used or published.

## 7. Live app catalogue and dock recovery

Research: Android [broadcast lifecycle guidance](https://developer.android.com/develop/background-work/background-tasks/broadcasts) and [package visibility](https://developer.android.com/training/package-visibility).

Listen for protected package changes only while the activity is started, debounce refreshes and recheck on resume. Defer rebuilding while a dialog has focus, retain menu snapshots, and preserve focus outside the dock. Unavailable favourites keep their saved positions; an empty visible dock explains how to choose apps and focuses All apps. No new package-query permission is added.

Validation: host suite and APK build passed. On the isolated TV emulator, disabled the YouTube fixture, verified the empty-dock hint, restored the original default enable state and verified the tile returned. The QuietHome process stayed the same throughout. Inspected both screenshots, including preserved All apps focus. This test did not touch the household TV.

## 8. Portable, strictly validated settings restore

Research: Android's [JSONObject](https://developer.android.com/reference/org/json/JSONObject) getters can coerce types; [AtomicFile](https://developer.android.com/reference/android/util/AtomicFile) provides the existing recovery-file write pattern. Validate the full data structure before any restore side effects.

Added shared, side-effect-free backup validation for required and optional fields. Empty docks and unavailable favourites now restore; empty saved docks also survive process recreation. Older schema-1 backups retain documented defaults. Invalid booleans/numbers/sections are rejected instead of silently coerced. A pending image decode cannot overwrite a newer restored gallery, and display preferences refresh immediately. Recovery failure messages distinguish a failure before changes from an unexpected failure after the recovery snapshot.

Validation: all host tests, Android backup/parser/security/clock tests, live provider checks and the APK build passed. On the TV emulator, restored an empty dock with the new settings and exported it again; checked all new values. A malformed backup changed neither settings nor the pre-restore snapshot count. Restored an older backup and checked its defaults, then recovered and compared the original baseline exactly. Inspected successful/failed-restore screenshots. Synthetic images were moved out of the public checkout before the final empty-asset build.

## Publication and limits

Each numbered loop was committed and pushed separately after its local checks. GitHub CI runs the offline suite and APK build. The AI guide is an additional documentation commit, not one of the eight loops. The final source is 1.5-preview.8; this is not a stable hardware-validation claim. No preview was installed on the household TV, and no personal wallpapers, keys or device backups were published.
