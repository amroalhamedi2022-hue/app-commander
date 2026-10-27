# N57 — Restrict exported ZIP extra fields to canonical timestamp metadata

**Date:** 2026-10-27  
**PR:** #1 — App Commander; keep Draft/open, no merge.

## Engineering question

N55/N56 already compare local and central ZIP metadata and reject unsafe Unix entry types. However, byte-identical local/central extra fields could still carry an unsupported field ID or timestamp flags. Because the verifier only accepts archives produced by App Commander's own exporter, accepting arbitrary opaque extra fields expands the parser-differential surface without providing a product benefit.

## Source review

- Reviewed the OpenJDK `ZipEntry` source/API: `setTime(0L)` uses DOS time plus an extended timestamp field when the date is outside the DOS range. Reference: [OpenJDK ZipEntry source](https://code.googlesource.com/edge/openjdk/%2B/refs/heads/master/jdk/src/share/classes/java/util/zip/ZipEntry.java).
- ZIP extra-field ID `0x5455` is the extended timestamp field; the canonical exporter uses epoch zero. The verifier remains specific to this format and does not copy third-party ZIP code or add dependencies.

## Implementation

- Validate the local extra-field bytes as exactly one 9-byte UT field: ID `0x5455`, data length 5, modification-time flag only, timestamp zero.
- Require the central-directory copy to match the validated local bytes and independently satisfy the same canonical rule.
- Reject unsupported/malformed extra fields even if the local and central copies are identical.
- Preserve streaming verification, payload hashing, size limits, and the no-extraction behavior.

## Acceptance criteria

1. Exporter-produced base-only and base+split bundles still verify.
2. Existing local/central mismatch, UTF-8, CRC/digest, size, type, truncation and inflation-budget regressions remain green.
3. A crafted archive that changes both local and central extra-field IDs to the same unsupported value is rejected.
4. Run `:app:testDebugUnitTest :app:lintDebug --stacktrace` on the exact source/test SHA and again on the final docs-updated PR head; confirm zero artifacts.
5. No APK packaging/signing/upload, new dependency/permission, root, Device Owner, merge, main write, or auto-merge.

## Verification ledger

- **Source + regression-test commit:** `e3dc1e9a1a7ab68a6be0b224d594fede2031acef`.
- Source/JVM/Lint run [#40563943994](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40563943994) was in progress when this entry was prepared; no PASS is claimed for that run yet.
- Final docs-head CI result will be recorded in the PR conversation after the recheck.
- Xiaomi Pad 5 / HyperOS, real SAF provider behavior, APK signer authenticity and malware detection: **NOT TESTED**.
- APK artifacts: **0 expected**; this workflow tests and lints only.



## Follow-up correction — 2026-10-27

- The first source/test attempt `e3dc1e9a1a7ab68a6be0b224d594fede2031acef` **FAILED** CI #40563943994 because the older generic ZIP fixture used the current wall-clock timestamp and therefore failed the new canonical-extra check before reaching the malformed-manifest assertion. This was a fixture-ordering failure, not a production regression.
- Corrected the shared test archive helper to set `ZipEntry.time = 0L`, matching the actual exporter. This keeps fixtures canonical so each negative test reaches the behavior it intends to exercise.
- **PASS — corrected source + tests:** `dd4fc2ce63dfe6a0139c2ea2726694312e2cec30`, [CI #40564471753](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40564471753), `:app:testDebugUnitTest :app:lintDebug --stacktrace`.
- Verified on a local OpenJDK 21 probe that `ZipEntry.time = 0L` serializes identical 9-byte local and central extra fields: `55 54 05 00 01 00 00 00 00`. Android libcore's ZIP implementation also documents the extended timestamp path for times before 1980: [Android 35 ZipOutputStream source](https://android.googlesource.com/platform/prebuilts/fullsdk/sources/%2B/refs/heads/main/android-35/java/util/zip/ZipOutputStream.java).
- Artifacts: **0**. The workflow does not package an APK.
- Final docs-updated PR-head recheck remains required and will be recorded in the PR conversation.
