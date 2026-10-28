# N79 — Idempotent cleanup for each Shizuku bind attempt

Date: 2026-10-28  
PR: #1 App Commander; keep Draft/open.

## Engineering question

Can the independent N78 timeout and a late Shizuku callback race to call `unbindUserService` more than once for the same `ServiceConnection`?

## Open-source/API review

Reviewed the official Shizuku API implementation: https://github.com/RikkaApps/Shizuku-API/blob/master/api/src/main/java/rikka/shizuku/Shizuku.java. Its `unbindUserService(..., remove=false)` implementation documents that callbacks can still arrive after unbinding. N78 correctly isolates stale callbacks, but timeout cleanup and a late callback can both reach the current unbind helper. This milestone adds a per-connection gate; it does not copy third-party code or change the Shizuku API/dependency.

## Implementation

- Associate a weakly keyed `UnbindOnce` gate with each exact `ServiceConnection`.
- Route timeout, stale callback, disconnect, binding-death, null-binding and synchronous-bind-failure cleanup through that gate.
- Make concurrent/duplicate cleanup invoke the platform unbind at most once after a successful call; if the platform call throws, allow a later cleanup attempt to retry.
- Preserve `remove=false`; never request UserService termination.

## Acceptance criteria

1. Repeated and concurrent cleanup requests for the same connection call the platform action once after success.
2. A failed cleanup action remains observable to the caller and permits a later retry.
3. A successful retry makes all later requests no-ops.
4. Source-contract test proves the N78 timeout and callback paths share the same per-connection gate.
5. `:app:testDebugUnitTest :app:lintDebug` pass on the exact source/test commit; a final docs-head recheck is recorded.
6. No APK packaging/signing/upload, new permissions/dependencies, root, Device Owner, merge, `main` write or auto-merge.
7. Xiaomi Pad 5/HyperOS Shizuku callback timing remains **NOT TESTED**.

## Evidence

Source/test SHA and CI: pending. Final docs-head recheck: pending.
Google Drive LIVE sync: **SYNC FAILED** if no Drive/Docs connector is available; do not claim sync without read-back verification.
