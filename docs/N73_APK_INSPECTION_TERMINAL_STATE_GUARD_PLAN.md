# N73 — APK inspection terminal-state guard

Date: 2026-10-28

## Engineering question (before CI)
Can a delayed callback for the **same request generation** overwrite a terminal APK inspection state after that request has completed, failed, or been cancelled?

Source inspection is not enough: `ApkInspectionLoadState` currently compares only generation numbers. A cancellation keeps the generation unchanged, so a late completion/failure carrying that same token can still change the state object. Unit regressions should prove that each request accepts exactly one terminal transition and that a later retry remains possible.

## Implementation
- Permit `complete`, `fail`, and `cancel` only when both the generation matches and `loading == true`.
- Treat late duplicate terminal callbacks as no-ops, returning the exact same state instance.
- Preserve the UI's existing busy-lock cleanup and latest-request-wins behavior.
- Add JVM tests for cancel-then-complete/fail, complete-then-fail/cancel/complete, and failed-request stale completion, plus retry after failure.

## Acceptance criteria
1. A cancelled request cannot restore an APK result or invent a parse error.
2. A completed request cannot later be replaced by a second result, error, or cancellation.
3. A failed request cannot later become a success or be cleared by stale cancellation.
4. Stale generations remain no-ops; a new `begin()` still clears old state and permits retry.
5. `:app:testDebugUnitTest` and `:app:lintDebug` pass.
6. No APK packaging/signing/upload. Xiaomi Pad 5/HyperOS, real SAF providers/installer handoff, and Shizuku device flow remain NOT TESTED.

## Boundaries
No new permissions, dependencies, root, Device Owner, or paid service. No merge or main-branch write.

## Evidence

- Production state-machine commit: `823d57443d2562f6e9819236db9dc5aa97bd2b7c`.
- Regression-test commit / source-test CI head: `46503cc9876c599253e4db26ebb0a6633273ca85`.
- **PASS — JVM unit tests + Android static lint:** [CI #40663951308](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40663951308), `:app:testDebugUnitTest :app:lintDebug`, completed successfully.
- Final documentation-head recheck: pending after this evidence update.
- Artifacts: 0 at source-test stage. No APK packaging/signing/upload.
- Xiaomi Pad 5 / HyperOS, real SAF providers/installer handoff, and Shizuku device flow: NOT TESTED.
- No new permissions/dependencies, root, Device Owner, merge, main-branch write, or paid service.
