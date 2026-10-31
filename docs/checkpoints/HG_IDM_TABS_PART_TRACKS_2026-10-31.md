# HyperGet — IDM tab comparison and compact parts follow-up (2026-10-31)

## Authoritative references consulted
- `docs/Download_Manager_Execution_Plan_v1.md`: P3-01–P3-06.
- `docs/IDM_Repair_Addendum_2026-10-21.md`: M06 and N14–N15 (file download window), M07/N18–N20 (main app layout), M08/N16–N17 (themes and scale).
- `docs/design/file-download-window-approved-2026-10-17.webp` plus `docs/design/APPROVED_FILE_DOWNLOAD_WINDOW.md`.
- `docs/WORK_HANDOFF_IDM_2026-10-23.md` describes the historical side-by-side screenshot package `IDM_Repair_Handoff_2026-10-21.zip` with P01–P27 and `SCREENSHOT_INDEX_AR.md`. These exact originals were **not located** in the accessible Library/Drive searches. The comparison below therefore uses the authoritative product plan and approved download-window reference, and is **not** a claim of full parity with those missing screenshots.

## Current implementation and remaining IDM parity

| Requirement | What exists on the PR branch | Acceptance still needed |
| --- | --- | --- |
| Info | Real file properties and transfer chart; graph hide/show preference persisted, real Windows click test verified on `aff1c1a`. | Long URLs, mixed RTL/Latin and 100–200% DPI not all visually checked. |
| Speed | `Settings -> RenderSettings` exposes real per-download speed and connection-limit settings through the base component. | Side-by-side comparison against the original IDM Speed screenshot and save/restart UI acceptance. |
| On completion | `OnCompletion -> RenderOnCompletion` binds the shutdown action and completion-dialog choice to existing per-download settings. | Human/UI acceptance for all actions; preserve safety around shutdown. |
| Tab visual/navigation | Existing tabs were icon-heavy, and scroll offset was reused across content. | Patch makes **only the download dialog** tabs text-only with selected accent underline and resets scroll on tab switch; global widget preserves prior defaults. |
| Overall liquid | 42dp animated main track, live percentage in window title, file stats underneath. | Preserved unchanged; continue real installed Windows visual acceptance. |
| Individual parts | Concurrent `fe9710a` already reduced tracks to 14dp high in a 20dp row, made borders subtle and inactive state distinguishable. | This patch increases horizontal gaps to 8dp between adjacent parts to reduce the too-wide appearance; keep independent fractional fills and semantic state colors. |
| Main app IDM navigation | M07/P3-01 and P3-04 require menu/toolbar/tree/table parity and persisted column controls. | Not complete by this window-only PR. Compare against original screenshots when accessible; do not mark complete prematurely. |

## Release and concurrency rules
- Never replace or redo verified commits from other workers. Compare the latest SHA before pushing.
- Continue only on `experiment/liquid-reference-match-20261030` / Draft PR #4, leaving `main` and PR #3 untouched.
- Windows CI must compile and capture installed actual GUI on the exact commit; source assertions alone are not visual acceptance.

## Installed-tab visual evidence
- The Windows Liquid Progress E2E now records three additional screenshots when the runner presents the known 1024x728 dialog: `liquid-progress-tab-speed.png`, `liquid-progress-tab-on-completion.png`, and `liquid-progress-tab-info-restored.png`.
- These are real on-screen mouse interactions. Merely saving screenshots **does not** certify that the correct tab was clicked or that IDM parity was met. Review the screenshots manually and state any missing source comparison image explicitly.

## Final pass: compact settings panels and per-file preference retention
- The real Windows screenshots show that **Speed** and **On Completion** have two controls each but were forced into the same 232dp panel as the chart-heavy **Info** tab. The new tab-aware panel is 144dp for those two compact tabs, preserving a scrollable container for accessibility/large fonts and expanding the connections table instead of leaving blank space.
- **Show Download Completion Dialog** formerly only changed `itemShouldShowCompletionDialog` in memory. A nullable `showCompletionDialogOverride` is now stored with `DesktopExtraDownloadItemSettings`. `null` remains backward-compatible and inherits the global setting; explicit true/false is persisted per download. The already persisted shutdown power action is untouched.
- Source and Windows compile/installed UI checks are required on the exact new HEAD; disk reload/restart behavior should not be marked manually verified unless a runtime test demonstrates it.

## Real Windows preference disk probe
- On the isolated 1024×728 Windows CI fixture, the visual E2E captures the **On Completion** tab, verifies its blue selected underline and the actual switch color, then clicks only **Show Download Completion Dialog** twice. It reads the transactional per-download JSON after both actions, checks the nullable override was written with each new Boolean value, and restores the original UI choice. The **Shutdown System On Completion** switch is never touched.
- The JSON result reports `CompletionPreferenceDiskTested` and a bounded explanation. The probe is skipped rather than risk guessed clicks when geometry or pixels differ. Even an on-disk roundtrip is **not** a full app restart test; that remains open until separately run.
