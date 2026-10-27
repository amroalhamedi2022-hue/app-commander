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