# Validation and release checks

A passing build is not proof of TV behaviour. Keep preview builds on an emulator or an explicitly authorised test device until the relevant checks pass.

## Automated tests

With JDK 21 and SDK Platform 35 / Build Tools 37 configured:

```powershell
pwsh -File ./test.ps1
pwsh -File ./build.ps1
# Optional Android runtime tests. Contacts public BBC/Good News Network/Open-Meteo endpoints.
pwsh -File ./test.ps1 -DeviceSerial YOUR_TEST_DEVICE
```

The offline suite covers shuffle/restart repair, obsolete request rejection, temperature/clock display decisions and rotation timing across pause/resume. The Android suite covers strict settings-backup validation, old/new/empty/unavailable-app backups, weather parsing, RSS/CDATA, hostile XML/links, locale clock patterns and live provider availability. It uses a temporary DEX with app_process; it does not install QuietHome or change app preferences.

CI runs the host suite and builds/signs the APK with an ephemeral development key. Android runtime/UI checks are separate. Keys from unrelated builds are not interchangeable.

## Emulator UI regression checklist

Use an isolated Android TV AVD, an explicit ADB selector and synthetic images. Do not publish personal settings or screenshots. Install with `adb -s YOUR_TEST_DEVICE install -r build/QuietHome-debug.apk`, then launch the Home activity.

1. Fresh Home: clock visible, online widgets off, plain background without assets; tools reachable with arrows and OK.
2. Widgets: enable a feed, check loading/failure/cached ages, change its source, clear and turn off. Verify an old response cannot reappear after clearing. Choose/forget a synthetic town and verify weather disables.
3. Display: select Celsius/Fahrenheit, system/12/24-hour clock, and restart; choices persist. Confirm midnight/noon formatting and long labels at your display/font scale.
4. Gallery: use differently shaped images with visible borders. Check fit versus fill, one complete timed advance, pause for longer than the previous interval, manual advance while paused and return after opening another app.
5. Artwork: select Art mode and check that every overlay disappears immediately. Left/Right change the picture without revealing controls; reversing direction returns to the same picture, including at a wrap boundary. Up/Down/OK/Back reveal controls without opening an app. Also check the automatic delay and normal media/volume actions.
6. Apps: change the availability of a disposable test app; its tile disappears and returns in the saved order. Keep an app picker open during a package change and check its selections do not shift. Restore the test package's original state afterwards.
7. Backups: save a baseline; restore an empty dock with all new preferences, save again and compare. Try a malformed optional field and verify the current settings and pre-restore snapshot count are unchanged. Restore an older schema-1 snapshot, check documented defaults, then recover the baseline.
8. Check launcher logs for crashes/ANRs and inspect screenshots after each significant change.

The eight-loop run used an Android TV API 31 x86 emulator at 1920 x 1080. Synthetic square images were used for framing/timing; private wallpaper images are excluded from the public repository. See the [loop record](IMPROVEMENT-LOOPS.md) for actual checks and their limits.

## Before deployment to a household TV

Follow the [AI-assisted setup guide](AI-ASSISTED-SETUP.md): back up, verify signatures/storage, preserve the stock launcher and obtain the user's physical checks. Test actual TV/HDMI picture and sound, Home/arrows/OK/volume, streaming apps and any casting/CEC/voice/Bluetooth/accessibility features in use. Preserve the previous APK, settings and signing key for recovery. Do not use uninstall or data clear to resolve an installation problem.

## 1.6 preview validation

On 21 October 2026, host tests, the Android parser/settings suite and live BBC, Good News Network and Open-Meteo requests passed on an authorised Sony BRAVIA running Android 12. Good-news tests cover HTML cleanup, publisher-host isolation, distressing topics in titles and summaries, word boundaries and an entirely filtered feed. Gallery tests cover manual previous/next inverses at every position, including wrap boundaries, with 1, 2 and 122 images.

