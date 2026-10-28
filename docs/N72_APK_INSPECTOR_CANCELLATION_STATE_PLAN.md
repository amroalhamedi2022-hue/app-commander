# N72 — APK Inspector cancellation and stale-result isolation

Date: 2026-10-28

## Finding
The APK file-picker callback used runCatching, which catches CancellationException as well as ordinary inspection failures. A cancelled withContext(Dispatchers.IO) could therefore be converted into a user-visible parse error rather than cancelling the request. The callback also managed result, URI, and loading flags independently, making stale-result races harder to prove.

## Implementation
- Add a pure generation-aware ApkInspectionLoadState used by the Inspector UI.
- Clear the previous inspection and selected install URI synchronously before starting a new inspection.
- Lock busy before launching work, explicitly rethrow inspection cancellation, and clear loading only for the request generation that owns the lock.
- Wrap the installer handoff in cancellation-aware try/catch/finally so provider failures show a retryable message and cannot leave the Inspector permanently busy; clear the selected result only when integrity revalidation explicitly fails.
- Ignore stale success/failure/cancellation completions so they cannot restore an older APK result or unlock a newer request.
- Add JVM regressions for new-request invalidation, cancellation, stale completions, and retry after failure.

## Acceptance criteria
1. Starting a new file inspection removes the prior result and install URI before inspection begins.
2. Cancellation stops the current load without inventing a parse error and is rethrown to coroutine machinery.
3. An older generation cannot overwrite the newer result/error/loading state.
4. Ordinary inspection failures remain retryable and never leave a stale result available for install.
5. Installer preparation/provider exceptions are surfaced without leaving the UI busy; cancellation is rethrown.
6. :app:testDebugUnitTest and :app:lintDebug pass; no APK packaging or artifact upload.

## Evidence
- Final source/test SHA: bd7d2b81bbb2751b3a335e0f537053f8794e85a0.
- Initial CI #40660978319 FAIL: the UI still assigned to the derived result; corrected in the follow-up commit.
- Final source/JVM/lint CI [#40661366429](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40661366429): PASS, BUILD SUCCESSFUL in 1m 9s; 0 artifacts.
- APK packaging/build/upload: NOT PERFORMED.
- Final docs-head recheck: recorded in the PR verification comment for the final documentation head; source/test code remains unchanged after the source/test SHA above.
- Xiaomi Pad 5 / HyperOS UI cancellation and installer handoff: NOT TESTED.
- SAF provider and Shizuku device behavior: NOT TESTED.
- No root, Device Owner, external API, or paid service added.
