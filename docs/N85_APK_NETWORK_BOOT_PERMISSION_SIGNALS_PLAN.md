# N85 — APK network and boot permission review signals

Date: 2026-10-28  
PR: #1 App Commander; keep Draft/open.

## Engineering question

Does the APK Inspector's curated manifest-review list surface declarations for network access and boot-completed broadcasts without implying that either permission proves actual network traffic or automatic app startup?

## Why source inspection alone is insufficient

The signal list is consumed directly by the APK Inspector UI. A JVM regression must prove that exact manifest permission names yield visible, deduplicated findings and that the wording remains a review prompt rather than a behavior/security verdict.

## Production change

- Add `android.permission.INTERNET` with wording that declaration permits network sockets but does not prove that data is sent or a server is contacted.
- Add `android.permission.RECEIVE_BOOT_COMPLETED` with wording that the declared permission alone does not prove auto-start; the APK's receiver components and behavior must also be reviewed.
- Keep the analyzer offline, deterministic, manifest-only, and dependency-free.

## Acceptance criteria

1. Exact INTERNET and RECEIVE_BOOT_COMPLETED declarations produce one finding each.
2. Duplicate declarations are deduplicated by the existing permission-set projection.
3. Similar but non-identical names do not create a finding.
4. Finding descriptions explicitly avoid claims of actual traffic or automatic startup.
5. Existing APK signal JVM tests and Android static lint pass on the exact source/test commit.
6. No APK packaging/signing/upload, new permission in App Commander's own manifest, root, Device Owner, merge, or main write.

## References reviewed

- Android `Manifest.permission` API: https://developer.android.com/reference/android/Manifest.permission — INTERNET permits network sockets; RECEIVE_BOOT_COMPLETED allows receipt of ACTION_BOOT_COMPLETED, but that declaration alone is not evidence of a receiver or actual startup.
- No third-party code was copied and no dependency was added.

## Verification — 2026-10-28

- Exact source/test commit: `f14ef215aeac5d30dfb1debfbbecdf617f79ef89`.
- Production blob: `2ac961de1c5889082b7621d4cf52713b7afb1735`.
- Test blob: `77051d8c6549c46d1aa4a01aaf9e361f2236c9da`.
- **PASS:** source/JVM/Lint [CI #40749452576](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40749452576); actual steps `:app:testDebugUnitTest` and `:app:lintDebug` succeeded; log reports `BUILD SUCCESSFUL in 1m 54s`.
- Workflow artifacts: 0. No APK build/package/sign/upload.
- Xiaomi Pad 5 / HyperOS, live PackageManager metadata and real APK behavior: **NOT TESTED**.
- PR #1 remains Draft/open. No new app permission/dependency, root, Device Owner, merge, main write or auto-merge.
- Google Drive LIVE sync: **SYNC FAILED** — this runtime exposes no Google Drive/Docs connector; no Drive read/write/read-back is claimed.
