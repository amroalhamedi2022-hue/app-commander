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

