# N20 — Recover stale Shizuku Binder and failed binding callbacks

Date: 2026-10-26. PR #1 remains Draft and unmerged.

## Engineering question
`ShizukuController.obtainService()` currently pings a cached Binder but leaves the stale `remote` reference non-null when `pingBinder()` fails. Its timeout recovery only resets `binding` when `remote == null`; therefore a dead cached Binder can leave `binding=true` and the pending deferred stuck across subsequent calls. In addition, `ServiceConnection.onBindingDied` and `onNullBinding` are not handled explicitly, so waiters may sit until the full timeout.

## Acceptance criteria
1. A dead cached Binder is discarded only if it is still the same cached instance, so a newer live connection is not cleared by a stale failure.
2. Binding state and pending waiters are managed by a small pure Kotlin state helper that can be tested without Android/Shizuku runtime.
3. Binder disconnect, binding-death and null-binding callbacks fail the current waiter and make retry possible immediately.
4. A timeout from an older attempt cannot reset a newer pending attempt.
5. Add JVM tests for stale Binder recovery, failed callback recovery, successful connection completion, and stale-timeout isolation; retain all existing Shizuku consent, process runner, package guard, and command validator tests.
6. Run `:app:testDebugUnitTest` and `:app:lintDebug` on the exact source+test SHA. No APK packaging/build, artifact upload, release, merge, or main write.

## Licensed-source review
Inspected the official `RikkaApps/Shizuku` LICENSE; it is Apache-2.0. This change uses Android's public `ServiceConnection` lifecycle callbacks and a project-local state helper; no third-party code is copied and no new dependency is added.

## Baseline
- Current PR #1 head before N20: `a5272b10c58fef8b8abfdf9ecee66c1a5358a75d`.
- N19 process-pipe cleanup source/test SHA: `8cf5fe00b3e1423ce54ffc094f3f01df862383dc`, source checks PASS in run 40247055115.
- Latest PR-head source checks: run 40247266510 PASS on `a5272b10c58fef8b8abfdf9ecee66c1a5358a75d`.
- Android minSdk is 28, so `onBindingDied` and `onNullBinding` are available. Real Xiaomi Pad 5/HyperOS Shizuku behavior remains NOT TESTED.
- Google Drive LIVE sync is unavailable in this runtime; no Drive sync will be claimed without read-back verification.

## Result
Pending implementation and exact-SHA CI.
