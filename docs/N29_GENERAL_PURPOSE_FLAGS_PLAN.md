# N29 — Validate ZIP general-purpose flags before platform parsing

Date: 2026-10-26. App Commander PR #1 remains open Draft; no merge.

## Engineering question

N26-N28 validate ZIP32 boundaries, local/central metadata agreement, and extra-field framing. The preflight still permits some general-purpose flag bits that are reserved, unused, or describe patched/enhanced compression modes outside this inspector's supported STORED/DEFLATED subset. Passing such metadata to a platform parser can create avoidable parser-differential behavior.

Can the preflight reject unsupported flag combinations before `ZipFile`, while retaining the standard data-descriptor and UTF-8 bits and the DEFLATE compression-option bits only when the method is DEFLATED?

## Specification review

- PKWARE APPNOTE §4.4.4 describes the general-purpose bit flag, including the data-descriptor bit, UTF-8/EFS bit, compression-option bits and reserved/unsupported modes: https://support.pkware.com/display/PKZIP/APPNOTE
- The ZIP notes at the Library of Congress document the local-header/central-directory structure used by APK containers: https://www.loc.gov/preservation/digital/formats/fdd/fdd000354.shtml
- No third-party implementation is copied and no dependency is added. This is a narrow policy gate for the existing ZIP32 STORED/DEFLATED parser, not a general-purpose ZIP library.

## Acceptance criteria

1. Validate general-purpose flags in each central-directory record before advancing toward `ZipFile`.
2. Revalidate local-header flags after proving they match the corresponding central record.
3. Continue rejecting encryption and masked-header flags with a clear diagnostic.
4. Allow only bits 1-3 and 11 in the supported subset; bits 1-2 are rejected for methods other than DEFLATED.
5. Preserve valid ordinary APKs and existing data-descriptor/UTF-8 behavior.
6. Add byte-level JVM regressions for reserved/unsupported flag bits and DEFLATE-only option bits on a STORED entry.
7. Run `:app:testDebugUnitTest :app:lintDebug` on the exact source+test SHA. No APK packaging/artifact upload, release, merge, main write, root, or Device Owner.

## Why source inspection alone is insufficient

The regression fixtures mutate both local and central headers while keeping them mutually consistent, so they exercise the flag policy itself rather than merely triggering the existing header-mismatch guard. The Gradle CI must run the actual repository JUnit tests and Android lint; source inspection alone cannot establish the byte-level behavior.

## Status — CI PENDING

Implementation, tests and acceptance ledger are committed together. Await the exact-SHA source-only CI result before marking this milestone PASS. Device/HyperOS and real-world APK corpus validation remain separate acceptance gates.
