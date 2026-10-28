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
