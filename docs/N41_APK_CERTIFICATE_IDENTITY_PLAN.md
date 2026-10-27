# N41 — APK signer certificate identity metadata

**Date:** 2026-10-27  
**PR:** #1 — App Commander; keep Draft, no merge.

## Engineering question

Can the APK Inspector provide useful X.509 certificate identity and date metadata without mistaking it for cryptographic APK verification, while keeping fingerprints available if certificate parsing fails? Source inspection alone cannot establish parser behavior for valid DER, truncated/invalid bytes, empty input, oversized certificates, and signer-history ordering.

## Acceptance criteria

1. Parse subject, issuer, serial number, signature algorithm, public-key algorithm, and X.509 not-before/not-after dates from each parseable signer certificate.
2. Always preserve SHA-256 of the raw certificate bytes even if X.509 parsing fails.
3. Reject empty input, certificates larger than 1 MiB, trailing data, and non-canonical/non-DER encodings from metadata extraction without crashing the rest of APK inspection.
4. Keep current-signer fingerprints deterministic and deduplicated; preserve oldest-to-current order for a single-signer rotation history only when Android reports past signing certificates.
5. Show certificate details in the existing Arabic APK Inspector and explicitly state that X.509 metadata is not APK signature verification, a malware verdict, or proof of origin.
6. Add JVM tests using a generated self-signed DER fixture, malformed bytes, empty input, oversized input, and the integrated signer-summary path.
7. Run source-only `:app:testDebugUnitTest :app:lintDebug` on the exact source/test SHA and independently re-check the docs-updated PR head. Confirm zero artifacts; do not package/sign/upload APKs or merge.

## Implementation

- Extend the existing `ApkSignerSummary` pipeline with bounded best-effort X.509 metadata parsing.
- Preserve fingerprint output when details are unavailable and keep rotation history semantics unchanged.
- Add the metadata fields to `ApkInspection` and display identity, algorithm, serial and date fields in the current inspector UI.

## Open-source and API review

- Android `SigningInfo` documentation defines the single-signer certificate history ordering: https://developer.android.com/reference/android/content/pm/SigningInfo
- Google's `apksig` source is Apache-2.0 and provides actual APK signature verification, but its official project description says it is meant to be used outside Android devices: https://android.googlesource.com/platform/tools/apksig/
- No apksig or third-party parser dependency was added. This milestone uses the platform/JDK `CertificateFactory` and `X509Certificate` APIs only; it intentionally reports certificate metadata, not signature verification.

## Verification ledger

- Engineering question and acceptance criteria recorded before source-only CI.
- **PASS — exact source/test SHA:** `a0a4b3695ae1ed5351c243143755c94d565e8af6`.
- **PASS — source/JVM/Lint CI:** [run #40452757223](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40452757223), `:app:testDebugUnitTest :app:lintDebug`, BUILD SUCCESSFUL in 1m 54s, 36 tasks.
- **Artifacts: 0 confirmed**; no APK packaging/signing/upload.
- Docs-updated PR-head recheck will be verified separately and recorded in the PR discussion.
- APK packaging/artifacts: **NOT RUN / 0 expected**.
- Xiaomi Pad 5 / HyperOS, OEM archive parser behavior, APK signature verification, malware detection: **NOT TESTED**.
- Google Drive LIVE sync: **SYNC FAILED** — no Drive/Docs connector is exposed; no LIVE document read/write or folder metadata verification is claimed.
