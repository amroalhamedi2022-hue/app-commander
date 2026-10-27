# N45 — Explicit locale-independent inventory ordering

**Date:** 2026-10-27  
**PR:** #1 — App Commander; keep Draft, no merge.

## Engineering question

Installed-app ordering must not change when the device or process locale changes. N44 uses canonical Unicode normalization and invariant lowercase; make the locale contract explicit at the implementation boundary and exercise it under Turkish and English defaults, where locale-sensitive casing can otherwise differ.

## Planned production change

- Use Java/Kotlin's standard `Locale.ROOT` lowercase mapping in the inventory comparison key, retaining NFC before and after case conversion.
- Keep original `AppEntry.label` unchanged; sort tie-breaks remain canonical label, canonical package name, then raw package name.
- Add no dependency, permission, network access or copied third-party implementation.

## Acceptance criteria

1. The same candidate set yields identical package order with English and Turkish process defaults.
2. Canonically equivalent composed/decomposed labels retain the N44 stable package tie-break.
3. Display labels are preserved exactly after mapping.
4. Existing metadata fallback, row membership, non-metadata fields and deterministic ordering tests remain passing.
5. Run source-only `:app:testDebugUnitTest :app:lintDebug` on the exact source/test SHA, then recheck the docs-updated PR head. Confirm zero artifacts.
6. No APK packaging/signing/upload, release, merge, main write, auto-merge, root or Device Owner.

## Evidence boundary

This proves JVM ordering logic and static lint only. Xiaomi Pad 5/HyperOS PackageManager ordering and UI rendering remain NOT TESTED.
