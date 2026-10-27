## 2026-10-25 milestone: verify persisted SAF APK bundle before success (CI PENDING)
- Functional gap: writing a ZIP and hashing source APKs did not prove that the selected SAF provider actually persisted the same bytes. Export now closes the destination, reopens the newly created document and independently checks all APK SHA-256 digests, manifest and ZIP central directory before reporting success.
- Failure path: existing scoped best-effort cleanup deletes only the newly created SAF document. Readback denied, truncated or changed bytes fail closed; provider may refuse deletion, in which case the UI warns about manual cleanup. Readback adds I/O proportional to archive size; device latency and provider support remain NOT TESTED.
- New JVM regressions: successful base+split readback after close, silent truncation, silent mutation, write-only provider and write failure.
- Evidence status: source change committed; CI/JUnit PENDING until matching SHA run is observed. Android/HyperOS SAF providers and physical-device behavior NOT TESTED. No APK build requested because this stage tests archive byte integrity; on-device provider compatibility remains a separate acceptance gate.
- Reference: Android's official Storage Access Framework and DocumentsContract APIs; no copied third-party source or new dependency.

# Autonomous acceptance ledger — App Commander Android

AUTOMATION_COMPLETE: NOT READY
Updated: 2026-10-26

## 2026-10-23 follow-up: single-pass APK SHA-256 optimization (production source: YES)
- Production commits: `a567312d35be6865f9dbd062eb2ce90834d3d632` (`ApkCacheCopy.copyWithSha256`) and `a55b2eae7346250b5d7779b9e3c68bc49976650b` (`AppRepository.inspectApk` uses streamed hash/size). This removes a second full read of up to 512 MiB of cached APK after SAF import while preserving the 512 MiB cap, short/zero read safeguards and temporary file cleanup.
- JUnit regression tests added in `5ab35ce55a9204a56ac8cf4b3b801a832523e11d`: SHA-256 of `abc`, binary 131079-byte short-read stream, size limit and write-failure behavior.
- **PASS (local Kotlin smoke):** matching `ApkCacheCopy.kt` logic compiled using `kotlinc`, executed with JDK; exact SHA-256, 131079-byte binary digest and integrity, cap, stalled provider and cache cleanup passed. The full repository's Gradle JUnit task was **NOT TESTED**.
- **PASS (Android compile on exact code SHA):** GitHub Actions [run 39956818805](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/39956818805) completed successfully for `5ab35ce55a9204a56ac8cf4b3b801a832523e11d`: `:app:assembleDebug` succeeded and `AppCommander-debug` artifact ID `12502677669` (10,573,628 bytes) uploaded. This workflow does **not** run JUnit tests or install APK on Android.
- **NOT TESTED:** actual Xiaomi Pad 5 install, large SAF APK import performance/CPU/RAM, signer correctness against known signed APKs, Shizuku and system safeguards, Liquid UI visual parity, all real-device operations. No Device Owner provisioning or destructive commands executed.
- **Next:** instrument safe device APK inspection and Shizuku recovery; run `./gradlew :app:testDebugUnitTest` on an authorized free environment without changing protected CI workflows; continue acceptance before double recheck.

## Actual development performed this round
- Production source changed: **YES**. New `app/src/main/java/com/appcommander/mobile/data/ApkCacheCopy.kt` with 512 MiB bounded streaming, persistent-zero-read detection, empty-file rejection and exception-safe temporary APK deletion. `AppRepository.inspectApk()` now uses this helper.
- Tests added: `app/src/test/java/com/appcommander/mobile/data/ApkCacheCopyTest.kt`, 6 JUnit4 tests for binary integrity, size boundary, empty/invalid, transient/persistent zero reads, provider failure and cache cleanup on both success and parse failure. `app/build.gradle.kts` now has JUnit4 test dependency.
- Main source commits: `29a0a719c872193561181c4e7c6f9cd2f842b000`, `81c5527a18c9b807eeff4cd4a15fb459b8a6af38`, `b4101509deaa87cf71a3580690ea31744630c0d6`, `a4d9f15639460cee892636a76e5729f53a9d3946`; final test commit `74b7d021152b93f75d154627bae7e8220dc82526`.
- The 512 MiB inspection cap is an explicit safety tradeoff: larger APKs fail with an error rather than exhausting app cache. No file is installed or modified during inspection.

## Evidence and gates
| Requirement | Status | Evidence / limitation |
|---|---|---|
| Bounded APK import, byte preservation, stall and error behavior | PASS | Actual `ApkCacheCopy.kt` logic compiled with local `kotlinc` and exercised using a standalone smoke harness on JDK 21. Passed binary, exact/over limit, empty, invalid, transient/stuck, provider failure cases. |
| Temporary cache deletion after success/failure | PASS | Standalone smoke harness exercised the helper's `withTemporaryApk` success and exception paths; files absent afterward. |
| Android Gradle unit tests (`./gradlew :app:testDebugUnitTest`) | NOT TESTED | JUnit tests added to repo but Gradle/Android SDK not available for this round's local Kotlin smoke validation. |
| Android APK compile on PR SHA | PASS | GitHub Actions `:app:assembleDebug` succeeded on PR SHA `5022b8e4576145d401f39f28fcdb9e6ecc37beb8` (run `39949063774`). `AppCommander-debug` artifact uploaded (ZIP digest `sha256:ec43e84410e6beef697ec56486b061bd00fdd0abae7ab19e3117bb3ba90a7aa7`; **not** APK hash). https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/39949063774 |
| Installed apps / Activity Launcher / APK metadata / Shizuku force-stop, freeze, unfreeze, AppOps | NOT TESTED | Real-device end-to-end validation required; code presence and old CI do not establish success. |
| Critical package safeguards and Device Owner recovery | NOT TESTED | No destructive commands executed. Existing protections were not changed; Device Owner provisioning remains out of scope on personal devices. |
| Offline security | PASS | No Internet permission or network service added; changes limited to local file inspection. |
| Liquid UI parity, battery/performance and Xiaomi Pad 5 usability | NOT TESTED | Real-device visual/functional acceptance outstanding. |

## Next bounded actions
1. Verify new branch on GitHub Actions, run `./gradlew :app:testDebugUnitTest :app:assembleDebug` on a permitted free runner, inspect errors if any.
2. Instrument APK inspection with Android SAF providers and malformed/oversized inputs on a test device; verify cache remains clean.
3. Validate Shizuku permission/recovery and protected packages without enabling Device Owner on a personal device.
4. Independent double recheck of every feature and device workflow only after all mandatory acceptance gates are evidenced.

## 2026-10-23 — Elevated Shizuku service critical-package defense in depth
- **Production source changed: YES.** New `PrivilegedCommandPolicy.kt` centralizes known critical Android/HyperOS package protection and the seven destructive operation codes. `CriticalPackageGuard.kt` reuses the same static policy while retaining dynamic default launcher / active keyboard protections. Crucially, `CommandUserService.execute()` now enforces the critical-package rule **inside the elevated Shizuku UserService**, not only in the unprivileged controller/UI. It returns `exit=1` on denied destructive commands, preserving existing command result handling. The UserService constructor records its runtime app package name to protect against accidental self-disable even with a different application ID. Recovery (enable/unfreeze/allow/grant) remains available. No Device Owner provisioning or root operations performed.
- **Regression source:** `app/src/test/java/com/appcommander/mobile/privilege/PrivilegedCommandPolicyTest.kt` tests protected package denial, recovery exceptions, ordinary package compatibility and runtime package-name override. Test source commit `027d8d22631479ec03a14418967977dfe53e5423` (policy `6e71687256939efac7ce7645f9b6c438e8f5cec7`, service `4c93e3869e1a5ab8097f3f3ed68e226c2c3f545f`, UI guard `6179a075645fa93f1b7d2f31af0fc50348517e0b`).
- **PASS (local Kotlin smoke):** JDK 21 / `kotlinc-jvm 1.9.0`, manually transcribed matching policy and operation constants compiled and ran **75** safety assertions: deny critical destructive actions, allow recovery, allow ordinary app operations, and protect custom application ID. Not a Gradle/JUnit test of repository checkout.
- **PASS (exact test-source SHA Android compile):** GitHub Actions [run 39964505419](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/39964505419) **success** for code SHA `027d8d22631479ec03a14418967977dfe53e5423`; job steps include `Build debug APK` and `Run actions/upload-artifact@v4`. The workflow does **not** run JUnit tests, install on device or exercise Shizuku commands.
- **NOT TESTED:** `./gradlew :app:testDebugUnitTest`, privileged Shizuku UserService on Xiaomi Pad 5, dynamic launcher/keyboard protection on HyperOS, APK install, rollback, UI Liquid fidelity, memory and negative Android device tests. Native CI compile is not evidence of actual privileged execution or safety.
- **Next:** run Gradle JUnit on free authorized runner; verify Shizuku binder permission and denial/recovery behavior on disposable test app (not critical system packages), and test App Commander UI/device recovery. No automatic merge or public release.

## 2026-10-23 — Shizuku process timeout and bounded-output regression (new production work)
- **Production code: YES.** `PrivilegedProcessRunner.kt` added at `e87de7cef05ef5ca78e2bcae5548bd200a58f822` and synchronized drain/snapshot at `90b0d4739b20b9e230d2032718ff21c2af74dbd1`. `CommandUserService.kt` now invokes it at `f71c09a304d2a5134fa9714585f4dd23a0e7213a`. All privileged commands remain fixed, typed argv (`am`, `pm`, `cmd appops`), **not a shell string**. A stuck command is forcibly terminated after 15s (exit=124); stdout/stderr are drained concurrently but retained at a maximum of 16 KiB per stream, protecting Binder responsiveness and RAM. No root/Device Owner/device commands executed.
- **Regression source:** `PrivilegedProcessRunnerTest.kt` at `4eb23e7e3c442ef2b004dfedc19f5049d3567896`: success/nonzero exit and both streams, fake-process timeout/destroy, two 200,000-character output streams truncated, invalid timeout. Fake processes do **not** exercise actual Shizuku commands.
- **PASS (limited local Kotlin/JVM smoke):** standalone Kotlin 1.9/JDK21 compilation of matching pre-synchronization process-runner logic with four fake-process scenarios. Not a repository Gradle checkout.
- **PASS (actual repository Gradle JUnit + APK build):** CI workflow now runs `./gradlew :app:testDebugUnitTest --stacktrace` **before** `:app:assembleDebug`. GitHub Actions [run 39973150605](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/39973150605) completed **successfully** on exact production/test SHA `90b0d4739b20b9e230d2032718ff21c2af74dbd1`; both `Run JVM unit tests` and `Build debug APK` steps passed and debug artifact upload passed. This validates compiled JVM tests and Android debug build, **not** device Shizuku execution.
- **NOT TESTED:** Android 13/HyperOS Xiaomi Pad 5 physical install, real privileged pm/am/appops timeout and recovery, binder behavior under deadlock/low RAM, critical-package safety under actual Shizuku, Activity Launcher/Liquid visual acceptance and Device Owner. No actual device or destructive command was accessed.
- **Next:** instrument Shizuku safe operations on a disposable test package, verify 15s timeout is appropriate on slow devices, then verify UI/permission recovery and do independent second-pass audit after all requirements pass. PR #1 remains unmerged.


## 2026-10-23 — APK manifest indicators + Shizuku bind recovery / dynamic protection
- **Production code:** `ApkInspectionSignals.kt` evaluates a limited, exact-match list of manifest permission indicators and legacy target SDK; the Inspector UI displays human-readable Arabic findings and an explicit warning that these are **not** malware detection, cryptographic APK signature verification, or a safety guarantee. `ApkInspectionSignalsTest.kt` checks empty/legacy/missing signer, deduplication and exact permission matching.
- **Shizuku reliability:** `ShizukuController.obtainService()` now captures its per-attempt deferred, resets a stale bind on timeout/exception and fails pending callers on disconnect. The previous timeout could leave `binding=true` indefinitely. This fix is compiled, but **not** tested against a real Shizuku service or a Binder death on Android.
- **Shizuku safety:** `CommandUserService` now rechecks the active launcher and keyboard through `CriticalPackageGuard` when the service receives a destructive operation and its Android Context is available. Static protected packages remain guarded even without that Context. Recovery operations remain permitted. `PrivilegedCommandPolicyTest` covers destructive-vs-recovery classification. Real dynamic protection on HyperOS is **NOT TESTED**.
- **PASS: exact source/test commit `5d2d2b0a4e574ec5b77fbe34b47cf3cba7d4d21a`**, free GitHub Actions [run 39990920939](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/39990920939): `:app:testDebugUnitTest`, `:app:assembleDebug` and artifact upload succeeded.
- **PASS: downloaded actual debug APK artifact** ID `12517359227` from run 39990920939, not merely the workflow status. ZIP SHA-256 `48f75b4cdd1a463570e7d7fce1c0c88dca42c53847b7c9690249b23ca60d7b27`; extracted APK SHA-256 `f7be68ac341d5f814b4736e8be97066d29bc1cb076bdba9afaaf2a61339bf860`. APK ZIP integrity PASS; parsed binary manifest package `com.appcommander.mobile`, versionCode 2, versionName 0.1.1, minSdk 28, targetSdk 36; APK signing-block marker present (**not** cryptographic signature validation).
- **NOT TESTED:** Android installation, Shizuku binder operations and timeout/reconnect, SAF picker/real APK parsing on Xiaomi Pad 5, UI Liquid fidelity, Device Owner recovery, real CPU/RAM and independent device double recheck. **Debug APK is not final delivery.** No main write, auto-merge, paid service, root or Device Owner provisioning.


