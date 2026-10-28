# N40 — APK signing-certificate lineage inspection

**Date:** 2026-10-27  
**PR:** #1 — App Commander; keep Draft, no merge.

## Engineering question

The APK Inspector currently displays the current signer SHA-256 fingerprints but does not expose Android's signing-certificate rotation history or clearly distinguish multiple current signers from a single signer with lineage. Source inspection identifies the missing UI/model field; pure JVM tests are needed to preserve the ordering and avoid mislabeling multi-signer packages.

## Acceptance criteria

1. Preserve current signer SHA-256 fingerprints for the APK Inspector.
2. For a single current signer, expose SigningInfo.getSigningCertificateHistory() in platform-provided order (oldest certificate first, current last) only when hasPastSigningCertificates() is true.
3. For multiple current signers, use getApkContentsSigners(), normalize fingerprint ordering because signer-array ordering is not guaranteed, and do not claim a single-signer rotation history.
4. Missing or failing signing metadata remains unknown/unavailable; it must not crash APK inspection or be described as proof that an APK is unsigned or safe.
5. Show the history and signer mode in the existing Arabic APK Inspector UI, with explicit wording that fingerprints/history are metadata and not a malware verdict or independent authenticity guarantee.
6. Add pure JVM tests for no history, rotated single signer with ordered lineage, multiple current signers, and unavailable metadata.
7. Run source-only :app:testDebugUnitTest :app:lintDebug on the exact source/test head and independently re-check the docs-updated PR head. Zero artifacts; no APK packaging/signing/upload, release, merge, main write, root, Device Owner, or auto-merge.

## Source/license review

- Reviewed the official Android SigningInfo API: https://developer.android.com/reference/android/content/pm/SigningInfo
- The API documents that getSigningCertificateHistory() is ordered from original to current and returns null for multiple signers; getApkContentsSigners() is the correct path for multiple signers.
- Use only the existing Android API and project-owned Kotlin. No third-party implementation, dependency, permission, network call, or paid service.

## Implementation

- Add a pure JVM ApkSignerSummary adapter to hash current signer certificates and preserve valid rotation order.
- Extend ApkInspection with signer mode/history fields while retaining the existing signerSha256 contract.
- Populate the summary from PackageInfo.signingInfo best-effort in AppRepository.inspectApk.
- Render the signer mode and certificate history in the current inspector details screen.
- Add focused unit tests for ordering, multiple signers, and unavailable metadata.

## Verification ledger

- Engineering question and acceptance criteria recorded before the test run.
- **PASS — source/test SHA:** `feb1ee582dd9c7a59cb2184a9b1fb12c6785d377`.
- **PASS — source/JVM/Lint CI:** [run #40447641304](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40447641304), `:app:testDebugUnitTest :app:lintDebug`, BUILD SUCCESSFUL in 1m 43s.
- **Artifacts: 0 confirmed** from the run artifact listing; no APK packaging, signing or upload.
- The docs-updated PR-head recheck is performed after the remaining ledger/status commits; its exact SHA and CI result will be recorded in the PR discussion so the check is not invalidated by another docs-only commit.
- Xiaomi Pad 5 / HyperOS, OEM-specific archive metadata, cryptographic signature verification and malware detection: **NOT TESTED**.
- Google Drive LIVE sync: **SYNC FAILED** — no Drive/Docs connector is exposed in this runtime; no LIVE read/write or folder metadata verification is claimed.