The signed private build was installed in place after saving the previous APK and settings. All 122 bundled wallpaper files matched the previous build byte for byte. Screenshots confirmed the Art mode button hides every overlay, Right changes the image, Left returns to an identical screenshot, and OK restores Home without launching an app. The good-news card, summaries and external-browser handoff were exercised on the TV. Existing dock entries and widget preferences survived; the news edition was changed explicitly in settings.

An in-place rollback to the prior source, followed by reapplication of 1.6, also passed on that TV. Exported dock, widget and gallery settings were verified after both operations; no uninstall or data clear was used.

These are ADB-driven checks. They do not establish compatibility with every physical remote, streaming service, HDMI source, Bluetooth accessory or casting setup. Private wallpapers, settings and device screenshots are excluded from this repository.

## 1.7 preview validation

On 21 October 2026, the host suite, full public build, Android settings/parser suite and live provider checks passed on the same authorised Android 12 TV. New tests cover 10,000 mailbox offers with cancellation/resume, all nine valid theme/layout combinations, and rejection of invalid theme/layout/position field types and ranges. Tests validate behaviour and backup compatibility; they do not substitute for UI measurements.

TV screenshots and ADB remote input exercised Frosted Glass, Midnight and Gallery; standard, compact and clock-only widgets; swapped card positions; visible focus; news/weather dialogs; and clock-only persistence across process recreation. Exported clock-only settings retained the underlying feed choices, and returning to compact restored the cards. Ten rapid Right presses moved exactly ten gallery positions, ten Left presses returned to the original image, and Art mode kept every overlay hidden.

A selected app was temporarily disabled to exercise catalogue updates, then re-enabled while Home was paused. Its tile disappeared and returned on resume; the saved favourite order was preserved. No app was uninstalled or data-cleared.

The private update used the original signing certificate and retained all 122 wallpaper files byte for byte. An in-place rollback to 1.6 source and reapplication of 1.7 passed, with settings exported and compared after each operation. A recovery-script export timing issue was caught and corrected: verification now requires a newly created backup instead of accepting an older file.

See [performance](PERFORMANCE.md) for the startup, image-change, rendering and memory measurements, including regressions and methodological limits. This release adds no permissions, network providers or system-package changes. Fresh physical playback/audio/remote confirmation remains separate from these ADB checks.

## 1.7 preview 2 visual corrections

User feedback identified overly opaque glass and tiles drawing outside the dock. Glass now combines a translucent cached frost layer with a lighter tint, without an opaque undercoat when wallpaper is available. Focus retains a stronger translucent tint for readable dark labels. The dock has a rounded clipped frame and an inset scrolling viewport; focus scrolling reserves room for the enlarged highlight at both ends.

Host tests and public/private builds passed. TV screenshots were inspected with the first tile selected and after scrolling through the dock, including a scrolled selection after a launcher restart. Selected highlights remain inside the frame in compact and standard tile sizes. The absolute final favourite in the expanded dock was not separately checked. Widget focus and wallpaper visibility were checked on the actual TV. No new network, permission or settings-schema change is included. The previous preview's full performance table is labelled separately; it is not a fresh benchmark of this visual update.

An original-key update, rollback to preview-1 source and reapplication of preview 2 passed on the TV, with dock/widget/theme preferences exported and verified. All 122 bundled wallpapers remain byte-identical. Recovery packages, private settings and screenshots are excluded from the public repository.

## 1.8 preview 1 appearance and botanical borders

Adds Woodland, Meadow, Sakura, Autumn and Halloween presets, highlight/panel colour swatches and custom hex entry, a live transparency slider and Off/Light/Lush borders. Borders are original static vector drawings recorded on size/style changes. Art mode hides the decoration with the rest of the interface. Focus highlights are solid with automatic black/white text; very transparent panels deliberately expose the wallpaper, so preview readability before saving.

Validation on the authorised Android 12 TV:

