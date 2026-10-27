# N48 — App-detail loading resilience

**Date:** 2026-10-27  
**PR:** #1 — App Commander; keep Draft, no merge.  
**Status:** source/JVM/lint CI PASS; docs-head recheck pending.

## Engineering question

If an installed app is removed, updated, or temporarily unreadable between inventory rendering and the detail query, does the detail sheet remain stuck on an indefinite spinner? The current UI converted `loadDetails` failures to `null` without a loading/error state, so a failure was indistinguishable from a request that never completed. Repeated loads also needed latest-request-wins protection.

## Acceptance criteria

1. Initial detail-query failure stops the spinner and shows a clear, retryable Arabic message.
2. A failed refresh preserves the last successful detail snapshot rather than erasing it.
3. Out-of-order detail successes, failures, and cancellations cannot overwrite or stop a newer request.
4. Successful loading clears the error; cancellation is not turned into a false failure message.
5. The real Compose detail pane uses the state machine for initial load, retry, and post-command refresh.
6. `:app:testDebugUnitTest :app:lintDebug` passes on the exact source/test SHA, with zero APK artifacts.
7. No APK packaging, signing, upload, merge, main write, root, Device Owner, paid API, or auto-merge.

## Implementation

- Added `AppDetailsLoadState`, a pure latest-request-wins state machine with success, failure, retry, and cancellation semantics.
- Wired the actual detail sheet to this state machine for first load, explicit retry, and post-command refresh.
- Replaced the infinite-spinner-on-error behavior with a retryable error card; previously loaded details remain visible when a refresh fails.
- Added five JVM tests for initial failure, preserved snapshot, stale result rejection, retry/cancel behavior, and stale cancellation.

## Source/license review

This is a focused change to existing project state/UI code. No third-party implementation or dependency was copied or added; no permissions or privileged operations were broadened.

## Verified result

- **PASS — exact source/test SHA:** `20c0a63b2ae12145f822ba2f9e7ca18d91019c02`.
- **PASS — source/JVM/lint CI:** [run #40491926201](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40491926201), `:app:testDebugUnitTest :app:lintDebug`, BUILD SUCCESSFUL in 1m 10s; 36 actionable tasks.
- **Artifacts: 0 confirmed**; no APK packaging, signing or upload.
- Xiaomi Pad 5/HyperOS UI interaction, PackageManager races on-device, SAF, and Shizuku remain **NOT TESTED**.
- Google Drive LIVE sync: **SYNC FAILED** — no Drive/Docs connector is exposed in this runtime.
