# N51 — Preserve SAF cleanup diagnostics when export is cancelled

**Date:** 2026-10-27  
**PR:** #1 — App Commander; keep Draft/open, no merge.

## Engineering question

When an SAF base-APK or APK-bundle export is cancelled after a fresh document was created, does cleanup still run, and can the UI tell the user when deletion could not be confirmed without swallowing coroutine cancellation?

The N50 cleanup wrapper already attaches a typed suppressed diagnostic to the original failure. However, both export UI paths immediately rethrew CancellationException without checking that diagnostic. If cleanup was refused or threw during cancellation, the warning to inspect/remove a partial document was lost.

## Implementation

- Add a pure Kotlin message mapper for base APK and bundle export outcomes.
- Keep cancellation semantics intact: the UI records a manual-cleanup warning only when the cancellation carries an unconfirmed-cleanup diagnostic, then rethrows the same CancellationException.
- Keep ordinary failure messages distinct for base APK vs ZIP bundle and for confirmed vs unconfirmed cleanup.
- Add JVM regressions using the actual InstalledApkExport cleanup wrapper for false deletion, thrown deletion and cancellation; assert the original exception identity is preserved.

## Acceptance criteria

1. A cancelled base export still attempts cleanup, preserves the original CancellationException, and exposes a manual-cleanup warning only if deletion is unconfirmed.
2. A cancelled bundle export behaves the same and identifies the partial ZIP document.
3. Ordinary export failures keep the existing Arabic success-of-cleanup vs manual-cleanup messaging.
4. Run ./gradlew :app:testDebugUnitTest :app:lintDebug --stacktrace on the exact source/test SHA and again on the documentation-updated PR head.
5. No APK packaging/signing/upload, new dependency/permission, root, Device Owner, merge, main write or auto-merge.

## Source review

This is project-owned UI/error handling built on the existing Android Storage Access Framework APIs and N50 diagnostic type. No third-party implementation or dependency is added. Cancellation is never converted into a successful export or swallowed.

## Verification ledger

- **Production + regression-test SHA:** b3a5a45f90dd4da7d90a15f5da66912c0ec51692.
- **PASS — source/JVM/Lint:** [CI #40521536855](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40521536855), :app:testDebugUnitTest :app:lintDebug, BUILD SUCCESSFUL in 1m 43s.
- **Artifacts: 0**; no APK packaging/signing/upload.
- Documentation-updated PR-head recheck remains pending at this update; the source/test SHA result does not cover later docs commits.
- Xiaomi Pad 5 / HyperOS and real SAF provider cancellation/deletion behavior remain **NOT TESTED**. Google Drive LIVE sync remains **SYNC FAILED** because no Drive/Docs connector is exposed in this runtime.
