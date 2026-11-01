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

## 2026-11-02 — HyperGet rebrand and aria2 actual integration status

- Latest branding **code** commit on main: `c5aab783380d2e9e4b6834f6ce639e165fd2fb06`. The main branch has subsequent documentation/Android-label commit `1b503101f71a83ce2b4bb4b274abc25a8c6ea6f8`.
- User-visible Windows name: **HyperGet**. Main GUI `HyperGet.exe`, custom NSIS `HyperGetSetup.exe`, version metadata, shortcuts, Start Menu and Add/Remove Programs display use HyperGet; Windows icon assets are ported from tested experimental work. `README.md` and Android labels also now show HyperGet (Android APK verification not performed in this Windows pass).
- Compatibility: do **not** rename the `rootProject.name`, old app-data path `.personal-download-manager`, Java/Kotlin package IDs, existing registry uninstall key and native host / CLI executable identities while legacy browser and upgrade integrations depend on them. The NSIS updater removes only known obsolete GUI shortcuts and the old GUI executable, keeps persistence intact, and chooses the new GUI launcher. Actual cross-version upgrade and rollback with the main branded installer remain **NOT TESTED** until an exact-head upgrade run.
- Exact branded Windows build [41355550914](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41355550914) was **IN PROGRESS** while writing. Do NOT claim a new validated EXE until this run is green including real branded silent install/uninstall. Separate older green main run [41353394844](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41353394844) verifies pre-rename code and has `DownloadManager` legacy installer identity; do not give it as the new branded installer.
- aria2 **has NOT been integrated into production main's active DownloadJob/UI/download-engine routing** and is not bundled as aria2c.exe. Experimental source under `downloader/core/src/desktopMain/kotlin/ir/amirab/downloader/aria2/` includes process/RPC control, binding store, pause/unpause and progress tests, with optional feature flag off. Exact Windows/Ubuntu adapter validation [40972502773](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40972502773) **SUCCESS** on its experimental source, Stage 2 paired throughput gate [40833072288](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40833072288) **SUCCESS**. Tested medians: ~+10.24% at 1, +9.48% at 4, +54.19% at 16 tasks on specified 32 MiB fixture; not general performance guarantees. The user's 2026-10-29 explicit decision **retains aria2 BitTorrent capability** in the prospective adapter (supersedes older no-Torrent notes); that capability is not active in main or included in current build.
- Future aria2 work: connect accepted adapter to dedicated desktop `DownloadJob` behind disabled-by-default switch, preserve old downloader fallback, verify resume/cancel/partial file identity, destination collision handling, mixed HTTP/HLS/FTP, browser/native integration, CPU/RAM and upgrade/rollback. **No automatic engine switch, no production aria2 packaging yet.**
- Roadmap: real Explorer drop, Windows 125/150/200 DPI, long RTL screenshots, 1k/10k live concurrent transfers, browser upgrade integration and P3–P8 still require evidence.

