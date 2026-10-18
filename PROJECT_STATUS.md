# App Commander — Current Status

Version: 0.1.0 debug

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
- GitHub Actions build workflow and Gradle wrapper.

## Validation completed here

- GitHub Actions successfully compiled the project with Android SDK 36 and JDK 17.
- The generated debug APK was uploaded as a workflow artifact and downloaded for verification.
- APK archive integrity passed with no errors (30,361,757 bytes).
- APK SHA-256: `1af1783e183885c751c41ab0804d06233d8c22bb2192d647c4b0e40bce4c6205`.
- XML parsing passed for the manifest and every resource file.
- Source structure, bracket balance, Git status, and whitespace checks passed.
- No Internet permission exists in the manifest.
- Shell inputs are restricted to Android package, component, and permission name characters.

## Device validation still required

Install and exercise the debug build on physical devices. Start with Android 13 / HyperOS on Xiaomi Pad 5, then verify permission flows and Shizuku commands on the phone. Device Owner mode should only be tested on a suitable test device/profile because provisioning has system-level consequences.
