> **CURRENT PR #4 HANDOFF — 2026-11-01, VERIFIED WINDOWS:** Exact last verified **code SHA `9ce2fc6a61e2a495d9f9a2294e3a84bac253ea1e`**, Windows Actions **[41306279017](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41306279017)** `success`. This file's own follow-up commit is **documentation-only**; do not mistake it for new untested production code. Stay on `experiment/liquid-reference-match-20261030` / Draft PR #4; never touch main or PR #3.\n\n# HyperGet — PR #4 cross-chat handoff (2026-11-01)

> This is a durable project checkpoint. GitHub HEAD and Actions are authoritative; refresh them before starting work. A documentation-only commit after the verified SHA does NOT mean new code was tested.

## Scope and verified state
- Repo: amroalhamedi2022-hue/personal-download-manager
- ONLY work branch: experiment/liquid-reference-match-20261030; Draft PR #4: https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/4
- Last **code** SHA verified on an installed Windows runner: c545031d1c9372677757e4969d7c6cdc2d590a32
- All checks on this code SHA passed: https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41192127768
- Windows visual artifact: windows-liquid-progress-visual, artifact id 13040170006.
- Never merge main, change PR #3, create unnecessary branches, or repeat verified Codex/automation commits.

## Confirmed completed work
- Light approved download window, blue/cyan animated main liquid bar (42dp), real transfer status, dynamic bubbles/graph.
- Segmented per-part transfer bars are 14dp high in a 20dp row, with improved spacing and active/inactive/completed states.
- Fitted resizable parts table columns, typography hierarchy, Windows Segoe UI fallback; per-file graph show/hide is persisted.
- Info / Speed / On Completion tabs are text-first, with selected blue underline and scroll reset on tab switch.
- Compact Speed and On Completion upper panels: 144dp vs Info 232dp (graph shown) or 196dp (graph hidden), showing more part rows.
- Per-download Show Download Completion Dialog override is nullable and JSON-backed; null follows the global preference. It is restored immediately and observed for subsequent changes. Existing shutdown logic unchanged.
- Theme selector shows colors of each *candidate* palette, not only active theme. Theme palette and source regression tests added.
- Latest Windows verification report for exact code SHA: Verified=true, FinalStatus=Finished, ExpectedBytes=ActualBytes=16777216, matching SHA-256, GraphToggleTested=true (real clicks), CompletionPreferenceDiskTested=true (real safe mouse toggles and JSON readback). Install, real GUI launch and reinstall/uninstall succeeded.

## Screenshot evidence
- Download latest exact-SHA artifact from run 41192127768; inspect liquid-progress-report.json.
- Screenshots: liquid-progress-active.png, liquid-progress-graph-hidden.png, liquid-progress-graph-restored.png, liquid-progress-tab-speed.png, liquid-progress-tab-on-completion.png, liquid-progress-tab-info-restored.png.
- Screenshots are actual Windows captures. NEVER show stock/generated placeholders as real app screenshots.

## Primary source documents
- docs/checkpoints/HG_IDM_TABS_PART_TRACKS_2026-10-31.md (completed/open IDM tab comparison matrix).
- docs/Download_Manager_Execution_Plan_v1.md (P3-01 through P3-06).
- docs/IDM_Repair_Addendum_2026-10-21.md (M06/N14-N15, M07/N18-N20, M08/N16-N17).
- docs/IDM_REQUIREMENTS_AUDIT_2026-10-30.md and docs/IDM_Requirements_Trace_82_2026-10-21.md.
- docs/design/file-download-window-approved-2026-10-17.webp and the accompanying reference Markdown.
- Historical source reference IDM_Repair_Handoff_2026-10-21.zip (P01–P27 and SCREENSHOT_INDEX_AR.md) was NOT found in the accessible Library/Drive during the previous search; do not claim exact original IDM comparisons without those screenshots.

