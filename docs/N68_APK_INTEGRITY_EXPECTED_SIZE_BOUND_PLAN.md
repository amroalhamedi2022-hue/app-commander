# N68 — Bound APK install-integrity recheck by inspected size

Date: 2026-10-28. App Commander PR #1 remains open Draft; no merge.

## Engineering question

ApkInstallIntegrity.matches already knows the byte count recorded during APK inspection, but its re-read used the global 512 MiB copy limit. If a SAF provider replaced a small inspected APK with a much larger stream, integrity verification would continue reading the replacement until the global limit or EOF even though the expected size had already been exceeded.

## Acceptance criteria

1. Validate expected size and SHA-256 metadata before reading; expected size remains constrained to 1..MAX_APK_BYTES.
2. Pass the inspected size as the stream maximum byte budget so a larger replacement fails closed at the first bounded read chunk that crosses the expected size.
3. Preserve exact-size and matching-hash success, same-size hash-mismatch rejection, ordinary I/O failure behavior, cancellation propagation, and JVM Error propagation.
4. Add a counting-stream regression that proves an oversized replacement is rejected after one 64 KiB read instead of consuming a multi-megabyte stream.
5. Run ./gradlew :app:testDebugUnitTest :app:lintDebug on the exact source/test SHA. Do not package an APK or upload artifacts.

## Validation boundary

- Real SAF provider behavior, Xiaomi Pad 5/HyperOS, and installer handoff remain NOT TESTED.
- No new permissions, dependencies, root, Device Owner, merge, or main write.
