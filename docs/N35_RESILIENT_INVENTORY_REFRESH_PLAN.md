# N35 — Resilient app-inventory refresh state

**Date:** 2026-10-27  
**PR:** #1 — App Commander (keep Draft; no merge)

## Engineering question

Can a failed, cancelled, or overlapping PackageManager inventory refresh leave the Liquid UI spinning indefinitely, erase the last usable inventory, or let an older request overwrite a newer result?

Source inspection alone cannot establish the asynchronous state transitions. N35 therefore adds a pure JVM-tested refresh reducer and validates the actual Compose screen compilation with the existing source-only workflow.

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
- Physical Xiaomi Pad 5 / HyperOS behavior remains NOT TESTED until a device is available.

## Evidence

- Initial state: **PENDING** — plan is recorded before source CI.
- CI/artifacts/SHA will be appended after the source-only run completes.
- Google Drive LIVE handoff/index sync is separate and must not be claimed unless read/write/read-back and folder metadata verification succeed.
