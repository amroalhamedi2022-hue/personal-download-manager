# HG-UI-LIQUID-PROGRESS-CONTRAST — stronger but restrained liquid treatment
Date: 2026-10-27
PR: #3
Branch: automation/idm-ftp-cancel-and-category-flush
Base: codex/repair-rf00-rf01

## Prior visual proof (PASS)
- Exact source SHA `77f8c7e637b0fc6430484249af148588cc01837f`.
- Windows run [40425830147](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40425830147) succeeded; source tests [40425830410](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40425830410) passed 17/17.
- Visual artifact [12699952296](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40425830147/artifacts/12699952296), ZIP SHA-256 `110dd6ae8a51bf619b093707a4e3e39fe29784666ee73ba310ab2eb5c2babc3d`.
- Screenshot captured at 33.2% fill while status was `Downloading`, one task was active, and bytes advanced. The 16 MiB output finished with matching expected/actual SHA-256 `341aacac661ccb210720bedaa9ead5d668fe5ea41a73532fc147c71e34040df1`.
- Visual inspection: the glossy band and two bubbles are present but too faint at normal window scale.

## Purposeful pre-build gate
Question: does a 7dp track, stronger cached shimmer, more legible bubbles, and a narrow top-edge gloss improve visibility without changing the progress algorithm or creating per-row animation loops?
Expected:
- Source regression keeps one list-level clock, active-only animation, draw-phase reads and cached gradient allocation.
- Exact-head Windows Compose build/install succeeds.
- Screenshot captured at >=25% of the real 16 MiB transfer, with status `Downloading`, one active task, and advancing bytes.
- Final output size is 16,777,216 bytes and expected/actual SHA-256 match.
- Inspect the screenshot at native scale before accepting visual parity.

Source tests cannot prove actual pixel contrast, Compose rendering, IPC, or transfer integrity; one targeted Windows visual build is justified. No release build, main write, merge, paid runner/API, or Torrent.


## 2026-10-27 — exact-head contrast recheck preparation

The current PR-branch source now contains the proposed visual delta relative to the prior passing screenshot SHA `77f8c7e637b0fc6430484249af148588cc01837f`:
- track height: 5dp → 7dp;
- cached shimmer white alpha: 0.30 → 0.42;
- top-edge gloss alpha: 0.16;
- bubble alpha/radius: first 0.42 / 2dp, second 0.30 / 1.3dp (previously lower/smaller).

These are source observations, not visual acceptance. The previous screenshot proved only the older rendering; the new values require an exact-head Windows build, screenshot inspection at native scale, and the same live-transfer/SHA gates. Before that build, add numeric lower-bound regression assertions so future changes cannot silently reduce the approved contrast while leaving only string-presence checks.

**Pre-build question:** does the current exact PR head render the stronger 7dp Liquid Progress treatment visibly at ≥25% real transfer, with status `Downloading`, advancing fixture bytes, and final 16 MiB SHA-256 equality?
**Expected:** source contracts and PowerShell parsing pass; Windows installer builds and launches; the screenshot demonstrates the shimmer/gloss/two bubbles at native scale; final file is 16,777,216 bytes and expected/actual SHA-256 both equal `341aacac661ccb210720bedaa9ead5d668fe5ea41a73532fc147c71e34040df1`.
**Why source tests are insufficient:** pixel contrast and Compose rendering cannot be established from source assertions, and the installed CLI/IPC plus real transfer must remain live during capture.
**Gate:** OPEN until the exact-head Windows artifact is inspected. No release/merge/final EXE claim.


## 2026-10-27 — source-gate regression caught a test-parser typo (FIXED BEFORE UI ACCEPTANCE)

The first run after adding numeric contrast bounds failed only in the new Python source test: its raw-regex literal was over-escaped, so the shimmer token was not matched. The production Compose source was not implicated; all other 18 Liquid Progress source-contract tests passed. The fast source job stopped before the installed UI job could be accepted, so this is a **test implementation FAIL**, not a visual or transfer FAIL. Correct the regex literal and rerun the exact-head source and Windows UI gates; do not accept the contrast milestone until both the numeric test and the screenshot/transfer artifact pass.


## 2026-10-27 — exact-head contrast + installed visual E2E PASS (resource metadata caveat)

**Exact tested PR source SHA:** `90e8d6eac09d4d84ef3391754778dfb17a70b88d` (`test: parse Liquid Progress contrast thresholds safely`).  
**Windows run:** [40429083834](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40429083834) — terminal **SUCCESS**.  
**Source gate:** [40429083802](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40429083802) — **PASS**, including 19/19 Liquid Progress source tests; the new numeric contrast-threshold test passed after removing the over-escaped regex implementation.

### Exact-head evidence
- **PASS:** Compose/NSIS build, isolated install, real GUI launch, screenshot, active-transfer E2E, same-version silent reinstall and uninstall.
- Tested installer SHA-256: `7ea2916fdae41e910ec95f0b38d26752cb5eacee9329c7d907e3650450834858` (test installer only, not final delivery).
- Visual artifact [12701159241](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40429083834/artifacts/12701159241), ZIP SHA-256 `36936a472a0b9dc80e45c3b326bae0aedde286e1d4e2fb021a87282b089f380c`; downloaded locally and independently rehashed to the same digest.
- Report identifies source SHA `90e8d6eac09d4d84ef3391754778dfb17a70b88d`, status `Downloading`, one active task, advancing bytes, capture at 5,243,136/16,777,216 bytes (31.3%), and final status `Finished`.
- Final output size: 16,777,216 bytes. Expected and actual SHA-256 both `341aacac661ccb210720bedaa9ead5d668fe5ea41a73532fc147c71e34040df1`; independently recomputed for both the downloaded output and fixture.
- Native-scale screenshot inspection: 7dp fill and two moving light bubbles are discernible; the gloss remains subtle. **Functional Liquid Progress gate PASS; full IDM visual parity is not claimed.**
- Lifecycle artifact [12701084518](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40429083834/artifacts/12701084518), ZIP SHA-256 `d7a487de8f9648b984a08d0f5af106c0151199d4805b885b1d608167584780c2`. GUI smoke artifact [12701666874](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40429083834/artifacts/12701666874), ZIP SHA-256 `6333c9bb95849b37487c97f2f139129d8f3446ec08973d40cfdeb66a3de54aac`.

### Evidence not accepted
The idle metrics artifact [12701507224](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40429083834/artifacts/12701507224), digest `b08fab6c1d89b6c2a762587475e54f0f4fc72c52a35f8eec4faf6174e829222a`, labels samples with `SourceSha=4fe66db27d185f69d2ccbbf01810d35b6f954354`, while the checked-out exact PR head is `90e8d6eac09d4d84ef3391754778dfb17a70b88d`. The workflow passes `$env:GITHUB_SHA` (PR merge ref) into the idle benchmark instead of `git rev-parse HEAD`; **these idle samples are invalid as exact-head performance evidence**. Do not claim a CPU/RAM improvement from them.

### Gate decision and next milestone
**Liquid Progress functional/visual acceptance: PASS** for the exact tested source SHA above. The earlier cross-version upgrade/rollback proof remains PASS on its recorded source SHA; it was not rerun by this UI-only delta. Next, correct idle-metric source provenance with a regression guard, then run a controlled, comparable resource profile before making any performance claim. Overall HyperGet release acceptance remains OPEN: external/restart cases, controlled performance comparison, final independent Windows recheck and final single `HyperGet.exe` artifact are still required. No merge/release/Torrent.
