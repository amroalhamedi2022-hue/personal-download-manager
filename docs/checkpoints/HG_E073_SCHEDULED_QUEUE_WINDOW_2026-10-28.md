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
