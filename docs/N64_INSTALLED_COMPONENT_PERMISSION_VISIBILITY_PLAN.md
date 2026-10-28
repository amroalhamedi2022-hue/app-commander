# N64 — Preserve installed component permission gates in App Details

## Engineering question
Does App Commander retain and display the manifest permission required by installed Activities, Services, and Receivers, while only exposing the existing launch action for Activities and keeping permission checks fail-closed?

## Source research
- Android's `ActivityInfo` represents both activities and receivers and carries manifest metadata: https://developer.android.com/reference/android/content/pm/ActivityInfo
- Android's `ServiceInfo` carries service metadata: https://developer.android.com/reference/android/content/pm/ServiceInfo
- Android's manifest reference explains that component declarations can require permissions: https://developer.android.com/guide/topics/manifest/manifest-intro
- No third-party implementation was copied and no dependency, manifest permission, or elevated command was added.

## Implementation
- Add a pure `ComponentEntryMapper` to map permission gate, exported state, effective enabled state, and caller-held permission consistently for all installed component kinds.
- Use it for Activities, Services, and Receivers in `AppRepository.getAppDetails`; preserve the Activity launch-time recheck and keep Services/Receivers non-launchable from this list.
- Show the declared permission in each Liquid-style component row and use a neutral caller-permission warning instead of Activity-specific wording.
- Add JVM tests for stable ordering, missing/held/unheld permissions, non-exported components, null inventories, and repository/UI wiring.

## Acceptance criteria
- Activities, Services, and Receivers all retain `requiredPermission` and computed `callerHasPermission`.
- Only Activities receive the launch callback; Service/Receiver rows remain management-only.
- Existing component enable/disable safety checks and critical-package protections remain unchanged.
- `:app:testDebugUnitTest :app:lintDebug` pass on source/test and final docs-updated PR head; no APK packaging or artifact upload.
- Xiaomi Pad 5/HyperOS UI and actual installed-package behavior: NOT TESTED.

## Evidence
- Source/test SHA: PENDING.
- JVM + Android Lint CI: PENDING.
- APK artifacts: 0; no APK build requested.
