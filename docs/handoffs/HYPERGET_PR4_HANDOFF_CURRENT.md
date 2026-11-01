# HyperGet — PR #4 cross-chat handoff (2026-11-01)

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

## 2026-11-01 follow-up implementation — Windows restart persistence (UNVERIFIED until exact-SHA CI)
- This changeset extends `scripts/Run-WindowsLiquidProgressVisualE2E.ps1` only within the safe isolated Windows fixture, without altering the visual implementation, main, or PR #3.
- The On Completion UI probe now writes both Boolean states and deliberately leaves the inverse of the initial UI state before ending the already-verified HTTP transfer.
- After full transfer and SHA-256 validation, the installed GUI is forcibly terminated, relaunched from the installed EXE, and the same *completed* download is reopened with the installed CLI.
- It captures a new actual Windows screenshot `liquid-progress-tab-on-completion-after-restart.png`, verifies the selected tab underline and the per-file override's inverse switch color, then toggles the safe completion-dialog switch back to its original value and checks the restored JSON.
- The shutdown-on-completion switch is never operated. If the known geometry or pixel probes cannot be confirmed, the probe reports untested or fails instead of claiming a false pass.
- Report keys: `CompletionPreferenceRestartTested` and `CompletionPreferenceRestartResult`. Source assertions cover the new gate.
- **CI status at commit creation: not yet run.** Mark as Windows verified only after inspecting the new exact-SHA run and screenshot; do not infer from preexisting green runs.

### 2026-11-01: corrected Windows restart fixture before acceptance
- Source review confirmed `DesktopOnDownloadCompletionActionProvider` runs `CleanExtraSettingsOnDownloadFinish` for **finished** tasks. Therefore comparing a completed task after restart would be an invalid persistence claim.
- The updated Windows harness now adds a second **paused** HTTP task after verifying the original 16 MiB file, safely selects its On Completion tab, captures `liquid-progress-tab-on-completion-before-restart.png`, flips only the completion-dialog switch, and verifies its JSON.
- It then terminates and relaunches the actual installed Windows GUI, reopens the **same paused task** (not the completed download), captures `liquid-progress-tab-on-completion-after-restart.png`, checks its inverted switch color, confirms JSON restoration after a safe click, and restores the verified original task ID in the transfer report.
- Do not change post-completion cleanup semantics or claim this test passed before a green exact-SHA run.
