# N77 — Shizuku duplicate-bind and callback isolation

Date: 2026-10-28  
PR: #1 App Commander; keep Draft/open.

## Engineering question

Can a Binder connection arrive between the controller's initial remote probe and `beginOrJoin`, causing a redundant binding, or can a duplicate `onServiceConnected` callback replace the already-live Binder for the same request generation?

## Why source inspection alone is not enough

The race spans two synchronized state-machine calls and platform callbacks. A JVM regression must model the interleaving directly, prove that the existing Binder and waiter remain stable, and ensure stale-generation protection continues to work.

## Implementation

- `beginOrJoin` reuses the current completed waiter whenever a live Binder is already installed, closing the gap between remote probe and bind request.
- `connectedIfCurrent` treats a duplicate callback for a completed request as idempotent and does not replace its Binder.
- If a current but incomplete waiter encounters a Binder installed by a competing callback, it is completed with the existing Binder and the redundant connection is rejected for unbinding.
- Add JVM state-machine regressions for duplicate callback isolation and the remote-probe/bind-request race.

## Acceptance criteria

1. A duplicate callback cannot replace a live Binder or complete the original waiter with a different object.
2. A successful connection racing with `beginOrJoin` does not create another platform binding.
3. Stale-generation, timeout, disconnect, retry, and joiner/owner tests remain green.
4. `:app:testDebugUnitTest :app:lintDebug` pass; source-only validation, no APK packaging/signing/upload.
5. No new permission/dependency, root, Device Owner, merge, main write, or auto-merge.
6. Real Shizuku service reconnect behavior on Xiaomi Pad 5/HyperOS remains **NOT TESTED**.

## Evidence

- Source/test/docs commit: pending.
- JVM and lint CI: pending.
- APK artifacts: 0; no APK build requested.
- Device Shizuku callback behavior: **NOT TESTED**.
- Google Drive LIVE sync: **SYNC FAILED** if no Drive/Docs connector is exposed; no sync claim without read-back verification.
