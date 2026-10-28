# N84 — Fail closed when a Shizuku timeout scope is cancelled after scheduling

Date: 2026-10-28  
PR: #1 App Commander; keep Draft/open.

## Engineering question

After schedule() returns and its immediate job.isCancelled check has passed, can cancellation of the parent scope cancel a lazy timeout coroutine before its body runs, leaving the shared Shizuku waiter pending and binding=true forever?

## Why source inspection alone is insufficient

The race depends on the lifecycle of a lazy coroutine and its parent Job. A deterministic JVM regression must cancel the parent only after schedule() returns, then verify waiter completion, state reset, exactly-once cleanup, and retry availability.

## Production change

- Register a completion handler on the successfully registered timeout job.
- If the job is cancelled before the timeout has fired, attempt reset only for the exact still-current binding request.
- Remove only that request's timer entry and call timeout cleanup only when the state machine confirms that this attempt was reset.
- Preserve existing behavior: successful connection and stale/failed attempts are no-ops; pre-cancelled scopes still fail closed; caller cancellation does not own the shared bind timeout.

## Acceptance criteria

1. Cancelling the parent after schedule() returns completes the shared waiter exceptionally.
2. The current attempt leaves binding=true, invokes cleanup exactly once, and permits a fresh bind.
3. Successful completion, stale attempts, and cancellation after timeout firing do not trigger duplicate cleanup.
4. Existing Shizuku state, timeout, unbind and UI safety tests remain green.
5. :app:testDebugUnitTest and :app:lintDebug pass on the exact source/test commit; no APK packaging.
6. No new permissions/dependencies, root, Device Owner, merge, main write or auto-merge.
7. Xiaomi Pad 5/HyperOS and real Shizuku callback timing remain NOT TESTED.

## References reviewed

- Kotlin Job API documents parent-to-child cancellation and invokeOnCompletion: https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-job/
- Official Shizuku API developer guide describes UserService binding through ServiceConnection: https://github.com/RikkaApps/Shizuku-API
- No external implementation was copied; this is a focused lifecycle fix in the existing state machine.

## Verification status at implementation commit

Source/JVM/Lint CI is pending exact-commit verification.
