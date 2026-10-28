# N72 — APK Inspector cancellation and stale-result isolation

Date: 2026-10-28

## Finding
The APK file-picker callback used runCatching, which catches CancellationException as well as ordinary inspection failures. A cancelled withContext(Dispatchers.IO) could therefore be converted into a user-visible parse error rather than cancelling the request. The callback also managed result, URI, and loading flags independently, making stale-result races harder to prove.

## Implementation
- Add a pure generation-aware ApkInspectionLoadState used by the Inspector UI.
- Clear the previous inspection and selected install URI synchronously before starting a new inspection.
- Lock busy before launching work, explicitly rethrow coroutine cancellation, and clear loading only for the request generation that owns the lock.
- Ignore stale success/failure/cancellation completions so they cannot restore an older APK result or unlock a newer request.
- Add JVM regressions for new-request invalidation, cancellation, stale completions, and retry after failure.

## Acceptance criteria
1. Starting a new file inspection removes the prior result and install URI before inspection begins.
2. Cancellation stops the current load without inventing a parse error and is rethrown to coroutine machinery.
3. An older generation cannot overwrite the newer result/error/loading state.
4. Ordinary inspection failures remain retryable and never leave a stale result available for install.
5. :app:testDebugUnitTest and :app:lintDebug pass; no APK packaging or artifact upload.

## Evidence
- Source/test commit: CI pending.
- Xiaomi Pad 5 / HyperOS UI cancellation and installer handoff: NOT TESTED.
- SAF provider and Shizuku device behavior: NOT TESTED.
- No root, Device Owner, external API, or paid service added.
