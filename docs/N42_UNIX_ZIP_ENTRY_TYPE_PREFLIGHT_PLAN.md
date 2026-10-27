# N42 — Unix ZIP entry-type preflight

**Date:** 2026-10-27  
**PR:** #1 — App Commander; keep Draft, no merge.

## Engineering question

Can the APK archive preflight reject central-directory entries that advertise Unix symbolic links or special filesystem objects, and detect a mismatch between POSIX file type and directory-path form before handing the archive to Android's parser? Source inspection alone is insufficient because host-system IDs and external attributes are binary fields whose interpretation must be exercised with crafted ZIP fixtures.

## Acceptance criteria

1. Read central-directory "version made by" and external-attributes fields without changing ZIP offsets or existing bounds checks.
2. Interpret POSIX type bits only for Unix host ID 3; tolerate absent/zero type bits and non-Unix hosts for compatibility.
3. Reject Unix symbolic-link and special-file types (FIFO/device/socket), and reject regular-file/directory metadata that conflicts with a trailing-slash path.
4. Accept well-formed Unix regular-file and directory metadata.
5. JVM regressions cover valid Unix metadata, symlink, FIFO and file/directory mismatch.
6. Run source-only `:app:testDebugUnitTest :app:lintDebug` on the exact source/test SHA and re-check the docs-updated PR head; confirm zero artifacts. No APK packaging/signing/upload, release, merge, main write, root, Device Owner or auto-merge.

## Open-source/spec review

- The ZIP format defines external attributes as host-system-dependent and identifies the host via the "version made by" field; the central-directory external-attribute field is at byte offset 38 in the fixed record. See the ZIP APPNOTE mirror's central-directory layout: https://github.com/ross-spencer/386965c55359d86e7c59
- Reviewed Google's AOSP `apksig` project as the established open-source APK signing/verifying implementation. Its stated purpose includes APK signature verification and it is intended for use outside Android devices: https://android.googlesource.com/platform/tools/apksig/
- No third-party code or dependency was copied/added. This is a bounded metadata consistency check, not signature verification, malware detection or proof of APK authenticity.

## Implementation

- Parse host-system ID and external attributes from each central-directory record.
- For Unix entries with populated POSIX type bits, allow only regular files and directories with consistent path form; reject symlinks and special filesystem types.
- Add four JVM tests using generated ZIPs with patched central-directory metadata.

## Verification ledger

- Source/test commits and exact CI result will be appended after CI completes.
- Android device/HyperOS and broad real-world APK corpus: **NOT TESTED**.
- Google Drive LIVE sync: **SYNC FAILED** if the Drive/Docs connector remains unavailable; no LIVE write is claimed unless read-back and folder metadata are verified.
