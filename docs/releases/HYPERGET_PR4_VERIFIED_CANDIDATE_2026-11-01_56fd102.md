# HyperGet — Verified Windows development candidate (2026-11-01)

## Exact package identity
- Repository: `amroalhamedi2022-hue/personal-download-manager`
- Working branch: `experiment/liquid-reference-match-20261030` (Draft PR #4, base `experiment/jules-liquid-progress-20261030`, **do not edit/merge main or PR #3**)
- Verified application code commit: **`56fd102309d95abe1cabccdfe1d265f3c4c64c03`**
- GitHub Actions: [run 41340178985](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41340178985) — **SUCCESS**
- Download: [single verified EXE artifact 13096319578](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41340178985/artifacts/13096319578), retained by GitHub Actions for 7 days.
- Windows x64 installer: `HyperGet_1.0.0-sha.g56fd102_windows_x64.exe`, **79,934,345 bytes**; **SHA-256: `9c3a9414a39dd6db1f9744d9bd1ceadc09a76815c46552472cdc085c60fb6610`**.
- Also delivered to ChatGPT as a direct executable attachment and one-executable ZIP.
- Status: **verified development candidate, not final IDM or full roadmap acceptance**. Verify the SHA-256 before installation.

## Changes in this iteration
- Limit the **number of examined dropped files** to 16, including invalid/missing/duplicate file paths, instead of only limiting successfully imported files. Continue enforcing 1 MiB aggregate raw input.
- Import text saved in Windows Notepad's BOM UTF-16LE/UTF-16BE and UTF-8 BOM encodings; reject malformed UTF-16 and embedded NUL binary content.
- Preserve embedded Windows Shortcut syntax `URL=https://...` when the same link-list also contains standalone URLs. Keep original valid whole FTP URLs, including spaces in filenames, deduplicating results in input order.
- Kotlin Desktop tests read real temporary UTF-16 `.url` and UTF-8 `.txt` files, parse their combined content, and assert two unique URLs are retained. Other tests cover invalid path floods, BOM variants, corrupt UTF-16, FTP credentials, duplicate links, and fallback embedded URLs.

## Exact-source automated acceptance
- Source tests and all configured Kotlin Desktop test suites passed.
- One Windows NSIS EXE generated, installed silently into isolated directory, application GUI launched and screenshot recorded.
- Real 16 MiB loopback HTTP transfer: `ExpectedBytes = ActualBytes = 16,777,216`, SHA-256 output **`341aacac661ccb210720bedaa9ead5d668fe5ea41a73532fc147c71e34040df1`**, `Verified=true`, `FinalStatus=Finished`. Actual downloaded payload independently rehashed after artifact extraction.
- Actual installed Windows UI: source-URL search retains active row, Columns popover opens, chart graph hides/restores.
- Real per-download completion preference disk write, process restart and reload, and NSIS reinstall/uninstall passed.
- Evidence: [visual artifact 13096539064](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41340178985/artifacts/13096539064); Windows GUI screenshot artifact 13096334740; installer lifecycle artifact 13097500081.

## Suggested manual checks before updating your main installation
1. Back up personal HyperGet queue/data. The CI used a fresh isolated install; do not assume arbitrary older installations upgrade perfectly.
2. Drag two small text link files onto HyperGet (a Windows UTF-16 `.url` file and an ordinary `.txt` file), check both URLs appear in the Add flow. Drop a directory or large file and verify safely ignored.
3. Confirm Ctrl+F/Search, Escape clear, Ctrl+C/V inside search, and Ctrl+Shift+O for containing folder.
4. Verify normal pause/resume/cancel, downloads list layout, light/dark UI, mixed Arabic/English URLs, 125/150/200% Windows scale, and settings across restart using nonessential test downloads.

## Remaining blockers (do not mark complete)
- Physical mouse multi-file drag/drop interaction and clipboard keyboard acceptance on a user's PC.
- Historical IDM comparison ZIP `IDM_Repair_Handoff_2026-10-21.zip` / reference images P01–P27 unavailable; exact original-IDM layout parity cannot be asserted.
- Full DPI/RTL matrix, 96 theme/icon/size combinations, long-run thousand-item performance, comprehensive P3/P5/P6/P7/P8 acceptance and final human review.
- PR #4 remains Draft; this test pass does **not** authorize merging code into main.
