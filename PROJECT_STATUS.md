# App Commander — Current Status

Version: 0.1.1 safe debug

Date: 2026-10-18

## Completed

- Native Android project in Kotlin and Jetpack Compose.
- Liquid animated UI with four palettes, Arabic RTL, phone/tablet layouts, and system light/dark colors.
- Installed-app inventory, search, filters, usage access, app details, and component browser.
- Activity launching and standard Android app/settings/uninstall actions.
- Xiaomi/HyperOS Auto-start settings shortcut.
- Offline APK Inspector with metadata, permissions, components, SHA-256, signer fingerprints, and installer handoff.
- Shizuku permission flow and official UserService command engine.
- Shizuku commands for Force stop, freeze/unfreeze, background AppOps, runtime permissions, and component enable/disable.
- Device Owner receiver and suspend/hide controller.
- Critical-system-package guard without restricting controls for ordinary user or system apps.
- Typed Shizuku operation gateway; arbitrary shell command strings are not accepted.
- Recovery operations remain available for protected packages.
- GitHub Actions build workflow and Gradle wrapper.

## Validation completed here

- GitHub Actions successfully compiled the project with Android SDK 36 and JDK 17.
- The generated debug APK was uploaded as a workflow artifact and downloaded for verification.
- APK archive integrity passed with no errors.
- v0.1.1 safe APK SHA-256: `daa319bf1886a018923f98f08aa37648e5af71ac352e44fe3f30182b92164716`.
- XML parsing passed for the manifest and every resource file.
- Source structure, bracket balance, Git status, and whitespace checks passed.
- No Internet permission exists in the manifest.
- Shell inputs are restricted to Android package, component, and permission name characters.

## Device validation still required

Install and exercise the debug build on physical devices. Start with Android 13 / HyperOS on Xiaomi Pad 5, then verify permission flows and Shizuku commands on the phone. Device Owner mode should only be tested on a suitable test device/profile because provisioning has system-level consequences.


## 2026-10-27 — N36 inventory metadata resilience (source/test PASS)

