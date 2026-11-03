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

## Exact-head Windows result — PASS

**Candidate source:** `068e2ff9b66d47764d4d76bae59361a1cf983c9d`.  
**Cross-version Windows run:** [40528393289](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40528393289) — source contracts, PowerShell parsing, both installer builds, real transfer, upgrade, candidate uninstall, rollback and normal uninstall **PASS**.  
**Proof artifact:** [12744944789](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40528393289/artifacts/12744944789), SHA-256 `10672fded4edc52c3911551aa12b0b47d6145ee305db44b096fed3096d8ec2a4`; downloaded and independently rehashed to the same value.  
**Installer SHA-256:** baseline `06f562cb2c6558a729f2a34718a8e7c9a233052487d5a1d801862b555a2f1a5d`; candidate `8908a03c81c04896b1aaf693899bc69d4930c421a9c6259aaae3eb5f69f8415c`.  
**Real download:** 8,388,608 bytes; expected and actual SHA-256 both `7d212b9c884f5c77896de960ae17cc341cda43b14d6a971f34ca29ebd4badf7f`.  
**Unrelated Start Menu sentinel:** SHA-256 `90bf149b878c69fa46b07c4c2e5f2bba7619a9f644b9030dce966ba3c27b3ca9`, preserved through pre-upgrade, HyperGet install, candidate uninstall, rollback and normal uninstall. Logs confirm it was removed only afterward when its content/hash still matched the test-owned marker; its folder was removed non-recursively only when empty. Registry/product shortcuts were absent before harness cleanup. Settings/history, user data and synthetic WinCred remained valid; WinCred test entry was then deleted and absence verified.

**Installed UI/Liquid Progress:** [run 40528393112](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40528393112) **PASS** on the same source. Test installer SHA-256 `310692913f1afb2873709d87d214fd12f16f19072fb2682d1d16bfc5eeade47b`; active visual ZIP [12745446957](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40528393112/artifacts/12745446957), SHA-256 `d964b753cf1c05b6a8675af85a2fad412fb8c6496e25caa807bac05fc2f2cc90`. Independently downloaded and rehashed. At capture the report showed `Downloading`, one active task and advancing bytes at 5,308,672/16,777,216 (31.6%); final 16-MiB output matched fixture SHA-256 `341aacac661ccb210720bedaa9ead5d668fe5ea41a73532fc147c71e34040df1`. Same-version reinstall, silent uninstall and registry cleanup passed; silent install did not auto-launch.

**Additional exact-head CI:** source-only [40528393345](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40528393345) PASS; HLS [40528393465](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40528393465) 29/29 PASS; FTP/WinCred [40528393231](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40528393231) 29/29 PASS.

**Milestone status: PASS.** The final user-facing `HyperGet.exe`, final Liquid Glass icon, current-head browser/throughput reruns (scope-skipped here), and independent final release recheck remain **OPEN / NOT COMPLETE**. The test installers are not the final deliverable. No merge, release, main write, paid service or Torrent.
