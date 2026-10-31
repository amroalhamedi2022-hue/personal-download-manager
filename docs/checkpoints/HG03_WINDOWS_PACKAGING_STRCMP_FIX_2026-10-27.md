# HG-03 packaging unblock — valid NSIS process-path comparison

Date: 2026-10-27  
PR: [#3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3)  
Branch: `automation/idm-ftp-cancel-and-category-flush`  
Parent source SHA: `01c1342ac5a12bc58e7b3b0ec797a5a5fbc0e41a`

## Previous milestone continuity

- E061 cap-60 HTTP performance comparison: **PASS on its exact tested source** `c99b6f627de5f1ef1cba9e79f4f8e1baba20ffe1`, with 126/126 SHA-verified outputs and the unchanged 15% throughput/peak-memory gates.
- Windows uninstall/user-content safety: **PASS on exact source** `068e2ff9b66d47764d4d76bae59361a1cf983c9d`.
- That cross-version harness distinguishes the legacy baseline GUI `DownloadManager.exe` from the candidate `HyperGet.exe`; this is historical test evidence, not a final release artifact.
- The approved HyperGet SVG and multi-size Windows ICO already exist in the PR branch and have source-format checks. Current-head packaging/icon runtime acceptance still requires a successful Windows installer build.

## Current-head failure

Windows GUI smoke run [40545095153](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40545095153) checked out exact PR head `01c1342ac5a12bc58e7b3b0ec797a5a5fbc0e41a`. Kotlin compilation and `createReleaseDistributable` succeeded, but NSIS packaging failed at `:desktop:app:createInstallerNsis`:

`StrCmp expects 3-4 parameters, got 5.`

The offending line used `StrCmp /I ...`. NSIS `StrCmp` has no `/I` option and is already case-insensitive (official reference: https://nsis.sourceforge.io/Reference/StrCmp). This is a packaging syntax failure; it is not evidence of a download-engine failure, nor a successful current-head installer test.

## Production + regression fix

- Remove the unsupported `/I` token while preserving the case-insensitive directory comparison that prevents the installer from terminating itself when setup and target share a directory.
- Add a source regression that rejects `StrCmp /I` inside `closeProcess` and requires the valid four-operand command.
- Keep the executable/package/data/CLI/native-messaging identities unchanged. Do not change `settings.gradle.kts` root project identity.

## Pre-build acceptance question

Does the corrected NSIS template compile on Windows and allow the candidate installer to install, expose exactly one `HyperGet.exe` (no `DownloadManager.exe` in the candidate install), preserve the legacy CLI/native host names, launch the GUI, and complete the existing real HTTP/Liquid Progress and upgrade/rollback gates?

**Expected:** source contracts and PowerShell parser pass; `createReleaseFolderForCi` emits one installer; installed-tree assertions and GUI launch pass; installer lifecycle and cross-version tests retain their data/shortcut/WinCred protections.

**Why source tests are insufficient:** only `makensis.exe` and the actual Windows installer can prove NSIS syntax, launcher layout, icon embedding, shortcut targets and self-removal behavior.

## Status

- Source change: prepared for commit; current-head Windows build failure is the confirmed blocker.
- Current-head NSIS build/runtime: **NOT TESTED after fix** until the new workflow completes.
- Final signed/release `HyperGet.exe`, independent release recheck and final artifact SHA: **NOT COMPLETE**.
- No merge, main write, release, paid service or Torrent.


## 2026-10-27 — HG-03 identity harness follow-up (exact source `b63114d0c2fee6e667175302c9bc16a5da414c61`)

### Actual Windows result

The corrected NSIS script compiled and the installer installed successfully in [Windows GUI smoke run 40546589588](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40546589588). The installed tree contained exactly `HyperGet.exe`, no `DownloadManager.exe`, and the stable `DownloadManagerCli.exe`; the installer SHA-256 was `bf9f01d881c03fd067944f8682ca203238fcb95d3b1b538d947f152694132b39`. The GUI became visible and produced screenshot artifact [12753841163](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40546589588/artifacts/12753841163), whose uploaded SHA-256 digest was `eb2cd4e4ea412bafda80bd8e4da6a99ed7585de9dba5b3018ba32e14fabfef26`.

The GUI smoke then **failed** at the idle CPU/RAM sample because its harness still searched for process `DownloadManager`; the actual renamed GUI process is `HyperGet`. The screenshot and install evidence show the app did launch; this is a stale acceptance-harness identity, not a compile/NSIS or download-engine failure. Liquid Progress and the same-version lifecycle steps were skipped after this failure and are not counted as new passes.

The separate real browser run [40546589599](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40546589599) **PASS** on the same exact source: real Chrome/Firefox extension packaging and native-messaging protocol, with Firefox capturing a real 2 MiB transfer through the installed HyperGet host and verifying SHA-256. Evidence artifact [12754145299](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40546589599/artifacts/12754145299), SHA-256 digest `28fe2b0a3ccbcde654dd0101e4061c9eee789a0d5804e09cfc646749e8ab7714`. The source-only contract run [40546589656](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40546589656) also passed; FTP and HLS JUnit gates [40546589446](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40546589446) and [40546589605](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40546589605) passed, as did queue lifecycle [40546589576](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40546589576). Those results are exact-head evidence for `b63114d0c2fee6e667175302c9bc16a5da414c61`, not a final release.

### Fix in this checkpoint

- Change idle sampling to target `HyperGet`.
- Make the 1/4/16 process sampler derive the root process name from its supplied GUI executable, while explicitly allowing only the legacy baseline `DownloadManager` and branded candidate `HyperGet`.
- Update installed active benchmark, JVM profile, HTTP E2E and authenticated FTP E2E to require exactly one `HyperGet.exe`, reject a leftover `DownloadManager.exe`, and retain `DownloadManagerCli.exe` / `DownloadManagerNativeMessagingHost.exe` names.
- Add PR path triggers and exact-PR-head checkout to installed HTTP E2E; make FTP scope gating rerun on installer/identity/workflow changes.
- Add a source contract for all these identity assumptions. The next Windows run must prove actual process sampling and transfers, not merely that the scripts mention the new name.

### Acceptance question before the next build

Does each installed Windows harness now discover the branded `HyperGet.exe`, sample the correct visible GUI process, preserve legacy CLI/native-host names, and complete real HTTP pause/resume, authenticated FTP REST resume, Liquid Progress, 1/4/16 throughput/memory sampling, and cross-version data/shortcut/WinCred checks?

**Why source tests are insufficient:** only the Windows installer and actual process tree can establish the launcher name, window process identity, installed shortcuts, real transfers and resource samples.

**Status:** identity-harness patch committed on the existing PR branch; exact-head reruns pending. Final independent release recheck and final `HyperGet.exe` artifact/SHA remain OPEN.


## 2026-10-27 — HG-03 exact-head executable-identity gates (OPEN)

**Exact PR head inspected:** e36266add4eb4e009eb6c64a4e71ceae5d5090b7 on automation/idm-ftp-cancel-and-category-flush; PR #3 remains open/Draft. This checkpoint is recorded before the next targeted Windows build.

### Evidence from that exact head
- Source-only run [40549485454](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40549485454) failed four identity subcases. The assertion expected the literal DownloadManagerCli$, while the active benchmark, memory profile, HTTP E2E and FTP E2E contained incomplete -match 'DownloadManagerCli lines. These were malformed acceptance scripts, not a download-engine failure.
- Windows GUI run [40549485330](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40549485330) built and installed the exact-head installer and launched the GUI/screenshot step, but Liquid Progress E2E failed at Run-WindowsLiquidProgressVisualE2E.ps1:162: the harness still filtered ProcessName -match 'DownloadManager' after the branded process became HyperGet. Artifact [12753789808](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40549485330/artifacts/12753789808), SHA-256 b1e2404c460c8b40e13b4a923f5bfb18dc99f768c73fc920d6673d012ca734aa.
- Cross-version run [40549485561](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40549485561) passed change-scope only; the actual upgrade/rollback job was skipped. Browser run [40549485303](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40549485303) also skipped its installed Chrome/Firefox job. Neither is a current-head functional PASS.
- Exact-head queue lifecycle [40549485344](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40549485344), FTP Kotlin/WinCred [40549485310](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40549485310), and HLS [40549485488](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40549485488) completed successfully. These do not replace the skipped installed gates.

### Controlled repair
- Replace all four malformed CLI selectors with exact BaseName -eq 'DownloadManagerCli' selection and require exactly one branded GUI, zero legacy GUI binaries and exactly one backward-compatible CLI.
- Make the Liquid Progress harness derive the GUI process name from the supplied installed executable, require HyperGet.exe, and discover that exact process instead of the retired GUI name.
- Add regression assertions for the selector, cardinality and Liquid Progress process identity; add the regression file to cross-version and real Chrome/Firefox native-host workflow path/scope gates.

### Pre-build question
Can the exact-head installer now expose one HyperGet.exe plus the stable CLI, display a real active Liquid Progress window, and proceed through cross-version upgrade/rollback and Chrome/Firefox capture?

**Expected:** source contracts and PowerShell parsing PASS; Windows installer builds; installed HTTP output matches fixture SHA-256; Liquid Progress is captured while a real transfer is active; upgrade/rollback and installed browser capture run rather than skip. **Why source tests are insufficient:** the renamed process name, native window handle, NSIS layout, CLI IPC, transfer bytes and Windows lifecycle only exist in the installed Windows execution.

**Acceptance remains OPEN.** No final EXE/release is claimed; no merge or main write.

---

