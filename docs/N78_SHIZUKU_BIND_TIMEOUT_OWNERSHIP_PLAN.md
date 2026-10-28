# N78 — Shared Shizuku bind timeout ownership

Date: 2026-10-28  
Status: implementation committed; CI pending at plan creation.

## Engineering question

Can cancelling the coroutine that first requested a Shizuku bind accidentally reset and unbind a shared request that other callers are still awaiting?

The previous controller caught every `Exception` around `withTimeout` and reset the owner attempt in that catch. Coroutine cancellation is also an exception; therefore cancellation of the initiating UI scope could be mistaken for an expired bind timeout. The shared waiter would fail and the binding would be torn down even when a joiner still needed it.

## Source review

- Reviewed the official Shizuku API implementation and its `bindUserService` / `unbindUserService` behavior: https://github.com/RikkaApps/Shizuku-API/blob/master/api/src/main/java/rikka/shizuku/Shizuku.java
- Reviewed Android's `ServiceConnection` lifecycle contract, including the requirement to release dead/null bindings: https://developer.android.com/reference/android/content/ServiceConnection
- No third-party implementation code copied; no dependency or permission added.

## Change

- Add a coroutine-scope-owned timeout for each actual bind owner request.
- Keep each caller's `withTimeout` as a bound on its own wait, but do not let its cancellation reset the shared bind state.
- The independent timer resets and unbinds only if the exact request is still current, has no live Binder, and has not already failed or timed out.
- Successful connection and stale callbacks remain no-ops for the timer.
- A joiner never schedules or owns the binding timeout.

## Acceptance criteria

1. Cancelling the original waiting coroutine does not immediately complete the shared waiter or unbind the connection.
2. A joiner sharing the same waiter remains pending until connection or the independent attempt timeout.
3. Independent timeout completes the shared waiter, resets binding state and invokes unbind once.
4. Successful connection before the deadline makes the timer a no-op.
5. Existing stale-callback, duplicate-callback, timeout/retry and permission-safety tests remain passing.
6. `:app:testDebugUnitTest :app:lintDebug` pass on the exact source/test commit; docs-head recheck follows.
7. No APK packaging/artifacts, no root/Device Owner, no merge or `main` write.

## Device limitation

JVM tests can prove the request-state and timeout ownership logic, but cannot prove real Shizuku/HyperOS callback timing. Xiaomi Pad 5 device behavior remains NOT TESTED.
