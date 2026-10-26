# N31 — Central-directory path collision preflight

Date: 2026-10-26. App Commander PR #1 remains open Draft; no merge.

## Engineering question

The ZIP preflight validates record lengths, UTF-8 bytes, local-header ranges, and declared sizes before constructing `ZipFile`, but path-policy checks (duplicate/canonically equivalent names, traversal/control characters, and file/directory aliases) currently run only after `ZipFile` has parsed and allocated its entry table.

Can the same bounded path policy be applied while the central directory is scanned, before handing attacker-controlled names to the platform ZIP parser, while preserving valid nested directories and visible Unicode names?

## Source and compatibility review

- Continue using the repository's existing policy and JDK `Normalizer` / UTF-8 APIs; no external implementation is copied and no dependency is added.
- Android libcore `ZipFile` remains the downstream parser; the preflight is defense-in-depth, not a signature or malware verdict.
- Keep the post-`ZipFile` checks as a second validation layer so the decoded entry inventory is independently checked.

## Acceptance criteria

1. Decode each central-directory name only after the existing strict UTF-8 validation.
2. Apply the same bounded path policy before `ZipFile`: length, absolute/backslash paths, empty/dot/dot-dot segments, control/bidi spoofing, NFC canonical-name collisions, duplicate names, parent-under-file conflicts, and file/directory collisions.
3. Reuse one path-registration helper for the central-directory preflight and the later `ZipFile` enumeration so the two policies cannot drift.
4. Add byte-level regression coverage proving duplicate and file/directory/parent collisions fail with central-preflight diagnostics before `ZipFile` construction.
5. Preserve valid nested directories, visible Arabic/Unicode names, valid empty directories, and existing N26–N30 archive cases.
6. Run `:app:testDebugUnitTest :app:lintDebug` on the exact production+test SHA and verify CI again on the docs-updated PR head.
7. Do not package or upload an APK; no release, merge, `main` write, root, Device Owner, or auto-merge.

## Why source inspection alone is insufficient

The regression fixtures keep local and central names byte-for-byte consistent while changing the archive's logical entry inventory. They assert the explicit central-preflight rejection reason, proving the path-collision guard executes before `ZipFile` rather than relying on a later parser exception.

## Status

PLANNED — implementation and source-only CI pending.
