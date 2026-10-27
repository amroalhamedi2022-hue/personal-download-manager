# HG-UNINSTALL-USER-CONTENT-SAFETY — preserve unrelated Start Menu files

Date: 2026-10-27  
PR: [#3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3)  
Branch: `automation/idm-ftp-cancel-and-category-flush`  
Base: `codex/repair-rf00-rf01`

## Previous milestone continuity

- E061 cap-60 performance comparison **PASS**: [run 40494020066](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40494020066), 126/126 SHA-verified outputs, nine resource rounds per version, real 1/4/16 overlap, all unchanged 15% throughput/peak-memory gates passed.
- Cross-version upgrade/rollback and normal-uninstall runtime proof **PASS**: [run 40519751066](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40519751066), candidate production SHA `fbc5b695253a9ecd3d9a8eef357ad28f17f0b179`, artifact [12741747280](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40519751066/artifacts/12741747280), digest `9e3d4660ce9099fe5077f923dc78ec2644d07ee0b74281189c57a5ab3b56a1ba`.
- That runtime proof covered app data/history/settings/WinCred and product registry/shortcut removal, but did not place unrelated content inside the Start Menu product folder.

## Finding

The NSIS `RemoveStartMenu` macro used `RMDir /r` on `$SMPROGRAMS\HyperGet`. The official [NSIS RMDir reference](https://nsis.sourceforge.io/Reference/RMDir) states that `/r` recursively removes all files/subdirectories; plain `RMDir` removes only an empty directory. This can delete unrelated files a user puts in the product folder.

## Production and regression change

- NSIS now deletes only `$SMPROGRAMS\${APP_DISPLAY_NAME}\${APP_DISPLAY_NAME}.lnk`, then attempts non-recursive `RMDir`.
- The cross-version Windows harness refuses to use a pre-existing HyperGet Start Menu folder, creates a unique sentinel file, and checks exact content plus SHA-256 after candidate installation, candidate uninstall, rollback, and normal baseline uninstall.
- Test cleanup removes the sentinel only after exact content and SHA-256 verification, then removes the folder without recursion only if it is empty. Unexpected/changed files are preserved.
- Source regressions guard both the NSIS macro and runtime-harness assertions.

## Pre-build acceptance question

Does the actual NSIS installer/uninstaller preserve unrelated content in the HyperGet Start Menu folder through upgrade, candidate uninstall, rollback and normal uninstall, while removing product shortcuts and registry entries and preserving download/history/settings/user-data/synthetic WinCred?

**Expected:** source contracts and PowerShell parser PASS before builds; exact baseline/candidate Windows installers build; real 8-MiB HTTP transfer matches fixture SHA-256; full upgrade → candidate uninstall → rollback → normal uninstall completes; sentinel hash survives each transition; registry keys and product shortcuts are absent immediately after uninstaller exit and before any harness cleanup.

**Why source tests are insufficient:** NSIS self-removal, per-user shell folder context, shortcut files and HKCU registry state are OS-level runtime behaviors. One targeted Windows cross-version build/run is required; this is not a release build and does not publish an EXE.

## Status before this run's Windows result

**OPEN — pending exact-head Windows cross-version run.** The previous run [40521425980](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40521425980) only reported success because the expensive transfer matrix was scope-skipped on a docs-only commit; it is not new runtime proof. No merge, main write, auto-merge, paid service, public release or Torrent.
