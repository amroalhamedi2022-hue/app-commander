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

Reviewed the official RikkaApps/Shizuku-API UserService API and its unbindUserService semantics. The app uses the public API; no third-party implementation is copied and no dependency is added. remove=false is intentional so retry cleanup does not request killing the UserService.

## Status before implementation

- PR #1 head: c575365cafb784f83e9821097d6139c506bbc6b3.
- Existing source CI on that head: PASS, run 40266987835.
- Physical Xiaomi Pad 5 / HyperOS Shizuku callback ordering: NOT TESTED.
