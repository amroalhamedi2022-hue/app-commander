# N67 — preserve fatal JVM errors during APK install integrity checks

Date: 2026-10-28

## Engineering question
Does the installer preflight turn every thrown Throwable into a normal content-mismatch result? The catch-all also swallows JVM Error subclasses, which are not ordinary provider read failures and should not be disguised as a checksum mismatch.

## Implementation
- Keep cooperative cancellation propagation unchanged.
- Continue failing closed (return false) for ordinary exceptions from provider reads, checksum streaming, or access checks.
- Catch Exception, not Throwable, so JVM Error subclasses propagate instead of being silently downgraded.
- Do not alter installer invocation, permissions, Shizuku policy, APK execution, or signing claims.

## Acceptance criteria
1. Existing unchanged-content, digest-mismatch, invalid-metadata, I/O-failure and stalled-stream tests pass.
2. Cancellation remains propagated rather than converted to false.
3. A synthetic provider stream throwing a specific AssertionError propagates the same instance.
4. :app:testDebugUnitTest and :app:lintDebug pass.
5. No APK assembly/signing/upload, no root/Device Owner, no main-branch write or merge.

## Evidence boundaries
- Source/test SHA: 5ca6f66331d0a0715e4fbb9340ae9a1f797b1f31. App Commander source CI #40629333773 PASS; :app:testDebugUnitTest and :app:lintDebug completed successfully, without APK packaging. This later documentation-only commit will receive a separate final-head recheck.
- Xiaomi Pad 5/HyperOS, live PackageManager and real SAF provider behavior: NOT TESTED.
- APK artifacts: 0.
- Google Drive LIVE sync: SYNC FAILED if no Drive/Docs connector is exposed; no sync is claimed without read-back verification.