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

## Startup safety follow-up
- Restore the per-file Boolean override synchronously during the desktop component's initialization, before the asynchronous storage observation, so a very short download cannot finish under the global default before the stored per-file choice is loaded.
- Harden the Windows switch pixel guard against mistaking a pure-white background for a disabled grey switch before issuing a coordinate click.


## 2026-11-01 — PR #4 IDM parity evidence update (live Windows)
- **Main UI columns:** existing `TableState` support for visibility/order/sort/width/reset remains in use; now a visible 28dp Columns control is opt-in for the downloads table, right-click header unchanged. Verified installed on Windows `a43cf58` [run 41308254173](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41308254173).
- **Persistence corruption tolerance:** `TableState.load` safely rejects removed/non-sortable sort IDs and non-finite/fixed saved widths; clamps out-of-range sizes; three Kotlin roundtrip tests. Exact Windows success `694092d`, [run 41308826679](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41308826679).
- **Main UI during transfer:** genuine `hyperget-main-list-active-download.png` evidence from Windows while CLI reports Downloading, plus verified 16 MiB SHA-256. `4d2cd76`, [run 41309137539](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41309137539).
- **Menu click verified visually** from real installed Windows `02c62a2`: `hyperget-main-columns-menu-open.png` screenshot and log reporting 400+ changed pixels under the live download row. That run FAILED afterward at restart geometry, so it is not accepted as whole-workflow regression success.
- **Still OPEN:** original IDM screenshot side-by-side pixel parity (original `IDM_Repair_Handoff_2026-10-21.zip` unavailable), DPI 125–200%, RTL, multi-theme/icon pack matrix, speed preference after restart, power-action safety checks beyond completion-dialog switch. Do NOT conflate verified 1024px GUI parity with full IDM acceptance.


### 2026-11-01 — Main search N20
- Search matches source URL as well as filename, including previously completed entries; category/status/queue filtering unchanged. Four Kotlin tests added. Exact-SHA Windows runtime acceptance remains to be checked.


## 2026-11-01 — Search accepted on installed Windows (authoritative update)
- Last production code verified: `cdd93578e62ed7a83ebfdb61161ff814d231d6c2`; [run 41334962719](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41334962719) green. `Ctrl+F` → existing header search interaction proven by `installed-gui-ctrl-f-search.png` showing typed `hgfocusprobe`; screenshot and test data under Windows artifact **13095490702**.
- Filename **or URL** search proven for a real ongoing loopback task: query `127.0.0.1` only matches the URL, yet the filename row remains visible while Downloading. Evidence `hyperget-main-search-by-source-url.png` and verified 16 MiB HTTP/SHA-256 report in Windows artifact **13095456141**.
- Four Kotlin matcher tests also pass for filenames, completed URL/domain/query, mixed Arabic/Latin and empty search. `Tools > Search in the list` and Ctrl+F share one action.
- Other IDM N18/N19/N20 menu actions and pixel parity remain **PARTIAL**, not full acceptance. Required original IDM P01–P27 screenshots still missing; do not invent comparison images.


## 2026-11-01 — Latest PR #4 Windows verification and shareable EXE
- **Fully green code:** `fb9163b55a9782446858b22ac73d40155d41982b`, Actions [41337403929](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41337403929). All Windows installed GUI, 16 MiB SHA-256, Liquid graph, Columns menu, search URL, per-download completion preference restart and NSIS lifecycle accepted on this SHA. Screenshots and report artifacts 13096276650 and 13095903645.
- **Windows EXE delivery:** [GitHub Actions artifact 13095794001](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41337403929/artifacts/13095794001), containing one `HyperGet_1.0.0-sha.gfb9163b_windows_x64.exe` (79,936,855 bytes, SHA-256 `8ee364b3706efd986effed464079d28ea6cf66d102964564c44b747096a92874`). Verified development candidate, NOT production-IDM parity; artifact retention seven days.
- **N19 shortcuts:** Ctrl/Cmd+F unique binding; Ctrl/Cmd+Shift+O opens selected file folder. Escape clears active home search; Ctrl/Cmd+C/V within focused search no longer route through the global download commands. Unit/source and Windows suite verified, separate physical clipboard typing on a user's device still open.
- **Link-list import:** now reads up to 16 dropped files with 1 MiB aggregate cap instead of only the first. Four Kotlin real-file tests passed in verified Windows run. GUI drag gesture acceptance remains open; do not mark it done.
- **Still open:** original IDM pixel-reference archive, full DPI/RTL/icon/theme matrix, stress/long-running performance, other P3/P5/P6/P7 gaps. Main/PR #3 unchanged.


## 2026-11-01 — Latest verified code and installer (supersedes earlier checkpoint)
- Last compiled/Windows-accepted CODE SHA **`56fd102309d95abe1cabccdfe1d265f3c4c64c03`**; [run 41340178985](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41340178985) **success**.
- `readDroppedLinkFiles` now bounds **inspected candidates** at 16, not only successfully read files; caps actual raw bytes at 1 MiB; decodes BOM-marked UTF-16LE/UTF-16BE/UTF-8, rejects malformed UTF-16 and embedded NUL. `StringUrlExtractor` now handles standalone plus `URL=...`/other inline links in the same list without dropping either, preserves full FTP and source order.
- Kotlin Desktop tests include mixed UTF-16 old Windows Internet Shortcut `.url` with UTF-8 plain list real-file end-to-end; exact-SHA Windows installer/gui/live HTTP SHA+restart/lifecycle passed. OS drag gesture remains **UNTESTED**, not completed.
- New Windows x64 EXE: `HyperGet_1.0.0-sha.g56fd102_windows_x64.exe`, 79,934,345 bytes, SHA-256 `9c3a9414a39dd6db1f9744d9bd1ceadc09a76815c46552472cdc085c60fb6610`, [CI artifact 13096319578](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41340178985/artifacts/13096319578). Latest real visual proof artifact **13096539064**.
- Original IDM ZIP reference, complete DPI/RTL/theme/icon/pixel checks, 1K/10K load stress and original P4–P8 roadmap remain OPEN. This is a development candidate, main and PR #3 untouched.
