# N36 — Preserve inventory rows when optional PackageManager metadata fails

**Date:** 2026-10-27  
**PR:** #1 — App Commander (keep Draft; no merge)

## Engineering question

Can a transient per-package metadata error (label loading, version lookup, or update timestamp) silently remove an installed application from the inventory even when the base installed-application snapshot already contains it?

The current source wraps the entire row construction in `runCatching { ... }.getOrNull()` and uses `.mapNotNull`, so any one optional metadata failure discards the whole row. Source inspection identifies the failure path; deterministic JVM tests are needed to verify each fallback and ensure normal rows remain unchanged. This is a source/test milestone, not a device-runtime claim.

## Acceptance criteria

1. A valid package name from the installed-application snapshot remains represented when label, version, or update-time lookup throws or returns blank.
2. Label fallback is the package name; unknown version is `—`; unavailable/negative update time is `0L`.
3. Valid metadata is trimmed and preserved; target SDK, system/enabled flags, usage counters, and package identity remain intact.
4. A blank package identity is excluded explicitly without failing other rows.
5. The production repository path calls the tested mapper and no longer drops a row because optional metadata failed.
6. JVM tests cover exception fallbacks, blank values, metadata preservation, invalid identity isolation, and resolved-label sorting.
7. Run `:app:testDebugUnitTest :app:lintDebug --stacktrace` on the exact source/test commit and re-check the latest PR head. No APK packaging, signing, artifact upload, release, merge, main write, root, or Device Owner.

## Source/license review

- Reviewed Android's official `PackageManager` API contract for `getInstalledApplications` and `getPackageInfo`: https://developer.android.com/reference/android/content/pm/PackageManager
- Inspected the Android Open Source Project `PackageManager.java` API documentation under Apache-2.0: https://android.googlesource.com/platform/frameworks/base/%2B/android-8.0.0_r4/core/java/android/content/pm/PackageManager.java
- No third-party implementation code was copied; this is a small local mapper around the existing Android API calls. No new dependency or permission.

## Scope and safety

- No change to package visibility, Shizuku authority, runtime permission policy, SAF, APK parsing/export, or installation behavior.
- A failure of the top-level `getInstalledApplications` query still fails the refresh and shows the existing retry state; the mapper only isolates optional per-row metadata.
- Device-specific PackageManager behavior on Xiaomi Pad 5 / HyperOS remains **NOT TESTED**.

## Implementation

- Added pure JVM `AppInventoryCandidate` / `AppInventoryRowMapper`.
- `AppRepository.loadApps()` now takes the base inventory as membership and queries optional `PackageInfo` metadata best-effort; it maps every valid candidate and sorts by the resolved label.
- Added `AppInventoryRowMapperTest.kt` with five tests for failure fallback, blank metadata, non-metadata field preservation, invalid identity isolation, and ordering.

## Exact source/test CI evidence

- Source/test commit SHA: **PENDING**.
- `:app:testDebugUnitTest :app:lintDebug`: **PENDING**.
- Artifacts: **0 expected**; workflow must remain source-only.
- Device/HyperOS runtime: **NOT TESTED**.
- Google Drive LIVE handoff/index: **SYNC FAILED** if no Drive/Docs connector is available; do not claim sync without read-back verification.
