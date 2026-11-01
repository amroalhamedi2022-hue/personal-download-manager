> **Historical candidate `fb9163b`**. A newer Windows-verified candidate `56fd102309d95abe1cabccdfe1d265f3c4c64c03` was built and delivered. See `HYPERGET_PR4_VERIFIED_CANDIDATE_2026-11-01_56fd102.md` for the latest EXE and test result. This document remains for traceability.

# HyperGet — Windows verified development candidate (2026-11-01)

## Exact package
- Repository: `amroalhamedi2022-hue/personal-download-manager`
- Source: branch `experiment/liquid-reference-match-20261030`, code commit `fb9163b55a9782446858b22ac73d40155d41982b`
- Windows Actions success: [run 41337403929](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41337403929)
- Installer: `HyperGet_1.0.0-sha.gfb9163b_windows_x64.exe` (Windows x64)
- Size: **79,936,855 bytes**
- SHA-256: **`8ee364b3706efd986effed464079d28ea6cf66d102964564c44b747096a92874`**
- Download: [GitHub Actions EXE artifact 13095794001](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41337403929/artifacts/13095794001), one EXE inside; 7-day GitHub retention. Also delivered as a direct ChatGPT conversation attachment.
- Status: **tested development candidate**, **not** a final signed/production release or complete original-IDM comparison.

## Automated acceptance on the exact SHA
1. Shared Kotlin/Desktop tests pass (theme/palette, table persistence, search URL/Arabic, bounded multi-file link imports); source contracts pass.
2. Exactly one NSIS EXE built and installed into clean isolated Windows x64 runner. Main installed GUI launches; Ctrl+F test types `hgfocusprobe` and screenshots it.
3. Loopback HTTP 16,777,216 bytes downloaded; SHA-256 output equals `341aacac661ccb210720bedaa9ead5d668fe5ea41a73532fc147c71e34040df1`.
4. Real Windows on-screen search for `127.0.0.1` keeps an ongoing download row visible although the string is absent from the filename. Real Columns popover click and live chart show/hide pass.
5. Per-file completion-dialog switch persisted on disk through actual Windows GUI restart for a paused download, then restored. NSIS same-version reinstall and silent uninstall passed.
6. Visual proof: Windows artifact 13096276650 (17 items including report and live screenshots), GUI artifact 13095903645, lifecycle artifact 13096062499.

## Manual checks still needed on a user's Windows PC
- Back up existing HyperGet data/download queue first. This candidate was tested with a fresh **isolated install**; do not infer a safe in-place upgrade over arbitrary personal app data from this run.
- Confirm Ctrl+F focuses Search, Ctrl+C/V copy/paste within Search, Escape clears it, and Ctrl+Shift+O opens the selected download's containing folder.
- Drop two small text files containing different downloadable URLs onto the main window; ensure both distinct links appear in the existing Add flow. Drop a directory/large file separately to confirm it is skipped.
- Test 125%, 150%, 200% Windows display scales, long bilingual filenames/URLs, light/dark themes, and keyboard/context interactions.
- Verify normal transfer pause/resume/cancel and settings against a test file; do not use important unfinished personal downloads as the first experiment.

## Remaining hard blockers
- Original `IDM_Repair_Handoff_2026-10-21.zip` / P01–P27 screenshot reference is not present in accessible project sources; no assertion of pixel-exact IDM parity is possible.
- Full 96 visual theme/icon-size combinations, wide DPI/RTL coverage, long-run performance under thousands of downloads and comprehensive original roadmap P3/P5/P6/P7 still open.
- Do not merge main or PR #3 as a consequence of this test pass. PR #4 remains Draft until broader product acceptance.
