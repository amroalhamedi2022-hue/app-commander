# N52 — Verify APK Inspector temporary-cache cleanup

**Date:** 2026-10-27  
**PR:** #1 — App Commander; keep Draft/open, no merge.

## Engineering question

ApkCacheCopy.withTemporaryApk used File.delete() in finally but ignored its Boolean result. Java/Android File.delete() can return false without throwing, so APK inspection could report success while leaving a potentially large temporary APK in private cache. Source inspection alone does not prove cleanup-failure handling preserves the original parser/provider exception; deterministic JVM tests are required.

## Implementation

- Check whether the temp file is already absent, invoke deletion when needed, then verify absence after the delete callback.
- If inspection succeeds but cleanup cannot be confirmed, fail with a typed TemporaryApkCleanupDiagnostic rather than silently leaking the file.
- If inspection already failed or was cancelled, preserve the exact original throwable and attach the cleanup diagnostic as suppressed.
- Keep the deletion callback injectable only through the internal overload so false/throwing cleanup can be tested deterministically.
- Do not use deleteOnExit; Android's process lifecycle does not guarantee VM termination cleanup.

## Acceptance criteria

1. Existing success and parser-failure tests continue to prove normal temp files are deleted.
2. A false delete result after successful inspection becomes a typed failure.
3. A false delete result after parser failure does not replace the original exception identity and is attached as suppressed.
4. A thrown cleanup exception is retained as the diagnostic cause.
5. Run ./gradlew :app:testDebugUnitTest :app:lintDebug --stacktrace on the exact source/test SHA, then re-check the documentation-updated PR head.
6. No APK packaging/signing/upload, new dependency/permission, root, Device Owner, merge, main write or auto-merge.

## Open-source/API review

Reviewed Android's official app-specific storage guidance and the Android/JDK File.delete() contract: cache files should be managed by the app, and delete() returns a Boolean that callers must check. This is a small project-owned correction; no third-party implementation or dependency is copied.

## Verification ledger — 2026-10-27

- **Source/test SHA:** d2f756368d8bf25b813396d70574382090320c80.
- **PASS — JVM + Android Lint:** [CI #40526102343](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40526102343), job source-checks completed successfully in 2m 22s. Command: :app:testDebugUnitTest :app:lintDebug --stacktrace.
- **Artifacts: 0**; workflow did not package/sign/upload an APK.
- The three new regressions cover false deletion after success, suppressed diagnostics without replacing the original parser failure, and a thrown cleanup callback retained as cause.
- Xiaomi Pad 5 / HyperOS: **NOT TESTED**.
- Google Drive LIVE sync: **SYNC FAILED** in this runtime because no Drive/Docs connector is exposed; no LIVE read/write or folder metadata verification is claimed.
- PR #1 remains Draft/open; no merge, main write, root, Device Owner or auto-merge.
