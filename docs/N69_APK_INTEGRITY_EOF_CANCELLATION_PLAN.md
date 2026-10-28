# N69 — Preserve cancellation arriving at APK integrity EOF

Date: 2026-10-28. App Commander PR #1 remains open Draft; no merge.

## Engineering question

`ApkInstallIntegrity.matches` checked cancellation before reading and `ApkCacheCopy.copyWithSha256` checked before each read. If cancellation arrived while the final read returned EOF, the copy could finish and the method could accept a matching size/hash without observing the cancellation until its caller returned. The final EOF is a valid race boundary and should be checked before integrity success is reported.

## Production change

- Recheck the caller's cancellation callback after the bounded copy/hash completes and before comparing/accepting the result.
- Preserve exact-hash success when not cancelled, fail-closed ordinary provider exceptions, cancellation propagation, and JVM Error propagation.
- No Android packaging, permissions, dependency, Shizuku command, or installer behavior changed.

## Acceptance criteria

1. A deterministic stream flips the cancellation state as its final read returns EOF.
2. `matches` throws the same `CancellationException` instance rather than returning success.
3. Existing hash, size-bound, I/O failure, stall, and fatal-error regressions remain green.
4. CI runs `./gradlew :app:testDebugUnitTest :app:lintDebug` only; no APK packaging/signing/upload.

## Evidence

- Production + regression test commit: d1a0b11b49fe528097485c4d4971cf8f6b284571.
- CI and final-head recheck: pending; record exact run IDs after completion.
- Xiaomi Pad 5/HyperOS, real SAF providers, and installer handoff: NOT TESTED.
- Open API reference checked: Java `InputStream.read(byte[])` returns -1 at EOF; https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/io/InputStream.html. No third-party implementation copied.
- No root, Device Owner, merge, main write, or paid service.