## Primary source code
- desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/pages/singleDownloadPage/ProgressDownloadPage.kt
- desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/pages/singleDownloadPage/DesktopSingleDownloadPageComponent.kt
- desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/storage/DesktopExtraDownloadItemSettings.kt
- desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/pages/singleDownloadPage/ShowDownloadDialogs.kt
- shared/app/src/commonMain/kotlin/com/abdownloadmanager/shared/ui/widget/Tabs.kt
- scripts/Run-WindowsLiquidProgressVisualE2E.ps1
- scripts/source-tests/test_hyperget_liquid_progress_source.py

## Still OPEN / not proven
1. Exact pixel/side-by-side parity with ORIGINAL IDM tab screenshots (historical ZIP not located).
2. Visual testing at 125%, 150–200% DPI; long URLs; RTL + English text; paused/failed/waiting animations.
3. Persisting completion-dialog override after a real **app restart**; JSON disk roundtrip was proven, but restart was not separately verified.
4. Per-file Speed settings persistence after full restart, plus safe acceptance of On Completion actions; DO NOT run shutdown on CI.
5. Whole-app IDM parity: main screen menus, sidebar, toolbar, lists/columns, application theme selection screenshots. Not finished merely by the download dialog.
6. Final design approval by the user; do not call it 100% identical without direct screenshot comparison.

## Required workflow for the next chat
1. Fetch fresh PR #4 HEAD, recent commits and GitHub Actions. Compare to verified code SHA above; never re-run completed work or overwrite concurrent changes.
2. Read this handoff, the IDM checkpoint, design reference and real Windows screenshots.
3. Pick the highest-value **unaccepted** item, make real code edits on the dedicated branch only.
4. Update checkpoint after new accepted work, run source/Kotlin tests and installed Windows GUI/sha256 tests, and inspect real screenshots.
5. Report exact commits, links to CI/artifacts, proven behavior, remaining gaps, and untested items. Do not stop after a plan or automation setup.
6. Do not copy code/features from khodemadi/HyperGet without the user's express approval.

## Suggested first message in the new conversation
Continue HyperGet from the latest actual HEAD of experiment/liquid-reference-match-20261030 / Draft PR #4. Read docs/handoffs/HYPERGET_PR4_HANDOFF_CURRENT.md and docs/checkpoints/HG_IDM_TABS_PART_TRACKS_2026-10-31.md. Check GitHub Actions BEFORE edits, avoid duplicate Codex/automation commits, and work only on remaining priorities. Never touch main or PR #3. Deliver real Windows tests/screenshots and a precise status; do not merely write a plan.

## 2026-11-01 — PR #4 verified restart-persistence Windows milestone

### Scope and exact evidence
- **Last fully Windows-verified code SHA:** `3dc06bf073a736d77e96594cd7d47c0f0d08e86f` (previous `c545031` remains verified baseline). GitHub Actions run **41304965325** passed, including source contracts, Kotlin theme palette tests, Windows NSIS build, silent install, GUI screenshot, real 16 MiB range download and SHA-256 comparison, graph visibility clicks, completion preference and reboot UI probes, and installer lifecycle checks.
- Exact-SHA proof: https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41304965325
- Downloadable real Windows evidence: **windows-liquid-progress-visual**, artifact **13083182003**, https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41304965325/artifacts/13083182003
- `liquid-progress-report.json` shows `SourceSHA=3dc06bf...`, `Verified=true`, `TaskId=0`, `FinalStatus=Finished`, `ExpectedBytes=ActualBytes=16777216`, identical expected/actual SHA-256 `341aacac661ccb210720bedaa9ead5d668fe5ea41a73532fc147c71e34040df1`, `GraphToggleTested=true`, `CompletionPreferenceDiskTested=true`, `CompletionPreferenceRestartTested=true`, `CompletionPreferenceRestartTaskId=1`.
- Real UI before/after screenshots `liquid-progress-tab-on-completion-before-restart.png` and `liquid-progress-tab-on-completion-after-restart.png` show same paused download (`hyperget-preference-restart.bin`), selected **On Completion** tab, enabled-to-disabled completion dialog choice after restart; the **Shutdown System On Completion** switch remained disabled. The runner then restored the initial choice in its JSON.
- **Important:** task 1 was created separately and kept **Paused/unfinished**. The normal completed-task cleanup removes per-download JSON on completion, so a completed task cannot be used as a valid persistence fixture. Neither cleanup semantics nor any production Kotlin file was altered.
- At this handoff update the diagnostic string is being corrected from the earlier misleading "completed task reopened" to "paused task reopened"; this diagnostic-only change is a NEW code SHA and is not yet Windows-verified. Keep `3dc06bf` as the last independently verified code until newer exact-SHA CI succeeds.

