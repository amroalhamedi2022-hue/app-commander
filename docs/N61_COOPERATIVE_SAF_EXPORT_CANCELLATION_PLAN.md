# N61 — Cooperative cancellation for large SAF APK exports

**Date:** 2026-10-28  
**PR:** #1 — App Commander; keep Draft/open, no merge.

## Engineering question

Can a user cancel a long base-APK or Base+Splits SAF export without waiting for the entire bounded stream to finish, while preserving the original cancellation exception and attempting cleanup only for the fresh document returned by CreateDocument?

## Production changes

- Add a cooperative cancellation checkpoint between bounded copy/readback chunks and ZIP parser reads.
- Thread the callback through base export, bundle export, central-directory verification, and repository boundaries.
- Re-throw coroutine cancellation from APK integrity readback instead of converting it into an ordinary hash mismatch.
- Add an explicit Arabic إلغاء التصدير control while a copy job is active.
- Keep the existing 512 MiB per-APK and 2 GiB aggregate limits, fresh-URI-only cleanup, readback verification, and no-root/no-Device-Owner policy.
- No new dependency, permission, APK packaging, signing, or upload.

## Acceptance criteria

1. Cancellation between copy chunks propagates the same CancellationException and stops reading more source bytes.
2. Base export cancellation invokes scoped partial-document cleanup and does not mask cancellation.
3. Bundle export cancellation skips readback and invokes scoped cleanup.
4. Cancellation is not swallowed by APK SHA readback.
5. Existing export, ZIP canonical-layout, source snapshot, and cleanup regressions remain green.
6. :app:testDebugUnitTest and :app:lintDebug pass on this exact source/test commit and again on the docs-updated PR head.
7. No APK build/artifact. Real SAF provider cancellation remains NOT TESTED.

## Evidence

- Source/test SHA: pending.
- Exact final PR head: pending.
- Source/JVM/Lint CI: pending.
- Device/HyperOS/SAF provider test: NOT TESTED.
- Google Drive LIVE sync: SYNC FAILED if the Drive/Docs connector remains unavailable; GitHub docs are not proof of Drive sync.
