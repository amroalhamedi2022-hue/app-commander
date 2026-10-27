# N46 — Sanitize optional UsageStats values before inventory display

**Date:** 2026-10-27  
**PR:** #1 — App Commander; keep Draft, no merge.

## Engineering question

N37 made UsageStats access/provider failures non-fatal, but a successful provider response was still trusted verbatim. Could negative foreground durations, future last-used timestamps, blank map keys, or durations larger than the requested 24-hour window leak into the installed-app inventory and distort the usage display?

## Source review

- Reviewed Android's official UsageStatsManager API contract: https://developer.android.com/reference/android/app/usage/UsageStatsManager
- Reviewed the official UsageStats fields: https://developer.android.com/reference/android/app/usage/UsageStats
- `queryAndAggregateUsageStats(begin, end)` is scoped to the supplied time window and returns values keyed by package name. This change normalizes optional values only; it does not infer usage events or alter installed-package membership.
- No third-party code copied and no new dependency or permission.

## Acceptance criteria

1. Preserve existing N37 fail-closed behavior when access is denied, the clock is invalid, or the provider throws/returns null.
2. Drop blank package keys from a successful usage snapshot.
3. Clamp foreground duration to the valid non-negative range and no greater than both the requested 24-hour window and elapsed wall-clock time.
4. Clamp last-used timestamps to `0..nowMillis`; future/negative provider values cannot appear as valid recent activity.
5. Keep normal valid values unchanged and preserve the installed-app inventory if usage metadata is unavailable.
6. Add JVM tests for negative, oversized, future, blank-key, valid, and invalid-clock cases.
7. Run `:app:testDebugUnitTest :app:lintDebug --stacktrace` on the exact source/test SHA, then recheck the docs-updated PR head. Confirm zero artifacts.
8. No APK packaging/signing/upload, release, merge, main write, auto-merge, root, or Device Owner.

## Evidence boundary

This proves deterministic JVM normalization and Android source compilation/lint only. Real Xiaomi Pad 5 / HyperOS UsageStats values and UI display remain **NOT TESTED**.

## Verification results — 2026-10-27

- **Exact source/test SHA:** `e1718717ed390700af2be391cfd6d590fa134149`.
- **PASS:** [source-only CI #40478165225](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40478165225); `:app:testDebugUnitTest :app:lintDebug --stacktrace`, `BUILD SUCCESSFUL in 1m 47s`.
- **Artifacts:** zero confirmed by the run artifact endpoint. No APK packaging, signing, or upload.
- The subsequent `PROJECT_STATUS.md` commit is documentation-only; the passing CI above is explicitly tied to the source/test SHA, not misrepresented as a test run on the docs head.
- **NOT TESTED:** real Xiaomi Pad 5 / HyperOS UsageStats values, permissions flow, and UI display.

