# N47 — Fail-fast validation before Shizuku dispatch

**Date:** 2026-10-27  
**PR:** #1 — App Commander; keep Draft, no merge.

## Engineering question

The elevated CommandUserService already validates privileged requests, but the app-side controller previously checked Shizuku permission and obtained/bound the UserService before that validation ran. Some destructive entry points also called a broad character filter before returning a normal result for malformed package names. Can invalid requests be rejected before permission checks, Binder binding or command dispatch, while retaining the independent elevated-service validation?

## Source and license review

- Reviewed the existing PrivilegedRequestValidator, CommandUserService and ShizukuController paths.
- Reviewed the official [RikkaApps/Shizuku repository](https://github.com/RikkaApps/Shizuku), whose code is licensed Apache-2.0, and its documented Binder/UserService model. No Shizuku implementation code was copied and no dependency, permission, root path or Device Owner flow was added.
- The existing elevated UserService validator remains in place as a second, independent gate.

## Acceptance criteria

1. Unsupported operations, malformed package names, invalid permission names and invalid component names return a failed CommandResult before the dispatch callback is invoked.
2. Valid requests invoke dispatch exactly once and preserve the service result.
3. ShizukuController runs preflight before hasPermission() and obtainService(); destructive operations validate the package before invoking CriticalPackageGuard.
4. Existing elevated CommandUserService validation and critical-package/component/permission checks remain unchanged.
5. Run source/JVM tests and Android lint on the exact source/test SHA using the existing free source-only workflow. No APK packaging, signing or artifact upload.

## Implementation

- Added PrivilegedCommandPreflight as a pure suspendable gate with a deterministic no-dispatch contract.
- Reused PrivilegedRequestValidator.packageNameError in the full request validator and before the destructive app-side guard, so malformed package input returns a controlled CommandResult instead of reaching later operations.
- Wrapped ShizukuController.exec in preflight before entering Dispatchers.IO; the existing elevated service still validates every request.
- Added JVM tests for malformed package/operation/permission/component input, zero dispatches on rejection, exactly-once dispatch for valid input, and production call ordering.

## Verification — 2026-10-27

- **Current source/test SHA:** a3f545ba00bc9970e96bc609352f44257ee9777b.
- **PASS — source/JVM/Lint:** [GitHub Actions run #40484909419](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40484909419), job source-checks; JVM tests and Android lint succeeded.
- **Artifacts:** 0 confirmed; the workflow contains no APK packaging, signing or upload task.
- **NOT TESTED:** real Shizuku Binder/ADB permission flow, Xiaomi Pad 5/HyperOS command behavior, SAF providers and destructive-command behavior on a physical device. Unit tests do not claim device acceptance.
- No root, Device Owner provisioning, APK, release, merge, main write or auto-merge.
- **Google Drive LIVE:** SYNC FAILED — no Google Drive/Docs connector is exposed in this runtime; no LIVE read/write or folder metadata verification is claimed.