## 2026-10-23 — Elevated IPC request validation and actual latest APK
- **Production source:** `PrivilegedRequestValidator.kt` validates operation IDs, package names, required permission/component arguments, unexpected arguments and length before any privileged process starts. `CommandUserService.kt` returns a structured `exit=1` denial for invalid IPC requests and fails closed on destructive commands if no Android Context is available for launcher/keyboard checks. Tests `PrivilegedRequestValidatorTest.kt` cover valid requests, malformed packages, command-like arguments, unsupported operations and oversize values.
- **PASS (exact HEAD `30e6f9ac5f6f0bfce20835d2f68d09038aa744b8`):** free Android Actions run [39998191971](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/39998191971) completed successfully: JVM JUnit tests, debug APK build and artifact upload all passed. This does **not** exercise real privileged commands.
- **Actual downloaded artifact:** ID `12521600226`, ZIP SHA-256 `d7d7940204e09f1b2fb17c1ad1e32bfcfff4cf7aaf1de82d390e8350fe9db84a`. Extracted `app-debug.apk` (30,394,525 bytes), SHA-256 `c110173d947e1f34c173db6e0e6b10637c440582553d40062a1040c005cb095d`. ZIP CRC/integrity PASS; binary AndroidManifest decoded as package `com.appcommander.mobile`, version `0.1.1` (code 2), minSdk 28, targetSdk 36. APK signing block marker present; cryptographic signature verification **NOT TESTED**.
- **NOT TESTED:** actual installation/launch, Xiaomi Pad 5/HyperOS Shizuku Binder and protected-package behavior, SAF on-device, Liquid UI visual parity, real CPU/RAM, Device Owner scenarios and independent double recheck. Debug artifact is not a final release; PR remains open/draft. No main write or auto-merge.


## 2026-10-24 — APK ZIP file/directory collision safety (source-only)
- **Production:** `ApkArchivePreflight.check` now rejects archives that use the same logical ZIP path as both a file and directory, or nest a child under a file path, regardless of central-directory ordering. Normal explicit and implicit directory nesting remains allowed. This is a structural preflight, **not** malware or signature validation.
- **Regression:** `ApkArchivePreflightTest` covers file→directory, directory→file, file→child, child→file and valid nested ZIP entries.
- **PASS:** exact source/test commit `4d88feac6ad211dfc5a90f19fc2f56554879e394` in [free CI run 40035746296](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40035746296): JVM tests and Android static lint completed successfully. No APK packaging task ran and no artifact was published.
- **NOT TESTED:** Android SAF provider/device behavior, actual APK signature, HyperOS Shizuku privileged operations, full Liquid UI, real-device double recheck. PR #1 remains draft/unmerged.

## 2026-10-24 — Expanded offline APK permission review (source-only)
- **Production changed: YES.** `ApkInspectionSignals.kt` now explains additional exact-match sensitive manifest permissions (camera, precise/background location, SMS sending, call log, phone numbers, calendar, contacts and media) and flags combined SMS read+send and precise+background location declarations. These are review prompts only; manifest requests do not prove a permission was granted or exercised.
- **Regression:** two new `ApkInspectionSignalsTest.kt` tests verify exact-match combinations, duplicate elimination, and no claim of granted permissions.
- **PASS:** exact source/test SHA `8d6e69edf18ff4501b3720d93c3ad4695f6ee9d8` — `:app:testDebugUnitTest :app:lintDebug` [CI 40046980231](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40046980231). No APK assembled/uploaded; 0 CI artifacts.
- **NOT TESTED:** real SAF import, package signature authenticity, HyperOS Shizuku/privileged commands, Liquid UI on Xiaomi Pad 5, installation and independent device double recheck. PR #1 draft; no main write/merge.

## 2026-10-24 — Verified private APK install snapshot (source-only)

- **Security fix:** APK Inspector no longer hands the Android installer the original mutable third-party SAF URI after comparing its hash. `ApkInstallSnapshot.kt` copies the selected source to a unique app-private cache file, limits reads to the previously inspected size, verifies the complete SHA-256 and deletes partial/mismatched files. The installer receives a read-only grant to that verified copy through a **non-exported AndroidX FileProvider** restricted to `verified-apk-installs/`. Expired copies are cleaned up after 24 hours; APK signatures remain the platform's responsibility.
- **Production wiring:** `AppRepository.prepareVerifiedInstall` and the Inspector install button use the verified FileProvider URI. The old SAF hash-only helper remains available but is no longer the install path. A changed provider stream fails closed and clears the selected inspection. No root, Device Owner, paid service or new third-party dependency was added.
- **Regression:** `ApkInstallSnapshotTest` checks byte-exact snapshot creation, equal-length substitution, oversized stream, SAF I/O failure, invalid metadata, temporary-file deletion and selective expiry. `VerifiedInstallProviderContractTest` verifies the non-exported, narrowly scoped provider manifest and XML paths.
- **PASS (exact production/test SHA `af80aa6fe84c38341a34a5f68f9690ba70c4b96e`):** free source-only [CI 40083913930](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40083913930), `:app:testDebugUnitTest :app:lintDebug`. No APK assembled, uploaded or signed.
- **NOT TESTED:** actual FileProvider grant/install behavior on Android/HyperOS, installation from real SAF providers, Android APK signature verification, Shizuku device commands, Liquid UI visual fidelity, memory/disk pressure and independent double recheck. Private snapshot files may remain in cache for up to 24 hours after a canceled install. PR #1 remains draft and unmerged.

## 2026-10-25 — Verified single-base SAF APK export (integrity milestone)
- **Acceptance target:** Base APK export must not report success solely because a provider accepted write calls. The newly created SAF document must be closed, reopened, streamed through an independent bounded SHA-256/size comparison and rejected on truncation, same-length substitution, denied readback or stalled input. Preserve the split-install warning and best-effort deletion of only the newly created document.
- **Production:** `InstalledApkExport.copyBaseAndVerify` adds this close → reopen → hash gate, reusing the existing `ApkInstallIntegrity.matches` bounded verifier; `AppRepository.exportInstalledBaseApk` now calls it. Compose export result reports readback verification and accurately warns when cleanup may be denied. No extra third-party dependency, root, Device Owner, signature claim or restore action.
- **Regression:** six new `InstalledApkExportTest` cases cover large binary success, output closure before readback, split warning, silently truncated or mutated provider data, denied readback with cleanup, invalid size before stream opening, write failure and stalled readback. Existing bundle verifier remains independent.
- **GitHub source+tests SHA:** `855025c0d4843daaec0cbd8d4090ec9c7d44d2bf`; source-only CI run [40148393937](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40148393937) **PENDING when recorded**; do not mark PASS until matching SHA/JUnit/lint complete.
- **NOT TESTED:** physical Xiaomi Pad 5 SAF providers, Android runtime behavior and installation, signature verification, Shizuku commands, Liquid UI parity, independent device double recheck. No APK build requested: this gate checks deterministic byte/stream integrity, for which JVM+lint is appropriate; real provider behavior remains a separate device gate.
- **Next:** test both Base and Base+Splits SAF flows against local and cloud document providers on Android, including denial, cancellation and readback restrictions; then Shizuku guarded operations on a disposable test app. Keep PR #1 draft; do not merge or write main.

- **CI resolution (same production/test content, docs-only commit): PASS** — [run 40148438634](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40148438634) completed successfully on SHA `c802e371c4d3186493118ede3187f86ed0f8bf4f` (source/test SHA `855025c0d4843daaec0cbd8d4090ec9c7d44d2bf` unchanged). `:app:testDebugUnitTest` and `:app:lintDebug` both PASS, artifacts `[]`, no APK built. Previous run 40148393937 was CANCELLED by CI concurrency after the docs-only commit; it is not a test failure. This is source validation only; real SAF on Android remains NOT TESTED.


## 2026-10-25 — Explicit Shizuku consent + honest two-step AppOps state (source milestone)
- **Problem addressed:** App detail controls previously invoked privileged force-stop, freeze, background AppOps, runtime-permission and component changes immediately on tap, without a distinct confirmation step. Background blocking/allowing attempted its second AppOps write even when the first failed, and partial success lacked a clear recovery warning.
- **Production changed: YES.** `PrivilegedActionConsent.kt` now maps all 13 supported typed operations to an explicit Arabic target/action description, validating package/argument syntax with the existing IPC validator before opening the dialog. The Compose AppDetailSheet routes force-stop, freeze/unfreeze, background allow/block, permission grant/revoke and component enable/disable through a one-shot, non-saveable `AlertDialog` request; cancel, back, outside dismiss or rotation cannot execute a pending action. The confirm button is the only UI `runCommand` callsite. A synchronous `commandBusy` guard blocks duplicate rapid taps; the elevated UserService retains its independent critical-package and argument validation.
- **Two-command safety:** `BackgroundAppOpsSequence.kt` stops before the second write if the first fails, reports PARTIAL explicitly if the second fails, and avoids inventing a rollback to `allow` because the previous AppOps mode is unknown. The UI displays the failure and advises manual recovery; no Device Owner/root or arbitrary shell.
- **Tests added:** `PrivilegedActionConsentTest` (13-operation coverage, invalid arguments, risk distinctions); `BackgroundAppOpsSequenceTest` (fail-fast, partial failure, both-success); `ShizukuUiConsentContractTest` (source routing through dialog). Source/test SHA `7aa0750f6cb1360b645c9354e300588fe7cd60fa`. Actual GitHub CI on this exact SHA **PENDING** at writing; do not report PASS until JUnit + Android lint complete.
- **Prior CI:** 40148438634 PASS applies to the *previous* source state, not this milestone. This round did **not** request APK packaging: the immediate question is Compose source compilation and pure JVM consent/sequence logic, addressed by `:app:testDebugUnitTest :app:lintDebug`; Android UI interaction and real Shizuku remain a separate device gate.
- **NOT TESTED:** confirmation dialog on Xiaomi Pad 5, real Shizuku Binder/pm/appops/permission changes, AppOps mode reconciliation on HyperOS, SAF providers, Android install and double independent recheck. PR #1 stays Draft; no merge/main/release. NEXT: verify CI on exact SHA, fix failures if any, then purposeful Android build + device tests for actual consent and recovery on a disposable test package.


### CI resolution — 2026-10-25 Shizuku consent milestone
- **PASS:** GitHub Actions [40155973253](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40155973253) completed `success` on exact production/source/test SHA `7aa0750f6cb1360b645c9354e300588fe7cd60fa`. Step `./gradlew :app:testDebugUnitTest :app:lintDebug --stacktrace` succeeded. No APK packaging or upload; artifacts count **0**. The earlier PR runs on intermediate SHAs were canceled due to concurrency, not source failures.
- **NOT TESTED remains:** physical Xiaomi Pad 5 confirmation UI, Shizuku binder/appops/permission behavior, SAF and actual Android install. This is JVM/source/Android lint acceptance only; PR remains Draft.


## 2026-10-25 — Safe exported Activity Launcher milestone (source + integration gate)
- **Acceptance:** Activity listing supports search and exported-only filtering; internal/disabled activities remain read-only. Clicking an activity must re-query PackageManager and reject non-exported, component-disabled, app-disabled, missing, malformed or stale targets without Shizuku/root/Device Owner. UI must show an honest dispatch/failure message rather than silently swallowing errors or claiming the target launched successfully.
- **Production:** new ActivityLaunchSafety validates names/eligibility and filters the activity inventory; AppRepository.launchActivity independently checks fresh ActivityInfo/exported state and component/application enabled overrides before dispatch. Compose AppDetailSheet adds activity search, exported-only filter, clear non-bypass explanation and explicit result feedback. Existing Shizuku confirmation paths unchanged.
- **Regression:** ActivityLaunchSafetyTest covers public/private/disabled eligibility, malformed names, nested class, search/exported filters, immutable inventory and source wiring of live PackageManager checks + UI feedback.
- **CI:** PENDING until :app:testDebugUnitTest and :app:lintDebug run on exact production SHA; do not claim PASS before confirmed result. No APK requested: source/UI compile + pure policy integration are testable in free CI; physical Activity launch and OEM export enforcement remain NOT TESTED.
- **NOT TESTED:** Xiaomi Pad 5, actual Android Activity launch/failure UI, Shizuku/SAF, physical-device double recheck, visual Liquid parity. PR #1 Draft, main untouched. NEXT: resolve CI, fix failures, then purposeful Android build/device test for Activity Launcher and consent on disposable apps.

- **CI RESOLVED — PASS:** exact production+test SHA `bb137aab9ae568eb242ffc80d07a3c9f2a0609e9` passed `:app:testDebugUnitTest :app:lintDebug` in [run 40163191517](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40163191517), job `source-checks` completed success. No APK packaging; artifacts `[]`. This is source/JVM/lint verification only; actual Android exported Activity launch, Xiaomi Pad 5, SAF and Shizuku remain **NOT TESTED**. PR #1 remains Draft.


## 2026-10-25 — Canonical ZIP32 central-offset integrity (App Commander)

- **Gap closed:** The SAF APK bundle verifier previously checked that central-directory local-header offsets were increasing, but did not prove they pointed to the actual local records. A forged central index could pass even when APK bytes, CRCs, and manifest SHA-256 were unchanged. This is a ZIP structural-integrity gap, **not** a signature-authenticity claim.
- **Production:** `ZipBundleStructure.validate` now binds every local offset to the canonical exporter layout (30-byte header + entry name + timestamp extra + compressed bytes + signed 16-byte data descriptor). It requires the last local record to end exactly at the central directory, permits only the expected DEFLATED/data-descriptor flags, and rejects unexpected entry comments/oversized extras. This is intentionally limited to App Commander-generated ZIP32 bundles, not arbitrary third-party ZIPs.
- **Regression/integration:** `InstalledApkBundleCanonicalLayoutTest` exercises Base-only, Base+1 and Base+3 split export→independent verify roundtrips with >64 KiB binary entries, forgeries of each non-first central offset that remain monotonically increasing, unsupported central flags and unexpected comments.
- **Source SHA:** `d67ce1c6c3b8fba85d10feb1f9ae34de894ec489`; **tests SHA:** `63e1ccdc578f5cc4ef54a94ccd0e8bf035a32685`. **PASS:** [GitHub Actions run 40170678844](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40170678844) on exact source+test SHA; `:app:testDebugUnitTest :app:lintDebug` succeeded; artifacts `[]`, no APK assembled. Superseded source-only run 40170660835 was **CANCELLED** by concurrency, not FAIL.
- **NOT TESTED:** actual SAF provider readback/exports on Xiaomi Pad 5, HyperOS Shizuku, Activity Launcher runtime, APK install/signature verification, Liquid UI and independent device double recheck. No main write/merge, no root or Device Owner.
- **Next acceptance gate:** Purposeful Android build and device testing for Base/Bundle SAF exports and imported ZIP verification on local and cloud document providers, including denial/truncation/cancel paths; then Shizuku confirmation on a disposable test app. Before an APK build, record the precise device question; do not equate CI source PASS with device PASS.


