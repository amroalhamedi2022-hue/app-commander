# N60 — fail closed on unsupported Shizuku operation codes

**Date:** 2026-10-27  
**PR:** #1 — App Commander; keep Draft/open, no merge.

## Engineering question

Does the privileged command policy itself deny unknown integer operation codes? Previously `allows()` returned true for any code not in the destructive-operation set. The UserService currently rejects unknown codes in `PrivilegedRequestValidator` before dispatch, but the policy is intended to be a last-line defense and should not report unsupported commands as allowed if a future caller bypasses the preflight.

## Production change

- Define the complete supported operation set from the existing destructive and recovery operation sets.
- Require operation membership before applying protected-package exceptions.
- Preserve recovery operations for protected packages and existing destructive-operation protections.
- Do not add commands, shell syntax, permissions, root, Device Owner, or dependencies.

## Regression / acceptance criteria

1. Unknown values (including zero, adjacent-to-current maximum, and integer extremes) are denied for ordinary packages.
2. The same unknown values are denied for protected Android packages.
3. All existing supported recovery/destructive policy tests remain green.
4. `:app:testDebugUnitTest` and `:app:lintDebug` pass on the exact source/test SHA and again on the docs-updated PR head.
5. No APK packaging/signing/upload or artifacts.

## Evidence ledger

- Production commit: `2b9b3600d77284083b16018722d29831bcc17b4b`.
- Regression-test commit: `ad8a56404bc4b21ae181c662271058d81b7b1e4d`.
- Test: `PrivilegedCommandPolicyTest.unknownOperationCodesFailClosedForEveryPackageClass`.
- CI on the final docs-updated head: **PENDING**.
- Xiaomi Pad 5 / HyperOS Shizuku execution and real SAF providers: **NOT TESTED**.
- This is policy hardening only; it does not claim device-level privilege validation.
- Google Drive LIVE sync must be independently verified; GitHub documentation is not proof of Drive sync.
