# N59 — canonical ZIP version-header parity

**Date:** 2026-10-27  
**PR:** #1 — App Commander; keep Draft/open, no merge.

## Engineering question

The bundle verifier compared ZIP names, flags, methods, timestamps, sizes, CRCs, offsets and external entry types, but did not validate the local/central “version needed” fields or the central “version made by” software version. Different ZIP readers can interpret version-dependent features differently. App Commander exports a deliberately narrow ZIP32 format, so those fields should match its canonical JDK writer rather than being accepted as arbitrary metadata.

## Implementation

- Capture the local header's version-needed field and require ZIP version 2.0 (20), the version used by the exporter's DEFLATE/data-descriptor layout.
- Read the central creator and version-needed fields.
- Require the creator software version to be 20 and the central version-needed value to match the local entry and canonical version 20.
- Keep host-system-specific external-attribute checks and all existing CRC/hash/path/extra-field/bounded-stream checks unchanged.
- No extraction, dependency, permission, network access, APK build, or signing behavior added.

## Acceptance criteria

1. Canonical base-only and base+split exports still verify.
2. A forged local version-needed field is rejected.
3. A forged central version-needed field is rejected.
4. An unsupported central creator software version is rejected.
5. Existing APK digest, CRC, UTF-8, entry type, central offset, flags, timestamp, extra-field, and stream-stall regressions pass.
6. Run JVM unit tests and Android Lint on the exact source/test SHA and again on the docs-updated PR head; verify zero artifacts.

## Evidence ledger

- Production: app/src/main/java/com/appcommander/mobile/data/ZipBundleStructure.kt.
- Crafted integration regressions: InstalledApkBundleCanonicalLayoutTest.kt.
- Xiaomi Pad 5 / HyperOS SAF providers, APK signature authenticity, and malware detection remain NOT TESTED.
- No merge, main write, auto-merge, root, Device Owner, or APK artifact.
- Google Drive LIVE sync requires a separate verified connector operation; GitHub docs do not prove Drive sync.
