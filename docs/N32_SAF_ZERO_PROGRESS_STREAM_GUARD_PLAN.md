# N32 — Fail closed on zero-progress SAF bundle streams

Date: 2026-10-26. App Commander PR #1 remains open Draft; no merge.

## Engineering question

The installed-APK bundle verifier wraps the user-selected SAF stream in `ZipBundleStructure.TrackingStream` and then parses local ZIP headers with `ZipInputStream`. Entry payload copying already bounds repeated zero reads, but the ZIP header parser runs before that per-entry guard. Some broken/custom `InputStream` implementations return zero for a positive-length read, which violates the usual progress contract; a parser that retries that result can spin before the bundle's size and integrity checks are reached.

Can the outer tracking stream bound repeated zero-progress reads, fail closed with a stable `IOException`, and retain byte accounting/zero-length-read semantics?

## Source review

- Android's Storage Access Framework supplies content through provider-backed streams; provider readback is not equivalent to an ordinary local file. The export already closes the destination and independently reopens it before success.
- No third-party implementation or dependency was added. The fix uses the existing JDK `InputStream` wrapper and bounded ZIP verifier.
- This is a liveness/integrity guard, not APK signature verification, malware detection, or proof of provider compatibility on every device.

## Acceptance criteria

1. A positive-length bulk read returning zero is counted as no progress; eight consecutive zero results fail with `IOException("APK bundle stream stalled")`.
2. Any successful single-byte read, positive bulk read, or EOF resets the consecutive-zero counter; zero-length reads return zero without consuming the stall budget.
3. Existing raw ZIP byte cap and circular tail accounting remain unchanged.
4. Add a regression that passes a valid exported bundle through a provider-like stream returning eight zero-progress reads while the ZIP header is being parsed; verification must fail promptly rather than hang.
5. Add integration regressions proving bundle readback failure triggers the scoped newly-created-document cleanup exactly once, and a cleanup denial does not mask the original integrity/readback error.
6. Run `./gradlew :app:testDebugUnitTest :app:lintDebug` on the exact production+test SHA and again on the docs-updated PR head.
7. No APK packaging/artifact upload, release, merge, `main` write, root, Device Owner, or auto-merge.

## Implementation and tests

- Production commit: `d965ce76dc11f32408802d78823624c766300b11`; source blob `e0e014c1b5a2cd577b27787e2b5d6a2e7fbd23b6`.
- Exact production + regression-test SHA: `975dc372a67dae0b1921bc27c73b1e4a5eb0aeee`; test blob `3347e657afaf6e8428dc86fb2765a616f6045ce7`.
- Added production guard to `ZipBundleStructure.TrackingStream`; centralized the zero-progress limit so both `ZipInputStream` header parsing and the trailing central-directory drain fail closed.
- Added integration coverage for zero-progress provider reads, readback denial cleanup, and cleanup denial preserving the archive-integrity failure.
- **PASS:** source-only GitHub Actions [run 40375961761](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40375961761) on the exact SHA. `:app:testDebugUnitTest` and `:app:lintDebug` both succeeded; log: **BUILD SUCCESSFUL in 1m 3s**, 36 tasks executed.
- **Artifacts: 0.** The workflow runs tests and lint only; no APK packaging, signing or upload.
- The passing run also exercised the existing N31 path-collision regressions and the prior SAF bundle readback/hash/central-directory test suite.
- **NOT TESTED:** physical Xiaomi Pad 5 / HyperOS SAF providers, provider deletion behavior on device, broad third-party ZIP/APK corpus, APK signature authenticity, malware detection, Shizuku device operations, and UI visual parity.
- **Google Drive SYNC FAILED:** no Google Drive/Docs connector is available in this runtime; LIVE handoff/index were not read or written.

## Docs-updated head recheck and next gate

- The docs-updated PR head `c603fde5d6222bebec4914bc69aee74211cd50b2` passed [source-only CI #40376337051](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40376337051). The JVM test and Android lint task completed successfully; **BUILD SUCCESSFUL in 1m 41s**, 36 tasks executed, zero artifacts.
- PR #1 remains open and Draft; no merge or APK packaging occurred.

## Next acceptance gate

1. Exercise base-APK and base+split ZIP export/readback against authorized physical Xiaomi Pad 5 / HyperOS SAF providers, including a provider that refuses readback or deletion.
2. Validate Shizuku permission/recovery and protected-package safeguards on device without root, Device Owner provisioning, or destructive operations.
3. Independently recheck remaining acceptance criteria; source CI alone does not authorize merge or final delivery.
