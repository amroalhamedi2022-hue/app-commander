# N35 — Resilient app-inventory refresh state

**Date:** 2026-10-27  
**PR:** #1 — App Commander (keep Draft; no merge)

## Engineering question

Can a failed, cancelled, or overlapping PackageManager inventory refresh leave the Liquid UI spinning indefinitely, erase the last usable inventory, or let an older request overwrite a newer result?

Source inspection alone cannot establish the asynchronous state transitions. N35 adds a pure JVM-tested refresh reducer and validates the actual Compose screen compilation with the existing source-only workflow.

## Acceptance criteria

1. Each refresh starts a new generation; only the currently active generation may complete, fail, or cancel the visible state.
2. A failed refresh ends loading, retains the last successful app list, and exposes a retryable error instead of crashing the coroutine or presenting a false empty inventory.
3. Cancellation is rethrown to coroutine structured-concurrency while clearing the spinner only if that cancelled request is still current.
4. A successful empty inventory is distinguishable from a failed inventory load.
5. The dashboard and Apps tab show a retry action when inventory loading fails; no raw exception text or paths are shown to the user.
6. JVM tests cover latest-request-wins, stale success/failure isolation, failure retention, cancellation, and successful-empty semantics.
7. Run `:app:testDebugUnitTest :app:lintDebug --stacktrace` on the exact source/test commit and verify the subsequent PR head. No APK packaging, signing, artifact upload, release, merge, main write, root, or Device Owner.

## Scope and safety

- No new dependency and no Android permission change.
- Does not change Shizuku authority, APK inspection, SAF document grants, or install behavior.
- No third-party implementation copied; this is a small local state reducer.
- Physical Xiaomi Pad 5 / HyperOS behavior remains NOT TESTED.

## Implementation

- Added `InventoryRefreshState`, a pure immutable reducer with a monotonically increasing request generation.
- Only the latest request can replace inventory or error/loading state; stale success/failure/cancel results are ignored.
- Failures preserve the last successful inventory, clear the spinner, and display a retryable generic Arabic error on the dashboard and Apps tab.
- Cancellation clears the current spinner without inventing an error and is rethrown to preserve coroutine cancellation semantics.
- Added five JVM tests covering failure retention, latest-request-wins, stale completion isolation, cancellation, successful-empty semantics, and retry generation reset.

## Exact source/test CI evidence — PASS

- **Source/test commit SHA:** `26d894ae6a031e33457d65c1a381eb119f7788ea`.
- **UI source blob:** `b5c12fbf1798be8a09114a63a7c75b3ef54a8643`.
- **State reducer blob:** `66208e273e4a1c861649b1b58f6e754f972f4dc9`.
- **New test blob:** `2fc8d4b2f2464d534a0ae94d1b6d94326d965180`.
- **PASS:** [GitHub Actions run #40415628864](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40415628864), job `source-checks`; actual `:app:testDebugUnitTest` and `:app:lintDebug` completed, log **BUILD SUCCESSFUL in 1m 37s**, 36 actionable tasks executed.
- Artifacts: **0**. The workflow did not run APK packaging/signing/upload.
- The CI warnings were existing Kotlin test-source warnings in `ApkInstallSnapshotTest.kt` and `PrivilegedProcessRunnerTest.kt`; they did not fail tests or lint.

## Remaining acceptance

- **NOT TESTED:** Xiaomi Pad 5 / HyperOS runtime, PackageManager behavior under real permission/provider failure, visual retry interaction, Shizuku device operations, APK signature authenticity, and malware detection.
- Source/JVM PASS is not a device PASS.
- PR #1 remains open Draft. No merge, main write, auto-merge, root, Device Owner, APK build, or release.
- **Google Drive:** SYNC FAILED — this runtime has no Google Drive/Docs connector. LIVE handoff/index were not read or written; folder metadata was not verified.
- A docs-updated PR-head recheck will be recorded separately; do not transfer the source/test result to a later head without querying its CI.
