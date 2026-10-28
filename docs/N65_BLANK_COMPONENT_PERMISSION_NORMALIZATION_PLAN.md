# N65 — Normalize blank installed-component permission metadata

**Date:** 2026-10-28  
**PR:** #1 — App Commander; keep Draft and unmerged.

## Engineering question

Can a null/empty/whitespace component permission be passed into the caller-permission checker, even though the UI treats blank permission text as absent?

## Why source inspection alone is insufficient

The regression crosses generic component mapping and the permission-check callback. A JVM test must prove that the callback receives null (not an empty/whitespace string) and that the mapped row retains the same visible no-permission semantics.

## Implementation

- Normalize null, empty and whitespace-only required-permission metadata to null before invoking the callback.
- Preserve every nonblank permission string exactly; do not trim or rewrite valid permission names.
- Keep exported/enabled metadata, component ordering, and the rule that only Activities are launchable unchanged.
- Add a JVM regression that records the exact callback argument for whitespace-only metadata.

## Acceptance criteria

1. Blank permission metadata never reaches the permission-check callback as a blank string.
2. The resulting row has no displayed required permission and records the callback's no-required-permission result.
3. Existing Activity/Service/Receiver permission-gate and repository/UI contract tests pass.
4. Source/JVM tests plus Android lint pass on the exact docs-updated PR head; no APK packaging/signing/upload, merge, main write, root, Device Owner or auto-merge.

## Reference review

- Android's official PackageManager reference documents ComponentInfo as the shared metadata base for Activities and Services: https://developer.android.com/reference/android/content/pm/package-summary
- The change is a defensive normalization at the existing boundary; no external source was copied and no dependency or permission was added.

## Verification ledger

- Production change: ComponentEntryMapper normalizes blank permission metadata before calling the permission checker.
- Regression: ComponentEntryMapperTest verifies whitespace-only input maps to null and the callback receives null.
- Exact source/test and docs-updated PR-head CI: PENDING.
- Xiaomi Pad 5 / HyperOS and real installed-package metadata: NOT TESTED.
- APK artifacts: 0 expected; no APK build requested.
- Google Drive LIVE sync: SYNC FAILED if the Drive/Docs connector is unavailable; no sync claim without read-back and folder metadata verification.
