# N19 — Bounded Shizuku process pipe cleanup

Date: 2026-10-26. PR #1 remains Draft.

## Engineering question
PrivilegedProcessRunner uses daemon threads to drain stdout/stderr. A process can exit or time out while a pipe reader remains blocked (for example, a broken stream or inherited pipe descriptor); collect() can return after its bounded join while leaving a daemon reader alive. Can cleanup close stalled streams, release readers, and preserve fail-closed status without leaking privileged-process details?

## Acceptance criteria
1. Preserve the typed allowlisted argv boundary, timeout range, output cap, exit-code semantics and structured error messages.
2. On timeout, forcibly terminate the process and close stalled streams after the bounded wait; if a reader remains alive after its join, mark output as incomplete, close its stream, and never report success.
3. Reader I/O exceptions remain sanitized; no exception message from a privileged pipe is surfaced.
4. Regression tests cover a blocking stream released by close, timeout termination, successful output, bounded output, zero-length reads, and read failure.
5. gradlew :app:testDebugUnitTest :app:lintDebug --stacktrace passes on the exact source+test SHA. No APK packaging, artifact upload, merge or main write.

## Why source/JVM checks are sufficient
The change is about Java Process/InputStream lifecycle, bounded threads and deterministic result classification. JVM tests exercise the actual runner with controlled Process/stream doubles. APK packaging cannot prove that a blocking reader is released and is not needed for this acceptance question; real Shizuku binder/device behavior remains a separate gate.

## Licensed reference review
Reviewed the official RikkaApps/Shizuku repository metadata and LICENSE (Apache-2.0). No code was copied; the existing typed-command boundary and Java Process API are retained.

## Baseline
- PR #1 current head before N19: 2949b762a27a60cb14a8da4e1927ebc5146a9dba (N18 docs head); N18 source/test SHA f08acb0e3d9e35fbae8a1c89b2b85c2525fef960.
- CI run 40242400761 PASS for :app:testDebugUnitTest + :app:lintDebug; follow-up docs/head CI run 40242661565 PASS.
- Xiaomi Pad 5/HyperOS, real Shizuku binder, SAF and visual UI remain NOT TESTED.
- Google Drive LIVE sync unavailable in this runtime; do not claim Drive sync without read-back verification.

## Result
Pending source changes and exact-SHA CI verification.

## Next
Continue from latest PR #1 head; no merge, main write, APK or release.
