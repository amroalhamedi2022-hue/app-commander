# N81 — Fail closed when the Shizuku timeout scope is cancelled

Date: 2026-10-28  
PR: #1 App Commander; keep Draft/open.

## Engineering question

If the parent CoroutineScope is already cancelled when a bind timeout job is scheduled, can the lazy timeout job fail to start and leave the shared Shizuku waiter permanently in the binding state?

## Why source inspection alone is insufficient

The failure depends on coroutine Job lifecycle rather than a Shizuku device callback. A JVM regression must cancel the parent scope before scheduling, then verify waiter completion, cleanup count, binding-state reset, and retryability.

## Production change

After starting the lazy timeout job, detect a cancelled job that never entered its body. If the exact attempt is still current, reset it through the existing attempt-owned timeout transition, remove its registry entry, and invoke the same cleanup callback once. If a success/failure callback already won the race, the identity guard prevents stale timeout cleanup.

No third-party implementation, dependency, permission, root, or Device Owner path is added.

## Acceptance criteria

1. A cancelled parent scope cannot leave a pending shared waiter indefinitely incomplete.
2. The current attempt exits binding state and completes exceptionally with the existing timeout failure.
3. Cleanup is invoked exactly once and a later bind attempt can start.
4. Successful connection, disconnect, owner cancellation, and ordinary timeout regressions remain green.
5. `:app:testDebugUnitTest :app:lintDebug` pass on the source/test commit and final documentation head.
6. No APK packaging/signing/upload, merge, main write, or auto-merge.
7. Xiaomi Pad 5/HyperOS Shizuku callback behavior remains NOT TESTED.

## Evidence

## Verification — 2026-10-28

- Exact production + regression-test commit: `f614afa7f23688498ad0e5aee52a1f307608edb5`.
- **PASS:** source/JVM/Lint CI [#40722107650](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40722107650), `:app:testDebugUnitTest :app:lintDebug --stacktrace`, BUILD SUCCESSFUL in 1m 43s.
- **PASS:** artifact query returned 0; no APK packaging/signing/upload.
- Regression cancels the timeout parent scope before scheduling and verifies the shared waiter completes exceptionally, binding state resets, cleanup executes exactly once, and a subsequent attempt can bind.
- Xiaomi Pad 5/HyperOS Shizuku callbacks and real SAF/installer behavior: **NOT TESTED**.
- No new permission/dependency, root, Device Owner, merge, main write, or auto-merge.
- Google Drive LIVE sync: **SYNC FAILED** — no Google Drive/Docs connector is exposed in this runtime; no LIVE read/write/read-back or folder metadata verification was possible.
