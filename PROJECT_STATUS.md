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


## 2026-10-27 — N36 inventory metadata resilience (in progress)

- Production change prepared on PR #1 branch: per-package metadata is best-effort; valid inventory identities are preserved with explicit label/version/update-time fallbacks.
- New JVM mapper tests: exception/blank fallback, field preservation, invalid identity isolation, and sorting.
- Exact source/test SHA and source-only CI result: pending until GitHub Actions confirms the pushed commit.
- Xiaomi Pad 5/HyperOS, Shizuku, SAF provider behavior, and UI interaction: NOT TESTED.
- No APK packaging/artifacts, merge, main write, root, or Device Owner.
