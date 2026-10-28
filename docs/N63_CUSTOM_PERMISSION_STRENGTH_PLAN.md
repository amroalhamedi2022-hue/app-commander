# N63 — Review exported gates backed by APK-defined non-signature permissions

## Engineering question
Does the APK Inspector distinguish a missing permission gate from an exported component whose gate is an app-defined normal or dangerous permission, without claiming that either metadata pattern proves runtime access or a vulnerability?

## Source review
- Android documents that application permission supplies defaults for components and that component attributes can override them: https://developer.android.com/guide/topics/manifest/application-element
- Android documents provider permission, readPermission, and writePermission precedence and URI-grant behavior: https://developer.android.com/guide/topics/manifest/provider-element
- ApplicationInfo.permission represents the default permission from the application manifest, and PermissionInfo exposes protection-level metadata: https://developer.android.com/reference/android/content/pm/ApplicationInfo and https://developer.android.com/reference/android/content/pm/PermissionInfo

No third-party implementation was copied and no dependency or permission was added.

## Implementation
- Build a set of permissions declared by the inspected APK whose base protection level is normal or dangerous; do not flag signature or unknown/external permissions.
- Count exported Activities/Services/Receivers using those permissions, plus provider read/write directions and path-rule directions using the same gates.
- Respect inherited provider defaults for path rules, and ignore non-exported components.
- Add a separate Arabic manifest-only review prompt, keeping it distinct from the existing no-declared-permission finding.
- Wire counts through the real archive-inspection repository, ApkInspection model and Compose Inspector UI.

## Acceptance criteria
- Only exported components with a permission defined by the inspected APK at base level normal/dangerous are counted.
- Signature-level and external/system permissions do not produce this finding.
- Provider read/write directions are counted independently; path rules inherit provider defaults only when a valid path-specific permission is absent.
- Zero/negative counts do not produce the finding; existing findings preserve their behavior.
- Actual repository JVM tests and Android Lint pass on the source commit and final docs-updated PR head; no APK packaging is needed for this metadata-only question.

## Evidence
- Source/test commit: PENDING.
- JVM + Android Lint CI: PENDING.
- Xiaomi Pad 5/HyperOS rendering and real APK-corpus behavior: NOT TESTED.
- APK artifacts: 0 expected.
