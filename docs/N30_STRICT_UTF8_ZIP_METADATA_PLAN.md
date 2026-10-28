# N30 — Strict UTF-8 validation for APK ZIP names and entry comments

Date: 2026-10-26. App Commander PR #1 remains open Draft; no merge.

## Engineering question

The bounded ZIP preflight compares raw local and central filename bytes and then delegates decoding to Android's `ZipFile`. However, it does not explicitly reject malformed UTF-8 name/comment bytes before that parser is constructed. Android libcore documents UTF-8 decoding for ZIP entry names and comments; malformed sequences can otherwise be handled differently across platform implementations or fail later with parser-specific exceptions.

Can the inspector reject malformed UTF-8 entry names and central-directory entry comments during the existing bounded scan, while preserving valid visible Unicode names even when the EFS flag is clear?

## Source/specification review

- PKWARE APPNOTE §4.4.4 / EFS bit 11 specifies UTF-8 for names and comments when the flag is set: https://support.pkware.com/display/PKZIP/APPNOTE
- Android libcore `ZipFile` documents UTF-8 decoding of entry names and comments: https://android.googlesource.com/platform/libcore/+/00efa8dec72a80bb751cda0dce2683b16c9f1e1c/ojluni/src/main/java/java/util/zip/ZipFile.java
- No third-party implementation is copied and no dependency is added. The implementation uses JDK `CharsetDecoder` with malformed/unmappable input set to REPORT.

## Acceptance criteria

1. Validate every raw central-directory entry name as strict UTF-8 before constructing `ZipFile`.
2. Validate non-empty central-directory entry comments as strict UTF-8 before platform parsing.
3. Convert malformed byte sequences into a stable, sanitized `IOException` diagnostic.
4. Preserve valid UTF-8 names with EFS set or clear, including visible non-Latin paths.
5. Add byte-level regressions for malformed names with EFS set and clear, malformed entry comments, and valid UTF-8 without EFS.
6. Run `:app:testDebugUnitTest :app:lintDebug` on the exact source+test SHA and verify CI on the docs-updated PR head.
7. No APK packaging/artifact upload, release, merge, main write, root, or Device Owner.

## Why source inspection alone is insufficient

The tests mutate ZIP header/name/comment bytes while preserving local/central structural agreement. This demonstrates that the UTF-8 gate itself rejects malformed sequences rather than merely relying on a later local-header mismatch or platform parser exception. The test suite runs against the actual repository checkout.

## Status — SOURCE ACCEPTANCE PASS

- Production commit: `720727e6ac8481aa1c411f1a329d0505f0ef5df2`; production blob `f37f773c7af6da4a71a4b01dd80b1fa07a87a221`.
- Exact source + regression-test SHA: `8d3be7e9d54da0ce1f411e1ae4030bf26df93732`; test blob `172863354ba757fc9c59c57f65088daa7d139ea9`.
- **PASS:** source-only GitHub Actions run [40354680824](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40354680824) on the exact source/test SHA. `:app:testDebugUnitTest` and `:app:lintDebug` passed; log reports **BUILD SUCCESSFUL in 1m 38s**, 36 tasks executed.
- Regression coverage: malformed UTF-8 entry names with EFS set and clear; valid non-Latin UTF-8 name without EFS; malformed central-directory entry comment. The existing valid non-Latin path test remains in the same suite.
- **Artifacts: 0.** No APK packaging, signing, upload, release, merge or main write.
- **NOT TESTED:** Xiaomi Pad 5 / HyperOS, broad third-party APK corpus, actual PackageManager/installer behavior, signature authenticity and malware detection. This is source/JVM acceptance, not device acceptance.
- **Google Drive SYNC FAILED:** connector unavailable in this runtime; LIVE documents were not read or written.
