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

- Exact source/test/docs commit: `ca874ff64d694fe8fab80e6d8afff209d1bc7dab`.
- **PASS — JVM tests + Android static lint:** [CI #40692700079](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40692700079); `:app:testDebugUnitTest` and `:app:lintDebug`, `BUILD SUCCESSFUL in 1m 47s`.
- Artifact query: **0**. No APK packaging/signing/upload.
- Xiaomi Pad 5/HyperOS and actual Shizuku reconnect callbacks: **NOT TESTED**.
- The evidence append triggers a final documentation-head CI recheck; its result will be recorded in the PR conversation to avoid a docs-only commit loop.
- Google Drive LIVE sync: **SYNC FAILED** — no Drive/Docs connector is exposed in this runtime; no sync is claimed.
