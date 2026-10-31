# HG-E072 — HLS restart resume command and deterministic evidence (2026-10-28)

## Milestone and acceptance question
After a real installed HyperGet HLS transfer completes and persists segment 1, force-kill the GUI while segments 2–4 are active, relaunch the GUI, explicitly resume the persisted task, and require the playlist and incomplete segments to be requested again. Segment 1 must not be requested twice; segments 2–4 must each finish after restart; sockets must drain; final assembled bytes and SHA-256 must match the deterministic fixture.

## Exact-head result before this fix
- Tested source/harness SHA: `15b0c72ec3ba7f3b335f1bcc3d70e8b0419dab86`.
- Windows run [40637465788](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40637465788): guarded HLS Kotlin/JUnit job PASS (29+ tests, zero failure/skip), installer packaging PASS, installed process-kill/relaunch E2E FAIL.
- Test installer SHA-256: `5c20c8e1ca50888830224e86f87e4940de19b4d1954b6bef42a91ea6810bd8ce`.
- Evidence artifact [12794708540](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40637465788/artifacts/12794708540), archive SHA-256 `4d1aab124f9cbf68865e08ca59b1a5b63cb98937ac5879e4ea1a8c40f2734d85`.
- Fixture counters after failure: playlist requested only once; segment 1 completed exactly once; segments 2–4 each requested once and aborted once; zero active sockets. This is consistent with no post-relaunch Resume command being sent.
- Root cause identified in the acceptance harness: it inferred live worker activity from the persisted status label `Downloading` and skipped explicit Resume. A process-kill test must not treat a stale persisted status as proof that worker coroutines survived.

## Cohesive change
- Always invoke the existing CLI `download resume <taskId>` after the relaunched GUI exposes the persisted task, regardless of its stored status label.
- Persist post-relaunch status, resume exit/output, and fixture counters into the failure artifact.
- Add a bounded 30-second request gate so a missing playlist/segment restart fails with counters instead of waiting four minutes for an output file.
- Require each incomplete segment (2–4) to have at least two requests across the kill boundary; segment 1 must still be requested/completed exactly once.
- Extend the source contract to prevent reintroducing status-label-based Resume suppression.

## Pre-build test declaration
**Question:** does the installed GUI + CLI, after a forced process termination, explicitly resume the persisted HLS job and refetch the playlist/re-request unfinished segments while reusing the completed segment?

**Expected:** source contract and HLS JUnit gate pass; the targeted Windows installer builds; fixture shows playlist requests >= 2, segment 1 request/completion exactly 1, each segment 2–4 request count >= 2 and completion >= 1, no active sockets, exact final byte count and SHA-256.

**Why source tests are insufficient:** only an installed Windows run can exercise process termination, persisted DB/staged segment files, real HTTP sockets, GUI/CLI IPC and final file assembly across process restart. This build is purpose-bound to that acceptance question, not a release build.

## Scope and status
- No production downloader logic or dependencies changed in this patch; it corrects the installed acceptance sequence and strengthens regression diagnostics.
- No Torrent, paid API/runner, main write, auto-merge, or release.
- **Current status before rerun: OPEN / NOT YET VERIFIED.** The next exact-head Windows run must prove the change; source-contract success alone is not acceptance.
- Other release gates remain open: behavioral scheduled start/stop, external HTTPS signed-link acceptance, independent final Windows recheck, and final standalone user-facing `HyperGet.exe` with verified SHA-256/artifact.


## Exact-head rerun result — PASS (2026-10-28)

- Exact tested source/harness SHA: `89cfb72f0ffeaf5f58d7b609d18c63999a949d71`.
- Windows workflow [40641435306](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40641435306): SUCCESS. The guarded HLS/JUnit job passed 29 tests with 0 failures, 0 errors and 0 skips; Python source contracts and PowerShell parsing passed before packaging; the targeted installer built successfully in 4m39s; installed process-kill/relaunch E2E passed.
- Test installer SHA-256: `7318eb3bced7ddd0aff6299069d6936134a719e6879d3a0b071b44c68231001f`.
- Evidence artifact [12796480062](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40641435306/artifacts/12796480062), independently downloaded and hashed here: `e5c574b0587e65ae3f69ff3b8d7453f83a17dab3a0abf3055f4055fc9a6b0933`.
- Final fixture counters: playlist requests 2; segment 1 requests/completed 1/1; segments 2–4 requests/completed 2/1 each; active requests 0; total segment requests 7.
- Final output: 25,296,896 bytes; expected and actual SHA-256 both `ea79f691986481e8983f41728fdf61174c54b548c3afcc5dedd05f9d0d5b25bb`. The GUI was force-killed while 3 segment requests were active, sockets drained, and the saved task was explicitly resumed after relaunch.
- The first attempt at source guard on intermediate SHA `083728f` failed because of a malformed Python assertion. That test-only syntax error was corrected in `89cfb72`; the exact-head source-only safety workflow then passed [40641435300](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40641435300). The malformed intermediate run is not treated as product acceptance.

**HG-E071/HG-E072 HLS process-restart acceptance is now closed for this exact SHA.** This is a targeted test build, not the final user-facing release. Scheduled start/stop, external HTTPS signed-link acceptance, independent final Windows recheck, and standalone `HyperGet.exe` delivery remain open.
