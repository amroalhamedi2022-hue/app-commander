# N28 — Validate ZIP extra-field framing before ZipFile

Date: 2026-10-26. App Commander PR #1 remains open Draft; no merge.

## Engineering question

N26/N27 validate central-directory bounds, local-record references, and selected entry metadata before `ZipFile` is opened. However, local and central ZIP extra-field regions are currently skipped as opaque byte ranges. A malformed extra-field TLV (a 2-byte ID plus 2-byte data length followed by that many bytes) can therefore be handed to the platform ZIP parser even though the APK inspector can cheaply reject malformed framing first.

Can the existing bounded preflight validate every local and central extra-field sequence without interpreting unknown vendor fields, while consistently rejecting ZIP64 because this inspector explicitly supports ZIP32 only?

## Specification review

- The ZIP Application Note describes extra fields as repeated 2-byte Header ID + 2-byte Data Size + data records, in little-endian order.
- Library of Congress ZIP format notes describe ZIP as the container used by APK and summarize the local-header/central-directory structure: https://www.loc.gov/preservation/digital/formats/fdd/fdd000354.shtml
- PKWARE APPNOTE reference: https://support.pkware.com/display/PKZIP/APPNOTE
- No third-party implementation is copied and no dependency is added. Unknown well-formed extra-field IDs remain opaque and accepted; only malformed framing and ZIP64 ID 0x0001 are rejected.

## Acceptance criteria

1. Validate the entire local extra-field byte range after the local filename and before trusting entry data offsets.
2. Validate the entire central extra-field byte range before advancing to the next central-directory record.
3. Require each extra-field subrecord to contain a complete 4-byte header and a data length that fits wholly inside its declared extra-field region.
4. Reject ZIP64 extra-field ID 0x0001 because ZIP64 archives/records are intentionally unsupported by the bounded ZIP32 preflight.
5. Accept unknown, well-formed extra fields (forward compatibility); do not parse or trust their payloads.
6. Add JVM regressions for malformed local framing, malformed central framing, ZIP64 extra field rejection, and acceptance of a valid unknown field.
7. Run `:app:testDebugUnitTest :app:lintDebug` on the exact source+test SHA. No APK packaging/artifact upload, release, merge, main write, root, or Device Owner.

## Why source inspection alone is insufficient

The requirement is to reject concrete byte layouts before the runtime ZIP parser is invoked. Source inspection can show that the check exists, but only malformed byte-level fixtures can establish that both local and central offsets are interpreted correctly and that valid unknown fields remain compatible. The planned CI is source/JVM validation only; it is not a device test or an APK build.

## Status

PLAN RECORDED BEFORE IMPLEMENTATION. Results will be appended after the exact-SHA test run.
