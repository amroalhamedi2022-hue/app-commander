# N74 — Cancellation-safe verified APK install snapshot

Date: 2026-10-28

## Engineering question (before CI)

When the user cancels while a SAF provider is streaming an APK into the private verified-install cache, does cancellation stop the copy promptly, preserve the original `CancellationException`, remove the partial snapshot, and prevent a URI from reaching Android's installer?

Source inspection shows `ApkCacheCopy.copyWithSha256` already supports cooperative cancellation, but `ApkInstallSnapshot.create` did not forward that callback. The UI's install preparation also used a non-cancellable blocking copy and the repository's `runCatching(...).getOrNull()` swallowed cancellation as an ordinary failure.

## Implementation

- Forward a cancellation callback through the verified snapshot copy and check it before cache allocation and after EOF.
- Keep the snapshot unverified until the final cancellation check passes; cancellation must take the same cleanup path as checksum/provider failure.
- Pass the launch coroutine context into the IO operation and call `ensureActive()` between stream chunks.
- Rethrow cancellation from `AppRepository.prepareVerifiedInstall`; continue returning null for ordinary provider/verification exceptions.
- Add JVM integration tests for mid-copy and EOF cancellation, exception identity, and partial-file cleanup.

## Acceptance criteria

1. Cancellation is checked during each bounded copy chunk and after EOF.
2. The exact cancellation exception propagates; it is not converted to a null result or parse error.
3. No partial verified-install snapshot remains after cancellation.
4. Normal matching APK bytes, provider-swap rejection, size limits, and expiry cleanup continue to pass.
5. `:app:testDebugUnitTest :app:lintDebug` pass with zero artifacts; no APK packaging/signing/upload.
6. Xiaomi Pad 5/HyperOS, real SAF-provider cancellation, and Android installer handoff remain NOT TESTED.
7. No new permission/dependency, root, Device Owner, merge/main write, or paid service.


## Evidence — 2026-10-28

- Production + regression-test source SHA: `647868609d15a748f870695f27a04948ed771646`.
- **PASS — source/JVM/Lint CI:** [run #40669431153](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40669431153), exact tested SHA; `:app:testDebugUnitTest :app:lintDebug`, **BUILD SUCCESSFUL in 1m 11s**.
- New regressions cover cancellation during a partial snapshot copy and cancellation exactly at EOF; both require the same `CancellationException` instance and an empty snapshot directory afterward.
- Workflow artifact query: **0 artifacts**. No APK packaging, signing, or upload.
- Xiaomi Pad 5 / HyperOS, real SAF-provider cancellation, and Android installer handoff: **NOT TESTED**.
- No new dependency/permission, root, Device Owner, merge, main write, or paid service.
- Google Drive LIVE handoff/index: **SYNC FAILED** — this runtime exposes no Google Drive/Docs connector; no Drive read/write/read-back is claimed.
