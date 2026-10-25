# Power and Restart

The two buttons sit at the right of the bottom controls. **Power** puts a compatible Sony BRAVIA into standby, like its remote. **Restart** asks Sony to reboot the television. Both present a confirmation with Cancel focused. The buttons disappear with the rest of the interface in Art mode.

## First use

1. With the TV owner's agreement, open Sony's **Network and Internet → Home network setup → IP control**. Record the current settings first. Set **Authentication → Normal and Pre-Shared Key**; retain an existing control password if other devices use it, or create a strong random password. Keep Simple IP control and Remote start at their existing settings.
2. Choose either QuietHome button, enter that TV control password and select **Connect**. This is separate from Wi-Fi and Google passwords. QuietHome checks it using an authenticated, read-only request before saving.
3. Connection setup does not turn off or restart anything. Choose Power or Restart again, then confirm the action.

On the tested Sony firmware, its normal IP control menu entry crashed because its settings-page address was missing. The existing settings page could still be opened through Android's exported settings action with the URI supplied:

```powershell
adb -s YOUR_TV shell am start -a android.settings.SLICE_SETTINGS --es slice_uri content://com.sony.dtv.b2b.ipcontrolsettings.slice/ip_control_key
```

This opens Sony's settings; it does not change a setting or bypass verification. Menu names and this Sony-specific URI may differ on other firmware. Do not disable authentication or overwrite another controller's password.

Keep the remote available to turn the TV back on. Standby is not a mains disconnect. A restart interrupts playback, recordings and other TV activity; choose a time when nobody needs the television.

## Implementation and compatibility

QuietHome sends Sony's documented [`setPowerStatus(false)` and `requestReboot` commands](https://pro-bravia.sony.net/remote-display-control/rest-api/) to the TV's loopback service with its control password in `X-Auth-PSK`. Sony's normal authentication stays available. No internet server, PC, ADB connection, root access or accessibility service is required for everyday use. Code pairing was investigated but is not offered: the tested firmware cancels its code when another app opens, making same-TV entry unreliable.

Support depends on the model and firmware. The buttons show an explanation on other manufacturers and Android versions below 7. A rejected command is reported rather than falling back to undocumented broadcasts, privileged permissions or an external computer. Not every Sony model supports this service or these commands. Initial development targets a BRAVIA running Android 12; see [actual validation](TESTING.md).

Only `127.0.0.1` is permitted to use HTTP, since Sony's local service has no TLS endpoint. Public news/weather still require HTTPS. The control password stays in app-private, backup-excluded storage. See [privacy](PRIVACY.md).

## Recovery

Cancel or Back leaves the TV on. Commands are never retried automatically. If the TV disappears before responding, check its screen before trying again.

If authentication stops working, choose either button, then **Connection → Update password**. **Forget connection** deletes QuietHome's copy. To undo a setup that enabled password control solely for QuietHome, restore Sony's recorded original authentication mode. On the tested TV, restoring **Normal** disables password authentication; Sony's editor did not reliably accept clearing the key, so the recovery kit retains that inactive key for reapplication. This restores the original authentication behaviour, not the original empty key field. Do not change a shared control password used by other devices without their owner's agreement.

Rolling back the launcher retains normal TV functions and other app data. Keep the previous APK, its signing key and a fresh settings export before upgrading. Android may reject a lower version code: prepare an original-key rollback with a compatible version code, and verify its code/assets match the saved previous APK. Never uninstall to bypass a signature or downgrade error.
