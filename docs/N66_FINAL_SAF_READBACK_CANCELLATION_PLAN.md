# N66 — Final cancellation gate after SAF readback

**Date:** 2026-10-28  
**PR:** #1 — App Commander; keep Draft/open, no merge.

## Engineering question

Can a cancellation that arrives after the last readback byte—but during stream close or final archive validation—still cause an export to return success?

Source inspection found both base-APK and Base+Splits export paths performed cooperative checks during copying/readback but had no final cancellation checkpoint after the readback stream closed and before returning success. That narrow window could report success after the user had cancelled.

## Production change

- Add a final checkCancelled() after the base APK readback stream closes and before accepting the integrity result.
- Add a final checkCancelled() after the independent Base+Splits verifier returns and before accepting its result.
- Preserve existing exception identity and the caller-owned cleanup boundary: cancellation remains primary and only the newly created SAF document is eligible for best-effort cleanup.
- No new dependencies, permissions, APK packaging, signing or upload.

## Acceptance criteria

1. A cancellation raised immediately after the base readback stream closes propagates the same CancellationException.
2. The same condition during bundle readback propagates cancellation and skips the success result.
3. The caller's scoped partial-document cleanup runs exactly once and does not replace the cancellation.
4. Existing hash/readback, central-directory, source-snapshot and cleanup tests remain green.
5. Free PR CI runs :app:testDebugUnitTest :app:lintDebug on the exact source/test commit; no APK build or artifact.
6. SAF provider behavior on Xiaomi Pad 5 / HyperOS remains NOT TESTED.

## Evidence at commit creation

- Production source and two integration regressions: included in this milestone.
- Source/JVM/Lint CI: PENDING.
- APK artifacts: 0; packaging not requested.
- Device/SAF provider behavior: NOT TESTED.
- Google Drive LIVE: SYNC FAILED unless connector read/write/read-back can be verified.
