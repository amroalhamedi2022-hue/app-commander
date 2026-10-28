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

- Source/test changes are committed through test SHA e03c12d63389664dc533bd41bb01e781fbf7d972.
- PR head tested: 0dfe8b6775dbaedf1355b7f47eb6683842d6a001.
- Source/JVM/Lint CI #40594740439: PASS; logs show :app:testDebugUnitTest and :app:lintDebug completed, BUILD SUCCESSFUL in 1m 49s.
- Artifacts: 0; no APK packaging.
- Device/HyperOS/SAF provider test: NOT TESTED.
- A docs-only evidence commit follows this tested head and requires its own CI recheck.
- Google Drive LIVE sync: SYNC FAILED if the Drive/Docs connector remains unavailable; GitHub docs are not proof of Drive sync.
