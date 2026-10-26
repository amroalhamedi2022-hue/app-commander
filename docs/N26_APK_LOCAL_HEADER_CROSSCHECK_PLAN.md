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

## Implementation and exact-SHA verification — SOURCE ACCEPTANCE PASS

- Production: ApkArchivePreflight now cross-checks every bounded central-directory entry against its local header: signature, raw filename bytes, flags, compression method, CRC/sizes when bit 3 is clear, zero-or-matching placeholders plus descriptor CRC/sizes when bit 3 is set, and the data/descriptor boundary. Encrypted or masked-header flags are rejected. Local header/data/descriptor ranges are sorted and overlap/aliasing is rejected before ZipFile is opened.
- No entry payload is extracted or inflated except the existing bounded AndroidManifest.xml read; no dependency was added.
- Production commits: initial cross-check `89aeed8b458b6d0789d6c5db63e559362530dba9`; encrypted/masked-flag hardening `04d00423ea17c400f12c55ec2b0e8459a6c497f6`.
- Regression commits: malformed local-header tests `6d4133cbc3c085ae57da83f244200607da971f39`, isolated local-size mismatch assertion `1a63072d1700744838e98a846bee55792431cb72`, and encryption/boundary regressions `8c3b7f791a53b8041f0ba916112c6805dd39a18f`.
- Exact production + regression source/test SHA: `8c3b7f791a53b8041f0ba916112c6805dd39a18f`.
- **PASS:** [source-only CI run 40290864869](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40290864869), job 121465337746. `:app:testDebugUnitTest` and `:app:lintDebug` both passed; log: **BUILD SUCCESSFUL in 1m 43s**, 36 tasks executed.
- New regressions cover invalid local signature, local/central filename mismatch, method mismatch, size mismatch with descriptor flag cleared, invalid data-descriptor CRC, aliased/overlapping local records, encrypted entry flags, entry data crossing the central directory, and truncated descriptor at the central-directory boundary. Existing valid ZIP/data-descriptor and 41+ other archive-safety regressions remained green.
- **Artifacts: 0.** Workflow did not package/sign/upload an APK.
- **NOT TESTED:** physical Xiaomi Pad 5 / HyperOS, broad real-world third-party APK corpus, cryptographic APK signature verification, malware detection, and installer behavior. Structural ZIP checks are not authenticity or safety certification.
- PR #1 remains open Draft; no merge, main write, root, Device Owner or release.
- **Google Drive SYNC FAILED:** Drive/Docs connector tools are unavailable in this runtime. LIVE HANDOFF/INDEX were not read or written; this GitHub ledger is not a successful Drive sync.
