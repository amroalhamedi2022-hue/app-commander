# N50 — Preserve and surface SAF partial-document cleanup failures

**Date:** 2026-10-27  
**PR:** #1 — App Commander; remain Draft, no merge.

## Engineering question

If exporting a base APK or APK bundle fails and SAF refuses to delete the newly created partial document, can the caller distinguish that cleanup failure from a successful cleanup without losing the original export/readback error? Source review found that `withPartialDocumentCleanup` discards cleanup exceptions, while the repository ignores the Boolean returned by `DocumentsContract.deleteDocument`; a provider returning `false` is therefore treated as if deletion were confirmed. Source inspection alone cannot prove the original exception remains primary across both export paths, so deterministic JVM tests are required.

## Acceptance criteria

1. Cleanup only targets the fresh CreateDocument URI; a URI not confirmed as a document fails cleanup closed.
2. A `false` result from the provider's delete operation is recorded as unconfirmed cleanup.
3. The original export/readback failure remains the thrown exception; cleanup diagnostics are attached as a typed suppressed cause and never replace it.
4. UI copy distinguishes a confirmed/attempted cleanup from a deletion that could not be confirmed and advises manual removal only in the latter case.
5. JVM tests cover successful cleanup, denied/false cleanup, preservation of the original failure, and that the delete callback is not invoked for an unconfirmed document URI.
6. Run `./gradlew :app:testDebugUnitTest :app:lintDebug --stacktrace` on the exact source/test SHA and re-check the docs-updated PR head. Zero APK artifacts.
7. No new dependency, permission, privileged operation, APK packaging/signing/upload, merge, main write, root, Device Owner or auto-merge.

## Open-source/API review

The implementation uses the existing Android `DocumentsContract` SAF API and project-owned cleanup wrapper; no third-party implementation is copied and no dependency is added. A successful ZIP/hash verification remains distinct from APK signature authenticity.

## Verification ledger

Pending source/test CI and independent PR-head recheck. Xiaomi Pad 5 and real SAF provider deletion semantics remain **NOT TESTED**.

## Implementation update — 2026-10-27

- `InstalledApkExport.withPartialDocumentCleanup` now requires an explicit Boolean deletion result. A provider `false` or thrown cleanup error adds a typed `PartialDocumentCleanupDiagnostic` as a suppressed exception while preserving the original export/readback failure as the thrown exception.
- `AppRepository` calls deletion only through `deleteDocumentIfConfirmed`; if `DocumentsContract.isDocumentUri` is false, deletion is not invoked and cleanup remains unconfirmed.
- Base APK and bundle export UI now distinguishes confirmed deletion from unconfirmed cleanup and recommends manual inspection/removal only when necessary.
- Added JVM regressions for false delete, thrown delete, original-error preservation, and fail-closed URI confirmation.
- Source/test CI and the docs-updated PR-head recheck are **PENDING**. Xiaomi Pad 5 and real SAF provider behavior remain **NOT TESTED**.

## Verification update — 2026-10-27

- **Source/test head after fixture correction:** `6173145507847dcbe33839c0077ab24bcf0e49f4`.
- **PASS — JVM tests + Android Lint:** [CI #40516604932](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40516604932), `:app:testDebugUnitTest :app:lintDebug`, `BUILD SUCCESSFUL in 1m 40s`.
- The first validation attempt [CI #40511432761](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40511432761) exposed three test-fixture lambdas returning `Int`/`Unit` after cleanup became Boolean. Fixtures now return explicit `true`; the passing run includes the correction.
- No APK packaging/signing/upload; zero artifacts expected by workflow. Source/test behavior is validated on JVM only.
- **NOT TESTED:** Xiaomi Pad 5/HyperOS and real SAF provider delete/readback behavior. The docs-updated PR-head recheck is pending.

## Documentation-head recheck — 2026-10-27

- **PASS:** docs-updated PR head `f995a3066f1c37918cf8d0ddf567358a2a6a4a89` was checked by [CI #40517821800](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40517821800); logs report `BUILD SUCCESSFUL in 1m 12s` for JVM tests and lint.
- The latest documentation append itself will trigger a further source-only PR check; do not treat the run above as verification of a later SHA.
- No APK tasks or artifacts.
