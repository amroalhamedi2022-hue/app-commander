# N49 — Snapshot SAF APK-bundle inputs across preflight and write

**Date:** 2026-10-27  
**Repository:** `amroalhamedi2022-hue/app-commander`  
**PR:** #1 — remain Draft; no merge.  
**Status:** source and regression tests committed; CI pending.

## Engineering question

Can a caller-owned mutable source list change after validation—during `openForWrite` or a provider-backed output callback—and cause the exported ZIP to differ from the list whose package, entry names, APK count and byte limits were validated?

## Acceptance criteria

1. Copy source descriptors to a stable snapshot before validating them.
2. Use the exact same snapshot for aggregate-size calculation and ZIP writing in both `write` and `writeAndVerify`.
3. Preserve existing behavior: reject invalid metadata before opening the SAF destination, source streams or readback callback.
4. A deterministic test mutates the original list from `openForWrite`; the verified archive must still contain the validated base APK and matching SHA-256.
5. A second test mutates the original list from an output-stream callback; the archive writer must remain stable and produce the expected entry.
6. Run `./gradlew :app:testDebugUnitTest :app:lintDebug --stacktrace` on the exact source/test SHA, with zero APK artifacts.
7. No new dependency, permission, privileged action, APK packaging/signing/upload, merge, main write, root or Device Owner.

## Implementation

- Both export entry points now snapshot `sources.toList()` before validation and pass only that snapshot into the writer.
- Added two regression tests that deterministically mutate the caller-owned list at the provider/output boundary and independently inspect the produced ZIP bytes and digest.

## Source and license review

This hardens the existing SAF/ZIP implementation without copying a third-party implementation or adding dependencies. The flow follows Android's Storage Access Framework contract: provider streams can represent remote or virtual documents, so successful writes are not assumed to imply durable, byte-identical readback. Reference: [Android Developers — Storage Access Framework](https://developer.android.com/guide/topics/providers/document-provider).

## Evidence

- Source/test SHA: pending.
- JVM and lint CI: pending.
- SAF provider behavior on Xiaomi Pad 5/HyperOS: **NOT TESTED**.
- APK artifacts: 0 expected; no packaging workflow invoked.
