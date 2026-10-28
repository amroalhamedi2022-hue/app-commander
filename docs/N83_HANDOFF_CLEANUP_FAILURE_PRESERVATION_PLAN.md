# N83 — Preserve primary install-handoff failures when cleanup throws

Date: 2026-10-28  
PR: #1 App Commander; keep Draft/open.

## Engineering question

If snapshot cleanup itself throws while the installer handoff is already failing or being cancelled, does the cleanup error replace the original failure or allow the handoff to be reused?

## Why source inspection alone is insufficient

Cleanup callbacks can throw synchronously, and reentrant callbacks can attempt another state transition while cleanup is in progress. Tests must exercise throwable identity, suppression, terminal-state sealing, and reentrancy.

## Production and regression changes

- Treat a thrown cleanup callback as secondary diagnostic information attached to the original handoff failure, including cancellation.
- Avoid attaching the same cleanup throwable twice if the callback already recorded it.
- Keep the state machine terminal in `CLEANED` even when cleanup throws.
- Add regressions for throwing cleanup, no retry/transfer after cleanup failure, and reentrant transfer/cleanup attempts.

## Acceptance criteria

1. The original failure instance remains primary and the cleanup throwable is suppressed.
2. Cleanup is invoked exactly once even when its callback throws.
3. A cleanup-failed handoff cannot later transfer or record a replacement snapshot.
4. Reentrant transition attempts during `CLEANUP_CLAIMED` return false without deadlock or duplicate cleanup.
5. Existing `:app:testDebugUnitTest :app:lintDebug` remain green without APK packaging.
6. No new permissions/dependencies, root, Device Owner, merge, `main` write or auto-merge.
7. Xiaomi Pad 5/HyperOS, real SAF providers and installer timing, and Shizuku device behavior remain **NOT TESTED**.

## Verification status

- Source/test SHA: pending commit.
- JVM + Android lint CI: PENDING.
- Artifacts: expected 0; no APK build requested.
- Google Drive LIVE sync: no claim without a successful read/write/read-back in the confirmed folder.