- Host tests and public/private builds passed. New host tests sample over 16,000 highlight colours for at least 4.5:1 label contrast, reject malformed hex values and check immutable preview values.
- Android settings tests accept older backups and all eight theme IDs, reject malformed appearance fields before mutation, and exercise existing widget/feed parsing. Live provider tests passed.
- Screenshots inspected for all five new presets, the custom colour editor and remote-adjusted transparency endpoints. Top branches/flowers were moved clear of the clock; swatch padding was corrected after visual inspection.
- Custom hex `#254F3A` and remote-selected white panel tint were saved and verified through fresh settings exports. Solid white panels switch labels to black. Transparency Cancel and border-density Back discard previews; Off and Lush save correctly. Theme reset restores defaults.
- Scrolling to the final favourite keeps its focus highlight within the dock. Art mode removes all UI/borders, Right changes the image, and OK restores the UI.
- Existing favourites, compact tiles and widget configuration were preserved. All 122 private wallpaper files were compared and remained byte-identical. The private APK grew by approximately 12 KiB.

No new permission, provider endpoint, animation loop or background media playback is added. This is not a new latency benchmark or physical TV/HDMI/audio certification. Private APKs, screenshots, settings and device identity remain outside the repository.

Implementation references: Android's [custom-view optimisation guidance](https://developer.android.com/develop/ui/views/layout/custom-views/optimizing-view) and [SeekBar keyboard increments](https://developer.android.com/reference/android/widget/SeekBar#setKeyProgressIncrement(int)).

Original-key APK rollback to the previous source and reapplication of 1.8 passed on the TV. Fresh exports verified dock, widget, gallery timing/framing, theme and new appearance fields; recovery files are kept privately.

## 1.9 preview 1 local video backgrounds

Adds one muted local H.264 decoder with Off, Home-and-Art and Art-only modes. A separate copy helper validates a clip and device free space, then verifies the transferred bytes. Video settings are optional schema-1 fields; files are backed up separately.

Validation on the authorised Android 12 TV:

- Host policy tests cover safe filenames, inactive/suppressed/art-only playback and media limits. Android backup tests accept old snapshots and reject wrong video-field types, out-of-range modes, paths and URLs before mutation. Existing live widget/feed tests pass.
- Public and original-key private APKs build and verify. All 122 private wallpaper files remain byte-identical; the private APK increases by 4 KiB. Existing dock, compact tiles, widgets, appearance and gallery timing/framing are preserved in fresh exports.
- A private 20-second, 1280 × 720, 24 fps H.264 demo made from an existing wallpaper was copied with SHA-256 verification. Repeating the copy reused the verified file. It is not included in the repository.
- TV screenshots show different decoded frames behind translucent panels, clean Art mode, Left/Right returning to still pictures, and OK restoring controls. Home-and-Art and Art-only modes were exercised.
- Android media diagnostics report looping enabled and both volume channels at zero. Opening TV Settings removes QuietHome's player; returning Home resumes it. Logs show decoder release/recreation.
- Missing and deliberately damaged local files fall back to still pictures without a launcher crash. The damaged-file message was changed to plain-language format guidance. Returning to a valid clip succeeds.

This validates the tested 720p clip, not every codec profile, bitrate or television. Video has continuous decoding/composition costs; this is not a new latency, energy or 1080p performance benchmark. Physical TV/HDMI picture, sound and the physical remote were not re-certified during this session. Existing manufacturer apps and system settings were not modified.

Original-key rollback to 1.8 source and reapplication of 1.9 passed on the TV. Fresh exports verified dock, widgets, theme, appearance, gallery timing/framing and the reapplied video mode/filename. The private recovery kit verifies APK/settings/media hashes and retains existing app data.

## 1.10 preview 1 Summertime and Home-only video

Adds Home-screen-only video as mode 3, preserving the meanings of modes 0–2. Summertime is theme 8; existing theme IDs are unchanged. Its realistic flowers and foliage come from a generated transparent raster asset, with [prompts and provenance](../assets/themes/README.md).

Validation on the authorised Android 12 TV:

