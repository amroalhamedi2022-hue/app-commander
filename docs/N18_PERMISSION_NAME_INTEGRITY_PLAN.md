# N18 — Shared Android permission-name integrity

Date: 2026-10-26. App Commander PR #1 remains Draft.

## Engineering question
The Activity Launcher already rejects malformed non-empty permission names with a conservative qualified-name pattern, but APK Inspector's exported Activity/Service/Receiver counters only reject blank or padded values. That inconsistency can make a manifest string containing an internal newline, bidi control, punctuation, or excessive length appear to be a reliable permission gate in the report.

## Acceptance criteria
1. A single pure Kotlin helper validates permission-name syntax consistently across Activity Launcher dispatch checks and manifest exposure counters.
2. Preserve existing semantics: null/empty activity permission means no extra caller permission is required; a malformed non-empty permission fails closed without calling PackageManager; malformed component/provider declarations are counted as not having a reliable declared gate.
3. Provider path-specific invalid permission names do not mask a valid provider-level fallback; read/write directions remain independent.
4. Regression tests cover internal newline, bidi formatting, punctuation, whitespace/padding, overlength values, valid Android/custom names, and provider path fallback.
5. ./gradlew :app:testDebugUnitTest :app:lintDebug --stacktrace passes on exact source/test SHA. No APK packaging, artifacts, merge or main-branch write.

## Why source/JVM checks are sufficient
The change is a pure string-policy contract and manifest-summary classification; unit tests exercise the exact helper and caller behavior. APK packaging/device tests cannot improve proof of this parser invariant and are not required. Physical Android/HyperOS behavior remains a separate gate.

## Baseline
- PR #1 branch: automation/apk-inspection-safety-20261023
- Previous source/test SHA: 37b1943d6252d907f36cab56cdb1ae4b8eb74bf5; CI run 40237317283 PASS for JUnit + Lint.
- Device SAF/Shizuku, Xiaomi Pad 5 UI, and real PackageManager behavior remain NOT TESTED.
- Google Drive LIVE sync is unavailable in this runtime; no Drive write/read-back will be claimed.

## Result — N18 SOURCE ACCEPTANCE PASS (2026-10-26)
- Shared helper commit: c35274246d91493f601f354ed4768896f122e5a3; Activity Launcher integration: 8c0ec76562213eed9b3170e353517ba33b738fc7; manifest exposure integration: f8bbb709c78c7df4a1c71bdda595de544203648b.
- New regression commits: 6f1724f8169eb3b874a5be1cb418da03afe764a0 and f08acb0e3d9e35fbae8a1c89b2b85c2525fef960. Exact source/test SHA: f08acb0e3d9e35fbae8a1c89b2b85c2525fef960.
- **PASS:** GitHub Actions [run 40242400761](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40242400761), job source-checks; exact tasks :app:testDebugUnitTest and :app:lintDebug both succeeded (BUILD SUCCESSFUL, 36 tasks executed).
- Regressions cover newline, bidi, punctuation, overlength, valid Android/custom permission names, fail-closed activity checks, provider read/write gates, and invalid path rules falling back to valid provider defaults.
- **Artifacts: []** No APK packaged or uploaded. Xiaomi Pad 5/HyperOS, real PackageManager, SAF, Shizuku and visual UI remain NOT TESTED.
- Google Drive LIVE sync: **SYNC FAILED** because Google Drive/Docs connector tools are unavailable in this runtime; no Drive write/read-back is claimed.

## Next
Return to Explode Arena for the next bounded native milestone; later resume App Commander with real-device SAF/Shizuku acceptance planning. Do not merge PRs or write to main.