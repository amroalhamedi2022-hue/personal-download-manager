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
