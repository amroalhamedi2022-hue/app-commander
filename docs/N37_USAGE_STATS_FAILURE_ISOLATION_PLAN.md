# N37 — Optional usage-stat provider failure isolation

**Date:** 2026-10-27  
**PR:** #1 — App Commander (keep Draft; no merge)

## Engineering question

Can the installed-app inventory still load when Usage Access is denied or the AppOps/UsageStats service is unavailable, throws, or returns a null snapshot? Usage counts are optional display metadata; they must not be able to turn the primary PackageManager inventory into a failed refresh.

## Acceptance criteria

1. AppOps lookup failures are reported as unavailable usage access, not propagated into the inventory/UI path.
2. UsageStats is queried only when access is allowed and the clock is valid.
3. A provider exception or null result yields an empty usage snapshot while preserving the independently loaded app list.
4. A successful provider result is returned unchanged and uses the existing 24-hour window.
5. The beginning timestamp is clamped to zero for synthetic/near-epoch clocks; zero time skips provider calls.
6. JVM tests cover denial, access-check exception, query exception, null result, successful map/window, and invalid-clock handling.
7. Exact source/test SHA and CI are inspected; source-only `:app:testDebugUnitTest :app:lintDebug` must pass with zero artifacts. No APK packaging/build, signing, upload, merge, main write, root, or Device Owner.

## Implementation

- Added pure JVM UsageStatsSnapshot to wrap the access gate, bounded 24-hour query window and failure fallback.
- AppRepository.hasUsageAccess now safely returns false if AppOps lookup fails or the service is unavailable.
- AppRepository.usageByPackage delegates the optional provider read to UsageStatsSnapshot; the PackageManager app list remains independent.
- Added six UsageStatsSnapshotTest cases for failure isolation and range semantics.

## Source/license review

Reviewed the official Android UsageStatsManager and AppOpsManager API contracts. The implementation uses existing platform APIs; no third-party source was copied, and no dependency, permission or network access was added.
- https://developer.android.com/reference/android/app/usage/UsageStatsManager
- https://developer.android.com/reference/android/app/AppOpsManager

## Evidence

- Helper commit: e21f948e767db86f6b072d361cabed8f2240fd8f.
- Repository integration commit: 3819900d649708e4d854504bd0ecade402a662db.
- Test commit / exact source-test SHA: a450c404ac7f7570d23bcd7b93b5a073803aabcb.
- **PASS — source/JVM/lint CI:** [run #40434828097](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40434828097), `:app:testDebugUnitTest :app:lintDebug`, BUILD SUCCESSFUL in 1m 41s, 36 tasks.
- **Artifacts: 0 confirmed**. This workflow did not package, sign or upload an APK.
- Xiaomi Pad 5 / HyperOS AppOps and UsageStats behavior: **NOT TESTED**.
- SAF, Shizuku, APK authenticity and visual UI acceptance remain separate device/runtime gates.