## 2026-10-25 — Bounded APK bundle inflation / canonical level-zero ZIP (source milestone)
- Security gap: App Commander ZIP verification inflated entries before checking the final central directory. A hostile compressed ZIP with a digest-consistent manifest could force disproportionate decompression work before rejection; previous total APK byte caps did not reject this early. The exporter itself uses DEFLATE level 0.
- Production commits: `200d79c3a59184d3516a292bcf58381e15488acf` adds a per-entry streaming inflation budget (1 MiB inflater slack + 16 times raw bytes read since entry start) in `InstalledApkBundleVerifier`; `bbbde7b597e7c2d4514910801d2c96758785cd5b` rejects central entries where compressed size is less than uncompressed size, which level-zero exports cannot produce. No arbitrary archive extraction or signature/authenticity claims.
- Regression commit `685a8cf37855eca9accafbbc146a080cb0ffa086`: four JUnit cases in `InstalledApkBundleInflationSafetyTest`: valid multi-MiB base+split export, high-ratio base rejection during streaming, high-ratio later split rejection independent of previous base budget, and compressed manifest rejection despite correct APK digest.
- Acceptance: `:app:testDebugUnitTest :app:lintDebug` on exact SHA; CI run https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40178200970 PENDING when recorded. Do not claim PASS before completion. No APK build/artifact: decompression safety is deterministic JVM behavior; SAF provider/device remains separate.
- NOT TESTED: Xiaomi Pad 5/HyperOS SAF and Shizuku, APK install, Liquid UI and independent device recheck. PR #1 remains Draft; no merge or main changes. Next: resolve CI, fix any failure, then prioritize device-gated SAF/Activity/Shizuku checks using disposable test apps. References: Oracle Java 21 ZipOutputStream setLevel documentation and general resource-exhaustion guidance; no third-party code copied.
### CI RESOLVED — 2026-10-25 streaming ZIP inflation milestone
- **PASS:** GitHub Actions [run 40178200970](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40178200970) completed SUCCESS on exact source/test SHA `685a8cf37855eca9accafbbc146a080cb0ffa086`; job `source-checks` and step `Run JVM tests and Android static lint without APK packaging` both succeeded. Four new `InstalledApkBundleInflationSafetyTest` cases included in the repository JUnit suite. Artifacts `[]`; no APK was assembled or uploaded.
- Scope: verified JVM/source and lint only. **NOT TESTED:** Xiaomi Pad 5, real SAF provider, Shizuku, install, Liquid UI, independent double device check. PR #1 remains Draft; no merge/main changes.


## 2026-10-25 — Installed-package replacement fence for SAF exports
- **Failure mode:** independent SAF SHA-256 readback proves saved bytes match the stream, but an installed app may update, disappear, or change its base/split APK set while export is running. The previous UI could report a verified backup for a no-longer-current installation.
- **Production:** `InstalledPackageSnapshot` captures package name, long version code, lastUpdateTime, ordered base/split source paths, file lengths and modification stamps from PackageManager and the filesystem before export. `AppRepository` re-reads and compares the full identity AFTER closing and independently verifying either Base APK or Base+Split ZIP. A mismatch or failed re-query aborts the operation and invokes the existing newly-created SAF-document cleanup path. Metadata capture validates size, count and repeated paths before writing. This is best-effort change detection, **not** an atomic OS snapshot or signature verification.
- **Source commits:** helper `f094a5ce4b7cd7e680f3fdb5613626f438794c3d`; Android integration `d93c26a7b1cd6dd09246df3c005a8a0911aa2afd`; eight JVM/integration regressions `4fb2f1ca5c9e9aa029b3d68d4f9c9db502cd51c5`. Tests cover stable identity, update/reinstall, changed split order/inventory/file stamp, package disappearance, malformed sources, verified Base/ZIP readback followed by mutation and cleanup, plus Android repository source wiring.
- **CI gate:** `:app:testDebugUnitTest :app:lintDebug` on test SHA `4fb2f1ca5c9e9aa029b3d68d4f9c9db502cd51c5`, run https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40186208772 **PENDING** at documentation time. No APK build or artifact requested: package-identity comparison and cleanup semantics can be verified with source/JVM tests; physical PackageManager/SAF race behavior remains a separate device question.
- **NOT TESTED:** Xiaomi Pad 5 real installed-app update during SAF export, OEM/cloud SAF provider cleanup, Shizuku, Android UI, APK signature, physical-device double recheck. No root/Device Owner, no main write or PR merge. Sources reviewed: Android PackageManager/PackageInfo public API and AOSP reference; no third-party code copied.
- **NEXT:** resolve exact-SHA CI; fix failures. Then test SAF export with an updating disposable installed app on Xiaomi Pad 5, verify partial-file cleanup and truthful UI; only a purpose-documented Android build is permitted for that device question.


### CI RESOLVED — 2026-10-25 installed-package replacement fence
- **PASS:** [source-only GitHub Actions run 40186208772](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40186208772) completed SUCCESS on exact production+8-test SHA `4fb2f1ca5c9e9aa029b3d68d4f9c9db502cd51c5`. Job `source-checks`, including `Run JVM tests and Android static lint without APK packaging`, completed SUCCESS. Workflow artifacts `[]`; **no APK assembled**. Earlier runs on intermediate commits were cancelled by branch concurrency, not evidence of source failure.
- **Accepted at source/integration-test level only:** installed package metadata fence for both SAF Base and Base+Split export routes, mismatch rejection and cleanup. **NOT TESTED:** real PackageManager update race, Xiaomi Pad 5 SAF provider deletion behavior, Shizuku, Liquid UI, install/signature and independent device recheck. PR #1 stays Draft; main untouched.
- **Next:** purposeful device test using a disposable app updated during SAF export; separately validate both document providers and actual cleanup. Record the device-specific question before any permitted APK build.

## 2026-10-25 — Effective component-state and Shizuku recovery UX (source milestone)
- **Functional defect repaired:** PackageManager's `ComponentInfo.enabled` reflects the manifest default and may disagree with explicit `getComponentEnabledSetting` overrides after a Shizuku component toggle. The old inventory/Activity Launcher UI could therefore show an already-disabled component as enabled and offer the wrong next action. A disabled parent app also allowed ineffective component toggles.
- **Production:** `ComponentEnabledState.kt` resolves DEFAULT/ENABLED/DISABLED/DISABLED_USER/DISABLED_UNTIL_USED and fails closed on unknown/unavailable settings. `AppRepository.kt` now checks actual app and per-component overrides for the installed-app list and all Activities/Services/Receivers; Activity Launcher reuses the same policy and still rechecks at launch. `isInstalledAppEnabled` re-queries after successful Shizuku commands instead of relying only on optimistic state inversion. `AppCommanderApp.kt` disables component toggles while the parent app is frozen, preserves protected-package recovery rules and explains the recovery order.
- **Acceptance tests:** 8 new JUnit tests in `ComponentEnabledStateTest.kt`: manifest defaults, explicit enable/disable overrides, unavailable/unknown fail-closed, disabled-parent behavior, protected-component recovery, inventory/launch integration contracts and UI gating. A separate local Kotlin/JVM smoke compiled matching platform-independent helper logic and passed 12 assertions; it is **not** an Android/device test.
- **PASS:** exact production+test SHA `fee0f991a70ad0cd41a668b6c8bca201206bbf78` in GitHub Actions https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40194940421 : `:app:testDebugUnitTest` and `:app:lintDebug` in the successful source-checks step. Artifact list empty, **no APK built**. This source/JVM validation does not exercise Android PackageManager or actual Shizuku on a device.
- **NOT TESTED:** Xiaomi Pad 5/HyperOS PackageManager override behavior, real Shizuku binder commands and consent, freeze/unfreeze and protected-component recovery, actual SAF providers, Liquid UI and independent device recheck.
- **Engineering gate:** No APK/CI packaging was needed; this change is deterministic enabled-state resolution tested via source/JVM and Android lint. The real Android acceptance gate remains separately outstanding. PR #1 Draft; no main write, auto-merge, root or Device Owner.
- **Next:** targeted disposable-app on-device freeze/component disable/re-enable and Activity Launcher checks; verify dynamic launcher/IME protection, then revisit SAF device export/reopen. Continue Explode Arena next turn. Official API reference: https://developer.android.com/reference/android/content/pm/PackageManager ; AOSP PackageManager.java (no third-party code copied).


## 2026-10-25 — Activity Launcher: permission-aware exported-activity dispatch

- **Functional gap:** exported and enabled Android activities may declare `android:permission`; the previous Activity Launcher advertised them as launchable even when App Commander lacked the required permission. Android may reject the attempt with `SecurityException`.
- **Production:** `ComponentEntry` carries `requiredPermission` and `callerHasPermission` for activity rows. `AppRepository` checks `ActivityInfo.permission` against its own UID through `Context.checkSelfPermission`, both when listing activities and on a fresh `PackageManager.getActivityInfo` lookup immediately before dispatch. `ActivityLaunchSafety.callerHasPermission` fails closed on a denied/throwing check. Compose labels restricted exported activities and disables their launch tap; filter wording no longer equates "exported" with "launchable". Ordinary unprotected exported activities still work. No Shizuku/root/hidden API bypass.
- **Tests:** six new JUnit cases in `ActivityLaunchSafetyTest` for permission absence/grant/denial/exception, eligibility with other restrictions, preserved search/filter state and repository/UI source wiring. Existing component-state UI regression updated to require the new combined parent-enabled AND caller-permission gate.
- **Initial CI FAIL:** [run 40204344756](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40204344756) on source+test SHA `734fd45e5004bdadb6b09abc9315bd397612fa08`: 156 tests, one pre-existing brittle `ComponentEnabledStateTest` string assertion expecting the old UI condition; Android lint did not execute. The failure was corrected, not ignored.
- **PASS:** [run 40204563542](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40204563542) on exact repaired SHA `90cd1741e92d3cdbe4672960ba9063d720c7ef42`: `:app:testDebugUnitTest :app:lintDebug` completed successfully, `source-checks` SUCCESS, artifacts `[]`. No APK assembled; deterministic eligibility logic and Android compilation/lint are sufficient for this source gate.
- **NOT TESTED:** real permission-protected activity behavior, Android activity lifecycle, Xiaomi Pad 5, SAF providers, Shizuku consent, Liquid UI, APK installation, and independent device double recheck. This is **not** final acceptance. PR #1 remains Draft; no main write, merge, Device Owner or root.
- **Milestone progress:** [████░] **4/5** source/tests/CI/docs gates; the real-device gate remains open. This is NOT the full project completion percentage. Next App Commander gate: disposable test app with an exported permission-protected Activity on Xiaomi Pad 5; test granted/denied states and verify UI feedback. Next development turn should alternate to Explode Arena.
- **Reference:** Android official cross-app permissions guidance: https://developer.android.com/training/permissions/restrict-interactions ; Android Activity reference. No third-party code copied.


