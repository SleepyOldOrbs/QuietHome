# Eight improvement loops

These previews evolve the public source. They are not automatically installed on the original household TV. Hardware playback, HDMI/CEC, audio and a physical remote still need real-device checks before promotion to a stable release.

The [AI-assisted setup guide](AI-ASSISTED-SETUP.md) was published separately before these loops.

## 1. Discard obsolete widget responses

Research: Android's [activity lifecycle](https://developer.android.com/guide/components/activities/activity-lifecycle) and [Java worker-thread guidance](https://developer.android.com/develop/background-work/background-tasks/asynchronous/java-threads).

Finding: a previous town request could overwrite a newly selected location with the same name. Paused or reconfigured widgets could also accept old replies. Feed and town-search generations now invalidate pending results on pause, visibility/source/location changes and restoration. The worker remains bounded to one executor; an obsolete completion triggers a fresh request only if Home is active.

Validation: offline regression scenarios cover same-label changes, switching back, pausing and clearing. Host tests and the Android APK build passed. Physical network races on the household TV were not exercised.

## 2. Opt-in feeds and privacy controls

Research: Android's [permission minimisation guidance](https://developer.android.com/privacy-and-security/minimize-permission-requests), including user-entered locations instead of GPS.

Weather/news now default off; explicit prior choices survive upgrades. The widget chooser identifies providers. New actions clear feed caches and turn feeds off, or forget the town/coordinates and disable weather. Existing exported/OS backups are explicitly outside these controls. Queued town searches also check lifecycle state before contacting the provider.

Validation: host tests and APK build passed. Installed on an isolated Android TV API 31 emulator; inspected fresh-home and widget-choice screenshots. Enabled BBC News, used Clear and turn off, then reopened the chooser and verified both online widgets off. A message/list conflict found in the first UI check was corrected before publication. This is not a network packet-capture audit.
