# N71 — Preserve fatal errors during temporary APK cleanup

Date: 2026-10-28

## Finding
`ApkCacheCopy.withTemporaryApk` caught every `Throwable` thrown by cleanup and wrapped it in an `IOException` diagnostic. If inspection had otherwise succeeded, a JVM `Error` (for example an `AssertionError` or `OutOfMemoryError`) was downgraded to an ordinary cleanup failure instead of propagating as the fatal condition it represents.

## Implementation
- Keep the existing cleanup diagnostic for ordinary exceptions such as `SecurityException`.
- If cleanup throws an `Error` after successful inspection, rethrow the exact same `Error` instance.
- If inspection already failed, preserve that original failure as primary and attach the cleanup `Error` as suppressed (unless it is the same instance).
- Keep scoped temporary-file cleanup behavior and do not alter APK bytes, hash verification, SAF grants, installer flow, permissions, or dependencies.

## Acceptance criteria
1. Successful inspection + fatal cleanup error propagates the same `Error` instance.
2. Inspection failure + fatal cleanup error preserves the original inspection exception and records the exact cleanup error as suppressed.
3. Existing ordinary cleanup failure diagnostics, cancellation, digest, size-bound, provider I/O, and cleanup tests remain green.
4. No APK packaging/signing/upload; real SAF and device behavior remain separate gates.

## Evidence
- Source + regression-test SHA: `6e7bc5b2683ef40fcce34a586d5a84150bdb1d3b`.
- Source/JVM/Lint CI: [#40653238999](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40653238999) — PASS; `:app:testDebugUnitTest :app:lintDebug`, build successful in 1m 53s.
- Artifact count: 0. APK packaging/signing/upload: NOT PERFORMED.
- Xiaomi Pad 5/HyperOS, real SAF provider and Android installer handoff, and Shizuku device flow: NOT TESTED.
- This change follows the JDK `Throwable` contract for `Error` and suppressed failures; no external code/dependency copied.
- Final documentation-head CI is recorded in the PR verification comment added after this plan update.
