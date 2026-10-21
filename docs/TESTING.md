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

The eight-loop run used an Android TV API 31 x86 emulator at 1920 x 1080. Synthetic square images were used for framing/timing; the public repository remains image-free. See the [loop record](IMPROVEMENT-LOOPS.md) for actual checks and their limits.

## Before deployment to a household TV

Follow the [AI-assisted setup guide](AI-ASSISTED-SETUP.md): back up, verify signatures/storage, preserve the stock launcher and obtain the user's physical checks. Test actual TV/HDMI picture and sound, Home/arrows/OK/volume, streaming apps and any casting/CEC/voice/Bluetooth/accessibility features in use. Preserve the previous APK, settings and signing key for recovery. Do not use uninstall or data clear to resolve an installation problem.

## 1.6 preview validation

On 21 October 2026, host tests, the Android parser/settings suite and live BBC, Good News Network and Open-Meteo requests passed on an authorised Sony BRAVIA running Android 12. Good-news tests cover HTML cleanup, publisher-host isolation, distressing topics in titles and summaries, word boundaries and an entirely filtered feed. Gallery tests cover manual previous/next inverses at every position, including wrap boundaries, with 1, 2 and 122 images.

The signed private build was installed in place after saving the previous APK and settings. All 122 bundled wallpaper files matched the previous build byte for byte. Screenshots confirmed the Art mode button hides every overlay, Right changes the image, Left returns to an identical screenshot, and OK restores Home without launching an app. The good-news card, summaries and external-browser handoff were exercised on the TV. Existing dock entries and widget preferences survived; the news edition was changed explicitly in settings.

An in-place rollback to the prior source, followed by reapplication of 1.6, also passed on that TV. Exported dock, widget and gallery settings were verified after both operations; no uninstall or data clear was used.

These are ADB-driven checks. They do not establish compatibility with every physical remote, streaming service, HDMI source, Bluetooth accessory or casting setup. Private wallpapers, settings and device screenshots are excluded from this repository.