- Host tests, Android settings/parser tests, live provider checks and public/private builds passed. The policy matrix covers all four playback modes when active, paused, in Art mode and suppressed. Backup tests accept the new mode and reject invalid values; older snapshots remain valid.
- The 1672 × 941 RGBA asset is 412,544 bytes; over 92% of its pixels are fully transparent. A worker decodes a sampled bitmap once per process. Android reported 1,571,680 allocated bitmap bytes (about 1.5 MiB), additional to existing caches. This does not measure total rendering memory.
- Screenshots were inspected for Light and Lush borders, Off preview, Cancel restoration and Art mode. Flowers remain around corners and the bottom edge, clear of the clock and controls. Art mode hides the border completely.
- Home-only playback used the existing 720p local demo. Media diagnostics confirmed a muted, looping hardware decoder on Home, no QuietHome player in Art mode or TV Settings, and resumed playback on return. Right changes the still picture in Art mode; OK restores Home.
- The update retained dock, compact tiles, widgets, appearance and video choices before explicitly selecting the new options. All 122 private wallpapers remain byte-identical. No new permissions, provider endpoints or manufacturer-package changes were introduced.

These are automated and ADB-driven checks, not a new latency benchmark or physical TV/HDMI/audio certification. Private APKs, screenshots, settings and device identifiers remain outside this repository. Bitmap sampling follows Android's [large-bitmap guidance](https://developer.android.com/topic/performance/graphics/load-bitmap).

Original-key rollback to 1.9 source and reapplication of 1.10 passed on the TV. Fresh exports verified dock, widgets, gallery timing/framing, theme, appearance and video mode/filename in both directions. The private recovery script verifies file hashes, preserves app data and refuses later app versions.

## 1.11 preview 1 realistic seasonal artwork

Woodland, Meadow, Sakura, Autumn and Halloween now use generated alpha PNGs, matching the approach approved for Summertime. Each of the six themes also has a dedicated hanging sprig for the stationary dock and news/weather rims. Plain themes are unchanged. Nature themes reserve more space above the panels; the dock frame itself keeps its position.

Validation on the authorised Android 12 TV:

- Host and Android settings/parser tests, live provider checks and public/private builds passed. There is no new settings field or permission; existing theme IDs and Home-only video mode retain their meanings.
- All twelve PNGs were inspected and checked for actual transparency. Generated files retain their original bytes. The added artwork is 9,372,265 bytes (about 8.9 MiB); the approved Summertime frame and all 122 private wallpapers remain unchanged.
- One worker loads the requested frame at half size and sprig at quarter size, and computes alpha bounds off the UI thread. The cache retains one theme pair. Android reported 1,963,292 bitmap bytes (about 1.9 MiB) per tested theme; this excludes transient decoding, old drawing resources and other app caches.
- TV screenshots were inspected for all six themes. Decorations have natural transparent edges, remain clear of labels, and attach to panel rims. The overlay has no touch or focus targets and no animation timer.
- Off hides the screen and panel artwork. Light/Lush change size. Scrolling through the final dock favourite leaves decoration anchored to the dock frame. Standard, compact, swapped and clock-only widget layouts were exercised; hidden cards leave no orphan sprigs. The secondary wallpaper/video filename is hidden only when its text bounds intersect a visible widget, preserving the dock position.

This is visual and behavioural validation, not a new latency/energy benchmark or physical TV/HDMI/audio certification. Artwork generation happened during development using the built-in ChatGPT image tool; the TV makes no generation requests. Prompts and provenance are in [assets/themes](../assets/themes/README.md). Private device screenshots, settings, wallpapers and signing material are excluded from this repository.

Home-only video was checked again with these decorations: Art mode hides the entire UI and stops the QuietHome player, Right changes the still image, and OK restores Home and muted hardware playback. The short resumed-playback diagnostic showed zero dropped frames over 256 frames; it is not a general performance benchmark.

Original-key rollback to the 1.10 implementation and reapplication of 1.11 both passed in place. Fresh settings exports verified dock, widgets, gallery timing/framing, theme, appearance and video mode/filename. The recovery helper retries transient UI-read failures without guessing tap coordinates, verifies file hashes and device identity, retains backups and refuses later versions. No uninstall or data clear was used.
