# N21 — Bound APK ZIP metadata before central-directory parsing

Date: 2026-10-26. PR #1 remains open Draft; no merge.

## Engineering question

`ApkArchivePreflight.check()` applied `MAX_ENTRIES` only after opening `java.util.zip.ZipFile`. ZipFile parses the untrusted central directory during construction, before the app's own entry-count and path checks run. Can the inspector reject oversized ZIP metadata from the bounded EOCD tail first, reducing the chance that a malformed APK forces large central-directory allocations before the safety limits apply?

## Acceptance criteria

1. Reject missing/empty and >512 MiB input files before ZIP parsing.
2. Read only the final ZIP32 EOCD search window (22 bytes plus maximum 65,535-byte comment) using a bounded random-access read.
3. Validate EOCD comment length, single-disk layout, entry-count consistency, ZIP32 fields and central-directory bounds before constructing `ZipFile`.
4. Reject declared entry count above `MAX_ENTRIES` and central directory above 32 MiB before the JDK ZIP parser is invoked.
5. Keep ordinary APK ZIP comments supported; reject truncated/malformed EOCD, multi-disk and ZIP64 layouts explicitly or fail closed on invalid bounds.
6. Add regressions proving early rejection from EOCD metadata and acceptance of a normal commented archive; retain all existing APK path, manifest CRC, inflation and canonical bundle tests.
7. Run `:app:testDebugUnitTest` and `:app:lintDebug` on the exact source+test SHA. No APK packaging/build, artifact upload, release, merge or main write.

## Licensed-source review

Inspected Apache Commons Compress's official `ZipFile.java` and repository license metadata (Apache-2.0). It demonstrates central-directory-first parsing and documents the associated metadata handling. No source was copied, no third-party dependency was added; the implementation here is a small preflight guard before the existing JDK `ZipFile`.

## Baseline and implementation

- PR #1 branch: `automation/apk-inspection-safety-20261023`.
- Baseline PR head before N21: `eadc59dc32ad2d8cbd6930831bce7c1eabfccf5e`.
- Production commit: `3b692c18c6583de6a1c3c22eb14f334f532bb5c3`.
- Regression commit: `13a12d1fd1f858e4bc4d7cecbde002fe786942c5`.
- Follow-up compile fix (JUnit `assertTrue` import): `c978642a1509f2be60a2dcd76852a248940c780b`.
- New checks: input size cap, bounded EOCD scan, single-disk and ZIP32 checks, central-directory 32 MiB cap and bounds, entry count cap before `ZipFile`; ordinary ZIP comments remain accepted.
- Regressions: accepts a commented archive; rejects >30,000 declared entries, >32 MiB central-directory metadata and truncated EOCD before `ZipFile`.

## CI result — SOURCE ACCEPTANCE PASS (2026-10-26)

- First run [40256820024](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40256820024) **FAIL**: test source was missing the `assertTrue` import. The production code compiled; JUnit test compilation caught the missing import.
- Fixed in commit `c978642a1509f2be60a2dcd76852a248940c780b`.
- **PASS on exact fixed source+test SHA:** [run 40257037990](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40257037990): `:app:testDebugUnitTest` and `:app:lintDebug` both completed; log ends `BUILD SUCCESSFUL in 1m 39s`.
- Artifacts: none. Workflow did not package or upload an APK.
- Xiaomi Pad 5 / HyperOS APK inspection remains NOT TESTED; this preflight is structural safety, not APK signature verification or malware detection.
- PR #1 remains Draft and unmerged; no main write or Device Owner action.
