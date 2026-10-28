# N53 — Strict UTF-8 decoding for APK-bundle metadata

**Date:** 2026-10-27  
**PR:** #1 — App Commander; keep Draft/open, no merge.

## Engineering question

The bundle verifier decodes the user-selected ZIP manifest and central-directory names using permissive UTF-8 string conversion. That constructor can replace malformed sequences, which makes the parser's failure mode implicit. Although canonical ASCII names already reject most such input, the parser should explicitly reject malformed or unmappable UTF-8 before applying canonical metadata rules.

## Implementation

- Add one bounded strict UTF-8 decoder using CharsetDecoder with CodingErrorAction.REPORT for malformed and unmappable input.
- Use it for the bounded manifest.json bytes and central-directory entry names.
- Translate decoding errors to IOException with a non-sensitive field label; do not echo attacker-controlled bytes.
- Preserve canonical valid exports and all existing ZIP, CRC, digest, entry-count, and expansion checks.

## Acceptance criteria

1. A valid exported bundle continues to pass the independent streaming verifier.
2. Malformed UTF-8 in the manifest fails with an explicit UTF-8 diagnostic.
3. Malformed UTF-8 in a ZIP central-directory name is rejected before canonical-name comparison.
4. Existing APK bundle export/verification, ZIP structure, APK preflight, and cleanup regressions pass.
5. Source/JVM tests and Android Lint pass on the exact source/test SHA and again on the documentation-updated PR head.
6. No APK packaging/signing/upload, dependency/permission change, root, Device Owner, merge, main write, or auto-merge.

## Source and API review

Reviewed the official Java SE CodingErrorAction contract. REPORT explicitly surfaces malformed/unmappable byte sequences as decoding errors, unlike replacement-mode string conversion: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/CodingErrorAction.html. This uses the JDK API already available to the Android/JVM module; no third-party code or dependency is added.

## Verification ledger

- **Exact source/test SHA:** `55420949d98f3b523ccb4163b9e89451e25c93f5`.
- **PASS — JVM tests + Android Lint:** [CI #40536857801](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40536857801), `:app:testDebugUnitTest :app:lintDebug --stacktrace`.
- Added a ZIP central-directory corruption regression that mutates the central name bytes while leaving local entries valid; the verifier fails with the explicit UTF-8 diagnostic.
- Artifact listing: **0**. Documentation-updated PR-head recheck: **PENDING**.
- Xiaomi Pad 5 / HyperOS and real SAF provider behavior: NOT TESTED.
- Artifacts: 0 expected; no APK packaging/signing/upload.
- Google Drive LIVE sync: SYNC FAILED if no Drive/Docs connector is exposed; no sync is claimed without verified read-after-write.