## 2026-10-25 — APK Inspector exported-component exposure + device API compatibility (new milestone)
- Purpose: show counts of exported Activities, Services and Receivers and exported components without a declared component-level permission, directly from the parsed APK manifest. These are review indicators, NOT proof of reachability or vulnerability. No permissions, Shizuku or network operations added.
- Separate device API compatibility assessment compares parsed minSdk with Build.VERSION.SDK_INT; meeting the minimum is necessary but not proof that installation will succeed (ABI, signing, features, target SDK and OS policy may block it). Unknown metadata is reported as unknown, never compatible by default.
- Source: ApkComponentExposure.kt and ApkCompatibility.kt, integrated into AppRepository.inspectApk, ApkInspection model and Compose Inspector UI. JUnit cases cover private/guarded/exported, malformed permission declarations, null inventory, separate component kinds, API min mismatch/equality/unknown, plus repository/UI integration source contracts.
- Pre-CI engineering question: Does Android Gradle compile the actual PackageManager ActivityInfo/ServiceInfo integration and Compose presentation, and do the JVM regressions plus Android lint pass? A standalone Kotlin unit check cannot verify Android platform types or Compose integration; run source-checks (testDebugUnitTest + lintDebug) only, NO APK packaging.
- License/reuse review: Android official PackageManager component metadata and existing repo patterns used. Reviewed Apache-2.0 jadx (https://github.com/skylot/jadx) and BSD-3-Clause APKParser (https://github.com/jaredrummler/APKParser); full decompiler/parser dependencies unnecessary for already parsed PackageManager metadata. No third-party source copied.
- Acceptance gates: (1) source integration; (2) JVM regression; (3) CI Android compilation/lint; (4) Drive sync; (5) physical Xiaomi Pad 5 display/behavior. CI/device evidence pending at commit time. Real device, SAF and Shizuku NOT TESTED. No root/Device Owner, no APK, no merge/main changes.

### 2026-10-25 — APK Inspector exposure/compatibility CI RESOLVED
- PASS on exact production+9-test SHA 8934fce31cc2fa4cc19fd4bff41b64a5c41c0b16: GitHub Actions https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40218343362 completed SUCCESS. Verified source-checks job, actual :app:testDebugUnitTest :app:lintDebug combined step SUCCESS, artifact list empty. No APK built or signed. Previous superseded runs were cancelled by PR concurrency, not test failures.
- NOT TESTED: physical Android/Xiaomi Pad 5 display, real APK sample compatibility, SAF provider behavior, Shizuku commands and install; source checks do not establish device success.
- Milestone gates [████░] 4/5 (source integration, JUnit regressions, CI/lint, Drive handoff; device validation pending). This is not overall app completion. Next alternate Explode Arena after this App Commander milestone.


## 2026-10-25 — APK Inspector Content Provider access inventory (milestone P01)
- Functional gap: prior APK Inspector counted Activities, Services and Receivers but entirely omitted Content Providers. ProviderInfo has separate readPermission/writePermission and grantUriPermissions fields; an exported provider can be gated differently for reading and writing. A non-exported provider may still grant access to individual URIs. Do not conflate an exported provider with an exploitable provider or count a URI grant as globally exported.
- Production: GET_PROVIDERS added to archive PackageManager query; ApkComponentExposure.countProviders reports exported count, exported without read gate, exported without write gate, and all URI-grant-capable providers (including private providers). ApkInspection model and Compose UI now expose these distinct indicators and explicit path/URI/runtime caveats. No privileged command, installation, external network or third-party code added.
- Regression: six JVM JUnit tests for null/empty, independent read/write gates, private vs exported, private URI grants, both gates absent, and Android repository/model/Compose integration source contracts.
- Pre-CI engineering question: Does the Android platform PackageManager.GET_PROVIDERS / ProviderInfo integration and Compose model compile, and do all JVM JUnit tests and Android Lint pass? Standalone Kotlin unit tests cannot verify Android SDK API types and Compose integration. Existing source-checks workflow is appropriate; NO assembleDebug/APK build needed.
- Reuse/security/license review: official Android ProviderInfo and <provider> documentation (https://developer.android.com/reference/android/content/pm/ProviderInfo and https://developer.android.com/guide/topics/manifest/provider-element), plus existing repository generic counter. AOSP framework reference Apache-2.0; no external code copied or new dependency. Provider path permissions, app-defined checks and URI grants are not resolved by archive counts.
- Source+test SHA 326e19035fce9eb75ff3a33d7d800575748ae681. CI on this exact SHA PENDING until observed. Device/Xiaomi Pad 5, actual APK provider examples, SAF/Shizuku NOT TESTED. No main changes, no merge, no APK. Acceptance gates: implementation PASS, tests committed (execution pending), CI pending, Drive sync pending, device NOT TESTED. Overall project % unknown.


### 2026-10-25 — Provider inventory source CI resolved
- PASS on exact code+six-test SHA 326e19035fce9eb75ff3a33d7d800575748ae681: GitHub Actions https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40227924993 completed SUCCESS. Verified source-checks job, actual "Run JVM tests and Android static lint without APK packaging" step SUCCESS, artifacts total_count=0. No APK built, no device execution.
- Acceptance gates [████░] 4/5: implementation PASS, six regression tests committed and JUnit step PASS, Android lint/source integration PASS, Google Drive LIVE sync verified, physical Xiaomi Pad 5/real provider samples NOT TESTED. SAF/Shizuku NOT TESTED. Whole-project % unquantified. NEXT alternate to Explode Arena.


## 2026-10-25 — ContentProvider path-permission inventory (verified source-only)
- **Production source changed: YES.** Source/test commit: `37b1943d6252d907f36cab56cdb1ae4b8eb74bf5`. App Inspector now inventories exported ContentProvider path-permission rules and reports path rules with no effective read/write gate. A path-specific permission is considered first; when that direction is not declared, the provider-level read/write permission is used as the fallback. Provider-level labels now say “default” permission so they do not imply path rules were ignored. Whitespace-padded/blank permission strings are not counted as reliable declarations.
- **Tests:** three new JUnit regressions cover per-direction override/fallback, absent provider defaults, and malformed whitespace permissions. Existing provider archive-to-UI integration assertions were expanded.
- **PASS:** exact-SHA GitHub Actions [run 40237317283](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40237317283), `:app:testDebugUnitTest` and `:app:lintDebug` both passed on the same source/test SHA. Workflow is source-only; artifact list is empty. No APK was built or uploaded.
- **NOT TESTED:** real APK ProviderInfo behavior on Xiaomi Pad 5/HyperOS, SAF provider behavior, Shizuku commands, visual UI/device workflow. Manifest counts remain indicators, not proof of reachability or a vulnerability.
- **Research:** Android's official [ProviderInfo.pathPermissions](https://developer.android.com/reference/android/content/pm/ProviderInfo.html), [PathPermission](https://developer.android.com/reference/android/content/pm/PathPermission.html), and [path-permission manifest element](https://developer.android.com/guide/topics/manifest/path-permission-element) documentation; no third-party implementation code copied or dependency added.
- **PR:** #1 remains open Draft; no merge, main write, APK, or release. Next: alternate to Explode Arena; real-device acceptance remains a separate gate.
- **Google Drive:** SYNC FAILED for this run because Google Drive/Docs connector tools were not exposed to the execution environment. LIVE HANDOFF and LIVE INDEX were not read or written; do not treat Drive as synced. GitHub ledger is updated as fallback only.


## 2026-10-26 — N18 shared permission-name integrity
- **Engineering question:** APK Inspector previously treated any nonblank, unpadded permission string as a reliable declared gate, while Activity Launcher used a stricter qualified-name pattern. That could overstate manifest protection for internal control characters, bidi formatting, punctuation or excessive lengths.
- **Production:** new pure Kotlin PermissionNameSafety centralizes the conservative 3..255-character qualified-name rule. ActivityLaunchSafety preserves null/empty-as-no-extra-gate semantics and fails closed for malformed non-empty values without invoking PackageManager. ApkComponentExposure uses the same rule for exported Activity/Service/Receiver and Provider read/write/path permission reporting; invalid path-rule permissions fall back to a valid provider-level gate rather than being treated as protection themselves.
- **Regression tests:** new cases cover newline, bidi, punctuation, 256-character names, valid Android/custom names, provider read/write independence and path fallback. Existing ActivityLaunchSafety malformed-name tests still run in the same suite.
- **PASS exact source/test SHA `f08acb0e3d9e35fbae8a1c89b2b85c2525fef960`:** GitHub Actions [run 40242400761](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40242400761), source-checks job SUCCESS; `:app:testDebugUnitTest` and `:app:lintDebug` passed, BUILD SUCCESSFUL (36 tasks executed). Production commits: `c35274246d91493f601f354ed4768896f122e5a3`, `8c0ec76562213eed9b3170e353517ba33b738fc7`, `f8bbb709c78c7df4a1c71bdda595de544203648b`; test commits `6f1724f8169eb3b874a5be1cb418da03afe764a0`, `f08acb0e3d9e35fbae8a1c89b2b85c2525fef960`.
- **Artifacts: []** no APK built/uploaded. Real Android PackageManager behavior, Xiaomi Pad 5/HyperOS, SAF, Shizuku and Liquid UI/device flow remain **NOT TESTED**. PR #1 stays Draft; no merge/main write.
- **Drive SYNC FAILED:** Google Drive/Docs connector tools are not available in this runtime, so LIVE HANDOFF/INDEX could not be read or written. GitHub plan `docs/N18_PERMISSION_NAME_INTEGRITY_PLAN.md` is the recovery record; no Drive sync is claimed.
- **Next:** return to Explode Arena and continue from N17; later do purposeful device acceptance for both projects.


## 2026-10-26 — N19 bounded Shizuku process-pipe cleanup
- **Engineering question:** PrivilegedProcessRunner could return after its bounded join while a daemon stdout/stderr reader remained blocked on a pipe. That could leak a reader thread across privileged Binder calls and make output completeness ambiguous.
- **Production:** after the bounded drain join, still-live readers are marked incomplete and their streams are closed to unblock reads; the cleanup path repeats this on exit/timeout. A stalled capture cannot be reported as exit=0, and underlying stream exception text remains hidden.
- **Regression:** a blocking InputStream released by close verifies exit=1, explicit incomplete-output status, sanitized errors and no surviving privileged-stdout thread. Existing timeout, bounded capture, zero-length read, successful exit-code and I/O-failure cases remain in the same suite.
- **PASS exact source+test SHA 8cf5fe00b3e1423ce54ffc094f3f01df862383dc:** GitHub Actions [run 40247055115](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40247055115), exact head_sha; :app:testDebugUnitTest + :app:lintDebug, BUILD SUCCESSFUL (36 tasks executed).
- **Artifacts: []** No APK packaging/upload. Xiaomi Pad 5/HyperOS, real Shizuku Binder behavior, SAF and visual UI remain **NOT TESTED**.
- **Drive SYNC FAILED:** Google Drive/Docs connector tools unavailable in this runtime; LIVE handoff/index were not written/read back.
- PR #1 remains open Draft; no merge or main write. Next: continue Explode Arena after this App Commander round.


## 2026-10-26 — N20 Shizuku stale-Binder and binding-lifecycle recovery
- **Engineering question:** if cached `ICommandService.asBinder().pingBinder()` fails while the old remote reference remains non-null, timeout recovery's `remote == null` guard never resets `binding`; later callers can wait on the same stale pending bind. `onBindingDied` and `onNullBinding` were also not handled explicitly.
- **Production:** added pure Kotlin `ShizukuConnectionState` / `BindingRequest`; dead cached remotes are discarded with identity checking, disconnect/dead/null binding callbacks fail the active waiter and allow retry, and timeout cleanup only resets the exact waiter that is still current. Errors from an old command cannot clear a newer Binder.
- **Tests:** `ShizukuConnectionStateTest` covers dead Binder retry, preserving a newer Binder, disconnect/binding-death/null-binding recovery, successful waiter completion, stale timeout isolation, and controller callback wiring.
- **PASS exact source+test SHA `659d5b3c3582e3bb97acfa0ae438fd007a0e1169`:** [GitHub Actions run 40252299297](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40252299297), `./gradlew :app:testDebugUnitTest :app:lintDebug --stacktrace`; both tasks PASS, `BUILD SUCCESSFUL` (36 tasks executed). [Artifacts](https://api.github.com/repos/amroalhamedi2022-hue/app-commander/actions/runs/40252299297/artifacts): total_count=0.
- **NOT TESTED:** actual Binder death/null-binding on Xiaomi Pad 5/HyperOS, SAF behavior on device, and Liquid UI/device workflows.
- **License review:** official Shizuku LICENSE is Apache-2.0; no external code or dependency added.
- **Drive SYNC FAILED:** Google Drive/Docs tools are not exposed in this runtime, so LIVE handoff/index were not read or written back. See `docs/N20_SHIZUKU_BINDER_RECOVERY_PLAN.md`; no Drive sync is claimed.
- PR #1 remains open Draft; no merge/main write, APK packaging, or Device Owner provisioning.



## 2026-10-26 — N21 APK ZIP metadata preflight (source acceptance: PASS)

- **Engineering question:** `ZipFile(file)` parsed an untrusted central directory before `ApkArchivePreflight` could enforce its own `MAX_ENTRIES` and path limits.
- **Production source changed:** before constructing `ZipFile`, the inspector now rejects files above the existing 512 MiB inspection cap, scans only the final ZIP32 EOCD/comment window with `RandomAccessFile`, validates single-disk layout and entry counts, rejects ZIP64 sentinels, enforces a 32 MiB central-directory limit, and checks central-directory bounds. Normal ZIP comments are supported. This is structural preflight, not APK signature verification or a malware scan.
- **Production commit:** `3b692c18c6583de6a1c3c22eb14f334f532bb5c3`.
- **Regression source commit:** `13a12d1fd1f858e4bc4d7cecbde002fe786942c5`; tests cover valid commented ZIPs, early entry-count rejection, central-directory budget rejection and truncated EOCD.
- **Initial CI FAIL:** run [40256820024](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40256820024) caught a missing JUnit `assertTrue` import in the new tests. Fixed in `c978642a1509f2be60a2dcd76852a248940c780b`; no production behavior was weakened.
- **PASS on exact fixed source+test SHA `c978642a1509f2be60a2dcd76852a248940c780b`:** [run 40257037990](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40257037990) completed `:app:testDebugUnitTest` and `:app:lintDebug`; log reports `BUILD SUCCESSFUL in 1m 39s`.
- **Artifacts:** none. No APK packaging/build/upload.
- **License/source review:** inspected Apache Commons Compress `ZipFile.java` and Apache-2.0 license metadata; no code copied or dependency added.
- **NOT TESTED:** physical Xiaomi Pad 5/HyperOS APK inspection, large real-world APK compatibility and install workflow. No signature-authenticity claim.
- **Drive SYNC FAILED:** Google Drive/Docs connector tools are unavailable in this runtime. LIVE HANDOFF/INDEX were not read or written; this GitHub ledger is only the recovery record, not a successful Drive sync.
- **PR state:** #1 remains open Draft; no merge, main write, APK build or Device Owner provisioning.


## 2026-10-26 — N22 SAF export validation order (source acceptance PASS)
- Production refactor: `InstalledApkBundleExport.writeAndVerify()` now validates package name, canonical base/split names, APK count, per-entry size and aggregate size BEFORE calling `openForWrite()`. The validated size total is passed to the private write path, avoiding a duplicate validation pass.
- Regression: invalid package, invalid archive entry name and zero-size source each fail without opening the SAF destination, readback stream, or source bytes.
- Exact source+test SHA: `874f6437fb5106ad584d51e9791fe2e67565874b`.
- **PASS:** [GitHub Actions 40261681763](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40261681763): `:app:testDebugUnitTest` and `:app:lintDebug`; `BUILD SUCCESSFUL in 1m 40s`, 36 tasks.
- **Artifacts: 0; APK not built.** This was a source/JUnit/static-lint verification question, not a packaging question.
- Xiaomi Pad 5 / HyperOS SAF provider behavior: **NOT TESTED**. The unit tests use deterministic streams and do not establish provider-side creation/deletion behavior.
- PR #1 remains open Draft, unmerged; no main write, root, or Device Owner.

## 2026-10-26 — N23 APK central-directory preflight

- **Engineering question:** EOCD counts and bounds alone do not prove that the central-directory record inventory fits the declared range. Can malformed counts/lengths and entry-level ZIP64 sentinels be rejected before `ZipFile` processes untrusted metadata?
- **Production:** `ApkArchivePreflight` now walks the bounded central directory before `ZipFile` construction, checking each 46-byte fixed header, record count/length, name length, per-entry disk number, ZIP64 sentinel sizes/offsets, and local-header offset. Variable fields are skipped by bounded seek, not allocated into per-entry buffers.
- **Regressions:** tests mutate a valid ZIP's EOCD count to disagree with actual records and set an entry's uncompressed-size field to the ZIP64 sentinel.
- **Production commit:** `352a29a40144c08c2bf3ae32abf6e00e66f5d99e`.
- **Exact source+test SHA:** `3a889a45ff72ef45d10286a59e5bd8ed56c8c22e`.
- **PASS:** [source-only CI 40266600791](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40266600791), job `source-checks`; the actual `:app:testDebugUnitTest :app:lintDebug` step completed successfully. No APK packaging or artifacts.
- **NOT TESTED:** physical device, broad third-party APK corpus, SAF provider behavior, Shizuku operations, signature verification. This is structural preflight, not malware scanning or APK authentication.
- **Source/license review:** reviewed OpenJDK ZipFile source license (GPLv2 + Classpath Exception) and official Android APK Signing Block documentation; no third-party implementation copied.
- PR #1 remains open Draft; no main write, merge, root, Device Owner, APK or release.

### N23 exact-SHA source CI RESOLVED — 2026-10-26

- **PASS:** source-only workflow [run 40266772466](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40266772466) completed SUCCESS on latest PR head `cfd517ade5be37f48cd8b4eb6e1d9045f9d87fef`, which includes production+regression source/test SHA `3a889a45ff72ef45d10286a59e5bd8ed56c8c22e`. The actual `:app:testDebugUnitTest :app:lintDebug` step passed.
- The earlier exact source+test commit also passed the same source-only workflow at run 40266600791.
- **Artifacts: 0.** No APK packaging, upload or release.
- **NOT TESTED:** physical device, broad third-party APK corpus, SAF provider behavior, Shizuku operations and cryptographic APK signature verification.
- PR #1 remains open Draft. No main write, merge, root, Device Owner or release.


## 2026-10-26 — N24 persisted SAF readback + N25 Shizuku callback generation isolation

### N24 — Persisted SAF export verification
- **PASS:** source-only CI run [40266987835](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40266987835) completed successfully on PR head `c575365cafb784f83e9821097d6139c506bbc6b3`. The workflow ran `:app:testDebugUnitTest :app:lintDebug`, which includes the current base and bundle SAF readback regressions.
- Export now closes the newly created SAF document, reopens it, validates bundle structure/manifest and independently compares APK entry sizes and SHA-256 before success. Readback failure is fail-closed and scoped cleanup targets only the fresh CreateDocument URI.
- **NOT TESTED:** real DocumentsUI providers, HyperOS behavior, provider deletion support and latency on Xiaomi Pad 5.

### N25 — Stale Shizuku binding callback isolation
- **Problem:** the previous singleton ServiceConnection and unscoped lifecycle transitions allowed delayed callbacks from an older binding attempt to mutate newer Binder state. Also, a joining caller shared the owner's deferred, so its timeout could reset the owner's bind attempt.
- **Production commits:** state helper `146dd606db517f39ef3b00d6d8621b0684e6b845`; controller `1ea2667e3cb18fd4e65dee15d112e1b57b26655f`.
- **Regression commit / exact source+test SHA:** `72f64652541cd01a5b6e6805bc949c7390f03fed`.
- **PASS:** source-only GitHub Actions [run 40276487224](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40276487224) on exact source+test SHA. `:app:testDebugUnitTest` and `:app:lintDebug` passed; `BUILD SUCCESSFUL in 1m 8s`, 36 tasks executed.
- New JVM tests cover stale connect callbacks, stale failure callbacks, joiner-vs-owner timeout behavior, prompt waiter failure, existing Binder identity protection and controller callback wiring.
- **Artifacts: 0.** No APK packaging, signing, upload or release.
- **NOT TESTED:** actual Shizuku service reconnect/process-death behavior on Xiaomi Pad 5 / HyperOS. Unit tests prove the state-machine contract, not platform callback ordering on a device.
- PR #1 remains open Draft and unmerged. No `main` write, auto-merge, root or Device Owner.
- **Next:** verify final docs-updated PR head CI, then continue the next App Commander milestone; alternate back to Explode Arena afterward.


## 2026-10-26 — N26 APK local-header cross-check (source acceptance PASS)

- **Engineering question:** can the inspector reject a structurally inconsistent local ZIP header/data descriptor even when the central directory is internally bounded, before Android's APK parser sees the archive?
- **Production:** `ApkArchivePreflight` now verifies local-header signature, raw local/central filename agreement, flags/method agreement, local CRC/sizes, optional signed or unsigned ZIP32 data descriptors, payload/descriptor bounds, and non-overlapping local record ranges. It rejects encrypted or masked-header flags. It still does not extract entries or claim signature authenticity.
- **Production commits:** `89aeed8b458b6d0789d6c5db63e559362530dba9` and `04d00423ea17c400f12c55ec2b0e8459a6c497f6`.
- **Regression commits:** `6d4133cbc3c085ae57da83f244200607da971f39`, `1a63072d1700744838e98a846bee55792431cb72`, and final exact source/test SHA `8c3b7f791a53b8041f0ba916112c6805dd39a18f`.
- **PASS on exact SHA:** [GitHub Actions run 40290864869](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40290864869), job 121465337746. `:app:testDebugUnitTest` and `:app:lintDebug` passed; log reports **BUILD SUCCESSFUL in 1m 43s**, 36 tasks executed.
- Regressions now cover local signature/name/method/size mismatches, descriptor CRC mismatch, local-record aliasing/overlap, encrypted flags, payload boundary overflow and truncated descriptors. Existing valid ZIPs and descriptor cases passed.
- **Artifacts: 0; APK not built.** No packaging, signing, upload or release.
- **NOT TESTED:** Xiaomi Pad 5 / HyperOS, broad APK corpus, installer UI, cryptographic APK signature verification and malware scanning.
- **Drive SYNC FAILED:** Google Drive/Docs connector tools unavailable; LIVE documents were not read or written. This GitHub record does not imply Drive synchronization.
- PR #1 remains open Draft and unmerged. No `main` write, auto-merge, root or Device Owner.


## 2026-10-26 — N27 central-entry semantic preflight (source acceptance PASS)

- **Engineering question:** can ZIP metadata that is semantically impossible be rejected during the bounded central-directory scan, before `ZipFile` and Android's APK parser are invoked?
- **Production changes:** reject compression methods other than STORED/DEFLATED; compressed size larger than the archive; nonempty entries with zero compressed bytes; STORED entries whose compressed and expanded sizes differ; aggregate declared expansion above 4 GiB; and directory records with nonzero expanded size.
- **Regression tests:** unsupported method, STORED size mismatch, aggregate 3 GiB + 2 GiB expansion with matching local/central metadata, zero compressed bytes for nonempty entry, and directory payload. Existing valid empty DEFLATED directories and nested paths remain accepted.
- **Initial CI:** [run 40303717793](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40303717793) **FAIL** on the aggregate test fixture; inspection of the job log identified the fixture's stale data descriptor. Corrected only the test fixture so local/central metadata agrees and the aggregate guard is the first expected rejection.
- **Exact source + tests SHA:** `f4cb690853ca81963c94ce4d297b4f951f9ea810`.
- **PASS:** [CI run 40304529587](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40304529587), `:app:testDebugUnitTest :app:lintDebug`, **BUILD SUCCESSFUL in 1m 9s**.
- **Artifacts: 0; APK not built.** No package/sign/upload/release.
- **NOT TESTED:** physical device / HyperOS, broad third-party APK corpus, install flow, signature authenticity, malware detection.
- **Drive SYNC FAILED:** no Google Drive/Docs connector is available in this runtime; LIVE HANDOFF/INDEX were not read or written.
- PR #1 remains open Draft; no merge, `main` write, auto-merge, root, or Device Owner.


## 2026-10-26 N28 — ZIP extra-field framing preflight (SOURCE ACCEPTANCE PASS)

- Engineering question and acceptance criteria were recorded before implementation in [N28 plan](N28_ZIP_EXTRA_FIELD_PREFLIGHT_PLAN.md).
- Production commit: `79f2497e9fce8f70027eaffda9609a38459c7731`. `ApkArchivePreflight` now walks every local and central extra-field TLV, requires a complete 4-byte ID/length header and a data length contained by that field's declared region, and rejects ZIP64 extra-field ID `0x0001` consistently with the ZIP32-only policy. Unknown well-formed fields remain accepted without parsing their payload.
- Regression test commit / exact source+test SHA: `453035caf44a72e2e8da8129a6b9d83f6391604e`; four tests cover valid unknown fields, malformed local/central framing and ZIP64 extra rejection.
- **PASS:** source-only [GitHub Actions run 40317867029](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40317867029) on exact SHA `453035caf44a72e2e8da8129a6b9d83f6391604e`: `:app:testDebugUnitTest :app:lintDebug`, **BUILD SUCCESSFUL in 1m 10s**, 36 tasks executed.
- **PASS:** post-CI source/test re-fetch confirmed both call sites and all four regression names on the PR branch.
- **Artifacts: 0.** No APK packaging/build artifact, signing, release, merge or main write.
- **NOT TESTED:** physical-device/HyperOS behavior, broad third-party APK corpus, actual installer behavior, APK signature authenticity and malware detection.
- **Google Drive SYNC FAILED:** connector unavailable in this runtime; the LIVE handoff/index were not read or written.


## 2026-10-26 N29 — ZIP general-purpose flag preflight (SOURCE ACCEPTANCE PASS)
- Engineering question: reject reserved/unsupported general-purpose ZIP flags before platform parsing without breaking standard data descriptors, UTF-8 names, or DEFLATE option metadata.
- Production source: central-directory and local-header flag validation now allows only bits 1-3 and 11; encryption/masked-header flags remain rejected, and DEFLATE option bits are rejected for non-DEFLATED entries.
- Regression source: byte-level tests mutate both central and local records consistently for reserved/unsupported bits (0x0010, 0x0020, 0x0080, 0x0100, 0x0200, 0x0400, 0x1000, 0x4000, 0x8000), plus DEFLATE option bits on a STORED entry.
- Specification reviewed: PKWARE APPNOTE §4.4.4 and Library of Congress ZIP format notes. No copied implementation or new dependency.
- **PASS:** exact production/test SHA `59ab87a763d64fe8a0ee7f431e72d90e4b7bdf5f`, [source-only CI #40334932127](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40334932127). `:app:testDebugUnitTest` and `:app:lintDebug` succeeded; **BUILD SUCCESSFUL in 1m 41s**, 36 tasks executed. Source blob `29920f2800eed346d7b4995c896f9c2f7fa8fa26`; test blob `2e022a7f162f07366632d6e4e0008937632dc1ca`.
- **Artifacts: 0.** No APK packaging, artifact upload, merge, main write, root, or Device Owner.
- **NOT TESTED:** Xiaomi Pad 5 / HyperOS, broad real-world APK corpus, Android installer behavior, cryptographic signature verification, malware detection. Google Drive LIVE sync remains unverified and must not be inferred from this GitHub ledger.

## 2026-10-26 — N30 strict UTF-8 ZIP metadata (source acceptance PASS)

- **Engineering question:** can malformed UTF-8 entry-name/comment bytes be rejected during the bounded central-directory scan, before Android's UTF-8 ZIP parser receives the archive, without breaking valid visible Unicode names?
- **Production:** `ApkArchivePreflight` now validates raw entry-name bytes and non-empty central-directory entry comments with a strict UTF-8 decoder configured to REPORT malformed/unmappable input. Invalid bytes become a stable, sanitized `IOException` before `ZipFile` construction. No entry extraction, new dependency, or third-party implementation was added.
- **Specification/source review:** PKWARE APPNOTE EFS bit 11 describes UTF-8 filename/comment encoding; Android libcore `ZipFile` source documents UTF-8 decoding of entry names/comments. See [PKWARE APPNOTE](https://support.pkware.com/display/PKZIP/APPNOTE) and [Android libcore ZipFile](https://android.googlesource.com/platform/libcore/+/00efa8dec72a80bb751cda0dce2683b16c9f1e1c/ojluni/src/main/java/java/util/zip/ZipFile.java).
- **Exact source + test SHA:** `8d3be7e9d54da0ce1f411e1ae4030bf26df93732`; source blob `f37f773c7af6da4a71a4b01dd80b1fa07a87a221`; test blob `172863354ba757fc9c59c57f65088daa7d139ea9`.
- **PASS:** [source-only CI run 40354680824](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40354680824), actual `:app:testDebugUnitTest :app:lintDebug --stacktrace`; log: **BUILD SUCCESSFUL in 1m 38s**, 36 tasks executed.
- **PASS:** post-CI re-fetch of production and test files confirmed the strict decoder and all three N30 regressions on the PR branch.
- **Artifacts: 0; APK not built.** No package/sign/upload/release, merge, main write, root or Device Owner.
- **NOT TESTED:** physical Xiaomi Pad 5 / HyperOS, broad real-world APK corpus, actual installer behavior, cryptographic signature authenticity and malware detection.
- **Google Drive SYNC FAILED:** Google Drive/Docs connector unavailable; LIVE HANDOFF/INDEX were not read or written. This GitHub ledger is not a Drive sync.


## 2026-10-26 — N31 central-directory path collision preflight (SOURCE ACCEPTANCE PASS)

- **Engineering question:** path-policy checks previously ran after `ZipFile` constructed its entry inventory. Can duplicate/canonically equivalent names, unsafe paths, file/directory aliases, and parent-under-file collisions be rejected during the bounded raw central-directory scan first?
- **Production:** introduced one `PathRegistry`/`registerPath` implementation used both during central-directory preflight and again after `ZipFile` enumeration. It applies NFC collision detection, bounded name length, absolute/backslash/dot-segment/control/bidi checks, duplicate-name rejection, parent-under-file checks, and file/directory collision checks before platform ZIP parsing.
- **Regressions:** exact duplicate logical names with local/central bytes kept consistent; traversal and unsafe paths; file/directory collisions in both entry orders; canonically equivalent Unicode names; Unicode alias of an implicit directory. Existing nested-directory and visible Arabic path acceptance tests remain in the suite.
- **Source/spec review:** reused the project's existing path policy and JDK `Normalizer`/UTF-8 APIs; no dependency or copied third-party implementation. The repository's research notes already identify Android libcore `ZipFile` as the downstream parser. This remains structural preflight, not APK signature verification or malware scanning.
- **Initial CI FAIL:** [run 40373353386](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40373353386) exposed two test-expectation issues. The overlong name was rejected earlier by the bounded central metadata check; a fixture that aliased a local record also created duplicate names, which the new earlier guard correctly rejected. Both assertions were corrected to test the actual preflight contract.
- **PASS:** exact corrected PR head `07d0e27e28be5f500a15782769299c8aec6339c4`, [source-only CI #40373884980](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40373884980). `:app:testDebugUnitTest :app:lintDebug` succeeded; log **BUILD SUCCESSFUL in 1m 19s**, 36 tasks executed.
- Source blob `99c601feb203bca4150c66f4c956a9a01d49dc35`; test blob `3340eea3f2bc83aef39ac28a2470a17d5bf42546`. CI run had zero APK artifacts; no APK packaging.
- **NOT TESTED:** Xiaomi Pad 5 / HyperOS, broad real-world APK corpus, actual installer behavior, cryptographic signature authenticity, malware detection.
- **Google Drive SYNC FAILED:** no Google Drive/Docs connector is available in this runtime; LIVE HANDOFF/INDEX were not read or written.
- PR #1 remains open Draft and unmerged. No `main` write, auto-merge, root or Device Owner.

## 2026-10-26 — N32 SAF zero-progress bundle stream guard (SOURCE ACCEPTANCE PASS)

- **Engineering gap:** the bundle verifier's outer stream wrapper delegated positive-length reads directly to a provider. A broken SAF/InputStream implementation returning repeated zero-byte reads could make `ZipInputStream` loop while parsing the first local header, before the per-entry copy guard or central-directory checks ran.
- **Production change:** `ZipBundleStructure.TrackingStream` now counts consecutive zero-progress bulk reads and throws a stable `IOException("APK bundle stream stalled")` after eight. A zero-length request remains a legal no-op; successful byte reads and EOF reset the counter. Existing raw-size and rolling-tail limits are unchanged.
- **Integration regressions:** a valid generated bundle is verified through a provider-like stream that returns eight zero-progress reads during header parsing; the verifier fails promptly. Additional tests wrap bundle write/readback in the same cleanup boundary used by the repository and verify readback denial deletes the new document exactly once, while cleanup denial cannot replace the original archive verification failure.
- Production commit: `d965ce76dc11f32408802d78823624c766300b11`; source blob `e0e014c1b5a2cd577b27787e2b5d6a2e7fbd23b6`.
- Exact production + tests SHA: `975dc372a67dae0b1921bc27c73b1e4a5eb0aeee`; test blob `3347e657afaf6e8428dc86fb2765a616f6045ce7`.
- **PASS:** source-only [CI #40375961761](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40375961761) on the exact production+test SHA. `:app:testDebugUnitTest :app:lintDebug` succeeded; log **BUILD SUCCESSFUL in 1m 3s**, 36 tasks executed.
- **Artifacts: 0.** No APK packaging/signing/upload, release, merge, `main` write, root, Device Owner or auto-merge.
- **Related SAF acceptance:** the existing bundle readback, hash/manifest/central-directory, package-snapshot and cleanup tests ran in the same JUnit suite. N31's exact corrected source/test SHA `07d0e27e28be5f500a15782769299c8aec6339c4` passed [CI #40373884980](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40373884980); docs-updated head `163068a12ab0f2db2c0105b85fd985d807897815` passed [CI #40374576894](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40374576894).
- **NOT TESTED:** real Xiaomi Pad 5 / HyperOS SAF provider behavior, provider-side deletion refusal, broad real-world APK corpus, cryptographic signer authenticity, malware detection, Shizuku device behavior and visual UI acceptance. Source/JVM PASS is not device PASS.
- **Google Drive SYNC FAILED:** Google Drive/Docs connector unavailable in this runtime; LIVE HANDOFF/INDEX were not read or written. This GitHub ledger is not a Drive sync.
- **Next:** independently verify the docs-updated PR head in CI, then continue device acceptance and remaining app requirements without merging or building routine APKs.

## 2026-10-26 — docs-updated PR-head recheck (PASS)

- Latest docs-updated PR head at this verification: `c603fde5d6222bebec4914bc69aee74211cd50b2`.
- **PASS:** [source-only CI #40376337051](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40376337051); `:app:testDebugUnitTest :app:lintDebug` succeeded on that head. Log: **BUILD SUCCESSFUL in 1m 41s**, 36 tasks executed.
- Artifacts: **0**. No APK packaging/signing/upload.
- PR #1 remains open and Draft. This is still source/JVM+lint acceptance, not device acceptance.
- **NOT TESTED:** actual Xiaomi Pad 5 / HyperOS SAF provider readback/deletion, Shizuku operations, installer behavior, broad real-world APK corpus, signature authenticity and visual UI acceptance.
- **Next:** authorized physical-device acceptance; no merge or routine APK build.



## 2026-10-26 — N33 APK Signing Block structural preflight (SOURCE/JVM CI PASS)

- **Engineering question:** the existing ZIP preflight checked central/local metadata but treated the bytes before the central directory as an unexamined gap. APK v2/v3 signing blocks occupy that exact region; malformed 64-bit sizes or ID/value pair lengths and a local entry overlapping the block should fail before Android's `ZipFile` parser is invoked.
- **Official format reviewed:** AOSP's [APK Signature Scheme v2 specification](https://source.android.com/docs/security/features/apksigning/v2). No third-party code or dependency was added. The preflight validates container framing only; it does not verify cryptographic signatures, signer trust, or malware status.
- **Production change:** detect exact `APK Sig Block 42` magic; validate matching 64-bit header/footer size, bounds, 64 MiB maximum block size, non-empty pair region, each uint64-prefixed pair length, and a 1,024-pair limit; reject any referenced local ZIP record that crosses into the signing block. Unknown pair IDs remain opaque. Archives without the magic retain existing v1-only behavior.
- **Regression coverage:** valid unknown-ID block; header/footer mismatch; malformed pair length; local payload overlap; oversize block; excessive pair count. Existing ordinary ZIP/v1-only and local-header/central-directory cases remain in the same JUnit suite.
- Source commit: `123172535e4de89afd66cb6b69126594e6a211e7`; source blob `dc487d55446111595557cc3db7c70b161c29bf66`.
- Exact source+test head: `87e4ffea6b71caeafa154c956deb8b0ea9c2b9cc`; test blob `537f37087ae51165aa495836c31adf2d8b3991e1`.
- **PASS:** source-only [CI #40391960259](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40391960259). `:app:testDebugUnitTest` and `:app:lintDebug` passed; log **BUILD SUCCESSFUL in 1m 46s**, 36 tasks; zero APK artifacts. The earlier run #40391722855 was cancelled by concurrency and is not counted.
- No APK packaging, signing/upload, release, merge, `main` write, root, Device Owner, or auto-merge.
- **NOT TESTED:** physical Xiaomi Pad 5 / HyperOS SAF providers, broad third-party APK corpus, cryptographic signature authenticity, malware detection, Shizuku device behavior and visual UI acceptance.
- **Google Drive SYNC FAILED:** Drive/Docs connector unavailable in this runtime; LIVE handoff/index were not read or written. GitHub docs are not claimed as Drive sync.
- **Next:** verify CI on the docs-updated PR head, then continue independent device acceptance and the remaining App Commander requirements.


## 2026-10-26 — N33 docs-updated PR-head recheck (PASS)

- Latest PR #1 head at this check: `8430bf8957766e0d2405e34ca12c6c582f1bcc35` (plan and acceptance ledger updated after the source/test pass).
- **PASS:** [source-only CI #40392555810](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40392555810), `:app:testDebugUnitTest :app:lintDebug`, **BUILD SUCCESSFUL in 1m 50s**, 36 tasks executed.
- Artifacts: **0**; no APK packaging/signing/upload. PR #1 remains open Draft and unmerged.
- This is a docs-updated source/JVM+lint check, not a device test. Xiaomi Pad 5 / HyperOS, real SAF providers, Shizuku device operations, cryptographic signature authenticity and broad APK corpus remain **NOT TESTED**.



## 2026-10-26 N34 — Ambiguous ZIP end-record preflight

- Production source changed: `ApkArchivePreflight.preflightCentralDirectory` now counts every EOCD signature candidate in the bounded tail whose declared comment ends exactly at EOF and rejects multiple candidates before opening `ZipFile`. This prevents a signature-shaped byte sequence inside the arbitrary ZIP comment from creating a parser ambiguity.
- Regression tests added: one fixture embeds a second EOCD signature in a 40-byte archive comment with a matching end-length and must fail with an explicit ambiguity error; a second fixture embeds the same signature with a nonmatching length and must remain accepted.
- Source commit: `08593232ec5898550f83fd5093d445a74c96d24c`; source blob `38e3b0881f38a602acc7daf6c6a82755db412950`.
- Test commit: `601f985c856ce910493901163fae5710ff6f0bf7`; test blob `3492204596a04b81bfe9e15ff52046d1d2075448`.
- **PASS — source/JUnit/lint CI:** [run #40408619247](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40408619247) on head `4b3570f0648af93e9f9c5fc72aae25c4e5605abf`; `:app:testDebugUnitTest` and `:app:lintDebug` succeeded, `BUILD SUCCESSFUL in 1m 19s`, zero artifacts. Initial run `40408442586` was cancelled by PR concurrency and is not counted.
- **PASS — post-ledger PR-head recheck:** [run #40408973395](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40408973395) on head `e9f99a58039cc44547a0a3f9cb9a8372ec635b2c`; `:app:testDebugUnitTest` and `:app:lintDebug` passed, `BUILD SUCCESSFUL in 1m 20s`, zero artifacts. **PASS — subsequent docs-head recheck:** [run #40409365147](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40409365147) on head `31b0fec399a48ee2c31d6a38595670c885df1fe2`; `:app:testDebugUnitTest` and `:app:lintDebug` passed, `BUILD SUCCESSFUL in 1m 23s`, zero artifacts. The current evidence-only commit still requires a final CI check. No APK build/artifact, merge/main write/root/Device Owner. Physical device, APK signature authenticity, malware detection, Shizuku operations, and broad real-world corpus remain **NOT TESTED**.
- Google Drive LIVE sync: **SYNC FAILED** because no Google Drive/Docs connector is available in this runtime; no Drive update is claimed.


## 2026-10-27 N35 — resilient app-inventory refresh state

- **Engineering question:** can failed, cancelled or overlapping PackageManager inventory refreshes leave the UI loading forever, erase the last good inventory, or allow an older request to overwrite a newer one?
- **Production change:** added pure `InventoryRefreshState` generation reducer; latest request wins; failure preserves cached apps, stops the spinner and shows a generic retry card in Dashboard/Apps; cancellation clears only its own current spinner and is rethrown.
- **Regression tests:** five JVM tests cover failure retention, stale success/failure isolation, cancellation, successful-empty semantics and retry generation reset.
- **Plan:** [N35 acceptance plan](https://github.com/amroalhamedi2022-hue/app-commander/blob/automation/apk-inspection-safety-20261023/docs/N35_RESILIENT_INVENTORY_REFRESH_PLAN.md).
- **Exact source/test SHA:** `26d894ae6a031e33457d65c1a381eb119f7788ea`; UI blob `b5c12fbf1798be8a09114a63a7c75b3ef54a8643`; reducer blob `66208e273e4a1c861649b1b58f6e754f972f4dc9`; test blob `2fc8d4b2f2464d534a0ae94d1b6d94326d965180`.
- **PASS:** [source-only CI #40415628864](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40415628864), `:app:testDebugUnitTest :app:lintDebug`, **BUILD SUCCESSFUL in 1m 37s**, 36 actionable tasks. Artifacts: **0**.
- **NOT TESTED:** Xiaomi Pad 5/HyperOS runtime, live PackageManager failure/cancellation, visual retry interaction, Shizuku device operations, APK signature authenticity and malware detection. Source/JVM PASS is not device PASS.
- No APK packaging/signing/upload, release, merge, main write, auto-merge, root or Device Owner. PR #1 remains Draft.
- The following ledger-only commit requires its own source-only CI query; do not transfer the above result to the new head without rechecking.
- **Google Drive:** SYNC FAILED — no Drive/Docs connector is available in this runtime, so LIVE handoff/index read/write/read-back and folder metadata verification could not be performed.


## 2026-10-27 N36 — Best-effort installed-app metadata mapping

- **Engineering question:** can a single per-package label/version/update-time failure silently drop an otherwise listed installed application?
- **Production change:** added pure `AppInventoryRowMapper`; `AppRepository.loadApps()` now preserves valid package rows and uses package-name / `—` / `0L` fallbacks for optional metadata failures. Top-level inventory query failures still surface through the N35 retry state.
- **Regression coverage:** five JVM tests exercise thrown and blank metadata, valid-field preservation, invalid package identity isolation, and ordering after fallback.
- **Source/license review:** Android PackageManager API docs and AOSP PackageManager.java (Apache-2.0) inspected; no third-party implementation copied and no new dependency/permission.
- **PASS — exact source/test SHA:** `075c1635ceb701ba771f3d330b44b197eb8d1d9c`.
- **PASS — source-only CI:** [run #40429462073](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40429462073), `:app:testDebugUnitTest :app:lintDebug`, **BUILD SUCCESSFUL in 1m 42s**, 36 actionable tasks; job `source-checks` PASS.
- **Artifacts: 0 confirmed**. No APK packaging, signing or upload occurred. A documentation-only follow-up head will be rechecked; its result will be recorded in the PR thread without another source edit so the checked head remains stable.
- **NOT TESTED:** Xiaomi Pad 5/HyperOS PackageManager runtime, actual UI rendering, Shizuku/SAF device behavior. No APK build, merge, main write, root, or Device Owner.
- **Google Drive:** SYNC FAILED — this runtime has no Google Drive/Docs connector; LIVE handoff/index read/write and folder metadata read-back could not be verified.


## 2026-10-27 N37 — optional UsageStats failure isolation

- **Production change:** AppRepository.hasUsageAccess now safely returns false if AppOpsManager lookup fails; usageByPackage delegates to a pure UsageStatsSnapshot helper. Denied access, unavailable service, thrown query, or null provider result yields empty usage metadata without aborting the installed-app inventory.
- **Regression tests:** six JVM cases cover denied access/no provider call, AppOps exception, UsageStats query exception, null result, successful data and 24-hour window, plus near-epoch/zero-clock handling.
- **Source review:** official Android UsageStatsManager and AppOpsManager API references reviewed; no copied third-party implementation, dependency, permission, or network access.
- **PASS — exact source/test SHA:** a450c404ac7f7570d23bcd7b93b5a073803aabcb.
- **PASS — source/JVM/lint CI:** [run #40434828097](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40434828097), :app:testDebugUnitTest and :app:lintDebug, BUILD SUCCESSFUL in 1m 41s, 36 tasks.
- **Artifacts: 0 confirmed**; no APK packaging, signing, or upload occurred.
- **NOT TESTED:** Xiaomi Pad 5/HyperOS AppOps and UsageStats provider behavior, UI rendering, SAF providers, Shizuku operations, APK authenticity and malware detection.
- **Google Drive LIVE sync: SYNC FAILED** — no Drive/Docs connector is exposed in this runtime; no LIVE document was read or written and no folder metadata was verified.


## 2026-10-27 N38 — fail-safe verified APK snapshot cleanup

- **Engineering question:** can cache expiry arithmetic remain safe under a negative/near-epoch wall clock, and can the returned verified installer snapshot be accidentally removed by a redundant post-URI cleanup? Source review alone cannot demonstrate the overflow regression is prevented.
- **Production changes:** cleanup fails closed for negative clocks and retention intervals greater than observed time; removed the redundant cleanup after generating the returned FileProvider URI. Existing cleanup still runs before a new snapshot is allocated.
- **Regression tests:** three JVM cases cover `Long.MIN_VALUE` cutoff overflow, near-epoch/retention-longer-than-uptime behavior, and `Long.MAX_VALUE` retention.
- **Source/license review:** own Kotlin cache-lifecycle code only; no new dependency, permission, network access or external code.
- **PASS — exact source/test/PR head:** `34feb808b7bee907147d4ad4c6082214d8f4dbc0`.
- **PASS — source/JVM/lint CI:** [run #40441604406](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40441604406), `:app:testDebugUnitTest :app:lintDebug`, BUILD SUCCESSFUL in 1m 41s, 36 actionable tasks.
- **Artifacts: 0** confirmed; no APK packaging, signing or upload occurred.
- **NOT TESTED:** real SAF providers, Android installer handoff, Xiaomi Pad 5/HyperOS. No APK packaging, signing, merge, main write, root, or Device Owner.
- **Google Drive LIVE sync: SYNC FAILED** — no Google Drive/Docs connector is exposed in this runtime; no LIVE document was read/written or folder metadata verified.

## 2026-10-27 — N39 fail-closed snapshot expiry

- **PASS — source/test head:** `676454c507e057a42adc8460265a0ea85debb5c8`.
- **PASS — source/JVM/Lint:** [run #40446325452](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40446325452); artifacts: **0**.
- Cache cleanup is best-effort for denied listing and unknown metadata; install snapshot preparation remains fail-closed for content verification but does not fail because optional cleanup could not inspect a file.
- Device/HyperOS installer behavior: **NOT TESTED**.

## 2026-10-27 — N40 APK signer lineage inspection

- **PASS — exact source/test SHA:** `feb1ee582dd9c7a59cb2184a9b1fb12c6785d377`.
- **PASS — source-only CI:** [run #40447641304](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40447641304), `:app:testDebugUnitTest :app:lintDebug`, BUILD SUCCESSFUL in 1m 43s.
- **Artifacts: 0 confirmed**; no APK packaging/signing/upload.
- Added platform-aware current-signer vs rotation-history display and pure JVM tests for rotation ordering, multi-signer normalization, unavailable metadata and duplicate fingerprints.
- Android documents that single-signer history is ordered oldest-to-current and is unavailable as a rotation chain for multiple current signers: https://developer.android.com/reference/android/content/pm/SigningInfo
- Fingerprints and lineage are metadata only; no malware scan or independent authenticity guarantee. Device/HyperOS archive behavior: **NOT TESTED**.
- The final docs-updated PR-head CI result will be recorded in the PR conversation after the final documentation commit.
- Google Drive LIVE sync: **SYNC FAILED** — no Drive/Docs connector; no LIVE document read/write or folder metadata verification claimed.


## 2026-10-27 — N41 APK certificate identity metadata (source CI PASS)

- **Engineering question:** can X.509 subject/issuer/serial/algorithm/date metadata be surfaced without losing the raw certificate SHA-256 when parsing fails or misrepresenting metadata as signature verification?
- **Production source:** extended `ApkSignerSummary` and `ApkInspection`, wired parsed metadata from `AppRepository.inspectApk`, and added current/history certificate details to the Arabic inspector UI.
- **Tests:** generated self-signed DER fixture, malformed DER, empty/oversized inputs, fingerprint retention, and signer-summary/history-order integration.
- **Open-source review:** Android `SigningInfo` docs checked; Google `apksig` is Apache-2.0 but explicitly intended for use outside Android devices, so no runtime dependency or copied implementation was introduced.
- **PASS — exact source/test SHA:** `a0a4b3695ae1ed5351c243143755c94d565e8af6`.
- **PASS — source/JVM/Lint CI:** [run #40452757223](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40452757223), `:app:testDebugUnitTest :app:lintDebug`, BUILD SUCCESSFUL in 1m 54s, 36 tasks.
- **Artifacts: 0 confirmed**; no APK packaging/signing/upload.
- Docs-updated PR-head recheck will be verified separately and recorded in the PR discussion.
- **Device / HyperOS / signature verification:** NOT TESTED.
- **Google Drive LIVE sync:** SYNC FAILED — no Drive/Docs connector is exposed; no LIVE document read/write or folder metadata verification is claimed.


## 2026-10-27 N42 — Unix ZIP entry type preflight

- Engineering question and acceptance criteria recorded in `docs/N42_UNIX_ZIP_ENTRY_TYPE_PREFLIGHT_PLAN.md` before CI.
- Production change: inspect central-directory host-system/external-attributes fields for Unix-created entries; reject symbolic links and special filesystem types; reject POSIX regular-file/directory type mismatches with trailing-slash paths; allow zero/unspecified modes and non-Unix hosts.
- Regression tests: valid Unix regular files/directories, symlink, FIFO and regular-file/directory mismatch.
- **PASS — source commit:** `3bf26b49bf5ddcf478fe619dc517a32c748fbd83`; **test commit:** `e1a0822a3cb29093bbac451d6ee78d1abd37c1d9`.
- **PASS — tested PR head:** `4cb373c23ffebd592028b52fe5a8801b2ea35c7b`; [CI #40457897855](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40457897855) and [CI #40457924106](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40457924106) both passed `:app:testDebugUnitTest :app:lintDebug`.
- **Artifacts: 0 confirmed** in both runs. Workflow has no APK packaging/signing/upload.
- No release, merge or main write. Device/HyperOS remains **NOT TESTED**.
- Google Drive LIVE handoff/index: **SYNC FAILED** — no Drive/Docs connector is available in this runtime; no LIVE read/write or folder metadata verification is claimed.


## 2026-10-27 N43 — deterministic installed-app inventory ordering

- **Production source + tests SHA:** `7621e0f04eec4b91ae2b03a4c6d7d9a6e7b80661`.
- **PASS — source/JVM tests + Android lint:** [Actions run #40465466282](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40465466282), `:app:testDebugUnitTest :app:lintDebug`, **BUILD SUCCESSFUL in 1m 43s**.
- **Artifacts: 0 confirmed.** This was source/JVM test and lint only; no APK packaging/signing/upload.
- Regression coverage: same duplicate-label candidate set in three different input orders; all resolve to package order `org.example.a`, `org.example.m`, `org.example.z`.
- Docs-updated PR-head recheck will be verified after this ledger update and recorded in the PR discussion.
- **NOT TESTED:** Xiaomi Pad 5 / HyperOS live PackageManager enumeration and UI rendering, SAF provider behavior, Shizuku device operations, APK authenticity and malware detection. No merge, main write, release, auto-merge, root or Device Owner.

## 2026-10-27 N44 — canonical Unicode inventory ordering (validation pending)

- Production source changed: `AppInventoryRowMapper.inventorySortKey` applies NFC → invariant lowercase → NFC for comparison only; display labels remain untouched.
- Added a regression with composed `Café` and decomposed `Cafe\u0301` labels. It asserts identical package ordering under reversed input and exact preservation of both display strings.
- Source/test SHA: `f9bc35f77a71f9350cc0a6a2f04f5443185dca99`.
- Source/JVM/Lint CI and docs-updated PR-head recheck: **PENDING**.
- Artifacts: 0 expected; no APK build/sign/upload. Xiaomi Pad 5 / HyperOS remains **NOT TESTED**.
- Drive LIVE sync: **SYNC FAILED** if connector unavailable; no Drive read/write or folder metadata verification is claimed.

## 2026-10-27 N44 validation update — PASS

- Exact production/test SHA `f9bc35f77a71f9350cc0a6a2f04f5443185dca99`: **PASS** — [source/JVM/Lint run #40467321993, attempt 2](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40467321993), `:app:testDebugUnitTest :app:lintDebug`.
- Docs-updated head `c2c9b6e547f3229d187786eb7d9265b750c22ce3`: **PASS** — [run #40467473913](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40467473913), `:app:testDebugUnitTest :app:lintDebug`.
- Unicode regression proves composed/decomposed `Café` labels use a stable package-name tie-break under reversed provider order while the original display strings remain intact.
- **Artifacts: 0** confirmed; workflow executes JVM tests and Android lint only, with no APK packaging/signing/upload.
- Xiaomi Pad 5 / HyperOS inventory UI, SAF and Shizuku device behavior remain **NOT TESTED**.
- Google Drive LIVE sync: **SYNC FAILED** — no Drive/Docs connector is exposed; no Drive document or folder metadata was read or written.

## 2026-10-27 N45 — explicit locale-independent inventory ordering

- **Engineering question and acceptance criteria recorded before CI:** `docs/N45_LOCALE_INDEPENDENT_INVENTORY_SORT_PLAN.md`.
- **Production change:** `AppInventoryRowMapper.inventorySortKey` now uses `lowercase(Locale.ROOT)` between NFC normalization passes; only comparison keys change, not displayed labels. No dependency, permission, network access or third-party code added.
- **Regression:** map the same `I` / `i` labels with English and Turkish process defaults, assert identical package order and exact display-label preservation. Existing N44 composed/decomposed Unicode test remains in the same suite.
- **Exact source/test SHA:** `170bef46a369911d16e1661c800cce8f97001623`; production commit `87afa93774f123fb0bc22f96668d8c53f73b773f`.
- **PASS — source/JVM/lint:** [run #40469466535](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40469466535), `:app:testDebugUnitTest :app:lintDebug`, **BUILD SUCCESSFUL in 1m 53s**.
- **Artifacts: 0 confirmed**; the workflow contains no APK packaging/signing/upload task. Docs-updated PR head requires a separate recheck.
- **NOT TESTED:** Xiaomi Pad 5/HyperOS PackageManager ordering and Compose rendering, SAF providers, Shizuku operations, APK signature authenticity or malware detection.
- No merge, main write, release, auto-merge, root or Device Owner. Google Drive LIVE sync remains **SYNC FAILED** because no Drive/Docs connector is exposed; no LIVE document or folder metadata read/write is claimed.


## 2026-10-27 N47 — Fail-fast validation before Shizuku dispatch
- Production source changed: YES. Added PrivilegedCommandPreflight; reused packageNameError; ShizukuController now validates operation/package/argument before permission checks and UserService binding. The elevated CommandUserService keeps its independent validation and safety checks.
- Tests: malformed package, unsupported operation, invalid permission/component never invoke the dispatch callback; valid input dispatches once; a source contract test checks validation/permission/bind ordering.
- **PASS — current source/test SHA:** a3f545ba00bc9970e96bc609352f44257ee9777b; [CI #40484909419](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40484909419), JVM tests + Android lint, success.
- **Artifacts: 0 confirmed**; no APK packaging/signing/upload.
- **NOT TESTED:** Xiaomi Pad 5/HyperOS, real Shizuku/ADB Binder flow, SAF providers or device commands. No root or Device Owner provisioning.
- PR #1 remains Draft/open; no merge, main write or auto-merge.
- Google Drive LIVE sync: **SYNC FAILED** — no Drive/Docs connector is exposed; no LIVE read/write or folder metadata verification is claimed.

## 2026-10-27 N48 — App-detail loading resilience

- **Production/source + tests SHA:** `20c0a63b2ae12145f822ba2f9e7ca18d91019c02`.
- Added `AppDetailsLoadState` and wired the real Compose detail sheet for initial loading, retry, and post-command refresh. An initial PackageManager/provider failure now stops the spinner and shows an Arabic retry card; failed refreshes preserve the last readable details.
- Latest-request-wins generation checks reject stale success, failure, and cancellation results.
- Five JVM tests cover initial failure, preserved successful snapshot, stale result rejection, retry/cancellation, and stale cancellation.
- **PASS — source/JVM/lint CI:** [run #40491926201](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40491926201), `:app:testDebugUnitTest :app:lintDebug`, BUILD SUCCESSFUL in 1m 10s; 36 actionable tasks.
- **Artifacts: 0 confirmed.** Workflow performed source tests and lint only; no APK packaging/signing/upload.
- **NOT TESTED:** Xiaomi Pad 5/HyperOS detail-sheet interaction, real PackageManager removal/update races, SAF and Shizuku on-device.
- **SYNC FAILED:** Google Drive LIVE handoff/index unavailable because no Drive/Docs connector is exposed in this runtime; no LIVE read/write or folder metadata verification is claimed.
- PR #1 remains Draft/open. No APK build, merge, main write, root, Device Owner, or auto-merge.


## 2026-10-27 N49 — SAF bundle source snapshot

- **Source/test SHA:** `c9b34e22b84f87adec2cd79d474ecea30a14e150`.
- **PASS — JVM tests + Android Lint:** [CI #40501376446](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40501376446), exact PR head; `:app:testDebugUnitTest :app:lintDebug` completed successfully.
- Production change: both bundle export entry points snapshot the caller-owned source list before validation, then use the same snapshot for size/name validation and ZIP writing. This prevents a mutable list changing from a provider callback after preflight.
- Two regressions mutate the original list from `openForWrite` and from an `OutputStream` callback, then assert the written base APK bytes and SHA-256 remain correct.
- **Artifacts: 0 confirmed.** Source/JVM/Lint workflow only; no APK packaging/signing/upload.
- Xiaomi Pad 5/HyperOS SAF provider readback: **NOT TESTED**.
- Google Drive LIVE sync: **SYNC FAILED** — no Drive/Docs connector is exposed in this runtime; no LIVE document read/write or folder metadata verification is claimed.
- PR #1 remains Draft/open. No merge, main write, root, Device Owner or auto-merge.

## 2026-10-27 N50 — SAF partial-document cleanup diagnostics (source/JVM PASS)

- Production implementation: typed suppressed `PartialDocumentCleanupDiagnostic` reports unconfirmed cleanup while preserving the original export/readback exception; `deleteDocumentIfConfirmed` refuses deletion unless the URI is confirmed as a document. Base APK and bundle export UI explain when manual inspection/removal may be needed.
- Regression coverage: provider delete returns false; cleanup throws; original failure remains primary; unconfirmed URI never invokes delete; base and bundle export failure paths retain cleanup diagnostics.
- **PASS — source/test head:** `6173145507847dcbe33839c0077ab24bcf0e49f4`; [CI #40516604932](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40516604932), `:app:testDebugUnitTest :app:lintDebug`, BUILD SUCCESSFUL in 1m 40s.
- **FAIL, superseded:** [CI #40511432761](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40511432761) found three test fixture return-type errors; all were corrected and the subsequent run passed.
- **Artifacts: 0**; workflow does not package or upload APKs. No merge, main write, root, Device Owner or auto-merge.
- **NOT TESTED:** Xiaomi Pad 5/HyperOS and real SAF provider semantics. Docs-updated PR-head recheck **PASS** on `140cb04d65e315e46b3eb0466c6d4d7f1d1c698f`: [CI #40559928664](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40559928664) succeeded; artifacts 0.
- Google Drive LIVE sync: **SYNC FAILED** — no Drive/Docs connector is exposed; no LIVE read/write or folder metadata verification is claimed.

## 2026-10-27 N50 documentation-head recheck

- **PASS — prior docs-updated head:** `f995a3066f1c37918cf8d0ddf567358a2a6a4a89`, [CI #40517821800](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40517821800), logs report `BUILD SUCCESSFUL in 1m 12s`.
- This result does not cover any commit appended after that SHA; the final docs-updated head requires its own check.
- No APK packaging/signing/upload; SAF provider/device tests remain NOT TESTED.


## 2026-10-27 N51 — cancellation-safe SAF cleanup diagnostics

- **Production + regression-test SHA:** `b3a5a45f90dd4da7d90a15f5da66912c0ec51692`.
- Base APK and bundle export cancellation handlers now surface a manual-cleanup warning when the fresh partial document's deletion could not be confirmed, while still rethrowing the original CancellationException.
- Added pure message mapping and three JVM regressions covering base cancellation, bundle cancellation, and ordinary failures with confirmed/unconfirmed cleanup.
- **PASS — source/JVM/Lint:** [CI #40521536855](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40521536855), `:app:testDebugUnitTest :app:lintDebug`, BUILD SUCCESSFUL in 1m 43s; no APK packaging step.
- **Docs-updated PR-head recheck:** pending at the time of this entry; do not treat the source/test result as a check of later documentation commits.
- **Artifacts: 0 expected/confirmed**; no APK packaging/signing/upload or dependency/permission changes.
- Xiaomi Pad 5 / HyperOS and actual SAF provider cancellation/deletion: **NOT TESTED**.
- Google Drive LIVE sync: **SYNC FAILED** — no Drive/Docs connector is exposed in this runtime; no LIVE read/write or folder metadata verification is claimed.
- PR #1 remains Draft/open; no merge, main write, root, Device Owner or auto-merge.
- Detailed plan: [N51 SAF cancellation cleanup diagnostics](N51_SAF_CANCELLATION_CLEANUP_DIAGNOSTICS_PLAN.md).


## 2026-10-27 N51 documentation-head recheck

- **PASS — docs-updated PR head:** `cdb50c9ccb000623196d37878a3d597dfb0e32ed`, [CI #40522694769](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40522694769), JVM tests + Android Lint, BUILD SUCCESSFUL in 1m 54s.
- Checkout log confirms the PR merge ref contained this head; the workflow ran `:app:testDebugUnitTest :app:lintDebug` and did not package an APK.
- **Artifacts: 0**. Xiaomi Pad 5 / HyperOS and real SAF provider behavior remain **NOT TESTED**.


## 2026-10-27 N52 — temporary APK cache cleanup

- **PASS — source/test SHA:** d2f756368d8bf25b813396d70574382090320c80; [CI #40526102343](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40526102343), JVM tests + Android Lint, BUILD SUCCESSFUL in 2m 22s.
- Production cleanup now checks File.delete() and confirms absence; successful inspection fails closed if the temp APK remains, while parser/cancellation failures remain primary with a typed suppressed cleanup diagnostic.
- Three regressions cover false delete after success, preserved original exception identity, and thrown cleanup cause. **Artifacts: 0**; no APK packaging/signing/upload.
- Xiaomi Pad 5 / HyperOS: **NOT TESTED**. Google Drive LIVE sync: **SYNC FAILED** because no Drive/Docs connector is exposed; no sync is claimed.


## 2026-10-27 N53 — strict UTF-8 for APK-bundle metadata (verification pending)

- Bundle manifest and central-directory entry names now use a UTF-8 decoder configured to REPORT malformed/unmappable input rather than silently replacing invalid bytes. Canonical ASCII manifest and entry names are unchanged.
- Added integration coverage for a malformed UTF-8 manifest and a malformed ZIP-name byte sequence; existing archive, digest, CRC, central-directory, and size-limit tests remain in the suite.
- **Source/test SHA:** `55420949d98f3b523ccb4163b9e89451e25c93f5` — JVM tests + Android Lint [CI #40536857801](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40536857801) **PASS** (`:app:testDebugUnitTest :app:lintDebug --stacktrace`).
- The malformed central-directory name test corrupts only the central name bytes and confirms strict UTF-8 rejection; valid bundle round-trip tests remain in the same run.
- Artifacts: **0**. Documentation-updated PR-head recheck: **PENDING**.
- Xiaomi Pad 5 / HyperOS and real SAF provider behavior: **NOT TESTED**. Artifacts: **0 expected**; no APK packaging.
- Google Drive LIVE sync: **SYNC FAILED** — no Drive/Docs connector is exposed; no LIVE read/write or folder metadata verification is claimed.


## 2026-10-27 N54 — ZIP local/central extra-field parity

- Production behavior: the streaming verifier snapshots each local ZIP entry's extra-field bytes and compares them byte-for-byte with the bounded raw central-directory record before accepting the bundle. A mismatched extra-field length or payload is rejected; no entries are extracted and no extra fields are interpreted.
- Exact source/test SHA: `ba790539abdd60319e7ab8f4d70bf8ca54571679`.
- **PASS — JVM tests + Android Lint:** [CI #40545628852](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40545628852), `:app:testDebugUnitTest :app:lintDebug --stacktrace`.
- Regression: `rejectsCentralDirectoryExtraFieldMismatchWithLocalEntry` mutates the central extra-field payload while leaving the local header and APK digests unchanged; the verifier rejects the archive. Existing base/split round-trip and ZIP integrity tests pass.
- **NOT TESTED:** Xiaomi Pad 5 / HyperOS, real SAF provider behavior, APK signature authenticity or malware detection. Artifacts: 0 expected; no APK packaging/signing/upload.
- Google Drive LIVE handoff/index: **SYNC FAILED** — no Drive/Docs connector is exposed in this runtime; no sync claimed.


## 2026-10-27 N55 — raw ZIP local/central header parity

- Production change: the streaming verifier now captures raw local-header flags, compression method, strict UTF-8 filename bytes, extra fields and logical offset, then compares them with the central-directory record before accepting a bundle.
- Regression `rejectsLocalAndCentralGeneralPurposeFlagMismatch` toggles only the local UTF-8 flag while keeping central metadata and APK payload bytes intact; acceptance is prohibited unless both header views agree.
- Source/test SHA: `7cdd8f925b4592b516eace5b4fd8eca2cbe157f4`.
- **PASS — exact source/JVM/Lint:** [CI #40556511097](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40556511097), `:app:testDebugUnitTest :app:lintDebug --stacktrace`; source-only workflow, no APK packaging.
- Docs-updated PR-head recheck: pending after this evidence update.
- Source basis: OpenJDK `ZipInputStream` reads local headers without central-directory metadata; ZIP APPNOTE defines separate local and central records. No third-party code or dependency copied.
- Xiaomi Pad 5 / HyperOS and real SAF provider: **NOT TESTED**. APK artifacts: **0 expected**.
- Google Drive LIVE sync: **SYNC FAILED** — no Drive/Docs connector is exposed in this runtime.

## 2026-10-27 N56 — shared ZIP entry-type safety

- Production/source + regression-test SHA: `263c3a13303c6608046c54cba6d03085e5d62efa`.
- Reused the existing N42 host-aware Unix file-type rule for the streaming exported-bundle verifier; it now parses central-directory host-system/external-attribute fields and rejects symlink/special types and path/type mismatches before success.
- Added policy-level JVM tests plus crafted central-directory integration fixtures for accepted regular-file metadata and rejected symlink, FIFO and directory-type-on-`base.apk` cases.
- **PASS:** [source/JVM/Lint CI #40559296718](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40559296718), `:app:testDebugUnitTest :app:lintDebug --stacktrace`; artifacts 0.
- Docs-updated PR-head recheck pending. Device/SAF, signer authenticity and malware detection remain NOT TESTED. No APK packaging, permission/dependency changes, merge or main write.
- Google Drive LIVE sync: **SYNC FAILED** — connector unavailable; no LIVE write/read claimed.



## 2026-10-27 N57 — canonical ZIP extra-field allowlist

- Production change: the streaming bundle verifier now accepts only the exporter's canonical UT extended-timestamp field (`0x5455`, length 5, mtime flag only, epoch 0) in both local and central metadata; matching but unsupported extra-field IDs are rejected.
- Regression `rejectsUnknownExtraFieldEvenWhenLocalAndCentralMatch` patches the first local and central extra-field IDs together, preserving their equality, and verifies rejection. Existing canonical base/split round-trip and ZIP integrity tests remain in the same suite.
- Source + regression-test commit: `e3dc1e9a1a7ab68a6be0b224d594fede2031acef`.
- Source/JVM/Lint [CI #40563943994](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40563943994) was in progress when this entry was written; final docs-head results will be recorded in the PR conversation after recheck.
- Device/SAF, signer authenticity and malware detection: **NOT TESTED**. APK artifacts: **0**. No dependency/permission changes, root, Device Owner, merge or main write.
- Google Drive LIVE sync: **SYNC FAILED** — no Drive/Docs connector is exposed in this runtime; no LIVE read/write or folder metadata verification is claimed.


## 2026-10-27 N57 follow-up — canonical ZIP test fixture correction

- **FAIL, superseded:** [CI #40563943994](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40563943994) showed one targeted UTF-8 test failed because the shared generic archive fixture used a non-canonical timestamp extra field and was rejected before its intended UTF-8 assertion.
- Fixed the fixture helper to use `ZipEntry.time = 0L`, exactly matching the production exporter. The test suite now reaches the intended malformed-manifest behavior while preserving the stricter production policy.
- **PASS — corrected production + test SHA:** `dd4fc2ce63dfe6a0139c2ea2726694312e2cec30`, [JVM tests + Android Lint #40564471753](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40564471753); all steps succeeded and artifacts **0**.
- OpenJDK 21 local probe confirmed the expected 9-byte UT extra field; Android libcore source reviewed. Device/SAF and signer authenticity remain **NOT TESTED**.
- Final docs-updated PR-head results will be posted in the PR conversation after CI completes.
- Google Drive LIVE sync: **SYNC FAILED** — no Drive/Docs connector is exposed in this runtime; no LIVE read/write or folder metadata verification is claimed.


## 2026-10-27 N58 — DOS ZIP entry-type consistency

- Production/source + tests: shared `ZipEntryTypeSafety` now rejects a DOS/FAT central-directory record when the DOS directory bit (`0x10`) is set on a path that is not directory-shaped. Slash-only directory entries remain compatible when writers omit the bit; Unix mode checks are unchanged.
- Regression coverage: unit tests for DOS attribute/path combinations plus a crafted canonical bundle whose central record marks `base.apk` as a directory.
- Source review: PKWARE ZIP APPNOTE's host-dependent external-attribute mapping and Apache Commons Compress ZIP docs/source (Apache-2.0) were reviewed; no code copied, no dependency added.
- **FAIL, superseded:** [CI #40572924157](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40572924157) exposed five compatibility regressions because the first version required a DOS directory bit for every slash path. Fixed by checking only contradictory set bits.
- **PASS — corrected source/test SHA:** `a0afdc0b3d8ef0bd0b251c2fb5d2a51389d8fbcd`; JVM tests + Android Lint [CI #40573621884](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40573621884).
- **PASS — prior docs-updated PR head:** `318c35be2e9aab177be508ef154a16e8714c0443` ([JVM/Lint #40574025887](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40574025887)). This ledger append creates a new head; final recheck is tracked in PR #1 conversation. APK artifacts: **0**.
- Xiaomi Pad 5 / HyperOS, real SAF provider behavior, APK authenticity and malware detection: **NOT TESTED**.
- No APK packaging, merge, main write, root, Device Owner or auto-merge. Google Drive LIVE sync remains **SYNC FAILED** because no Drive/Docs connector is exposed; no LIVE document read/write or folder metadata verification is claimed.


## 2026-10-27 — N59 canonical ZIP version-header parity

- **Production change:** ZipBundleStructure now captures and validates local version-needed, central version-needed, and central creator software version for the canonical ZIP32 export format (version 2.0 / 20), in addition to existing local-central metadata parity.
- **Crafted integration tests:** InstalledApkBundleCanonicalLayoutTest mutates the local version-needed field, central version-needed field, and central creator version independently; each malformed archive must be rejected. Canonical base/split export cases remain covered.
- Production commit: `a269c43f6a41e3b65b9fd434ac638e3e2624fe21`; test commit: `668caf15514cc25fb2f735226f32e75a203a0f3d`.
- **PASS — JVM tests + Android Lint on PR head `9ffeaddd284d44addfaf1fe05e753575196799c9`:** [CI #40581685497](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40581685497).
- ZIP specification/source review: PKWARE APPNOTE field definitions and OpenJDK ZipOutputStream contract; no source copied or dependency added. Local JDK 21 probe confirmed the exporter's version fields are 20 / 0x0014 / 20.
- Documentation updates create a new PR head; final-head CI recheck is tracked in the PR conversation.
- Xiaomi Pad 5 / HyperOS SAF behavior, APK signer authenticity and malware detection: **NOT TESTED**. APK artifacts: **0**. No permission/dependency/root/Device Owner change, merge, main write, or auto-merge.
- Google Drive LIVE sync: **SYNC FAILED** — no Google Drive/Docs connector is available in this runtime; no Drive read/write or folder metadata verification is claimed.
