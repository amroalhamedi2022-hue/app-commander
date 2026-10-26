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

## Result — N20 SOURCE ACCEPTANCE PASS (2026-10-26)
- **Root cause confirmed:** a cached Binder that failed `pingBinder()` stayed non-null, preventing timeout recovery from resetting `binding`. Failed binding lifecycle callbacks also had no explicit immediate recovery path.
- **Production:** added pure Kotlin `ShizukuConnectionState` / `BindingRequest` state management. The controller now discards a dead cached Binder only if it is still the same instance, handles `onBindingDied` and `onNullBinding` as well as disconnect, fails the current waiter, and resets only the matching timed-out attempt. If an old command fails after a newer Binder is installed, identity-guarded cleanup preserves the newer connection.
- **Production commits:** helper `cfd77e58b9ed8a3ce2a054080e7df47a6829f61e`; controller integration `fdc0e2377dc428177734a4f59edbfcb69c8f01aa`; final source head `659d5b3c3582e3bb97acfa0ae438fd007a0e1169`.
- **Regressions:** `ShizukuConnectionStateTest` covers dead-Binder retry, identity-safe cleanup, disconnect/binding-death/null-binding failure, successful waiter completion, stale-timeout isolation, and source wiring of Android callbacks.
- **PASS exact source+test SHA `659d5b3c3582e3bb97acfa0ae438fd007a0e1169`:** GitHub Actions [run 40252299297](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40252299297) ran `./gradlew :app:testDebugUnitTest :app:lintDebug --stacktrace`; both tasks passed, `BUILD SUCCESSFUL` (36 tasks executed).
- **Artifacts: []** zero. No APK build/package/upload.
- **NOT TESTED:** real Shizuku Binder death/null-binding callbacks on Xiaomi Pad 5/HyperOS; SAF/device workflows and Liquid UI remain separate device gates.
- **License review:** inspected official `RikkaApps/Shizuku` LICENSE (Apache-2.0); no code copied and no new dependency.
- **Drive SYNC FAILED:** Google Drive/Docs connector tools are unavailable in this runtime; LIVE HANDOFF/INDEX were not read/written back. GitHub plan and ledger are the recovery records; no Drive sync is claimed.
- PR #1 remains open Draft; no merge, main write, Device Owner provisioning, or APK build.

## Next
Return to Explode Arena on the next rotation. Keep real-device SAF/Shizuku validation and final independent recheck as separate acceptance gates.
