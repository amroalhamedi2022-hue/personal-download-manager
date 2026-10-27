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
