# HG-E063 — Stop All must prevent late part-worker dispatch

Date: 2026-10-28  
PR: [#3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3)  
Branch: `automation/idm-ftp-cancel-and-category-flush`

## Previous milestone and observed failure

The exact-head Windows regression run [40606764242](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40606764242) compiled the project and ran 203 downloader-core desktop tests; **202 passed and one failed**:

`ActualRestoredRetryJobTest.stopAllWaitsForActualHttpResumingThenLeavesNoLateWorker`

The downloaded JUnit XML in artifact [12781531198](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40606764242/artifacts/12781531198) records `expected:<1> but was:<2>` for the HTTP request count after Stop All had drained and the test waited for late requests. This is a real lifecycle regression, not a packaging failure. The EXE build steps were correctly skipped because the required JUnit gate failed.

## Root cause

`PartDownloader.start()` used the loop predicate `coroutineContext.isActive || !stop`. With OR, a worker coroutine that is still active can enter `download()` even when Stop has already set `stop = true`; this is possible when Stop races with the newly dispatched worker. That allows a late HTTP request after Stop All.

## Production + regression fix

- Replace the OR predicate with `shouldContinuePartWorker(coroutineContext.isActive, stop)`, defined as `coroutineActive && !stopRequested`.
- Add a Kotlin truth-table regression for active/stopped states.
- Strengthen the existing real HTTP lifecycle regression with an explicit late-request assertion message.
- Extend the source contract to forbid the old OR predicate and require the runtime regression.
- Preserve cancellation, connection-close, worker join, retry budgets and all performance thresholds.

## Pre-build acceptance question

After the exact PR-head fix, can the full downloader-core JUnit suite pass with **zero failures/errors/skips**, and can the real Stop All/Resume race finish with no request after Stop, zero active host connections and no late worker?

Expected result: the previously failing test passes; the full targeted suite reports zero failures; the exact-head Windows benchmark rebuilds the candidate containing this fix and validates 63 SHA-256-correct outputs per variant, nine resource sets per variant, live 1/4/16 concurrency and unchanged 15% throughput/memory limits.

Source tests can validate the loop predicate and test presence, but cannot prove coroutine dispatch timing, live HTTP sockets, the installed process or OS resource measurements. A Windows JUnit build and exact-head installed benchmark are therefore justified. No paid runner/API, main write, merge, release or Torrent.

## Status at checkpoint creation

- Previous exact-head full test suite: **FAIL — 1/203**, documented above.
- Source patch and regression changes: committed together in this checkpoint; fresh exact-head CI is the acceptance authority.
- Previous benchmark [40606763940](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40606763940) was already building the pre-fix candidate and cannot prove this fix.
- Current memory profile [40606764162](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40606764162) passed as a diagnostic on the pre-fix source, but it does not close the late-worker regression or controlled comparison.
- Final independent release pass, HTTPS/signed-link refresh, native-size icon audit and final user-facing `HyperGet.exe` artifact/SHA remain OPEN.
