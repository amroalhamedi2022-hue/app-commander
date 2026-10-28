# N56 — Shared ZIP entry-type policy for APKs and exported bundles

**Date:** 2026-10-27  
**PR:** #1 — App Commander; keep Draft/open, no merge.

## Engineering question

Does the streaming verifier for exported SAF APK bundles reject central-directory metadata that labels a normal APK payload as a Unix symlink or special filesystem object? The APK preflight already checked Unix host IDs and POSIX type bits, but the separate bundle verifier consumed local entries with `ZipInputStream` and only compared the central directory's names, flags, methods, sizes, CRCs and extra fields. OpenJDK documents that `ZipInputStream` does not read central-directory metadata such as external attributes, so source inspection alone was not enough; crafted archive fixtures were needed.

## Source/spec review

- OpenJDK `ZipInputStream` API note: https://cr.openjdk.org/~pminborg/lazy-constants-third-preview/api/java.base/java/util/zip/ZipInputStream.html
- ZIP format structure and external-attribute context: https://www.loc.gov/preservation/digital/formats/fdd/fdd000362.shtml
- Existing N42 APK preflight policy was reused. No third-party implementation was copied and no dependency was added.

## Implementation

- Extracted the host-aware POSIX type policy into `ZipEntryTypeSafety`, preserving the existing APK preflight behavior.
- Applied the same policy to the bounded streaming bundle verifier by reading the central-directory host-system byte and external-attributes field.
- Unix symlink/special types and regular-file/directory path mismatches now fail before bundle verification reports success. Unknown/unspecified type bits and non-Unix host metadata remain compatible.
- Added pure JVM policy tests and end-to-end bundle-verifier fixtures that patch only the central-directory type metadata for a valid exported bundle.

## Acceptance criteria

1. Valid exported base-only and base+split bundles still verify.
2. Unix regular-file metadata for `base.apk` is accepted.
3. Unix symlink, FIFO/special type, and directory-type metadata on the `base.apk` path are rejected with structural diagnostics.
4. Existing APK preflight, CRC/digest, strict UTF-8, local/central parity, extra-field and ZIP-layout tests remain green.
5. Run `:app:testDebugUnitTest :app:lintDebug --stacktrace` on the exact source/test SHA and again on the docs-updated PR head; confirm zero artifacts.
6. No APK packaging/signing/upload, dependency/permission change, root, Device Owner, merge, main write or auto-merge.

## Verification ledger

- **Source + regression-test SHA:** `263c3a13303c6608046c54cba6d03085e5d62efa`.
- **PASS — JVM tests + Android Lint:** [CI #40559296718](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40559296718), `:app:testDebugUnitTest :app:lintDebug --stacktrace`.
- **Artifacts:** 0 expected; the workflow runs tests and static lint only, no APK packaging.
- **PASS — docs-updated PR-head recheck:** `140cb04d65e315e46b3eb0466c6d4d7f1d1c698f`; source/JVM/Lint [CI #40559928664](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40559928664) completed successfully. The workflow contains no APK packaging step; artifacts 0.
- Xiaomi Pad 5 / HyperOS, real SAF providers, APK signature authenticity and malware detection: **NOT TESTED**.
- Google Drive LIVE sync: **SYNC FAILED** — no Drive/Docs connector is exposed in this runtime; no LIVE read/write or folder metadata verification is claimed.
