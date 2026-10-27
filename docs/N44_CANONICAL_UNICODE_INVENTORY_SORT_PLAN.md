# N44 — Canonical Unicode keys for deterministic app inventory

**Date:** 2026-10-27  
**PR:** #1 — App Commander; keep Draft, no merge.

## Engineering question

Can two installed applications whose labels are canonically equivalent Unicode strings (for example, precomposed and decomposed accented characters) receive a stable package-name tie-break regardless of PackageManager result order, while preserving the original display labels?

## Production change

- The inventory sort key normalizes to Unicode NFC, applies Kotlin's invariant-locale lowercase mapping, then normalizes to NFC again.
- Sort order remains resolved display label first, package name second, original package name last.
- Only the comparison key changes; the stored/displayed label is not rewritten.
- Uses Java's built-in `java.text.Normalizer`; no new dependency or copied third-party implementation.

## Acceptance criteria

1. Canonically equivalent labels compare equally for the primary sort key.
2. Package-name tie-break produces the same ordering when candidate input is reversed.
3. `AppEntry.label` retains the original composed/decomposed text.
4. Existing N43 case-insensitive ordering, metadata fallback, row membership and non-metadata fields remain unchanged.
5. Run source-only `:app:testDebugUnitTest :app:lintDebug` on the exact source/test SHA, then independently recheck the docs-updated PR head. Confirm zero artifacts.
6. No APK packaging/signing/upload, release, merge, main write, root, Device Owner or auto-merge.

## Reference review

- [Java Normalizer.Form NFC](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/text/Normalizer.Form.html): NFC is canonical decomposition followed by canonical composition.
- [Kotlin lowercase API](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/lowercase.html): no-argument `lowercase()` uses invariant-locale Unicode mapping.

## Status

- Source/test SHA: `f9bc35f77a71f9350cc0a6a2f04f5443185dca99`.
- CI: **PENDING** at plan creation; update this section only after the exact run is observed.
- Xiaomi Pad 5 / HyperOS UI ordering: **NOT TESTED**.
- Google Drive LIVE sync: **SYNC FAILED** if no Drive/Docs connector is available; no sync claim without read-back and folder metadata verification.
