# IDM / Personal Download Manager — Chat → Work Handoff
**Date:** 2026-10-23 (Asia/Hebron)  
**Repository:** https://github.com/amroalhamedi2022-hue/personal-download-manager  
**Working branch (DO NOT edit main by default):** `codex/repair-rf00-rf01`  
**Last verified branch HEAD at handoff:** `51a465881dd494200d6ecd1f0ca71d0d4ceeb767`  
**Repository visibility:** public, with write access confirmed through the connected GitHub app.  
**Authoritative execution plan:** `docs/Download_Manager_Execution_Plan_v1.md`  
**M09 acceptance protocol:** `docs/M09_Windows_Active_Resource_Acceptance.md`

## Mission and working rules

Continue building the **existing Windows IDM-like personal-download-manager**, not a replacement or greenfield prototype. Follow the existing plan's approved requirements, aim for high-fidelity IDM behavior and the approved Liquid-style progress window, and deliver one verified Windows EXE only when acceptance gates actually pass.

The user wants large, precise implementation batches, code/commits and tests, concise post-batch progress reports showing **implemented / verified / remaining**, and no invented build success, throughput figures or QA results. Research trustworthy maintained open-source implementations before reinventing substantial components; vet code quality, licensing, maintenance and security before reusing. **Torrent was explicitly cancelled—do not implement, restore or plan it.**

If Work credits run out, keep all source changes pushed to this same branch and update this handoff/checkpoint file with exact SHA, successful and failed run URLs, test outcomes, next action and blockers so development can resume in an ordinary Chat conversation without repeating work. Never leave important code only in ephemeral Work state.

## Most urgent defect — CURRENT HEAD IS NOT GREEN

**Run [39921472032](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39921472032)** failed on source `51a4658`. The previous **[39920627092](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39920627092)** also failed on `ea8924`.

Windows `:desktop:app:compileKotlin` compiler errors:

1. `desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/cli/download/add/shared/BaseNewDownload.kt` approximately lines 63–64: `convert(::parseDownloadHeaderOption)` is incompatible with the installed Clikt API, whose overload expects a conversion lambda/explicit `conversion` parameter. Fix preserving the new parser semantics; run all seven `HttpHeaderOptionTest` tests and desktop compile. Parser source is `shared/app/src/commonMain/kotlin/com/abdownloadmanager/shared/util/HttpHeaderOption.kt`.
2. The new persistent concurrency IPC functions in `desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/utils/singleInstance/service/IDefaultAppIPCService.kt`: `getMaxConcurrentDownloads()` and `setMaxConcurrentDownloads(value)` were declared at HEAD `51a4658` but **`DefaultAppIPCServiceImpl` does not implement them**. Implement using the **existing persisted app settings** `maxConcurrentDownloads` and their live backing flow; validate supported values, call `awaitAppBoot()`, do not create independent global state, and do not leak settings into the remote browser integration. If appropriate add a safe CLI control for the benchmark to set/restore the value.

Fix these **before** considering the newest source green. Run Windows validation with JUnit XML checks and release packaging. Do not confuse historical green builds from older SHAs with current HEAD.

## Real observed active-benchmark defect

The installed Windows `windows-installed-active-benchmark.yml` run **[39919998042](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39919998042)** on `6e9f98c9` **FAILED** at workload `active-4`: only **3/4 tasks were actually transferring simultaneously**. Its `active-1` rounds all completed and SHA-verified successfully. This is probably explained by the **legitimate default setting 3**: `shared/app/src/commonMain/kotlin/com/abdownloadmanager/shared/storage/appsettings/DefaultAppSettings.kt` defines `maxConcurrentDownloads: Int = 3`, respected by `ManualDownloadQueue` and `BaseAppRepository`. Do NOT misrepresent this as 4 active downloads or remove the user's concurrency limit. For a reproducible 4/16 benchmark, set the **existing persistent concurrent-download setting to 16 in the isolated Windows CI profile** through proper IPC or CLI, verify it was applied, then measure actual sockets; restore the original setting, avoiding changes to normal user defaults. Benchmark's local file is `scripts/Run-WindowsActiveBenchmarkMatrix.ps1`; its test server `scripts/LocalHttpRangeFixture.py` has a `/stats` endpoint measuring **distinct in-flight download task IDs**.

The failed run proves strict test gating is catching actual mismatches. Do not weaken `peak_distinct_tasks`/SHA-256 assertions to manufacture success.

## Verified passing tests with exact source references

- **[39918464707](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39918464707)** on `a37ed526`: **150 JUnit tests, 17 suites, zero fail/error/skipped**, desktop Kotlin compilation, exactly **one Windows NSIS EXE** successfully packaged. EXE SHA256: `5bf05216c917c590f7b282fbe60db5cd53166b019924887341851c06216d7867`. **This is historical passing source, not current HEAD.**
- **[39918864186](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39918864186)** on `78806831`: real **installed Windows** GUI+CLI HTTP transfer and **Pause → socket shutdown / bytes stop → Resume → 32 MiB exact SHA256**, including post-completion hash stability. NOT a GUI mouse-click test.
- **[39919442198](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39919442198)** on `81a26d8b`: isolated NSIS silent install with no unintended GUI autostart; GUI screenshot; same-version silent reinstall and uninstall, launcher and registry cleanup. **Not cross-version upgrade acceptance**.
- **[39919286369](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39919286369)**: 3 real Python HTTP fixture socket tests + PowerShell AST syntax checks passed.
- **[39918552742](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39918552742)**: 3 × warm idle and 3 × minimized idle Windows process-tree benchmarks, 120 total sample rows. Approximately **217.6 MiB working set / 241.3 MiB private bytes / 0.032% machine CPU** warm idle, and **219.0 MiB / 240.9 MiB / 0.032%** minimized. This is CI test hardware only, not a Windows PC guarantee, not during downloads.
- [39919998058](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39919998058): fixture contract passed on `6e9f98c9`.

