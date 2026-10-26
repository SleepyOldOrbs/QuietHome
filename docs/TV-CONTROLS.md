# TV controls

Version 1.13 adds a bottom-right **TV controls** menu. It uses the same Sony control connection as Power and Restart; see [connection setup](POWER-CONTROLS.md). Controls are requested on demand. Home does not continuously poll the TV.

## Screen off — keep listening

Start music in an app that supports background playback, return Home, and choose **TV controls → Screen off — keep listening**. Confirm **Screen off**. The TV stays running while Sony switches off the picture. This is different from standby.

Press **Home** on the physical remote to restore the picture. While QuietHome is foreground, its navigation buttons also request restoration without launching a tile. Volume/media keys keep their normal actions. QuietHome records the previous Sony power-saving mode before switching the picture off. Home checks that private recovery record and restores it, including after the launcher process restarts. A newer manual setting is respected. An uncertain command is never automatically repeated.

Playback depends on the music app, its connection and its background-playback support. The launcher does not keep the TV awake indefinitely or override Sony's idle/energy settings. Preserve the recovery kit before upgrading or rolling back; recover the picture before installing an older launcher that cannot read this record.

## HDMI favourites

Choose **TV controls → Pin HDMI inputs**, or **Edit dock**, and select the HDMI entries alongside your apps. They can be reordered or removed with the existing long-OK controls. Names use Sony's custom input labels where available. Connection status is refreshed when Home resumes; it is not a promise that the source is powered on or producing a picture.

Disconnected entries keep their saved positions. Opening one asks for confirmation. A temporarily unavailable Sony service retains the current process's cached entries, and saved input identifiers still have a generic HDMI fallback after a restart. No input switches automatically.

Input favourites use a reserved `local.quiethome.input.hdmiN` identifier, kept distinct from ordinary app entries. Existing schema-1 backups remain valid. Older launchers ignore unavailable input tiles while retaining their stored identifiers; do not mistake them for installed packages.

## Art brightness

Choose **TV controls → Art brightness**, or **Home options → Artwork mode → Art brightness**. Original is the default; the other presets dim the displayed artwork by 15%, 30% or 50%.

This is a black translucent layer over QuietHome's still/video artwork, visible only in Art mode. It disappears with the launcher or when Art mode exits, including after a crash. It does not alter Sony's backlight, picture calibration or other apps' settings, and no reduction in energy use is claimed. This choice avoids leaving a temporary global picture setting behind after an interrupted launcher session.

The preference is included in settings exports. Restoring an older backup selects Original. Sleep timers and temporary picture-off recovery records are deliberately excluded from exports and Android automatic backups.

## Sound

The sound panel reads the current speaker volume and mute state. Volume up/down change one step within the range reported by the TV; the state is read again before each change, so a physical-remote adjustment is not overwritten by an old slider value. The panel is refreshed after each action.

Speaker/sound-system switching is not offered yet. The tested TV reported speaker output and no connected HDMI devices; advertising a generic output-setting API does not establish that another usable output exists.

## Sleep timer

Choose Off, 30, 60 or 90 minutes, then confirm. The TV enters standby at expiry even while another app is open. Home's TV controls button shows the remaining whole minutes, refreshed at least once a minute; opening the menu reads the current remainder. Use **Cancel timer** to stop it.

Android 12 and later require **Alarms & reminders** access for accurate timing. If it is unavailable, QuietHome offers the relevant Android settings page; return and choose a duration after enabling it. No timer is started before that access is available. Other controls do not require this permission.

The timer uses one exact Android alarm and a non-exported broadcast receiver, with no permanent background service or continuous polling. A private token identifies the current timer. The receiver consumes it before contacting Sony, rejects old/duplicate tokens, and does not retry an uncertain standby command. Restarting the TV cancels the timer; elapsed time and boot identity prevent old saved timers firing after reboot. A timer delivered more than five minutes late is discarded. If the TV is already in standby, it is left there. Android force-stop, revoked alarm access or an unavailable Sony service can prevent delivery; Home clears invalid saved timers.

Restoring a settings backup cancels the active timer. Timers are not transferred to another device or revived by an APK rollback/reapply. The recovery helper cancels the timer before removing this implementation.

## References

- [Sony API reference](https://pro-bravia.sony.net/remote-display-control/rest-api/reference/): power-saving mode, input status/playback, volume and mute.
- [Android alarms](https://developer.android.com/develop/background-work/services/alarms): scheduling outside an Activity's lifetime and exact-alarm access.
- [Validation record](TESTING.md) distinguishes device tests, emulator tests and limitations.
