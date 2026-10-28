# N25 — Isolate stale Shizuku binding callbacks

Date: 2026-10-26. App Commander PR #1 remains open Draft; no merge.

## Engineering question

The controller reused one ServiceConnection object for every bind attempt, and lifecycle callbacks called unscoped state transitions. A delayed callback from an older binding could therefore replace a newer Binder or fail a newer waiter after timeout/retry.

## Acceptance criteria

1. Each bind attempt gets its own ServiceConnection associated with that attempt's waiter.
2. connectedIfCurrent and failIfCurrent ignore callbacks whose waiter is no longer current.
3. A stale connected callback cannot overwrite a newer live Binder; a stale disconnect/death/null callback cannot fail a newer waiter.
4. A timed-out attempt removes only its own callback and never unbinds a newer attempt.
5. Binding failures and stale callbacks make best-effort Shizuku.unbindUserService(args, connection, false) cleanup without killing the UserService.
6. Add deterministic JVM regressions for stale connected/failure callbacks and retain existing timeout, identity and callback recovery tests.
7. Run :app:testDebugUnitTest :app:lintDebug on the exact source/test SHA. No APK packaging, artifact upload, release, merge, main write, root or Device Owner.

## Source review

Reviewed the official RikkaApps/Shizuku-API UserService API and its unbindUserService semantics. The repository LICENSE was checked and is MIT. The app uses the public API; no third-party implementation is copied and no dependency is added. remove=false is intentional so retry cleanup does not request killing the UserService.

## Implementation and evidence

- State helper production commit: `146dd606db517f39ef3b00d6d8621b0684e6b845`.
- Controller production commit: `1ea2667e3cb18fd4e65dee15d112e1b57b26655f`.
- Regression tests / exact source+test SHA: `72f64652541cd01a5b6e6805bc949c7390f03fed`.
- Added per-attempt `ServiceConnection` objects, generation-scoped connect/failure transitions, best-effort callback removal with `remove=false`, owner-only timeout reset, and prompt exceptional completion for callers sharing a failed bind.
- Added JVM regressions for late connect callbacks, late failure callbacks, owner-vs-joiner timeout isolation, existing stale-Binder identity checks, and controller wiring.
- **PASS:** source-only CI run [40276487224](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40276487224) on exact source+test SHA. `:app:testDebugUnitTest` and `:app:lintDebug` passed; `BUILD SUCCESSFUL in 1m 8s`, 36 tasks executed.
- **Artifacts: 0.** The workflow ran no APK packaging, signing, upload or release.
- **NOT TESTED:** physical Xiaomi Pad 5 / HyperOS Shizuku callback ordering and actual service reconnection under process death.
- PR #1 remains open Draft. No merge, main write, root or Device Owner.
