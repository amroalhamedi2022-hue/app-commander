# N27 — Validate central-entry semantics before ZipFile

Date: 2026-10-26. App Commander PR #1 remains open Draft; no merge.

## Engineering question

N26 cross-checks each local header against the central directory, but several semantic ZIP invariants were still checked only after `ZipFile` had parsed the archive. A malformed record could advertise an unsupported method, impossible STORED sizes, a nonempty entry with zero compressed bytes, or aggregate expansion beyond the configured cap.

Can those invariants be rejected during the already-bounded central-directory scan, before `ZipFile` and Android's APK parser are invoked?

## Source review

- Continue using the public PKWARE APPNOTE and Library of Congress ZIP-format notes cited in N26.
- No third-party implementation copied and no new dependency.
- The change validates metadata only; it does not inflate entries, verify APK signatures, or classify malware.

## Acceptance criteria

1. Reject compression methods other than STORED and DEFLATED during the central-directory scan.
2. Reject compressed-size metadata larger than the archive and nonempty entries advertising zero compressed bytes.
3. Require STORED compressed and uncompressed sizes to match.
4. Bound the aggregate declared uncompressed size before opening `ZipFile`, not only during later enumeration.
5. Reject directory records with nonzero uncompressed size before opening `ZipFile`.
6. Add deterministic malformed ZIP regressions and preserve acceptance of ordinary DEFLATED empty directory records and valid nested paths.
7. Run `:app:testDebugUnitTest :app:lintDebug` on the exact source+test SHA. No APK packaging/artifacts, release, merge, main write, root, or Device Owner.

## Implementation and exact-SHA verification — SOURCE ACCEPTANCE PASS

- Production: the bounded central-directory scan now rejects unsupported compression methods, impossible compressed-size metadata, nonempty entries with zero compressed bytes, STORED size mismatches, aggregate declared expansion above 4 GiB, and directory records with nonzero expanded size before `ZipFile` is opened.
- Regression coverage: unsupported method with matching local/central headers; STORED compressed/expanded size mismatch; aggregate 3 GiB + 2 GiB declarations with matching local metadata; nonempty entry with zero compressed size; directory entry advertising payload bytes. Existing valid empty DEFLATED directory records and nested paths remain accepted.
- First source CI attempt [run 40303717793](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40303717793) **FAILED** because the aggregate-expansion fixture still carried a stale data descriptor, so a different structural rejection occurred before the aggregate assertion. Corrected the fixture to clear bit 3 and make local metadata match the central directory; no production rollback was needed.
- Exact production + regression source/test SHA: `f4cb690853ca81963c94ce4d297b4f951f9ea810`.
- **PASS:** [source-only CI run 40304529587](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40304529587), `:app:testDebugUnitTest :app:lintDebug`; log reports **BUILD SUCCESSFUL in 1m 9s**.
- **Artifacts: 0.** No APK packaging, signing, upload, or release.
- **NOT TESTED:** Xiaomi Pad 5 / HyperOS, broad real-world APK corpus, actual installer behavior, cryptographic APK signature verification, and malware detection.
- **Drive SYNC FAILED:** Google Drive/Docs connector tools are unavailable in this runtime; LIVE documents were not read or written. This GitHub record is not a successful Drive sync.
