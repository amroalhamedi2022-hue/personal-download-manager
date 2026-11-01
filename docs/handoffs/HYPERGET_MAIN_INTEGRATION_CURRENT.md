# HyperGet main — authoritative integration checkpoint (2026-11-01)

## Current actual code on main
- Main CODE SHA: `22651fd12f87f6eb0a6ebc51732b09bb55a67375` (followed only by documentation-only commits, if any).
- Main integration commits: `0ce216c`, `fe658cd`, `22651fd`.
- Latest exact-SHA Windows CI: [41353394844](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41353394844), **SUCCESS** on exact code SHA after Kotlin tests, compile, Windows NSIS and safe silent installer lifecycle. Verified artifact [13102642061](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41353394844/artifacts/13102642061): `DownloadManager_1.0.0-main-snapshot_windows_x64.exe` (79,589,979 bytes), SHA-256 `7a865244d62892a54629c65c4e39b847ebbcc970259e9371567695ca1d0980fd`. *This is the main snapshot with legacy installer naming, not the separate HyperGet-branded experimental installer.* Physical mouse-drop/RTL/DPI/live-10k still unaccepted.
- Older main exact-SHA [41352081893](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41352081893) was **SUCCESS** on `0ce216c`; the middle `fe658cd` run [41352911911](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41352911911) was **CANCELLED at packaging by the next main push**. Not a failing test; not a full pass either.
- Main's original HEAD `8fb64bc` included important newer HTTP/HLS/cancel/security/core recovery work that the 933-commit divergent PR #4 lacked. Those original main commits were **preserved**, not overwritten.

## Implemented features
1. Unicode/Arabic URL list extraction, mixed direct/Windows shortcut links, full query parameters; 10k parser unit test.
2. Home search matches filename and original URL; 10k visible/selected-state optimisations, keeping selection order.
3. Ctrl+F focuses Home Search; Ctrl+C/V when focused edit text instead of triggering global download actions; Ctrl+Shift+O opens selected folder.
4. Strict, bounded and multi-file text/Internet Shortcut import, including UTF-16 BOM and malformed-encoding rejection; reads only at final drop event.
5. Main table Columns button, persisted column size/sort/order/visibility with corruption-safety guards and Kotlin tests; selection membership uses one hash set.

## Still pending in priority order
1. Check the exact result and test reports for main run 41353394844; repair any failure by its logs, without repeating finished work. Produce installed EXE from green run.
2. Prove real native Explorer two-file drag and two distinct URLs populated in the Add multi-download UI. Experimental diagnostic under PR #4 was inconclusive (OLE effect missing even when source window shown). Never label this accepted based solely on an overall green workflow with `continue-on-error`.
3. Windows 125%, 150%, 200% scale and RTL/long URL installed-app visual evidence; original IDM P01–P27 archive currently missing.
4. P8-06 real 1k/10k live GUI/download/queue workload and bounded resource/latency, in addition to existing in-memory 10k unit tests.
5. Continue actual P3–P8 functional work systematically, including P3-07 settings backup/export, Tray, P4 browser extension channel, P5 link collector, P6 source/host handling, P7 video/archive workflows and P8 installer upgrade/rollback/security, without claiming incomplete items done.

## Branch/concurrency
Main integration was explicitly authorised. PR #4 is a separate Draft experimental UI/visual branch; PR #3 contains overlapping/diverged history and cannot be blindly merged over main. Avoid duplicate commits from Codex. Check fresh HEAD and test status before modifying any ref.