## Latest added code worth preserving

- Proper HTTP CLI header parser `HttpHeaderOption.kt` preserving `Authorization: Bearer abc:def=ghi`, cookies, signed CDN URLs, and rejecting CR/LF injection, plus 7 unit tests in `HttpHeaderOptionTest.kt`. Desktop Clikt call-site needs compile fix (above).
- New persistent concurrency IPC interface methods at `51a4658` are **incomplete until implemented** (above).
- `scripts/Run-WindowsActiveBenchmarkMatrix.ps1` tests 1/4/16 real in-flight HTTP tasks × 3 rounds, 63 32-MiB SHA-verified files plus process-tree samples; reports generated by `scripts/Summarize-WindowsActiveBenchmark.ps1`. Needs concurrency-setting enablement to run >3 simultaneously.
- `scripts/Run-WindowsInstalledResumeE2E.ps1`: Windows socket-level pause/resume acceptance (passed).
- `scripts/Run-WindowsInstallerLifecycleE2E.ps1`: isolated same-version reinstall and uninstall (passed).
- `desktop/app/resources/installer/nsis-script-template.nsi`: corrected silent /S installs to not auto-launch GUI; interactive finish-page launch preserved (passed).
- FTP support integrated into GUI/engine with Apache Commons Net, resume identity, credentials in Windows Credential Manager and unit/loopback test coverage; **external LAN/WAN and faithful GUI acceptance remain open**.

## Remaining workstreams (follow original plan, do not overclaim)

1. **Immediately restore green Windows source**: fix Clikt `convert` compile and IPC interface implementation; add meaningful tests, stage commits, get passing 157+ unit tests as appropriate, desktop Kotlin and packaging.
2. Fix/complete **1/4/16 simultaneous workload benchmark** while respecting normal max-concurrent setting. Run nine actual Windows resource measurement rounds and inspect output artifacts, hash manifest and report.
3. **FTP M04**: externally accessible FTP LAN/WAN real-world tests, GUI Add URL/OK button, error feedback, pause/resume, file name with spaces/brackets, credential vault, no secrets in log. No false claim based only on localhost.
4. **Visual/UI M06–M08**: compare approved `idm_liquid_approved.png` and `idm_reference_audit_contact.png` screenshot reference files provided in user's earlier chats (not necessarily tracked in Git). Keep Liquid Progress fidelity, chart speed graph, window layout, buttons and settings; do not substitute aesthetic mockups for app code.
5. **M09**: measured active resource usage and targeted optimization only after a verified baseline. No speculative RAM/CPU savings.
6. **M10**: IDM-style browser integration, online file types/media capture, headers, signed links, and real-world HTTP/HTTPS testing.
7. **M11**: true cross-version upgrade, uninstall retention policy, user-data safety, one EXE package, regression and final Windows acceptance.

Additional acceptance and scope: Windows app only; general HTTP/HTTPS/HLS/FTP, browser integration; **no Torrent**. A successful CI packaging artifact is not automatically a final user-facing release.

## Work → Chat fallback requirements

Before leaving any Work session, especially when near credits limits:
- Commit and push ALL code and tests to `codex/repair-rf00-rf01` (or explicitly name another feature branch / PR).
- Update `docs/WORK_HANDOFF_IDM_2026-10-23.md` (or a date-stamped successor) with exact latest SHA, work done, commands executed, green and red Actions run IDs + failure excerpts, unverified claims, pending queue and the first next action.
- Give the user a concise Arabic status report and link the latest handoff. They can paste it into normal Chat to continue, without losing source state.

## 2026-10-23 — Automated source-state reconciliation (NOT a new CI pass)

The earlier handoff above describes the historical failing commit `51a4658`. It is **not** the current source state on `codex/repair-rf00-rf01`. A read-only comparison of `51a465881dd494200d6ecd1f0ca71d0d4ceeb767` against branch HEAD `8ca703673339c06a0ae34329bc8d23cb885e1b61` showed **5 additional commits** and production-code changes. Verified by fetching the files on that branch:

- `BaseNewDownload.kt` now uses `.convert(conversion = { raw -> parseDownloadHeaderOption(raw) })`, addressing the original Clikt call-site compile complaint **in source**.
- `DefaultAppIPCServiceImpl.kt` now implements both `getMaxConcurrentDownloads()` and `setMaxConcurrentDownloads(value)`, waits for app boot, uses `appComponent.appSettings.maxConcurrentDownloads.value` and calls `requireSupportedMaxConcurrentDownloads`. The corresponding settings CLI and tests were added in this branch.
- `scripts/Run-WindowsInstalledFtpE2E.ps1`, `scripts/LocalFtpFixture.py`, `scripts/test_local_ftp_fixture.py` and FTP-related workflow code were added or modified. This is evidence of source implementation, **not** evidence that Windows FTP end-to-end, external LAN/WAN or real GUI acceptance passed.
- The last explicitly inspected historical failed Windows run is [39921472032](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39921472032) on `51a4658`, which is **older** than these source changes. Do not cite that failure as proof the new branch is broken, nor claim the new branch green until its own Windows validation, 1/4/16 active benchmarks, FTP tests and JUnit/artifact checks have completed successfully.

**Next step:** Check for CI results on the **current branch SHA**, not only main or the older run. If none, request one authorized Windows CI run. Fix only failures proven on the new SHA. Keep 1/4/16 active task and SHA256 assertions strict; do not alter the legitimate default concurrency value merely to pass benchmarks. Reconcile this dated appendix into the main handoff after review. This appendix does not modify production code or claim any build/test execution.
