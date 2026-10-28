# N80 — Cancel completed Shizuku bind-timeout jobs promptly

Date: 2026-10-28  
PR: #1 App Commander; keep Draft/open.

## Engineering question

Does the independent timeout coroutine remain scheduled for the full timeout window after a shared Shizuku bind waiter has already completed successfully or failed, and can completion cancel that timer without accidentally cancelling the shared request when one UI caller leaves?

## Open-source/API review

Continue using the existing official Shizuku API integration; no third-party implementation is copied and no dependency or permission is added. The current N78/N79 lifecycle design remains authoritative: the attempt-owned timer, not an individual caller, owns timeout cleanup; unbindUserService(..., remove=false) must not terminate the UserService.

## Change

- Keep one timeout job per bind-attempt waiter.
- Cancel and remove that job when the shared waiter completes successfully or exceptionally.
- Mark the timeout path before it completes the waiter, so its own completion callback cannot cancel the cleanup it is responsible for.
- Preserve the independent timeout when only an awaiting caller is cancelled.
- Remove completed/cancelled jobs from the registry so retries do not accumulate stale timer entries.

## Acceptance criteria

1. Successful connection promptly cancels the timer and does not unbind the live service.
2. Disconnect/failure promptly cancels the timer and still permits a fresh bind attempt.
3. Cancelling one waiter coroutine does not complete the shared waiter or cancel its independent timer.
4. The independent timeout still resets the current request and invokes cleanup exactly once.
5. :app:testDebugUnitTest :app:lintDebug pass on the exact source/test commit and final documentation head.
6. No APK packaging/signing/upload, new permissions/dependencies, root, Device Owner, merge, main write or auto-merge.
7. Xiaomi Pad 5/HyperOS Shizuku callback timing remains NOT TESTED.

## Evidence

Source/test SHA and CI: pending. Final docs-head recheck: pending.  
Google Drive LIVE sync must be marked SYNC FAILED if the Drive/Docs connector is unavailable; no sync is claimed without read-back verification.


## Source/test evidence — 2026-10-28

- Exact source + regression-test commit: `b006aaf4bc42e515e0b74d8eb1294d1b4bc1f9a9`.
- **PASS:** [source/JVM/Lint CI #40713617339](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40713617339), `:app:testDebugUnitTest :app:lintDebug --stacktrace`, BUILD SUCCESSFUL in 1m 58s.
- Regression coverage proves success and failure callbacks cancel the timer promptly, synchronous connection before timer registration creates no timer, and caller cancellation leaves the independent attempt timeout alive.
- Artifact query: **0**. No APK packaging/signing/upload.
- Xiaomi Pad 5/HyperOS, real Shizuku callbacks, and SAF/installer flows: **NOT TESTED**.
- Final documentation-head recheck: pending PR verification comment.
- Google Drive LIVE sync: **SYNC FAILED** — no Google Drive/Docs connector is exposed in this runtime; no read/write/read-back is claimed.
- No new permissions/dependencies, root, Device Owner, merge, main write or auto-merge.
