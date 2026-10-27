# N43 — Deterministic installed-app inventory ordering

**Date:** 2026-10-27  
**PR:** #1 — App Commander; keep Draft, no merge.

## Engineering question

Does the installed-app list remain stable when PackageManager returns packages in a different order and multiple applications resolve to the same display label? Source inspection showed that sorting by label alone preserves provider order for ties, so rows can jump on refresh.

## Acceptance criteria

1. Sort first by resolved display label case-insensitively.
2. Use normalized package name as the stable tie-breaker; use the original package name as a final deterministic tie-breaker.
3. Do not change membership, metadata fallbacks, usage fields, enabled/system state, or label rendering.
4. JVM regression tests feed equivalent candidate sets in multiple input orders and assert identical package ordering for labels differing only by case.
5. Run source-only `:app:testDebugUnitTest :app:lintDebug` on the exact source/test SHA, then independently recheck the docs-updated PR head. Confirm zero artifacts.
6. No APK packaging/signing/upload, release, merge, main write, root, Device Owner or auto-merge.

## Scope

This is deterministic inventory presentation, not a claim that installed-app metadata or UI rendering has been tested on Xiaomi Pad 5 / HyperOS. Device acceptance remains **NOT TESTED**.


## Validation results

- Exact source/test SHA: `7621e0f04eec4b91ae2b03a4c6d7d9a6e7b80661`.
- **PASS:** source-only [Actions run #40465466282](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40465466282), `:app:testDebugUnitTest :app:lintDebug`, BUILD SUCCESSFUL in 1m 43s.
- Artifacts: 0 confirmed; no APK packaging, signing or upload.
- Xiaomi Pad 5 / HyperOS remains NOT TESTED; docs-updated PR-head recheck is pending and will be recorded in the PR discussion.
