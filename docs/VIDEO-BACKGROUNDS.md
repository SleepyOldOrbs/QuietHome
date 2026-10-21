# Local video backgrounds

Version 1.9 adds silent, looping local MP4 backgrounds. Playback defaults to **Off**. Choose **Home and Art mode**, **Art mode only** or **Home screen only** in **Home options → Wallpaper settings → Video backgrounds → Playback mode**.

Use one H.264 video track, at most 1920 × 1080, 30 fps and 80 MiB. Start with 1280 × 720 at 24 fps for an older TV. Hardware capabilities vary; these limits are a conservative input policy, not a playback guarantee. Videos fill the screen with centred cropping. QuietHome mutes all audio and does not request audio focus or a wake lock.

**Home screen only** (1.10) plays behind the dock and widgets, then releases the player when Art mode opens so your still-picture collection can be browsed. Returning to the Home controls resumes video. Existing saved mode values are unchanged.

## Copy a video

Keep your original file on your computer. Install FFmpeg/ffprobe and Android platform tools, then run:

```powershell
pwsh -File ./scripts/Copy-Video.ps1 -Path ./Forest.mp4 -DeviceSerial YOUR_DEVICE
```

Pass `-Ffprobe /path/to/ffprobe` if it is not on PATH. The helper verifies format, checks free storage with a 100 MiB reserve, copies to a temporary filename, verifies SHA-256, and renames it into the library. It does not enable playback or replace an unrelated file. A content-hash suffix prevents ordinary filename collisions. Repeating the same copy verifies and reuses the existing file.

On the TV, open **Video backgrounds → Choose video**, select the file, then choose a playback mode. Copying requires ADB; there is no on-TV download or file importer. No video is included in the public build.

To create a smaller, silent compatible copy (the original is retained):

```powershell
ffmpeg -n -i ./Original.mp4 -vf "scale=1280:720:force_original_aspect_ratio=decrease:force_divisible_by=2,fps=24" -an -c:v libx264 -pix_fmt yuv420p -crf 24 -movflags +faststart ./Forest.mp4
```

This command scales the picture within 1280 × 720. It does not guarantee a file below 80 MiB; use a shorter clip or a higher CRF value if needed. Only copy media you have permission to use.

## Behaviour and recovery

- The still wallpaper stays underneath until the first decoded video frame. Missing, invalid or unplayable files fall back to still pictures. Toggle playback or leave and return to Home to retry.
- One decoder runs while the selected mode is active. Leaving Home releases it; returning prepares it again from the beginning. No video runs as an Android background service.
- In Art mode, Left/Right switches to browsing still pictures for that Art session. Returning to controls resets this temporary override. In Art-only mode the next Art session starts video again.
- Wallpaper rotation pauses while video is active. Cached wallpaper frost is bypassed while video is visible so panels retain their translucent tint without displaying a frozen image over the video. This is not a live blur effect.
- Choose **Off — still wallpapers** to reverse the feature immediately. Themes, dock and picture collection are retained.

Files live in `/sdcard/Android/data/local.quiethome/files/videos/`. Updates preserve them. Settings exports include the selected filename and playback mode, **not the video bytes**. Copy this directory separately when making a recovery kit:

```powershell
adb -s YOUR_DEVICE pull /sdcard/Android/data/local.quiethome/files/videos ./video-backup
```

Older settings backups remain valid and restore video playback to Off. A settings backup referencing a missing video uses still pictures. Android app-data removal/uninstallation can remove app-specific media, so keep the computer originals. Do not use uninstall or data clearing to downgrade.

## Why not a YouTube link?

YouTube's official embedded-player rules prohibit overlays in front of any part of the player. A launcher dock and widgets would be overlays. Its player also carries provider behaviour such as branding and possible adverts. QuietHome therefore does not embed, extract, download or stream YouTube backgrounds. Open YouTube links in a separate TV app/browser for ordinary viewing.

Primary references: [YouTube player overlays and frames](https://developers.google.com/youtube/terms/required-minimum-functionality#overlays-and-frames), [YouTube developer policies](https://developers.google.com/youtube/terms/developer-policies), [Android supported media formats](https://developer.android.com/media/platform/supported-formats), [MediaPlayer lifecycle and resource release](https://developer.android.com/media/platform/mediaplayer/state-resources), [TextureView](https://developer.android.com/reference/android/view/TextureView).

No new permissions, network destinations, analytics or media uploads are added. Decoding video consumes more power and GPU/decoder resources than displaying a still picture; Art-only is the conservative setting.