### Scope preservation and open items
- No changes to the download dialog, segmented 14dp bars, main Liquid Bar, transfer engine, power-action behavior, main branch, PR #3, or other feature branches. Existing selectors and 16 modern Light/Dark palette variants remain present; full 96 theme/icon/size combinations have NOT been visually accepted.
- Still open: exact original-IDM comparison screenshots `IDM_Repair_Handoff_2026-10-21.zip` (not located), native Windows visual acceptance at 125/150/200% DPI, long URLs and RTL, speed-setting full restart and safe On Completion actions, broader main screen menu/sidebar/toolbar/table parity and column UI, end-user visual approval.
- Do not mark overall IDM pixel parity as passed; source comparison references unavailable. Source shows `TableState.load/save` persists main list configuration; avoid implementing the same persistence mechanism again before checking UI acceptance.

### 2026-11-01 — main toolbar typography refinement (VERIFIED Windows)
- Only `desktop/.../home/Actions.kt`: home toolbar **labeled** icons increased 16dp → 20dp, labels use `myTextSizes.base` instead of tiny `sm` (10sp→12sp), spacing adjusted from 2dp→4dp, and labeled tap-target padding set to 9dp horizontal / 8dp vertical. Unlabeled actions keep the 24dp icon and 12dp padding.
- Horizontal scrolling behavior is preserved so narrow Windows layouts remain usable; no changes to menu callbacks, downloader logic, global typography, progress dialog, liquid rendering, themes or stored settings.
- The source test added to `scripts/source-tests/test_hyperget_liquid_progress_source.py` guards the toolbar text/icon geometry and existing horizontal scroll.
- **VERIFIED on exact code SHA `9ce2fc6a61e2a495d9f9a2294e3a84bac253ea1e`:** Windows run `41306279017` succeeded including source tests, theme contrast tests, NSIS packaging/installed GUI smoke and new `installed-gui-smoke.png`, real 16 MiB transfer/SHA256, Liquid graph switch tests, and completion preference persistence across actual installed-GUI restart. See `windows-installed-gui-smoke` artifact 13082764407 and `windows-liquid-progress-visual` artifact 13083278591. The new image confirms the toolbar is readable and not clipped at the tested 1024px desktop width; this is not full original-IDM parity.

### 2026-11-01 final batch verification ledger (carry across chats)
| Deliverable | Exact evidence | Status |
|---|---|---|
| Main Windows toolbar readable labels/icons | `Actions.kt`, labels 10sp → 12sp, icons 16dp → 20dp; installed 1024px screenshot in artifact [13082764407](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41306279017/artifacts/13082764407) | **Windows success** |
| Installed download fixture | `TaskId=0`; 16,777,216 bytes; expected/actual SHA-256 `341aacac661ccb210720bedaa9ead5d668fe5ea41a73532fc147c71e34040df1` | **verified=true** |
| Completion-dialog preference disk + app restart | `CompletionPreferenceDiskTested=true`, `CompletionPreferenceRestartTested=true`, `CompletionPreferenceRestartTaskId=1`; same paused task reopened, switch inverse in real before/after Windows screenshots, original JSON restored | **Windows success** |
| Liquid visual graph switch and thin connection rows | `GraphToggleTested=true`; actual screenshot/report in artifact [13083278591](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41306279017/artifacts/13083278591); production liquid window code untouched | **regression clear** |
| Installed Windows lifecycle | NSIS install/reinstall/uninstall evidence artifact `13082904172` | **Windows success** |
| Main branch / PR #3 | No code writes, ref updates, merges or new development branches | **preserved** |

