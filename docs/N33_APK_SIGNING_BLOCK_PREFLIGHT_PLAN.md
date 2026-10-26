# N33 — APK Signing Block structural preflight

Date: 2026-10-26. App Commander PR #1 remains open Draft; no merge.

## Engineering question

The APK inspector already bounds ZIP32 metadata, validates central-directory records, checks local headers and descriptors, rejects overlap between local ZIP records, and validates extra-field framing before constructing Android's `ZipFile`. APK Signature Scheme v2/v3 adds a signing block immediately before the central directory. The preflight currently treats that region as an unexamined gap and does not prove that a recognized signing block's size fields and ID/value pair framing are consistent or that local entry data ends before the block.

Can we validate the signing-block container without implementing cryptographic signature verification, while preserving v1-only APKs and unknown well-formed block IDs?

## Source and license review

- Android Open Source Project, official APK Signature Scheme v2 format: https://source.android.com/docs/security/features/apksigning/v2
- The documented layout is a uint64 block size, uint64-length-prefixed ID/value pairs, matching footer size, and the 16-byte `APK Sig Block 42` magic immediately before the central directory.
- Unknown IDs must remain opaque and be ignored structurally. This change does not parse signer payloads, verify certificates, or claim an APK signature is valid.
- No third-party implementation is copied and no dependency is added.

## Acceptance criteria

1. Detect the exact signing-block magic immediately before the central directory; if absent, preserve existing v1-only ZIP behavior.
2. Validate the 64-bit header/footer size match, overflow-safe bounds, a generous 64 MiB block limit, and a bounded maximum of 1,024 pairs.
3. Validate every pair length (at least the 4-byte ID and wholly inside the pair region) without interpreting unknown payloads.
4. Reject a signing block that overlaps any referenced local ZIP record or payload.
5. Preserve ordinary APKs without a signing block and accept a structurally valid block containing an unknown ID.
6. Add byte-level JVM regressions for valid unknown-ID blocks, header/footer mismatch, malformed pair length, and overlap with local entry data.
7. Run `./gradlew :app:testDebugUnitTest :app:lintDebug --stacktrace` on the exact source+test SHA and again on the docs-updated PR head.

## Why source inspection alone is insufficient

The acceptance question is about binary offsets and untrusted length fields. Tests must construct byte-accurate ZIP fixtures, place a signing-block container at the exact central-directory boundary, and mutate the 64-bit size/pair fields while keeping the ZIP's other offsets meaningful. Reading the code alone cannot prove the offset math or that the new guard rejects the intended malformed layout before `ZipFile` is opened.

## Scope and safety

- This is a bounded structural preflight, not APK signature verification, signer trust, malware detection, or install approval.
- The inspector's existing 512 MiB input cap remains in force.
- No APK packaging, signing/upload artifact, release, merge, `main` write, root, Device Owner, or auto-merge.
- Physical Xiaomi Pad 5 / HyperOS behavior and a broad real-world APK corpus remain separate acceptance gates.

## Status

- Implementation and JVM regressions: PENDING.
- Exact source/test CI: PENDING.
- Docs-updated PR-head recheck: PENDING.
- Device acceptance: NOT TESTED.
- Google Drive LIVE sync: PENDING runtime connector availability check.
