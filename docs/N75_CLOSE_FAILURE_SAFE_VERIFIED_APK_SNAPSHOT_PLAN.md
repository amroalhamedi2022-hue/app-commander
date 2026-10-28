# N75 — Close-failure-safe verified APK snapshot handoff

Date: 2026-10-28  
PR: #1 App Commander; keep Draft/open.

## Engineering question

Can a SAF provider throw from `InputStream.close()` after the snapshot bytes have been verified, causing `prepareVerifiedInstall` to return failure while leaving a verified cache file that was never handed to Android's installer?

## Why source inspection is not enough

The current code creates the verified snapshot inside `input.use { ... }`, but its cleanup `catch` is nested inside the block. Kotlin closes the stream after that block returns, so a close-time exception bypasses the inner cleanup. A deterministic stream fixture must throw only from `close()` after delivering the exact expected bytes to prove the leak and its fix.

## Production change

- Add an owned-stream snapshot boundary that records the created file before stream close completes.
- If stream close fails after verification, remove the snapshot and rethrow the exact original failure.
- Move the repository's FileProvider URI creation after the SAF stream has closed successfully.
- Keep snapshot ownership local until the final cancellation checkpoint; delete on cancellation, URI-construction failure or fatal error.
- Verify cleanup failures are suppressed onto the primary exception rather than masking cancellation or a fatal failure.

## Acceptance criteria

1. Exact bytes + successful close return a verified snapshot.
2. Exact bytes + close-time IOException return the same IOException and leave no snapshot.
3. Cancellation/URI failure before handoff cannot return an installer URI and attempts snapshot deletion.
4. Cleanup failure does not replace the primary CancellationException; cleanup diagnostics remain suppressed.
5. `:app:testDebugUnitTest :app:lintDebug` pass; no APK packaging/signing/upload.
6. Xiaomi Pad 5/HyperOS, real SAF provider close failures and Android installer handoff remain NOT TESTED.
7. No new permission/dependency, root, Device Owner, merge/main write or paid service.

## Evidence

CI is pending for the source/test commit. This milestone tests an exception boundary not proven by ordinary source inspection; device/provider behavior remains a separate gate.
