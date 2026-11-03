# HG-03 installed Pause/Resume — explicit HTTP transport cancellation + FTP fixture atomicity

Date: 2026-10-27  
PR: [#3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3)  
Branch: `automation/idm-ftp-cancel-and-category-flush`  
Parent source SHA: `4979ee80145d0f77ce646a6088c0f08cededc53f`

## Previous milestone continuity

- HG-03 branding harness at exact source `4979ee80145d0f77ce646a6088c0f08cededc53f` installed exactly one `HyperGet.exe`, preserved `DownloadManagerCli.exe`, and completed the initial 16 MiB HTTP download with matching SHA-256 `341aacac661ccb210720bedaa9ead5d668fe5ea41a73532fc147c71e34040df1`.
- Source-only checks [40563418442](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40563418442), FTP Kotlin/WinCred [40563418329](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40563418329), queue lifecycle [40563418402](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40563418402), HLS [40563418725](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40563418725), installed browser capture [40563418552](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40563418552), GUI smoke [40563418612](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40563418612), and cross-version [40563418472](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40563418472) passed their actual jobs on that SHA.
- Active HTTP 1/4/16 [40563418394](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40563418394) was pending; it is not a new performance result.

## Exact-head failures and artifact inspection

### HTTP pause/resume — functional FAIL

Run [40563418358](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40563418358) built and installed the candidate and passed the initial 16 MiB transfer. Its second stage failed at `Run-WindowsInstalledResumeE2E.ps1:77` because the local server still reported active HTTP response bodies after Pause. Artifact [12762277892](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40563418358/artifacts/12762277892) was downloaded and its SHA-256 verified locally as `852f4bcf192c5506027f683c1e9bbab681b8ede5ce70052ee923d5169ef47971`. Its recorded initial-transfer hashes match, but it contains no successful Pause/Resume proof.

### FTP pause/resume — inconclusive due to fixture failure

Run [40563418659](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40563418659) built and installed the candidate (installer SHA-256 `115f30d744aa54287a542d805a5ea8b47b78e763a30cc53ca679039abb4b9f3d`). The FTP fixture sent 3,735,552 bytes, then its own stats update crashed with Windows `PermissionError [WinError 5]` while replacing `ftp-stats.json`; the stale file still said one active retrieval. Artifact [12762376373](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40563418659/artifacts/12762376373) was downloaded and SHA-256 verified locally as `ad04abe761096891ad864e85b6d1cda49e6ab4efd758d79f11482e916c19b1df`. Therefore this run does **not** prove an FTP transport leak; the fixture failed before it could record the final count.

## Production + regression repair

- Extend the shared HTTP `Connection` with an optional transport-cancellation callback.
- The OkHttp adapter retains the actual `Call` and supplies `call.cancel()` as the connection close action; closing a connection now cancels the exchange before closing the wrapped Okio source and releasing host permits.
- Add a Kotlin lifecycle regression that requires both the transport cancellation hook and source close to execute.
- Add source assertions for the exact OkHttp call-cancel wiring.
- Make the FTP fixture's atomic stats replace retry transient Windows sharing violations with a bounded retry; use a per-thread temporary file and a regression that injects two transient `PermissionError` failures.
- Emit the full loopback HTTP stats snapshot if Pause still fails, so any residual socket leak can be attributed to the active task/request counters instead of an opaque timeout.

## Pre-build acceptance question

Can the newly built installed HyperGet close every live HTTP response body on Pause, remain byte-idle while paused, then resume to the exact original 32 MiB SHA-256; and can the FTP fixture reliably record `active_retrievals=0` after a genuine pause, then verify positive REST offset, GUI restart, final SHA-256 and WinCred cleanup?

**Expected:** source-only regression and fixture tests PASS; the purpose-built Windows installer/E2E runs complete Pause → stopped socket/unchanged bytes → Resume SHA-256 for HTTP and Pause → server-side retrieval reaches zero → GUI restart → exact REST resume for FTP. No acceptance threshold is weakened.

**Why source tests are insufficient:** only the installed Windows process, actual OkHttp/FTP data sockets, server-side loopback counters, persisted staged bytes, REST command and final file hash can prove cancellation and resumption end-to-end.

**Status before the new runs:** HTTP Pause/Resume **FAIL** on the parent SHA; FTP result **INCONCLUSIVE (fixture stats-write failure)**; no final release or performance result is inferred from either run. Final independent release recheck and user-facing `HyperGet.exe` artifact/SHA remain OPEN.


## 2026-10-27 — deterministic local HTTP port readiness (OPEN)

**Observed exact-head failure:** Windows installed HTTP run [40571810012](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40571810012) built and installed source 8e74203ded3ca0177238d0a8fe24a702f885d0fc, then failed before the first transfer with HttpDownloadCredentials.validate: url is not valid; Pause/Resume was skipped. This is not evidence that HTTP Pause/Resume failed because the test never reached it. Installed FTP Pause/restart/REST/SHA/WinCred [40571810155](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40571810155), browser capture [40571810035](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40571810035), GUI launch [40571810241](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40571810241), and cross-version upgrade/rollback [40571810080](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40571810080) passed on that head.

**Root-cause hypothesis:** HTTP fixture consumers treated port-file existence as readiness and immediately cast its content to an integer. The fixture writes that file after binding, but Windows can observe the newly created/truncated file before the decimal port has been fully written. An empty read can become port 0 and produce an invalid HTTP URL. The failing log did not print the port, so the guarded rerun must confirm this diagnosis.

**Repair:** add scripts/Wait-WindowsLoopbackHttpPort.ps1, a bounded waiter that checks server liveness and rereads until the file contains a complete decimal TCP port in 1..65535, preserving fixture stderr on early exit. Wire it into installed HTTP/Pause-Resume, Liquid Progress visual, cross-version upgrade/rollback, and active 1/4/16 benchmark scripts. The inline HTTP workflow validates the resulting URI is loopback HTTP on the selected port. Add scripts/source-tests/test_hyperget_http_fixture_port_readiness.py and ensure affected workflows trigger and scope-admit the helper change.

**Pre-build question:** does a fresh installed Windows run obtain a valid loopback URL, finish the initial 16 MiB transfer with exact SHA-256, then execute Pause → zero active requests → stable bytes → Resume to exact 32 MiB SHA? Does the same helper keep visual, upgrade/rollback, benchmark and memory-profile runs deterministic?

**Expected / why build:** source contracts and PowerShell parsing should pass first; a Windows build/install is required because source tests cannot prove port-file timing, process lifetime, installed GUI/CLI IPC, actual sockets, or final transfer hashes. No performance threshold is relaxed. Current-head paired performance, independent final recheck and final HyperGet.exe SHA/artifact remain OPEN. No merge, main write, release, paid runner/API or Torrent.
