# QuietHome

**A quieter home screen for your Android TV.**

[![Build](https://github.com/SleepyOldOrbs/QuietHome/actions/workflows/build.yml/badge.svg)](https://github.com/SleepyOldOrbs/QuietHome/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

QuietHome is a small, native Android TV launcher built around the things you actually want to open: your apps, your pictures and a few useful widgets. No recommendation carousel, sponsored tiles or advertising SDKs.

It began as a custom home screen for a Sony BRAVIA and grew into a standalone launcher. It replaces the Home screen; Android and the TV manufacturer still handle playback, tuning, HDMI, sound and picture processing.

## What it does

- **An app dock you control.** Choose favourites, use compact or standard tiles, and hold OK to rearrange them. The dock refreshes when apps change; temporarily unavailable favourites keep their saved positions.
- **Your own wallpaper collection.** Shuffle without repeats, avoid an immediate repeat between cycles, and remember the current image after restarting. Rotate every 1, 5 or 15 minutes, or pause for manual changes. Time away from Home does not consume the interval. Choose fill/crop or fit the whole image with borders.
- **Artwork mode.** Hide the interface immediately with Art mode, or after 30 seconds, one minute or two minutes. Left/Right browse the pictures while the interface stays hidden. Up, Down, OK or Back bring the controls back without launching an app.
- **Silent video backgrounds.** Play a local H.264 MP4 on Home and in Art mode, only in Art mode, or only on the Home screen. Playback stops when you open another app; still pictures remain the fallback. See [video setup and recovery](docs/VIDEO-BACKGROUNDS.md).
- **Remembered focus.** Return to the app you were selecting, including after visiting a widget.
- **Clock and date.** Follow the TV's local time without a network request; choose system, 12-hour or 24-hour display.
- **Nine themes.** Frosted Glass, Midnight and Gallery are joined by Woodland ivy, Meadow flowers, Sakura blossom, Autumn leaves and Halloween pumpkins/bats. Summertime adds realistic, AI-generated transparent flower/foliage artwork. Borders stay around the edges and disappear in Art mode. Choose a preset in **Home options → Appearance → Theme**.
- **Your own colours and transparency.** Pick highlight/panel swatches or enter a custom `#RRGGBB` colour. Preview transparency with Left/Right in 5% steps and choose Off, Light or Lush borders. Save commits a preview; Cancel or Back discards it. Selection highlights remain solid with automatic black/white labels. Choosing a theme resets its appearance to that preset.
- **Widget layouts.** Choose standard, compact or clock-only cards and swap the news/weather positions in **Home options → Widgets**. Clock-only pauses feeds while preserving their settings.
- **Optional weather.** Choose a town; see modelled conditions, temperature and today's high/low in Celsius or Fahrenheit. Unit changes work offline.
- **Optional news.** Choose Good news from Good News Network, or BBC UK, World or Technology. Good news excludes common distressing topics using a conservative word filter; it cannot guarantee editorial tone. Read publisher headlines and short summaries with the remote.
- **Local settings backups.** Save and restore the dock, gallery state, artwork delay and widget preferences.
- **TV input shortcuts.** List the available passthrough inputs using the TV's own labels.

The app is Java and Android framework code: no Gradle dependencies, WebView, analytics SDK, background service or wallpaper downloader. Network work uses a separate worker from image decoding.

## Current status

Current source: **1.10-preview.1**, adding Home-screen-only video playback and a Summertime preset with generated alpha-channel artwork. Version 1.9 added optional local video backgrounds, a verified copy helper and still-picture fallback. Version 1.8 added five botanical/seasonal presets and reversible colour, transparency and border controls. Version 1.7 also adds themes, widget layouts, neighbour-picture caching and background app-list refresh. See [measured performance](docs/PERFORMANCE.md), [validation](docs/TESTING.md) and the earlier [eight improvement loops](docs/IMPROVEMENT-LOOPS.md) for evidence and limits.

Earlier versions were exercised on a Sony BRAVIA running Android 12, including artwork mode, remote navigation, widget feeds, cached results, settings restoration and APK rollback. Automated and ADB checks do not certify HDMI, playback, audio or a physical remote.

The public build has portable tooling and an empty-wallpaper fallback. It is not a certified replacement for every Android TV or Google TV device. The manifest declares API 23 as its minimum; older Android versions and other manufacturers have not been validated. Some input shortcuts and the initial TV tile are Sony-specific.

**No personal wallpapers, TV backups, configured location, signing keys or device details are included.** The launcher works with a plain dark background until you add your own images before building. Public build tooling uses its own local development key and does not replace an existing installation signed with a different key.

## Build

Install:

- PowerShell 7
- JDK 21
- Android SDK Platform 35 and Build Tools 37.0.0

Point `JAVA_HOME` and `ANDROID_HOME` at those installations, then:

```powershell
git clone https://github.com/SleepyOldOrbs/QuietHome.git
cd QuietHome
pwsh -File ./test.ps1
pwsh -File ./build.ps1
```

Alternatively pass `-Jdk` and `-Sdk` explicitly. The script runs aapt2, javac, D8, zipalign and apksigner. Output: `build/QuietHome-debug.apk`.

A local development signing key is created in `.local/debug.p12`, which Git ignores. Keep it to make compatible updates from the same checkout. The development password is the standard `android`; use a separately managed production signing process before distributing releases.

### Add wallpapers

Put your own `.webp`, `.jpg`, `.jpeg` or `.png` files in `assets/wallpapers/` before building. These files are ignored by Git. Use reasonably sized landscape images; around 1920 × 1080 is a practical starting point for a TV launcher UI. Larger collections increase APK and device storage use.

Wallpapers are bundled at build time; there is no on-TV photo importer yet. Only use images you have permission to use. The repository's MIT licence does not grant rights to any images you add.

## Install and return to your previous Home screen

Want an AI agent to help with developer options, ADB, backups and installation? Start with **[AI-assisted setup](docs/AI-ASSISTED-SETUP.md)**, which includes copyable prompts and explains the steps that still need the physical remote.

See **[Installation and recovery](docs/INSTALL.md)** for the full process.

In brief: keep the original launcher installed and enabled, record its Home component, authorise ADB yourself, install QuietHome alongside it, and try QuietHome before changing the default Home app.

```powershell
adb -s YOUR_DEVICE install -r ./build/QuietHome-debug.apk
adb -s YOUR_DEVICE shell am start -n local.quiethome/.HomeActivity
```

This project does not root the TV, flash firmware, disable manufacturer packages or uninstall apps. There is no bundled debloating script.

## Remote controls

| Action | Control |
|---|---|
| Open an app | Arrows, then OK |
| Rearrange a dock tile | Hold OK |
| Choose favourites | Edit dock, or Menu |
| Reach widgets | Up from the dock |
| Read news summaries | OK on the news card |
| Return from a widget | Down |
| Hide the interface | Art mode |
| Browse pictures while the interface is hidden | Left / Right |
| Configure widgets or artwork mode | Home options |
| Restore controls in artwork mode | Up, Down, OK, Back, Menu or Home |

Volume and media buttons retain their normal actions. Open dialogs pause the artwork timer. The launcher does not keep the screen awake or override the TV's sleep/panel-protection settings.

## Privacy

The only requested permission is `INTERNET`, for optional weather and news. No device-location permission is used. Weather and news are off until you enable them; the clock works locally. Explicit widget choices from earlier versions are retained. You can turn each widget off in **Home options → Widgets**.

Feeds are fetched about every 30 minutes while Home is active, with local caching and visible update ages. Providers receive your public IP; weather requests include the selected town's coordinates. Opening a news article uses an external browser.

Read **[Privacy and data flows](docs/PRIVACY.md)** for endpoints, caching and limitations.

## Tests and development

```powershell
# Offline shuffle, request-generation, display and rotation tests on the host
pwsh -File ./test.ps1

# Optional: Android parser/settings tests plus live public feeds on an authorised device
pwsh -File ./test.ps1 -DeviceSerial YOUR_DEVICE
```

Device tests require Android SDK configuration and ADB on PATH. They push a temporary test DEX, run it with `app_process`, and remove it afterwards. They do not install the launcher or alter its preferences.

CI builds the APK and runs the offline tests. [Validation instructions](docs/TESTING.md) describe the separate Android and UI checks. It does not prove TV remote usability, input playback, audio, visual layout or live-provider availability. Contributions should include the relevant device/API details and actual checks performed; see [CONTRIBUTING.md](CONTRIBUTING.md).

## Licence and credits

[MIT](LICENSE), copyright SleepyOldOrbs.

Weather: [Open-Meteo](https://open-meteo.com/), CC BY 4.0. Town search uses GeoNames data through Open-Meteo. News headlines and summaries come from [BBC RSS feeds](https://support.bbc.co.uk/platform/feeds/NewsFeeds.htm) or [Good News Network](https://www.goodnewsnetwork.org/category/news/feed/) and remain subject to their publishers' terms. Third-party content and installed apps are not covered by this project's MIT licence.

QuietHome is an independent project, not affiliated with Sony, Google, Apple, BBC, Good News Network or Open-Meteo.
