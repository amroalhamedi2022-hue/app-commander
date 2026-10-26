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
- **PASS — source/test + plan CI:** [run #40408619247](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40408619247) on head `4b3570f0648af93e9f9c5fc72aae25c4e5605abf`; `:app:testDebugUnitTest` and `:app:lintDebug` passed, `BUILD SUCCESSFUL in 1m 19s`, zero artifacts. The earlier run `40408442586` was cancelled by PR concurrency and is not counted as PASS.
- **PASS — post-ledger PR-head recheck:** [run #40408973395](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40408973395) on head `e9f99a58039cc44547a0a3f9cb9a8372ec635b2c`; `:app:testDebugUnitTest` and `:app:lintDebug` passed, `BUILD SUCCESSFUL in 1m 20s`, zero artifacts.
- **PASS — subsequent docs-head recheck:** [run #40409365147](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40409365147) on head `31b0fec399a48ee2c31d6a38595670c885df1fe2`; `:app:testDebugUnitTest` and `:app:lintDebug` passed, `BUILD SUCCESSFUL in 1m 23s`, zero artifacts. The current evidence-only commit still requires a final CI check.
- Device testing, signature verification, malware detection, Shizuku operations: **NOT TESTED**.
- Google Drive LIVE sync: **SYNC FAILED** in this runtime because no Drive/Docs connector is available; no Drive write is claimed.
