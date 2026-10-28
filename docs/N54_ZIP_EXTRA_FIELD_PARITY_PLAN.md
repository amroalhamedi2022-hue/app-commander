# N54 — Cross-check ZIP local and central extra fields in APK bundles

**Date:** 2026-10-27  
**PR:** #1 — App Commander; keep Draft/open, no merge.

## Engineering question

The streaming bundle verifier compares local-entry names, sizes, compressed sizes and CRCs with the raw central directory, but its saved local-entry record omits extra-field bytes. Can an archive keep APK payload digests and names valid while presenting different extra-field metadata in the local header and central directory?

## Planned production change

- Snapshot each local entry's extra-field bytes from the parsed `ZipEntry` before moving to the next entry.
- Compare the exact bounded raw central-directory extra bytes against that local snapshot, including length.
- Reject mismatches with a generic ZIP-structure IOException before reporting bundle verification success.
- Do not interpret arbitrary extra-field payloads, extract files, inflate additional content, or broaden the accepted archive format.

## Source review

Reviewed the official Java SE `ZipEntry.getExtra()` / `setExtra()` contract and the separate local-header / central-directory extra-length fields. Reference: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/ZipEntry.html. This uses existing JDK APIs; no third-party ZIP code or dependency is copied.

## Acceptance criteria

1. An exporter-produced base-only and split-APK bundle still round-trips through the independent streaming verifier.
2. A central-directory extra-field byte changed without changing the local entry is rejected with an explicit structural diagnostic.
3. A malformed/truncated extra field is rejected by the existing ZIP parser or the cross-check; no attacker-controlled bytes are echoed in errors.
4. Existing digest, CRC, central-directory, manifest UTF-8, expansion-limit, cancellation, and cleanup tests remain green.
5. Run `:app:testDebugUnitTest :app:lintDebug --stacktrace` on the exact source/test SHA and again on the docs-updated PR head; confirm zero artifacts.
6. No APK packaging/signing/upload, new dependency/permission, root, Device Owner, merge, main write, or auto-merge.

## Verification ledger

- **Exact source/test SHA:** `ba790539abdd60319e7ab8f4d70bf8ca54571679`.
- **PASS — JVM tests + Android Lint:** [CI #40545628852](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40545628852), `:app:testDebugUnitTest :app:lintDebug --stacktrace`.
- **PASS — documentation-updated PR-head recheck:** head `7f114203ea964c451d80d59251cd3c957f51d139`, [CI #40545999579](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40545999579) PASS. The final recheck after this verification-note commit will be recorded in the PR comment; no further repository edits are planned.
- Xiaomi Pad 5 / HyperOS and real SAF provider: **NOT TESTED**.
- APK artifacts: **0 expected**.
- Google Drive LIVE sync: **SYNC FAILED** if no Drive/Docs connector is exposed; no sync claim without verified read-after-write.
