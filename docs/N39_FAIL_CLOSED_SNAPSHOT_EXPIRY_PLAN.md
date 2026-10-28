# N39 — Fail-closed verified-snapshot expiry metadata

**Date:** 2026-10-27  
**PR:** #1 — App Commander; keep Draft, no merge.

## Engineering question

The snapshot cleanup is described as best-effort, but a denied directory listing or a per-file metadata exception can currently abort the install preparation. Also, `File.lastModified() == 0` is ambiguous: it can mean an epoch timestamp or unavailable metadata, yet the current predicate treats it as an ancient file. A focused regression is needed to show cleanup cannot break installation or delete a snapshot whose age is unknown.

## Acceptance criteria

1. Directory metadata/listing denial returns zero deletions rather than aborting the install flow.
2. An inaccessible entry is skipped without preventing cleanup of other valid expired snapshots.
3. A non-positive modification timestamp is treated as unknown and preserved.
4. Only regular files with the exact private snapshot prefix/suffix and a positive timestamp older than the cutoff are eligible; fresh, future-dated and unrelated files remain.
5. Source-only `:app:testDebugUnitTest :app:lintDebug` passes on the exact updated PR head, with zero artifacts.
6. No APK packaging, signing, upload, release, merge, main write, root, Device Owner or auto-merge.

## Implementation

- Catch `SecurityException` at directory and per-entry filesystem boundaries; cleanup remains best-effort.
- Fail closed for unknown/non-positive modification timestamps.
- Add JVM regressions for listing denial, per-entry metadata denial and epoch/unknown timestamps.

## Source/license review

This hardens the repository's own Kotlin lifecycle logic using the existing JDK/Android `java.io.File` API. No third-party code, dependency, permission, network access or paid service is added. This is cache housekeeping, not APK signature authentication or malware detection.

## Verification ledger

- **PASS — exact source/test head:** `676454c507e057a42adc8460265a0ea85debb5c8`.
- **PASS — source/JVM/Lint CI:** [run #40446325452](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40446325452), `:app:testDebugUnitTest :app:lintDebug`, job `source-checks` completed successfully.
- **Artifacts: 0 confirmed** from the run artifact listing; no APK packaging/signing/upload.
- Xiaomi Pad 5 / HyperOS and actual installer handoff: **NOT TESTED**.
- Google Drive LIVE sync: **SYNC FAILED** — this runtime exposes no Google Drive/Docs connector; no LIVE document read/write or folder metadata verification is claimed.