**Still NOT verified/unfinished:** final pixel-perfect IDM layout parity with original reference image archive; complete action parity of main menus/toolbar/context/shortcuts/tray; full 96 visual theme/icon-size combinations; 125%/150%/200% DPI, RTL, long URL state/window stress tests, 30-minute load stability and full resource regression; remaining software features from original broader roadmap. Do not treat those as done because this GUI smoke test passed. Progress on only these outstanding items from fresh HEAD, not from older handoffs or repeat commits.


### 2026-11-01 — Discoverable IDM-style column customization (pending exact-commit Windows test)
- `shared/.../customtable/Table.kt` now has **opt-in** `showColumnConfigButton: Boolean = false`: a compact 28dp header-end Columns button with a localized accessible label and tooltip. The Home downloads table opts in, while all unrelated tables retain their existing UI.
- Clicking the button opens the already-implemented `ShowColumnConfigMenu`; existing right-click header still works. No new persistence schema or separate menu. Existing `TableState` controls visibility/order/sort/default reset, and `HomeComponent` continues to save/load the same state. No transfer engine, download-window Liquid, or theme modifications.
- Source-contract regression asserts visible launcher, existing context menu, reordering, visibility, reset. Requires Kotlin compile/installer, actual Windows screenshot review and exact-commit Actions before marking accepted. The button may overlap a rightmost table heading on narrow windows: inspect Windows evidence before claiming visual acceptance.


### 2026-11-01 — Saved table column configuration hardening (needs exact-SHA Windows acceptance)
- `TableState.load` now ignores removed/fixed saved width IDs, rejects non-finite widths, and clamps out-of-range widths to the active column's supported bounds. A persisted sort ID referring to a non-sortable/removed column safely falls back to no sort rather than throwing `ClassCastException`. Valid column size/order/visibility/sort IDs remain round-trippable.
- Three real Kotlin Desktop unit tests in `TableStatePersistenceTest.kt` cover non-sortable sort/fixed width, non-finite/removed values and valid cross-instance roundtrip.
- Windows workflow includes the exact tests in its theme/Kotlin phase and triggers on changed model/test paths. Does not modify any download engine, transfer settings, completion logic, themes, or Liquid visuals.
- Status: implementation committed, acceptance pending **exact-SHA Windows workflow**. This work is complementary to the visible Columns launcher and is not an excuse to re-implement `TableState` persistence or overwrite user data.


### 2026-11-01 — Real Windows screenshot of an ACTIVE main download row (pending CI)
- The already-installed Windows Liquid test now captures `hyperget-main-list-active-download.png` while the actual 16 MiB HTTP download has CLI status `Downloading`, verified active server request and >=25% bytes transferred. It is captured *before* opening the file's details window, with a live foreground-window and display-bounds guard.
- The screenshot and `MainDownloadListStatusAtCapture` are included in the existing `windows-liquid-progress-visual` artifact and `liquid-progress-report.json`. This is the first explicit actual busy-list evidence in addition to the existing empty main GUI smoke and download-details captures; it is **not** a claim of pixel-exact IDM parity.
- Production components and all downloader data/persistence logic remain unchanged. Source contract verifies report keys, screenshot and foreground guard. Accept after exact-commit Windows GUI/HTTP/sha256 run and real-image inspection.


### 2026-11-01 — Real Windows Columns menu interaction gate (await exact-SHA CI)
- Windows E2E now clicks the new header-end Columns button in the known 1040x744 isolated GUI layout, with foreground and actual icon-pixel guards (never blind or unknown-DPI clicks).
- Records `hyperget-main-columns-menu-open.png` and uses a real pixel-difference gate **below** the animated transfer row to distinguish a substantial popup from a changing Liquid transfer bar. Closes the popup by clicking empty list space without mutating saved columns.
- Report fields `ColumnsMenuTested`, `ColumnsMenuResult`, `ColumnsMenuScreenshot`. Acceptance requires exact-SHA Windows run and screenshot review; icon/layout differences in other DPI remain to test. No production code changes.
