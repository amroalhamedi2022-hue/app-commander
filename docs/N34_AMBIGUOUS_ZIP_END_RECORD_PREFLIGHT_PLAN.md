# N34 — Ambiguous ZIP End-of-Central-Directory Preflight

Date: 2026-10-26. App Commander PR #1 remains open Draft; no merge.

## Engineering question

The bounded ZIP32 preflight scans the last 65,557 bytes for an EOCD signature whose declared archive comment ends exactly at EOF. ZIP comments are arbitrary bytes and can themselves contain `PK\\x05\\x06`. If a comment embeds another signature-shaped record whose own comment length also reaches EOF, a backwards scanner may select that record instead of the real EOCD. The APK inspector should fail closed on ambiguous candidates before delegating to `java.util.zip.ZipFile`.

## Standards and source review

- ZIP APPNOTE EOCD layout: signature, disk/count/central-directory fields, 16-bit comment length, then arbitrary comment bytes. Reference: https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT
- OpenJDK `ZipFile` is publicly available under GPLv2 with the Classpath exception; consulted only to understand parser behavior, not copied: https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/zip/ZipFile.java
- The implementation is a small repository-local guard; no third-party code or dependency is added.

## Acceptance criteria

1. Count EOCD candidates in the bounded tail whose declared comment length reaches EOF.
2. Reject more than one matching candidate with an explicit `IOException` before constructing `ZipFile`.
3. Preserve a normal archive comment containing the EOCD signature when that embedded candidate's comment length does not reach EOF.
4. Add byte-level JVM regressions for both ambiguous and nonmatching embedded signatures.
5. Keep existing ZIP32, central-directory, local-record, UTF-8, path-collision, extra-field, manifest-CRC, and APK-signing-block regressions passing.
6. Run `./gradlew :app:testDebugUnitTest :app:lintDebug --stacktrace` on source+tests and again on the docs-updated PR head. No APK packaging or artifact upload.

## Scope and safety

- This is structural ZIP preflight only, not APK signature verification, malware detection, signer trust, or install approval.
- ZIP64 and multi-disk archives remain explicitly unsupported; input remains bounded to 512 MiB.
- No APK build, release, merge, `main` write, root, Device Owner, or auto-merge.
- Xiaomi Pad 5 / HyperOS and broad real-world APK corpus remain separate NOT TESTED gates.

## Status at initial commit

- Source change: `08593232ec5898550f83fd5093d445a74c96d24c`; source blob `38e3b0881f38a602acc7daf6c6a82755db412950`.
- Regression-test commit: `601f985c856ce910493901163fae5710ff6f0bf7`; test blob `3492204596a04b81bfe9e15ff52046d1d2075448`.
- Exact source/test CI run `40408442586`: **PENDING at initial ledger write**; update this section only after a run result is observed.
- Device testing, signature verification, malware detection, Shizuku operations: **NOT TESTED**.
- Google Drive LIVE sync: **SYNC FAILED** in this runtime because no Drive/Docs connector is available; no Drive write is claimed.
