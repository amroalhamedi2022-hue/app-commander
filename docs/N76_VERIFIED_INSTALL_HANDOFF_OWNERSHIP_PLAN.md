# N76 — Verified APK snapshot ownership through installer handoff

Date: 2026-10-28  
PR: #1 App Commander; keep Draft/open.

## Engineering question

Can a verified APK cache file be orphaned if the UI coroutine is cancelled while `withContext(Dispatchers.IO)` returns, or if `startActivity` throws before Android's installer is launched?

## Why source inspection alone is not enough

The repository previously returned only a URI, transferring the file handle out of reach. If activity launch failed, the snapshot remained until best-effort expiry cleanup. A cancellable dispatcher hop also needs an ownership reference recorded inside the IO block before its result is dispatched back to the UI. Deterministic JVM tests and a source-contract integration check are needed to verify both paths.

## Research

Reviewed Android's FileProvider guidance and OWASP's URI-grant recommendations:
- Android Developers FileProvider: https://developer.android.com/reference/androidx/core/content/FileProvider
- OWASP MASTG-BEST-0049: https://mas.owasp.org/MASTG/best-practices/MASTG-BEST-0049/

The change preserves the existing private FileProvider, narrow cache subtree and read-only URI grant; it adds no broader sharing or copied third-party implementation.

## Implementation

- Return an internal `VerifiedInstallSnapshot(file, uri)` handle instead of losing the file behind a URI-only result.
- Add `VerifiedInstallHandoff`, which records the handle inside the IO block, retains it across cancelled `withContext` return, and marks ownership transferred only after `startActivity` returns successfully.
- On cancellation or launch/preparation failure, attempt cleanup and suppress cleanup diagnostics onto the original failure.
- Keep a successfully launched installer snapshot on disk for the installer to read asynchronously; expiry cleanup remains a separate safety net.
- Add JVM lifecycle tests plus a source-contract test proving UI ordering.

## Acceptance criteria

1. A prepared snapshot recorded before dispatcher return remains reachable for cleanup even if the UI coroutine is cancelled.
2. Failed/cancelled handoff attempts cleanup exactly once and preserves the original failure.
3. A successful activity launch transfers ownership and does not prematurely delete the file.
4. The private FileProvider path and read-only URI grant remain unchanged.
5. `:app:testDebugUnitTest :app:lintDebug` pass; no APK packaging/signing/upload.
6. Xiaomi Pad 5/HyperOS and real installer read timing remain **NOT TESTED**.
7. No new dependency/permission, root, Device Owner, merge/main write, auto-merge or paid service.

## Evidence

- Source/JVM/Lint CI: pending.
- Artifacts: 0; no APK build.
- Google Drive LIVE sync: **SYNC FAILED** if no Drive/Docs connector is exposed; no sync claim without read-back verification.
