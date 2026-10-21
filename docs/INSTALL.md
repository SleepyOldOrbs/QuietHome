# Installation and recovery

QuietHome is a launcher app, not a replacement Android firmware. No root access or manufacturer-package changes are required by this project.

## Before installing

1. Keep the manufacturer's launcher installed and enabled.
2. Enable developer options and ADB using your TV's own settings. Authorise your own computer on the TV.
3. Use `adb devices` to identify the device. Every example uses `YOUR_DEVICE`; substitute the identifier yourself.
4. Record the current default Home component:

```powershell
adb -s YOUR_DEVICE shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

Save the returned package/activity component somewhere outside this public repository. Do not guess another model's launcher package.

## Try QuietHome

Build the app, then install and launch it explicitly:

```powershell
adb -s YOUR_DEVICE install -r ./build/QuietHome-debug.apk
adb -s YOUR_DEVICE shell am start -n local.quiethome/.HomeActivity
```

If Android reports a signature mismatch, stop and retain your existing app/data. An APK signed with a new development key cannot update an installation signed by another key. Do not uninstall an existing launcher just to bypass this error.

Use Edit dock to select installed TV apps. The initial favourites include YouTube, Sony TV, Jellyfin and Netflix where available. An empty initial dock on another device can be populated through Edit dock or All apps.

Check navigation, Home, your normal streaming apps and TV input behaviour. Weather and news have independent switches under Home options. Set the weather town with the remote; no GPS is used.

## Make it the Home app

If your device exposes a Default Home app setting, choose Quiet Home there. On devices supporting the package-manager command, the equivalent is:

```powershell
adb -s YOUR_DEVICE shell cmd package set-home-activity --user 0 local.quiethome/.HomeActivity
adb -s YOUR_DEVICE shell input keyevent 3
```

Support and permissions vary by manufacturer. If the command is rejected, do not disable system packages to force it; return to the TV's supported Home settings.

## Restore the previous Home app

Choose the manufacturer's Home app in settings, or use the exact component recorded before installation:

```powershell
adb -s YOUR_DEVICE shell cmd package set-home-activity --user 0 ORIGINAL_PACKAGE/ORIGINAL_ACTIVITY
adb -s YOUR_DEVICE shell input keyevent 3
```

The original launcher must still be enabled. QuietHome can remain installed and its settings are retained. Restoring the default Home app does not require uninstalling or clearing either application.

## Save QuietHome settings

Use **Home options → Save backup**. To restore, use **Home options → Restore backup**; the app validates the entire snapshot and saves the current settings before applying it. Schema-1 backups from older versions remain supported. Empty docks and favourites not installed on this device are valid; unavailable entries retain their order and appear when the app becomes available.

New optional fields cover temperature units, clock format, wallpaper timing and framing. Older widget/gallery snapshots use Celsius, system clock format, one-minute rotation and fill framing for fields they do not contain. Missing whole widget/gallery sections leave that part of the current configuration in place.

Version 1.7 adds theme, widget layout and card positions. A backup without a theme uses Midnight; an older widget section without layout/positions uses standard cards with news on the left. Saved visibility choices survive clock-only mode. Unknown or incorrectly typed values are rejected before preferences are changed.

Version 1.8 adds an optional `appearance` object with RGB accent/tint, transparency (0-100), and border density (0-2), plus five new theme IDs. These values are validated before a restore changes anything. Older backups load the saved theme with its default appearance. Older app versions cannot restore the new theme IDs: keep a pre-update settings export with your compatible, original-key rollback APK.

On typical devices the files are under:

```text
/sdcard/Android/data/local.quiethome/files/backups/
```

Copy them to your own private storage if needed. They contain app package names, wallpaper filenames/shuffle state and widget settings including the selected town and coordinates. They do not contain the wallpaper pixels, other apps' account data or a firmware image.

Avoid committing these backups or device screenshots to a public issue. Keep your signing key so future builds can update the same app in place.

## Optional video backgrounds

See [local video setup](VIDEO-BACKGROUNDS.md) for the verified copy helper, input limits and recovery. Videos are separate from the APK and settings export. Back up the videos directory alongside your settings. Old backups restore video playback to Off.

Version 1.10 adds theme ID 8 (Summertime) and video mode 3 (Home screen only). IDs 0–7 for themes and 0–2 for video modes keep their earlier meanings. Older app versions cannot restore the new values: use a pre-update snapshot with the matching rollback APK. The generated decorative asset is bundled in the APK; local video files are still backed up separately.
