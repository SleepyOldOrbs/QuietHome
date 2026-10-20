# Set up QuietHome with an AI agent

An agent with terminal/file access can download tools, inspect an authorised TV, build QuietHome, install it and collect evidence. A chat-only assistant can explain the steps but cannot operate your computer or TV. You still need the physical remote for developer-option menus, pairing approval and checks involving picture/sound.

This workflow installs a launcher alongside your existing apps. It does not require root, a bootloader unlock or replacement firmware.

## 1. Tell the agent what you want

Start with this prompt, adapting the bracketed details:

> Help me install QuietHome from https://github.com/SleepyOldOrbs/QuietHome on my own Android TV. My computer runs [Windows/macOS/Linux], and my TV is [brand/model, if known]. Read the repository README, AGENTS.md and installation guide first.
>
> Inspect what is already installed on this computer. Download missing ADB Platform Tools only from Google's official Android developer site. Use a dedicated local working folder and report the tool version.
>
> Guide me through enabling developer options and the debugging connection my TV supports. I will approve the connection on the physical TV. Ask for my TV's IP address if needed; identify the device before making changes.
>
> Back up the current Home component, enabled/disabled package states and QuietHome settings if it is already installed. Preserve the existing APK and signing compatibility where applicable. Ask which apps/features I use. Keep the manufacturer's launcher available and preserve TV tuning, HDMI/CEC, casting, voice and accessibility.
>
> Build and test QuietHome, install it alongside my apps, and try it before changing the default Home screen. Do not uninstall or clear data, root or flash the TV, or disable manufacturer packages as part of this installation. Give me a restore procedure for my actual device.
>
> Check each meaningful screen change with a screenshot when possible, and ask me to verify picture/sound and physical remote behaviour. Keep private device information, keys, screenshots, settings and wallpaper files out of public GitHub commits. Continue authorised reversible work without repeatedly asking for permission.

The agent should distinguish **inspection**, **a successful build**, **installation**, and **verified TV behaviour**. They are different milestones.

## 2. Enable developer options using the remote

On many TVs, start in **Settings → System → About** or **Settings → Device Preferences → About**. Locate **Android TV OS build** or **Build**, then press OK repeatedly (usually seven times) until the developer message appears. Return to settings and find **Developer options**.

Menu labels and availability vary by manufacturer and software version. If the screen differs, tell the agent what you see and have it consult your model's official documentation. Do not enable unrelated developer settings.

Record the original debugging settings, then enable the supported debugging option. It may be labelled **USB debugging**, **Network debugging**, or **Wireless debugging**. Merely enabling developer options does not mean network ADB is available.

Google documents the general process in [Configure on-device developer options](https://developer.android.com/studio/debug/dev-options).

## 3. Install ADB on the computer

Tell the agent:

> Check whether `adb` is already available and run `adb version`. If it is missing, open the official SDK Platform Tools download page, download the package for this operating system, and extract it into our tool folder. Use the executable's full path if necessary. Do not replace another working installation or reset a shared ADB server unnecessarily.

Official download page: [Android SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools).

Platform Tools contains ADB; the Android platform/build tools and JDK needed to compile QuietHome are separate dependencies described in the README. Downloading ADB does not install Android firmware on the TV.

The agent should record the download source and local file hash. A locally calculated hash is a record, not proof of authenticity unless compared with a trusted published checksum.

## 4. Connect and approve on the TV

Use the connection method the TV actually exposes. Keep computer and TV on the same trusted local network; do not forward ADB ports through the router.

**Pairing-based wireless debugging:** use the pairing IP/port displayed by the TV. The connection port may differ from the pairing port.

```text
adb pair TV_IP:PAIRING_PORT
adb connect TV_IP:CONNECTION_PORT
adb devices -l
```

Enter the short-lived pairing code in the local terminal when prompted. Not every Android TV version/model exposes this workflow.

**Manufacturer-provided network debugging:** if official model guidance confirms a network ADB listener, connect to its documented port:

```text
adb connect TV_IP:DEBUG_PORT
adb devices -l
```

Some TVs use port 5555, but do not assume that enabling USB debugging opens that port on every TV.

**USB debugging:** only use a supported device/USB connection and cable arrangement for that TV. A TV's ordinary USB host port is not automatically a debugging connection. The generic `adb tcpip` workflow requires an existing authorised connection; it cannot remotely switch on ADB on an unreachable device.

Accept the debugging prompt on the physical TV only when it corresponds to your computer. The agent cannot legitimately bypass that approval.

Google's [ADB guide](https://developer.android.com/tools/adb) explains connection and authorisation. For `unauthorized`, check the TV prompt. For `offline`, confirm it is awake, recheck the address and reconnect. If it is unreachable, fix the connection before attempting installation.

## 5. Inspect and back up before launcher changes

Ask the agent to save a private, timestamped record containing:

- Model, Android version, available storage and exact ADB device identifier.
- Current default Home component and package enable states.
- Existing QuietHome version, APK/signature information and exported settings, if present.
- Initial screenshot and the apps/features you want preserved.
- Commands needed to return to the original Home component.

These are configuration backups, not a complete firmware or streaming-account backup. ADB cannot necessarily read private application data; the agent must say what it actually saved.

Useful inspection commands are in [Installation and recovery](INSTALL.md). Use an explicit `-s YOUR_DEVICE` selector so commands do not target the wrong device.

## 6. Build, install and test

Tell the agent:

> Run the documented tests and build. Check the APK signature and device storage. If QuietHome is already installed, verify signing compatibility before any update; never uninstall it to bypass a signature mismatch. Install with `adb install -r`, launch QuietHome explicitly, and check it before changing the default Home app.

Then check together:

1. Arrow buttons, OK, long-OK, Back, Home and volume.
2. Opening favourite apps and returning Home.
3. Normal TV/HDMI picture and sound.
4. Any casting, CEC, voice, Bluetooth or accessibility features you use.
5. Artwork mode, wake-up without accidental launch, wallpaper changes and remembered selection.
6. Chosen weather location, news edition and widget visibility.
7. Saving/restoring settings and the documented route back to the stock launcher.

Protected video surfaces may produce blank screenshots. That is not proof that playback is broken or working; a person must check the actual TV.

## 7. Optional personalisation and cleanup

Ask for a specific change and evidence:

> Add these apps to the dock in this order: […]. Use my own images from [local folder], preserve the originals, check image dimensions and APK size, and keep them private. Set the weather to [town/country] and news to [edition]. Back up first, then verify the result.

System debloating is a separate, device-specific task. If you request it, require an explanation and recovery record for each exact package, and review any features it may affect. QuietHome does not need a blanket list of disabled Sony/Google services. “Telemetry disabled” requires evidence; a launcher change alone does not establish that.

When finished, retain the private backups and signing key. Turn off debugging or revoke the computer's authorisation when you no longer need remote access. QuietHome runs without an active ADB connection.

## Asking an agent to improve the project

> Work in the public QuietHome repository. Research the relevant Android APIs using primary documentation, implement one focused improvement, run the host tests and build, and use an emulator or explicitly authorised test device for UI checks. Preserve existing backups and preferences. Report what was and was not tested. Check staged files for private material, then publish to the GitHub repository I authorise. Do not deploy an experimental build to my everyday TV unless that is part of my request.

For repeated cycles, specify the number, scope and stopping conditions. Keep a short research/change/verification record for each cycle; eight commits alone do not demonstrate eight useful improvements.
