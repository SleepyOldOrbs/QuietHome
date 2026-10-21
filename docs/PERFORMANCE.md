# Performance notes: 1.7 preview

Measured on one authorised Sony BRAVIA running Android 12, with a 1920 × 1080 launcher surface, 122 bundled WebP wallpapers and ten dock favourites, on 21 October 2026. This compares the installed 1.6 preview with 1.7 using Frosted Glass and compact widgets. It is a small on-device sample, not a guarantee for other TVs.

## Home and picture changes

| Measurement | 1.6 | 1.7 | Observation |
|---|---:|---:|---|
| Cold Home start, median of 7 | 787 ms | 565 ms | 28% lower |
| Warm return from Android Settings, median of 7 | 85 ms | 74 ms | 13% lower; small absolute change |
| Cached-neighbour picture change proxy, median of 12 | 150.5 ms | 64.5 ms | 57% lower |

Cold samples (ms): 1.6 `1289, 688, 705, 787, 952, 712, 813`; 1.7 `638, 463, 565, 583, 575, 518, 531`. Warm samples: 1.6 `95, 98, 81, 65, 85, 87, 85`; 1.7 `80, 77, 68, 74, 73, 75, 70`.

Startup uses `am start -W` TotalTime after force-stopping only the launcher, or after opening Android Settings for warm returns. Two seconds separate samples. The number describes Activity startup; it does not wait for every wallpaper or network result. Earlier and later samples were sequential, with no control over other TV processes, thermal state or OS caches.

The picture proxy measures device log timestamps from a marker immediately before ADB input injection to the launcher's image-applied log. Both versions alternated between the same two images twelve times, with 700 ms between changes. It includes ADB/input overhead and ends before physical panel presentation. **It is not remote-button-to-photon latency.** Cached neighbours benefit most; a new, uncached image still needs decoding.

## Frame rendering

An initial burst-key sample produced unequal frame counts (53 before, 19 after), so it is not used to compare smoothness. The controlled repeat starts on the first dock tile and sends Right, Right, Left, Left twelve times, with 200 ms between individual inputs.

| Controlled dock test | 1.6 | 1.7 Frosted Glass |
|---|---:|---:|
| Frames rendered | 432 | 432 |
| 95th percentile frame time | 5 ms | 8 ms |
| 99th percentile frame time | 6 ms | 18 ms |
| Janky frames | 0 | 1 (0.23%) |

Glass has a measurable drawing cost. The 95th percentile remains below the approximately 16.7 ms budget at 60 Hz, but this is **not a dock-rendering speed improvement**. The focus animation duration was not shortened to manufacture a latency claim.

## Memory

A separate controlled run restored the same image pair, restarted the launcher, waited three seconds, sampled `dumpsys meminfo`, then alternated pictures twelve times in Art mode. Settings restoration happened before the restart in both versions.

| Process memory snapshot | 1.6 | 1.7 Frosted Glass |
|---|---:|---:|
| Home PSS | 43.9 MiB | 49.4 MiB |
| After artwork browsing PSS | 67.3 MiB | 60.4 MiB |
| Home RSS | 124.2 MiB | 133.7 MiB |
| After artwork browsing RSS | 149.8 MiB | 145.6 MiB |

The new cache costs about 5.5 MiB more PSS in the initial Home snapshot, while this artwork-browsing snapshot was about 6.9 MiB lower. Garbage collection, shared pages and other TV activity affect these readings. These are snapshots, not peak-memory or endurance measurements; there is no universal RAM-saving claim. An earlier sequential test included a settings restore only in the newer artwork phase, so its artwork-memory readings are retained privately but excluded from this comparison.

## What changed

- App-list refresh runs on a worker after resume. Unchanged entries reuse their metadata and loaded icons; an unchanged list does not rebuild the dock. Initial discovery still runs during Activity creation, and icons are loaded when dock entries need them.
- Image decoding uses a single worker with a latest-request mailbox. Rapid input advances the gallery position immediately; obsolete decoded results cannot replace the latest request. Pause, restore and destruction invalidate pending requests.
- The current image and its neighbours use a three-entry cache with a 14 MiB retained-cache budget. Displayed images, transient decoding buffers and GPU allocations are additional memory; this is not a whole-process memory limit.
- Frost is prepared once per decoded picture, using an image no larger than 160 pixels on either axis. The theme paints that cached image under a dark tint. There is no continuous full-screen blur or cross-window blur API.
- Low-memory notifications clear the retained image cache. Artwork mode still lets the TV manage sleep and panel protection.

Android's [slow-rendering guidance](https://developer.android.com/topic/performance/issues/render) informed the frame measurements. [Window-blur documentation](https://source.android.com/docs/core/display/window-blurs) describes device and GPU constraints; this implementation uses cached image preparation instead.

Raw device logs and private screenshots are kept with the deployment evidence, outside this public repository.
