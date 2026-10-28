# N70 — Observe cancellation when bounded APK copy reaches EOF

Date: 2026-10-28. App Commander PR #1 remains open/Draft.

## Engineering question

`ApkCacheCopy.copyWithSha256` checked cancellation before every read but returned a successful digest immediately after the read loop saw EOF. If cancellation arrived during the final read that returned EOF, the helper itself could report success rather than propagating the cancellation. Higher-level exporters have later checkpoints, but the shared copy primitive should honor its own cancellation contract.

## Production change

- Added a final `checkCancelled()` after the EOF loop and before empty-stream validation/digest return.
- Existing byte cap, single-pass SHA-256, zero-progress handling, and output-write behavior remain unchanged.
- Added a deterministic stream that flips cancellation only when the final read returns EOF and asserts that the same `CancellationException` object propagates.

## Acceptance criteria

1. Normal non-empty binary copies retain exact byte count, bytes, and SHA-256.
2. Cancellation between chunks still stops without reading the remainder.
3. Cancellation triggered by the final EOF read throws the original cancellation rather than returning a digest.
4. Empty input without cancellation still fails with the existing empty-APK IOException.
5. CI runs `./gradlew :app:testDebugUnitTest :app:lintDebug --stacktrace` only; no APK packaging/signing/upload.

## Evidence

- Production + regression-test SHA: `1f2f128cccad515aed248e5a85980353055544b9`.
- **PASS:** App Commander source/JVM/Lint CI [#40646249969](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40646249969); `:app:testDebugUnitTest` and `:app:lintDebug`, BUILD SUCCESSFUL in 1m 51s.
- Artifact query: 0. No APK packaging/build/signing/upload.
- Xiaomi Pad 5/HyperOS, real SAF provider behavior, and Android installer handoff: **NOT TESTED**.
- Final documentation-head CI recheck: pending at plan creation; will be recorded in PR conversation after it completes.
- Open API references reviewed: [Java InputStream](https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/io/InputStream.html) defines EOF as `-1`; [Kotlin coroutine cancellation guidance](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/ensure-active.html) documents cooperative cancellation checks. No third-party implementation copied.

## Google Drive

**SYNC FAILED** — this runtime exposes no Google Drive/Docs connector, so the LIVE handoff/index could not be read, written, or verified. No root, Device Owner, merge, main write, or paid service.
