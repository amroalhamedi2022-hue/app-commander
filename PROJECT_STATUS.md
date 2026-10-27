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
