# N38 — Fail-safe verified APK snapshot cleanup

**Date:** 2026-10-27  
**PR:** #1 — App Commander; keep Draft, no merge.

## Engineering question

Can verified-install cache cleanup remain fail-safe when the device wall clock is invalid or near the epoch, and can the install flow avoid deleting the just-created snapshot during a second cleanup pass? Source inspection alone is insufficient because the subtraction overflow is a numeric boundary bug and needs a regression test that would fail against the old implementation.

## Acceptance criteria

1. Cleanup never subtracts a positive retention interval from a negative clock value; that case returns zero deletions.
2. A retention interval longer than the observed non-negative uptime fails closed rather than calculating an underflow-prone cutoff.
3. Expired snapshots are reclaimed before allocating a new snapshot; the install handoff does not run a redundant cleanup after creating the URI that will be returned to Android's installer.
4. Regression tests cover `Long.MIN_VALUE`, near-epoch time, retention longer than uptime, and an extreme positive retention interval.
5. Source-only `:app:testDebugUnitTest :app:lintDebug` passes on the exact updated PR head, with zero artifacts.
6. No APK packaging, signing, upload, merge, main write, root, or Device Owner.

## Implementation

- Made `ApkInstallSnapshot.cleanupExpired` return without deleting anything when the clock is negative or the retention interval exceeds the observed time.
- Removed the redundant post-`FileProvider` cleanup from `AppRepository.prepareVerifiedInstall`; `create()` already performs cleanup before allocating the new snapshot, so the returned file cannot be selected for deletion by that second pass.
- Added three JVM regression cases for clock/retention boundaries.

## Source/license review

This is a small hardening change to the project's own cache lifecycle. No third-party implementation, dependency, permission, network access, or paid service was added.

## Status

- Source/JVM/lint CI: pending exact updated PR-head run.
- SAF provider and Android installer behavior on Xiaomi Pad 5/HyperOS: NOT TESTED.
- APK artifacts: none permitted for this milestone.
