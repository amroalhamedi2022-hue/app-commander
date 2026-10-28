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
- Final source/test head: `459a8e428837dd55eebff976339b5a8807cfe67d`.
- **PASS:** CI #40612329865 ran `:app:testDebugUnitTest :app:lintDebug --stacktrace`; `BUILD SUCCESSFUL in 1m 18s`, 36 tasks. Artifact query: 0.
- Failure/fix history: CI #40611844665 exposed Kotlin's non-reified `Array<T>.orEmpty()` limitation; changed to `components?.asList().orEmpty()`. CI #40612076457 then caught a stale source-contract assertion after the component mapper refactor; updated `ActivityLaunchSafetyTest` in the final source/test head.
- The final docs-updated PR head is independently checked by the workflow attached to the resulting PR head; no APK packaging is involved.
- APK artifacts: 0; no APK build requested.
