# Contributing

Small, understandable changes are welcome. QuietHome aims to stay responsive on modest TV hardware and usable entirely with a directional remote.

Before a pull request:

- Run `pwsh -File ./test.ps1` and `pwsh -File ./build.ps1`.
- For UI changes, state which device or emulator, Android version and display configuration you tested.
- Check directional focus, OK/long-OK, Back, artwork wake-up and returning from an app.
- Keep network and image work off the main thread; avoid background polling while Home is paused.
- Preserve compatibility with existing settings backups and avoid destructive migrations.
- Do not commit APKs, signing keys, account/device details, private screenshots, exported settings or personal image collections.
- Treat feed content as untrusted data. Do not render remote HTML or relax endpoint validation casually.

CI currently covers host shuffle/state tests and APK compilation/signature verification. Live feed/parser tests are available through `test.ps1 -DeviceSerial YOUR_DEVICE` and are opt-in.

Please describe what changed, why, and the checks actually performed. Do not claim device-wide compatibility from a successful build.

Contributions are accepted under the repository's MIT licence.
