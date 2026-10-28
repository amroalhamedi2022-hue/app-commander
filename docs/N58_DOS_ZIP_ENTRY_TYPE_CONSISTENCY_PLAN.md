# N58 — DOS ZIP entry-type consistency

**Date:** 2026-10-27  
**PR:** #1 — App Commander; keep Draft/open, no merge.

## Engineering question

The shared ZIP entry-type policy interpreted Unix mode bits, but ignored the DOS/FAT directory attribute byte for non-Unix records. A central-directory record could therefore set the DOS directory bit on `base.apk` while the entry path remained a regular file. The verifier never extracts the archive, but accepting contradictory metadata is unnecessary for App Commander's canonical bundle format.

## Source review

- The PKWARE ZIP APPNOTE specifies that external attributes depend on the host system and that the low-order byte is the DOS attribute byte for MS-DOS records: [PKWARE APPNOTE](https://support.pkware.com/display/PKZIP/APPNOTE).
- Reviewed the public Apache Commons Compress ZIP documentation and Apache-2.0-licensed `ZipUtil` source for host-specific ZIP attributes. No third-party code was copied and no dependency was added.
- Compatibility constraint: directory entries are often identified by a trailing slash without the DOS directory bit. The policy must reject a contradictory *set* bit on a file path, not require the bit on every slash-terminated directory.

## Implementation

- For non-Unix host records, inspect the low byte of external attributes and reject `0x10` (DOS directory) when the entry name does not end in `/`.
- Continue to allow slash-only directory metadata when the DOS bit is absent.
- Keep Unix POSIX type validation unchanged.
- No extraction, shell command, permission, dependency, or APK packaging was added.

## Acceptance criteria

1. Canonical App Commander base-only and base+split exports still verify.
2. A crafted canonical bundle with the DOS directory bit set on `base.apk` is rejected.
3. A DOS directory entry with a trailing slash is accepted whether or not the writer sets the DOS bit.
4. Existing ZIP path, CRC, digest, UTF-8, extra-field, Unix-type, and bounded-stream regressions pass.
5. Run `:app:testDebugUnitTest :app:lintDebug --stacktrace` on the exact source/test SHA and again on the docs-updated PR head; confirm zero artifacts.
6. No APK packaging/signing/upload, dependency/permission change, root, Device Owner, merge, main write, or auto-merge.

## Verification ledger

- Production: `ZipEntryTypeSafety.kt`.
- Unit tests: `ZipEntryTypeSafetyTest.kt`; crafted central-directory integration: `InstalledApkBundleCanonicalLayoutTest.kt`.
- **Corrected source/test SHA:** `a0afdc0b3d8ef0bd0b251c2fb5d2a51389d8fbcd`.
- **FAIL, superseded:** [CI #40572924157](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40572924157) — five existing archive tests correctly exposed that the first version over-required the DOS directory bit and rejected common slash-only directory records. The policy was narrowed to reject only the contradictory set-bit case.
- **PASS — JVM tests + Android Lint on corrected source/test SHA:** [CI #40573621884](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40573621884).
- **PASS — previous docs-updated PR head:** `318c35be2e9aab177be508ef154a16e8714c0443`; JVM/Lint [#40574025887](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40574025887) succeeded. This status/ledger append creates a new PR head; its final recheck is tracked in the PR conversation.
- Xiaomi Pad 5 / HyperOS, real SAF providers, APK signer authenticity and malware detection: **NOT TESTED**.
- APK artifacts: **0**. No merge, main write, root or Device Owner.
