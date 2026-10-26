# N26 — Cross-check APK ZIP local headers against the central directory

Date: 2026-10-26. App Commander PR #1 remains open Draft; no merge.

## Engineering question

The current bounded preflight validates the ZIP end record and every central-directory record before ZipFile allocation, but only checks that each local-header offset is before the central directory. A malformed APK could therefore advertise a valid central directory while pointing a non-manifest entry at a truncated or inconsistent local header, mismatched filename/method/flags, or invalid data descriptor.

Can the inspector reject those structural inconsistencies before Android's APK parser without extracting payloads or inflating untrusted entries?

## Source/spec review

- Reviewed the ZIP local-file-header, central-directory, and optional data-descriptor layouts in the public PKWARE APPNOTE reference: https://support.pkware.com/display/PKZIP/APPNOTE
- Reviewed Library of Congress format notes on the ZIP container: https://www.loc.gov/preservation/digital/formats/fdd/fdd000354.shtml
- This is a format-spec implementation, not copied third-party code. No dependency is added. APK authenticity, signature verification, payload CRC validation for every entry, and malware scanning remain explicitly out of scope.

## Acceptance criteria

1. For every bounded central-directory record, validate its local-header signature and ensure its local offset and variable fields remain before the central directory.
2. Require local and central filenames (raw bytes), general-purpose flags, and compression methods to agree; reject encrypted entries.
3. When data-descriptor bit 3 is clear, require local CRC and sizes to match central metadata. When set, allow only zero-or-matching local placeholders and validate the optional-signature or unsigned descriptor against central CRC/sizes.
4. Reject entry data/descriptors crossing the central-directory boundary and reject overlapping local record/data ranges.
5. Add deterministic JVM regressions for invalid local signature, filename/method mismatch, local size mismatch, invalid data descriptor, and overlapping/aliased local records. Keep valid normal ZIPs (including data descriptors) accepted.
6. Run :app:testDebugUnitTest :app:lintDebug on the exact source+test SHA. No APK packaging, artifact upload, release, merge, main write, root, or Device Owner.

## Implementation evidence

Pending implementation and exact-SHA source CI. Physical Xiaomi Pad 5 / HyperOS and broad third-party APK corpus remain separate NOT TESTED gates.
