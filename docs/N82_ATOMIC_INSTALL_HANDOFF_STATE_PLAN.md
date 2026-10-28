# N82 — Linearizable verified-install snapshot ownership

Date: 2026-10-28  
PR: #1 App Commander; keep Draft/open.

## Engineering question

Can the verified APK snapshot be claimed for cleanup at the same time another completion path transfers ownership to Android's installer?

## Why source inspection alone is insufficient

The prior helper used separate atomic flags for transfer and cleanup. The dangerous interleaving depends on thread timing: cleanup can observe “not transferred”, a second thread can mark the snapshot transferred, and the first thread can still delete the file. A concurrent regression must force both terminal operations to race and prove that only one wins.

## Production and test changes

- Replaced independent transfer/cleanup flags with one synchronized state machine: `EMPTY → PREPARED → TRANSFERRED` or `EMPTY → PREPARED → CLEANUP_CLAIMED → CLEANED`.
- Transfer and cleanup are mutually exclusive transitions; cleanup callback runs outside the lock and is claimed at most once.
- UI now asserts that ownership transfer succeeds after `startActivity` returns, rather than silently ignoring an invalid state transition.
- Added a 64-iteration concurrent race regression and strengthened existing launch-success, cancellation, cleanup-failure and null-preparation assertions.

## Acceptance criteria

1. Concurrent transfer/cleanup has exactly one terminal winner.
2. If transfer wins, cleanup is not called; if cleanup wins, it is called exactly once and transfer is rejected.
3. Existing failure/cancellation cleanup preserves the original failure diagnostics.
4. The source-contract test confirms transfer remains after `startActivity`.
5. `:app:testDebugUnitTest :app:lintDebug` pass without APK packaging.
6. No new permission/dependency, root, Device Owner, merge, `main` write or auto-merge.
7. Xiaomi Pad 5/HyperOS, real SAF provider timing and Android installer behavior remain **NOT TESTED**.

## Verification — 2026-10-28

- Production + regression-test SHA: `4754de66a04a29a0b9c38db2d8614e8b001d2659`.
- **PASS:** App Commander source/JVM/Lint [CI #40732821049](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40732821049), `:app:testDebugUnitTest :app:lintDebug`, **BUILD SUCCESSFUL in 1m 57s**.
- Artifact query: **0**. No APK packaging/signing/upload.
- The final documentation-head recheck will be recorded in the PR conversation after it completes.
- Xiaomi Pad 5/HyperOS, real SAF providers and installer timing: **NOT TESTED**; Shizuku device behavior remains **NOT TESTED**.
- Google Drive LIVE sync: **SYNC FAILED** — no Google Drive/Docs connector is exposed in this runtime; no Drive read/write/read-back or folder metadata verification is claimed.
