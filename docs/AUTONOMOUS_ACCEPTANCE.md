# Autonomous acceptance ledger — App Commander Android

AUTOMATION_COMPLETE: NOT READY
Updated: 2026-10-23

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
