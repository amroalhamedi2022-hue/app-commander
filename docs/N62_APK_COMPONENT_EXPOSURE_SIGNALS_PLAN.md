# N62 — APK manifest component-exposure review prompts

## Question
Does the APK Inspector summarize exported-component and provider-permission counts it already extracts, without claiming that manifest metadata proves a vulnerability?

## Implementation
- Extend the offline signal evaluator with optional counts for exported Activities/Services/Receivers that lack a component-level permission, exported providers without default read/write permissions, and path rules without an effective read/write gate.
- Connect the real Compose Inspector screen to the new evaluator inputs.
- Keep gated exports quiet, clamp malformed negative counts, and label findings as manifest-only review prompts. No network calls, APK execution, permission grants, or new dependencies.

## Acceptance criteria
- Combined unguarded Activity/Service/Receiver count is reported deterministically.
- Provider read and write gaps are reported independently, including path-rule counts.
- Zero gaps and negative/malformed counts do not create exposure findings.
- Existing permission/signer/target-SDK findings retain their order and behavior.
- JVM tests and Android Lint pass; device rendering remains a separate gate.

## Source review
Android Developers documentation explains that exported components can be invoked externally and that component permissions can restrict access. Manifest counts do not prove runtime reachability or exploitability:
- https://developer.android.com/privacy-and-security/risks/android-exported
- https://developer.android.com/guide/components/intents-filters

No third-party implementation was copied and no new dependency was added.

## Evidence
- Source + JVM regression + UI wiring committed on the N62 PR branch; CI: PENDING.
- Xiaomi Pad 5/HyperOS visual and real APK corpus: NOT TESTED.
- APK packaging/artifacts: 0 expected; no build requested.
