# N23 — Validate APK central-directory records before ZipFile

Date: 2026-10-26. App Commander PR #1 remains open Draft; no merge.

## Engineering question

The APK preflight checked EOCD count/size fields before opening `ZipFile`, but did not parse the bounded central-directory record inventory itself. Could inconsistent EOCD counts, malformed record lengths, entry-level ZIP64 sentinels, or local-header offsets pass the preflight and reach the platform ZIP parser?

## Acceptance criteria

1. Before `ZipFile(file)`, parse exactly the EOCD-declared number of central-directory records within the already bounded directory range.
2. Reject bad central-directory signatures, truncated/overlong record inventories, empty or overlong names, nonzero per-entry disk numbers, ZIP64 sentinel sizes/offsets, and local-header offsets at or beyond the central directory.
3. Preserve valid ordinary ZIP32 APKs with the APK signing block located before the central directory; this parser does not interpret or authenticate that signing block.
4. Regression tests cover EOCD count mismatch and an entry-level ZIP64 sentinel.
5. Run `./gradlew :app:testDebugUnitTest :app:lintDebug` on the exact source/test SHA. No APK packaging, artifact upload, release, merge, main write, root, or Device Owner.
6. Actual APK corpus compatibility and Xiaomi Pad 5 behavior remain NOT TESTED.

## Implementation

`ApkArchivePreflight.preflightCentralDirectory` now invokes a bounded record walker after EOCD range/count limits and before constructing `ZipFile`. The walker reads only each 46-byte fixed header, validates fields and lengths against the bounded directory, seeks across variable fields without allocating entry-name/comment buffers, and requires the declared record count to consume the directory exactly. ZIP64 entry sentinel sizes/offsets and per-entry multi-disk records fail closed.

## Source/license review

Reviewed the OpenJDK `ZipFile` implementation and its GPLv2 + Classpath Exception header to understand the central-directory record layout, plus Android's official APK Signing Block documentation. No OpenJDK/third-party code was copied; this is a small local ZIP32 bounds parser over the existing preflight. The APK Signing Block remains outside the ZIP central directory and is not treated as a signature-verification result.

## Status

- Production commit: `352a29a40144c08c2bf3ae32abf6e00e66f5d99e`.
- Exact source+test SHA: `3a889a45ff72ef45d10286a59e5bd8ed56c8c22e`.
- **PASS:** source-checks run 40266600791; actual `:app:testDebugUnitTest` + `:app:lintDebug` combined Gradle step succeeded. No APK packaging or artifact upload.
- Device, real APK corpus, SAF and Shizuku behavior: NOT TESTED.
