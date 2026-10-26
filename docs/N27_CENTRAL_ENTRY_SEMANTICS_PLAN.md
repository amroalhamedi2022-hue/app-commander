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

## Implementation status

- Production and regression changes are committed to the existing PR branch.
- Exact-SHA CI: PENDING.
- Device / HyperOS behavior and broad real-world APK corpus: NOT TESTED.
