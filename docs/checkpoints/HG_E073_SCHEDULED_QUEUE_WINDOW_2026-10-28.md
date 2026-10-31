# HG-E073 — scheduled queue start/stop behavioral acceptance (2026-10-28)

## Milestone
Prove the existing HyperGet queue schedule is behavioral, not just persisted UI settings: with one queued task and both schedule switches enabled, the queue must emit the scheduled-start event at the next start time, start the task exactly once, emit the scheduled-stop event at the next end time, stop the task exactly once, and finish inactive.

## Pre-test declaration
**Question:** does `DownloadQueue` execute its configured start and end timers against the current local wall clock and transition the actual queue/task lifecycle in the expected order?

**Expected:** Windows Gradle/JUnit compiles the new `ScheduledQueueWindowTest`; within a bounded 18-second window, start and stop events each occur exactly once, the task's start/stop callbacks are each recorded exactly once, the start event observes an inactive queue, the stop event observes an active queue, and the queue is inactive afterward.

**Why source tests are insufficient:** static assertions cannot prove coroutine delay scheduling, local-time conversion, queue admission, event ordering, or stop callbacks under an actual runtime clock. This milestone requires the targeted Windows JUnit run, but does not require an installer because no production code or Windows packaging identity is changed.

## Change
- Added a bounded wall-clock integration test using the existing `QueueManager`, file-backed queue persistence, and a recording `DownloadManagerMinimalControl`.
- Wired the test into the existing Windows queue-lifecycle workflow and its JUnit artifact/count gate.
- No production code, dependencies, browser repo, paid service, Torrent, main write, or auto-merge.

## Status before test
OPEN / NOT YET TESTED. The next exact-head Windows run must pass the new test and existing queue/part lifecycle suites with zero failures/errors/skips. If it fails, use the exact event order and callback evidence to repair the scheduler rather than weakening the assertions.

## Exact-head result — PASS (2026-10-28)

**Tested source SHA:** `9f7c489055330119f818a1a146b26d7bf12009d6`.

The first Windows attempt on parent `9d762b4` failed to compile because the new test mixed the datetime Clock API and a LocalDateTime overload that does not accept a time-based unit. This was a test-source defect, not a scheduler result. The correction is in commit [`9f7c489`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/9f7c489055330119f818a1a146b26d7bf12009d6): use `kotlin.time.Clock`, add `Duration` to the `Instant`, then convert to local time. Assertions were preserved.

**PASS:** Windows queue/part-I/O workflow [40644326650](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40644326650). Gradle completed successfully; the new `ScheduledQueueWindowTest` reported **1 test passed**, and the workflow's JUnit XML gate confirmed zero failures/errors/skips for the targeted suites. The test observed exactly one scheduled-start event and one scheduled-stop event, one `startJob(73)`, one `stopJob(73)`, inactive state before start, active state before stop, and inactive state at completion.

- JUnit artifact: [12796742980](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40644326650/artifacts/12796742980)
- Artifact ZIP SHA-256: `ee81e7b7913333e9b23c03ec55a20a7c9447092d7b185d8c53b846973bec150e`
- Source safety: [40644326843](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40644326843) PASS.
- Relative-time tests: [40644326736](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40644326736) PASS.
- FTP Kotlin/WinCred unit suite: [40644326681](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40644326681) PASS.

**Scope:** this closes only behavioral scheduled start/stop acceptance for the tested queue path. It does not close an independent external HTTPS signed-link test, the remaining in-progress exact-head Windows gates, independent final recheck, or final standalone `HyperGet.exe` delivery. No production scheduler logic or acceptance threshold was changed; no merge, main write, release, paid service, or Torrent.

