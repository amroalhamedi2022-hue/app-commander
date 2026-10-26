# N22 — Validate SAF bundle inputs before opening destination

Date: 2026-10-26. App Commander PR #1 remains open Draft; no merge.

## Engineering question

writeAndVerify() evaluated openForWrite() before write() validated the package name, entry names, APK sizes, and aggregate limits. Because a SAF destination opener can create or truncate a provider document, invalid input could cause an external side effect before rejection, contradicting the exporter contract. Can metadata be validated before the destination callback runs while preserving the existing write/readback verification?

## Acceptance criteria

1. Validate package name, canonical base/split names, per-APK size, total-size budget, and APK count before invoking openForWrite.
2. Invalid metadata must not open the destination, readback source, or any APK source stream.
3. Valid exports retain exact base/split bytes, manifest hashes, and independent readback verification.
4. Existing provider truncation/mutation/read-denied/write-failure tests remain green.
5. Run ./gradlew :app:testDebugUnitTest :app:lintDebug on the exact source+test SHA. No APK packaging, artifact upload, release, merge, main write, root, or Device Owner.
6. SAF provider behavior on Xiaomi Pad 5 remains NOT TESTED; unit tests use deterministic streams.

## Baseline

- Branch: automation/apk-inspection-safety-20261023.
- Baseline PR #1 head: 749e1c7fd164d0af6e0f06ad1faaaf1625a1d24d.
- Latest source-only run on baseline: 40257266356, PASS.
- Licensed-source review: Android SAF / DocumentsContract contract reviewed; this is local validation-order correction, with no copied source or new dependency.

## Implementation status

Production refactor and regression test committed together. CI result pending; append final evidence to the acceptance ledger after checking the matching SHA.
