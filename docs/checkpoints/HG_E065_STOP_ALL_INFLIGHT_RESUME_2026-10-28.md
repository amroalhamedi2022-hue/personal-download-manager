# HG-E065 — Stop All must cancel in-flight Resume before part admission

Date: 2026-10-28  
PR: [#3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3)  
Branch: `automation/idm-ftp-cancel-and-category-flush`  
Prior lifecycle predicate patch: [9e090424d8320b447cd6970db765c58cb5ec3d8e](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/9e090424d8320b447cd6970db765c58cb5ec3d8e)

## Recheck result: first repair was incomplete

The exact-head Windows JUnit run [40609757642](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40609757642) ran 204 downloader-core tests: **203 passed, one failed**. The same real regression, `stopAllWaitsForActualHttpResumingThenLeavesNoLateWorker`, still observed a late request (expected 1, actual 2); JUnit/build report archive [12782437181](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40609757642/artifacts/12782437181). The worker-loop predicate fix was necessary but not sufficient. The failure is not hidden or counted as a pass.

## Refined root cause

`DownloadManager.stopAll()` raises `stopAllInProgress` and waits for a Resume command already in flight. The HTTP metadata request can return during that wait; without a Stop All gate, the resuming job continues to create and dispatch a part worker before Stop All reaches its final pause pass. That creates another HTTP request even though Stop All has already been requested.

## Production + regression repair

- Make `stopAllInProgress` volatile for visibility across the manager/job coroutine threads.
- Expose a read-only state check and `throwIfStopAllRequested()`.
- Both HTTP and HLS resume paths now check the gate after metadata validation and again immediately before part-worker admission.
- If Stop All owns the cancellation, the resume catch path no longer races it by launching a second asynchronous pause.
- Add a source contract for both HTTP/HLS gates. Keep the existing real HTTP test that asserts the request count remains stable after Stop All and active host connections return to zero.
- Keep the earlier PartDownloader predicate fix; it closes the separate stop-before-worker-dispatch race.

## Pre-build acceptance question

Can the fixed exact PR head pass the complete downloader-core desktop JUnit suite with zero failures/errors/skips, including the real blocked-HTTP Resume → Stop All → release fixture sequence, and then pass installed HTTP Pause/Resume plus the exact-head 1/4/16 benchmark?

Expected: no new HTTP request after Stop All; zero active host connections; final job status Canceled; no late completion; full JUnit pass; then 63 SHA-256-correct outputs per variant, nine resource sets per variant, true 1/4/16 concurrency and unchanged 15% throughput/memory gates.

Source tests can prove the gate is wired into both resume paths, but only the Windows JUnit and installed E2E can prove cancellation ordering and live socket behavior. The installed benchmark additionally requires a real NSIS tree; its separate stale-GUI-name harness failure is addressed by HG-E064 (baseline `DownloadManager.exe`, candidate `HyperGet.exe`).

## Current status

- Previous exact-head JUnit: **FAIL — 1/204**; not accepted.
- New Stop All gate and source contract: committed in this checkpoint; exact-head Windows reruns are authoritative.
- The pending benchmark from SHA `9e090424` may still run with the earlier harness and is not evidence for the combined fix.
- Fresh benchmark on the combined source + variant-specific harness is still required.
- HTTPS/signed-link refresh, full native-size icon audit, independent final release pass and final user-facing `HyperGet.exe` artifact/SHA remain OPEN.
- No paid runner/API, main write, merge, release or Torrent.