- Production source commit: `075c1635ceb701ba771f3d330b44b197eb8d1d9c`.
- Per-package label/version/update-time failures now preserve valid inventory identities with explicit fallbacks; top-level inventory-query failures still use the N35 retry state.
- Five JVM tests cover exception/blank fallbacks, field preservation, invalid identity isolation, and sorting.
- **PASS:** [source-only CI #40429462073](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40429462073), `:app:testDebugUnitTest :app:lintDebug`, BUILD SUCCESSFUL in 1m 42s; 0 artifacts.
- A docs-only head recheck is pending and will be reported in the PR thread without further source edits.
- Xiaomi Pad 5/HyperOS, Shizuku, SAF provider behavior, and UI interaction: NOT TESTED.
- No APK packaging/artifacts, merge, main write, root, or Device Owner.


## 2026-10-27 — N37 optional UsageStats failure isolation (source/test PASS)

- Production source/test head: a450c404ac7f7570d23bcd7b93b5a073803aabcb.
- Usage access/AppOps and UsageStats provider failures now degrade only optional usage metadata; installed-app inventory remains independently loadable.
- Six JVM regressions cover denied/throwing access, throwing/null provider results, successful 24-hour snapshot, and invalid clock boundaries.
- **PASS:** [source-only CI #40434828097](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40434828097), :app:testDebugUnitTest + :app:lintDebug, BUILD SUCCESSFUL in 1m 41s; 0 artifacts.
- Xiaomi Pad 5/HyperOS, SAF, Shizuku and UI interaction: NOT TESTED. No APK packaging, merge, main write, root, or Device Owner.
- Google Drive LIVE handoff/index: SYNC FAILED — connector unavailable; no Drive sync claimed.


## 2026-10-27 — N38 verified-install cache clock safety

- Added a fail-closed guard for negative/near-epoch wall clocks and retention intervals longer than observed time; prevents cutoff subtraction overflow from deleting valid verified APK snapshots.
- Removed redundant cache cleanup after creating the installer FileProvider URI; cleanup remains before snapshot allocation.
- Added three clock-boundary JVM regressions. **PASS:** exact PR-head source/JUnit/lint run [#40441604406](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40441604406), BUILD SUCCESSFUL in 1m 41s; 36 actionable tasks; zero artifacts.
- Device SAF/installer behavior: NOT TESTED. No APK packaging or artifacts.

## 2026-10-27 — N39 fail-closed snapshot expiry

- Source/test head: `676454c507e057a42adc8460265a0ea85debb5c8`.
- Expiry cleanup now treats denied directory listing, unknown/negative file metadata, and inaccessible entries as best-effort cleanup failures; verified install preparation is not aborted by optional cache cleanup.
- **PASS:** [source-only CI #40446325452](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40446325452), `:app:testDebugUnitTest :app:lintDebug`; zero artifacts.
- Xiaomi Pad 5 / HyperOS installer handoff: **NOT TESTED**. Google Drive LIVE sync: **SYNC FAILED** — connector unavailable.

## 2026-10-27 — N40 APK signer lineage inspection

- Source/test SHA: `feb1ee582dd9c7a59cb2184a9b1fb12c6785d377`.
- APK Inspector now separates current signer fingerprints from platform-reported single-signer certificate-rotation history; multiple current signers are sorted for stable display and never mislabeled as a rotation chain.
- Added pure JVM tests for no history, oldest-to-current rotation order, multiple signers, unavailable metadata, missing history bytes, and duplicate fingerprint normalization.
- **PASS:** [source-only CI #40447641304](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40447641304), `:app:testDebugUnitTest :app:lintDebug`, BUILD SUCCESSFUL in 1m 43s; **0 artifacts**.
- Signing fingerprints/history are metadata only—not malware detection or an independent authenticity guarantee. Xiaomi Pad 5 / HyperOS and real APK archive behavior: **NOT TESTED**.
- No APK packaging, signing, upload, release, merge, main write, root, Device Owner, or auto-merge.
- Google Drive LIVE sync: **SYNC FAILED** — no Drive/Docs connector is exposed; no LIVE document read/write or folder metadata verification is claimed.

## 2026-10-27 — N46 optional UsageStats value sanitization

- **Production/source + test SHA:** `e1718717ed390700af2be391cfd6d590fa134149`.
- Added a pure JVM sanitizer for successful optional UsageStats snapshots. Blank package keys are dropped; negative durations/timestamps become zero; foreground duration is capped to the smaller of the requested 24-hour window and elapsed wall-clock time; future last-used timestamps are capped at now.
- `AppRepository.usageByPackage()` now converts Android `UsageStats` values to the tested model and sanitizes them before rows are created. Existing denied-access/provider-failure fallback remains unchanged.
- Three new JVM tests cover negative/oversized values, blank keys, near-epoch bounds, invalid clocks, and preservation of valid values.
- **PASS:** [source-only CI #40478165225](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40478165225), `:app:testDebugUnitTest :app:lintDebug --stacktrace`; job completed successfully. **Artifacts: 0**; no APK packaging/signing/upload.
- Xiaomi Pad 5 / HyperOS real UsageStats and UI: **NOT TESTED**.
- No APK packaging/signing/upload, merge, main write, root, Device Owner, or auto-merge.
- Google Drive LIVE sync: **SYNC FAILED** — no Google Drive/Docs connector is exposed in this runtime; no LIVE document read/write or folder metadata verification is claimed.


## 2026-10-27 — N47: fail-fast Shizuku request preflight
- Production source changed: YES. Added a pure preflight gate before Shizuku permission checks and UserService binding; malformed package names are rejected before destructive app-side safety checks can throw.
- Regression tests prove malformed package/operation/permission/component requests do not invoke dispatch, valid requests dispatch once, and controller ordering places validation before permission/Binder access.
- **PASS — exact current source/test SHA:** a3f545ba00bc9970e96bc609352f44257ee9777b; source/JVM/Lint [CI #40484909419](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40484909419) succeeded.
- **Artifacts: 0 confirmed.** No APK packaging, signing or upload.
- **NOT TESTED:** Xiaomi Pad 5/HyperOS Shizuku/ADB flow, SAF providers, and physical-device commands. No root or Device Owner provisioning.
- PR #1 remains Draft/open; no merge, main write or auto-merge. Google Drive LIVE sync: **SYNC FAILED** because no Drive/Docs connector is available; no LIVE read/write or folder metadata verification is claimed.

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


## 2026-10-27 — N49 SAF bundle source snapshot

- **Source/test SHA:** `c9b34e22b84f87adec2cd79d474ecea30a14e150`; source/JVM/lint CI [#40501376446](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40501376446) **PASS**.
- Both `write` and `writeAndVerify` now use one stable source descriptor snapshot across preflight and archive output. Two deterministic tests cover list mutation at provider-open and output-write callbacks.
- **Artifacts: 0 confirmed**; no APK was built or uploaded.
- SAF provider behavior on Xiaomi Pad 5/HyperOS and Shizuku device operations remain **NOT TESTED**.
- Google Drive LIVE handoff/index: **SYNC FAILED** because the Drive/Docs connector is unavailable in this runtime; no sync is claimed.
- PR #1 remains Draft/open, unmerged; no main write, root, Device Owner or auto-merge.


## 2026-10-27 — N51 SAF cancellation cleanup reporting

- Implemented in the PR branch: base-APK and bundle export paths now expose a manual-removal warning when cancellation carries an unconfirmed partial-document cleanup diagnostic, while rethrowing the same cancellation.
- Source/test SHA: `b3a5a45f90dd4da7d90a15f5da66912c0ec51692`; source/JVM/Lint CI [#40521536855](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40521536855) **PASS**.
- Three new JVM tests cover cancellation cleanup, provider deletion denial, and ordinary error-message mapping. No APK packaging or artifacts.
- Current documentation-head recheck is pending; device/HyperOS and real SAF provider behavior remain **NOT TESTED**.
- Google Drive LIVE sync: **SYNC FAILED** — connector unavailable in this runtime; no Drive write is claimed.


### N51 documentation-head recheck — PASS

- PR head `cdb50c9ccb000623196d37878a3d597dfb0e32ed): [CI #40522694769](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40522694769), JVM tests + Android Lint passed in 1m 54s.
- Checkout log confirms this PR head was tested; artifacts: 0. No APK packaging/signing/upload.
- Physical-device and real SAF provider tests remain NOT TESTED.


## 2026-10-27 — N52 temporary APK cache cleanup — VERIFIED

- **Production/test SHA:** d2f756368d8bf25b813396d70574382090320c80.
- **PASS:** source/JVM/Lint [CI #40526102343](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40526102343); :app:testDebugUnitTest :app:lintDebug --stacktrace; BUILD SUCCESSFUL in 2m 22s.
- ApkCacheCopy.withTemporaryApk now verifies temp-file absence; if inspection succeeded but cleanup failed it throws a typed diagnostic, while an existing parser/cancellation failure remains primary with cleanup detail suppressed.
- Three new deterministic JVM regressions passed. **Artifacts: 0**, no APK packaging/signing/upload.
- Xiaomi Pad 5/HyperOS remains **NOT TESTED**. Google Drive LIVE sync **SYNC FAILED** because no Drive/Docs connector is exposed.


## 2026-10-27 — N53 strict UTF-8 APK-bundle metadata (verification pending)

- InstalledApkBundleVerifier and ZipBundleStructure now reject malformed/unmappable UTF-8 in the canonical bundle manifest and ZIP central-directory entry names instead of accepting decoder replacement characters.
- Added deterministic JVM regressions for malformed manifest bytes and malformed ZIP-name bytes.
- **Exact source/test SHA:** `55420949d98f3b523ccb4163b9e89451e25c93f5`.
- **PASS — source/JVM/Lint CI:** [#40536857801](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40536857801), `:app:testDebugUnitTest :app:lintDebug --stacktrace`; **0 artifacts**.
- Documentation-updated PR-head recheck: PENDING. No APK packaging/signing/upload.
- Xiaomi Pad 5 / HyperOS and real SAF provider behavior: NOT TESTED. No merge, main write, root, or Device Owner.
- Google Drive LIVE sync: SYNC FAILED — Drive/Docs connector unavailable; no LIVE read/write is claimed.


## 2026-10-27 — N55 ZIP local/central header consistency (source/JVM/Lint PASS; docs-head recheck pending)

- APK bundle verification now records raw local header flags/method/name bytes and checks them against central-directory metadata, closing a local-vs-central general-purpose flag mismatch.
- Source/test SHA: `7cdd8f925b4592b516eace5b4fd8eca2cbe157f4`.
- **PASS:** [CI #40556511097](https://github.com/amroalhamedi2022-hue/app-commander/actions/runs/40556511097), `:app:testDebugUnitTest :app:lintDebug --stacktrace`; no APK packaging or artifacts.
- Added regression: `rejectsLocalAndCentralGeneralPurposeFlagMismatch`.
- Exact source/JVM/Lint checks passed. Do not treat the final documentation-updated PR head as verified until its own CI run succeeds.
- Xiaomi Pad 5 / HyperOS, SAF providers, signature authenticity and malware detection: **NOT TESTED**. No APK artifact expected.
- Google Drive LIVE sync: **SYNC FAILED** — no Drive/Docs connector is exposed in this runtime.
