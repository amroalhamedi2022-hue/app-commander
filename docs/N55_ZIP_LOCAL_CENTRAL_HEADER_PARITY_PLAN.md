# N55 — Compare raw ZIP local-header metadata with the central directory

**Date:** 2026-10-27  
**PR:** #1 — App Commander; keep Draft/open, no merge.

## Engineering question

Can a bundle present different general-purpose ZIP flags or filename bytes in a local file header and its central-directory record while the current streaming verifier validates each view separately? OpenJDK documents that `ZipInputStream` reads local headers and does not read the central-directory header, so the verifier must explicitly compare both representations.

## Source review

Reviewed the OpenJDK `ZipInputStream` contract/source and the ZIP APPNOTE local-header/central-directory layouts. References:
- https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/zip/ZipInputStream.java
- https://github.com/wqweto/ZipArchive/blob/master/doc/APPNOTE-6.3.10.TXT

No third-party ZIP implementation was copied and no dependency was added.

## Implementation

- Capture each raw local header from the existing bounded 128 KiB tracking tail at its logical archive offset, accounting for inflater read-ahead.
- Strictly decode and retain the local raw filename bytes, local flags, method and extra fields.
- Compare local and central flags, compression method, filename bytes, offset and extra fields before accepting the bundle.
- Preserve streaming verification; do not extract APK entries or retain APK payloads in memory.

## Acceptance criteria

1. Exporter-produced base-only and base+split bundles still pass the independent streaming verifier.
2. A fixture that toggles only the local UTF-8 general-purpose flag while leaving the central directory and APK bytes untouched is rejected with a local/central metadata diagnostic.
3. Existing malformed UTF-8, CRC, digest, extra-field, central-directory, truncation, expansion-budget and size-limit tests remain green.
4. Run `./gradlew :app:testDebugUnitTest :app:lintDebug --stacktrace` on the exact source/test SHA and again on the docs-updated PR head; confirm zero artifacts.
5. No APK packaging/signing/upload, dependency/permission change, root, Device Owner, merge, main write or auto-merge.

## Verification ledger

- **Source + regression-test SHA:** `326c96a42840f3d1980c5d8d7c6f01ccfee72fa3`.
- **CI:** pending at plan creation; update this ledger and PR comment only after the exact SHA and docs-updated head have completed.
- Xiaomi Pad 5 / HyperOS and real SAF provider behavior: **NOT TESTED**.
- APK artifacts: **0 expected**.
- Google Drive LIVE sync: **SYNC FAILED** if no Drive/Docs connector is exposed; no sync claim without verified read-after-write.
