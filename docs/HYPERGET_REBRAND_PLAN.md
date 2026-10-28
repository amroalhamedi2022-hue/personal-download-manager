# HyperGet — Approved Rebrand and Non-Breaking Upgrade Plan

**Decision:** On 2026-10-23 the user approved **HyperGet** as the sole new product name for their Windows IDM-like download manager. Use exactly **HyperGet**, no space. **PLANNED ONLY:** this document does not itself rename production code or deliver a new EXE.

**Repository identity:** Keep existing `amroalhamedi2022-hue/personal-download-manager` and branch `codex/repair-rf00-rf01`/existing PR #3. No repository rename or reset needed. Separate browser integration repo is `amroalhamedi2022-hue/personal-download-manager-browser-integration`.

## Naming policy (applies to EVERY future task and plan)
- Call the product **HyperGet** in every new roadmap item, UI description, checkpoint, PR, release artifact and scheduled automation output.
- References to "IDM" mean *the original Internet Download Manager product being used as a functional/visual comparison*, **not our app's brand**.
- Historical documents, identifiers and GitHub paths containing "IDM" or "Personal Download Manager" may be retained for continuity and traceability; don't blindly rename IDs/paths/references and break links.
- All previously approved Download Manager execution-plan stages, bugfixes and acceptance checks now apply to **HyperGet**, unchanged unless user explicitly rescinds them. **Torrent remains excluded.**
- For the Windows deliverable use `HyperGet.exe` (and a matching installer/app title when appropriate) only when the migration is safe, build-tested and backward compatible.

## HG-01 — UI and product copy
- Rename user-facing application title, main window, progress dialog, notifications, Settings/About, setup wizard, shortcuts, Start Menu and uninstall text to **HyperGet**.
- Preserve the approved Liquid Progress UI and Windows IDM-like feature expectations, but avoid copying third-party copyrighted branding, marks or icons.
- Verify UI strings through search/tests and Windows screenshots after code changes.

## HG-02 — Existing user data compatibility
- **Do NOT silently change the installed app identity**, internal package/group IDs, IPC names, registered handlers, config-file names, registry keys, scheduled task names, credential vault targets, auto-update IDs, download database paths or backup keys merely to make them look like HyperGet.
- Prefer display-name changes and a compatibility layer for legacy persistence. Any planned storage/identity migration must be idempotent, preserve download records/history/settings/categories/accounts/credentials safely and be covered by previous-version upgrade tests, rollback scenarios and uninstall behavior.
- Never print secrets in logs or backups. Never delete previous user data to accomplish rename.

## HG-03 — EXE/installer and extension integration
- Ensure any `HyperGet.exe` rename is reflected in installer shortcuts, app launch config, add/remove programs, startup task hooks, browser extension native-messaging hosts and protocol registrations **without breaking old installed builds or IPC**.
- Implement corresponding user-visible HyperGet branding in the **separate browser integration repository** on an isolated branch/PR. Do not cross-write from the main repository's task. Preserve extension IDs, host names, connection protocols and existing downloads where migration requires backward compatibility.
- Treat main-app and browser-extension compatibility tests as an integration release gate.

## HG-04 — Icon/visual identity
- Create a distinctive, modern HyperGet icon in the user's existing approved liquid-glass design quality, representing a fast/safe download rather than another project's identity. Do not silently reuse the original IDM logo or another private app asset.
- Deliver Windows icon resources and test them at small taskbar, desktop and installer sizes.

## HG-05 — Required verification
- All existing functional requirements remain mandatory: HTTP/HTTPS/HLS, FTP, browser capture, pause/resume, simultaneous 1/4/16 workloads, SHA-256, CPU/RAM, installer/update/uninstall, one EXE, visual parity and security.
- Check **clean installation and in-place upgrade** with old user data, browser integration, shortcuts, executable launch, file associations, settings/queues, WinCred persistence, restart/resume, backups and recovery.
- Status labels must be **NOT TESTED** until exact-SHA Windows CI plus appropriate manual tests pass. Existing historical evidence is not retroactive evidence for the rename.
- Deliver only after full independent double recheck, real user-facing `HyperGet.exe` package and factual release summary. Never disable the scheduled developer just for a rename document.

## Execution instruction
Priority: design/plan accepted now; execute during future bounded development rounds without interrupting current queue/FTP/build fixes. No paid API, no paid CI, no automatic merge or release, and no direct main write. Keep the established PR and handoff continuity. Update `docs/AUTONOMOUS_ACCEPTANCE.md` with HG-01 through HG-05 evidence as implementation proceeds.


## 2026-10-27 — verified partial status after Windows uninstall/Liquid Progress acceptance

- **HG-01 UI/product copy:** PARTIAL. Installed window title and installer/Add-Remove Programs identity show HyperGet; full product-copy audit remains open.
- **HG-02 data compatibility:** RUNTIME SUBSET PASS on [cross-version Windows run 40528393289](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40528393289): max-concurrency=4, completed task/history/output, user-data sentinel and synthetic WinCred survived upgrade → candidate uninstall → rollback → normal uninstall. This does not yet prove every browser, queue/resume and old-version migration case.
- **HG-03 executable/installer integration:** NOT COMPLETE. The installed GUI launcher remains `DownloadManager.exe`; the final user-facing `HyperGet.exe` has not been built/delivered. Preserve stable package/registry/data-directory/CLI/native-host identities unless an exact-head migration test proves compatibility.
- **HG-04 icon/visual identity:** PARTIAL. Installed Liquid Progress visual E2E passed on [run 40528393112](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40528393112); the current screenshot and transfer SHA are verified. Final Liquid Glass app/installer icon replacement and native-size icon inspection remain open.
- **HG-05 verification:** uninstall user-content safety and same-version reinstall/silent-uninstall are PASS on exact source `068e2ff9b66d47764d4d76bae59361a1cf983c9d`; E061 performance remains PASS on its exact tested source. Browser E2E and throughput matrix were scope-skipped on the installer-only commit, and independent final release recheck is NOT TESTED.

Next: implement the executable-name/icon migration as one compatibility-tested milestone, then perform the independent Windows release recheck. Do not deliver the test installers as `HyperGet.exe` and do not mark HG-01–HG-05 fully complete from partial evidence.


## 2026-10-27 — HG-03 packaging repair checkpoint

The prior checkpoint line saying the launcher still remains `DownloadManager.exe` is stale for the tested candidate: cross-version Windows harness [40528393289](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40528393289) at source `068e2ff9b66d47764d4d76bae59361a1cf983c9d` explicitly requires baseline `DownloadManager.exe` and candidate `HyperGet.exe`, and recorded PASS. This is a test-install proof, not the final deliverable.

The latest exact-head GUI packaging attempt [40545095153](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40545095153) compiled the app but failed in NSIS because `StrCmp /I` is invalid; NSIS `StrCmp` is case-insensitive by default. The PR now removes `/I` and adds a source regression. Current-head rebuild, installed icon/launcher inspection and independent final release recheck remain OPEN until the new Windows jobs finish. Existing `hyperget-icon.svg` and six-size alpha `icon.ico` pass the source contract; do not infer runtime icon acceptance from source alone.


## 2026-10-27 — HG-03 identity harness follow-up (exact source `b63114d0c2fee6e667175302c9bc16a5da414c61`)

### Actual Windows result

The corrected NSIS script compiled and the installer installed successfully in [Windows GUI smoke run 40546589588](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40546589588). The installed tree contained exactly `HyperGet.exe`, no `DownloadManager.exe`, and the stable `DownloadManagerCli.exe`; the installer SHA-256 was `bf9f01d881c03fd067944f8682ca203238fcb95d3b1b538d947f152694132b39`. The GUI became visible and produced screenshot artifact [12753841163](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40546589588/artifacts/12753841163), whose uploaded SHA-256 digest was `eb2cd4e4ea412bafda80bd8e4da6a99ed7585de9dba5b3018ba32e14fabfef26`.

The GUI smoke then **failed** at the idle CPU/RAM sample because its harness still searched for process `DownloadManager`; the actual renamed GUI process is `HyperGet`. The screenshot and install evidence show the app did launch; this is a stale acceptance-harness identity, not a compile/NSIS or download-engine failure. Liquid Progress and the same-version lifecycle steps were skipped after this failure and are not counted as new passes.

The separate real browser run [40546589599](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40546589599) **PASS** on the same exact source: real Chrome/Firefox extension packaging and native-messaging protocol, with Firefox capturing a real 2 MiB transfer through the installed HyperGet host and verifying SHA-256. Evidence artifact [12754145299](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40546589599/artifacts/12754145299), SHA-256 digest `28fe2b0a3ccbcde654dd0101e4061c9eee789a0d5804e09cfc646749e8ab7714`. The source-only contract run [40546589656](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40546589656) also passed; FTP and HLS JUnit gates [40546589446](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40546589446) and [40546589605](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40546589605) passed, as did queue lifecycle [40546589576](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40546589576). Those results are exact-head evidence for `b63114d0c2fee6e667175302c9bc16a5da414c61`, not a final release.

### Fix in this checkpoint

- Change idle sampling to target `HyperGet`.
- Make the 1/4/16 process sampler derive the root process name from its supplied GUI executable, while explicitly allowing only the legacy baseline `DownloadManager` and branded candidate `HyperGet`.
- Update installed active benchmark, JVM profile, HTTP E2E and authenticated FTP E2E to require exactly one `HyperGet.exe`, reject a leftover `DownloadManager.exe`, and retain `DownloadManagerCli.exe` / `DownloadManagerNativeMessagingHost.exe` names.
- Add PR path triggers and exact-PR-head checkout to installed HTTP E2E; make FTP scope gating rerun on installer/identity/workflow changes.
- Add a source contract for all these identity assumptions. The next Windows run must prove actual process sampling and transfers, not merely that the scripts mention the new name.

### Acceptance question before the next build

Does each installed Windows harness now discover the branded `HyperGet.exe`, sample the correct visible GUI process, preserve legacy CLI/native-host names, and complete real HTTP pause/resume, authenticated FTP REST resume, Liquid Progress, 1/4/16 throughput/memory sampling, and cross-version data/shortcut/WinCred checks?

**Why source tests are insufficient:** only the Windows installer and actual process tree can establish the launcher name, window process identity, installed shortcuts, real transfers and resource samples.

**Status:** identity-harness patch committed on the existing PR branch; exact-head reruns pending. Final independent release recheck and final `HyperGet.exe` artifact/SHA remain OPEN.


## 2026-10-27 — latest HG-03 verification checkpoint (OPEN)

The prior exact-head build proved the installer can build and launch HyperGet.exe, but its Liquid Progress harness still searched for DownloadManager; the source identity suite also exposed malformed CLI selectors in four Windows acceptance workflows. The next patch repairs these harnesses and ensures cross-version and installed Chrome/Firefox checks are triggered by the identity regression. This is test/acceptance infrastructure, not evidence of a new production-engine change. Current-head Windows runtime, final independent recheck and final HyperGet.exe SHA/artifact remain NOT COMPLETE until the new exact-head runs pass.


## 2026-10-27 — HG-03 exact-head pause/resume blocker and next acceptance

The candidate installer at parent SHA `4979ee80145d0f77ce646a6088c0f08cededc53f` installed the branded `HyperGet.exe` and completed a real 16 MiB HTTP transfer with matching SHA-256, but the subsequent installed HTTP Pause/Resume check failed to observe all response bodies closing. The separate FTP test reached a partial transfer, but a Windows sharing violation in the FTP fixture's stats-file replacement left its active counter stale; FTP cancellation is therefore inconclusive on that run.

The current repair carries `Call.cancel()` through the shared HTTP connection close path and hardens the FTP fixture's atomic stats publication with bounded retries. Do not mark HG-03 complete from source tests or the initial download alone. Require new exact-head installed HTTP Pause → stable stopped bytes → Resume SHA, FTP Pause → server-side data socket closure → GUI restart → REST resume/SHA, and then the independent release recheck. Final user-facing `HyperGet.exe` artifact and SHA remain OPEN.


## 2026-10-27 checkpoint — deterministic HTTP fixture readiness (OPEN)

On exact PR head 8e74203ded3ca0177238d0a8fe24a702f885d0fc, installed HTTP run [40571810012](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40571810012) built/installed but failed before transfer with url is not valid; the Pause/Resume assertions were skipped, so no HTTP pause result is claimed. The same-head installed FTP REST-resume/SHA/WinCred [40571810155](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40571810155), Chrome/Firefox capture [40571810035](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40571810035), GUI launch [40571810241](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40571810241), and cross-version upgrade/rollback [40571810080](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40571810080) passed.

The HTTP harnesses waited only for port-file existence, then parsed the content. A visible-but-empty/truncated port file could produce port 0 and an invalid URL. The failing log did not record the actual port, so this is a harness-race hypothesis until the guarded rerun confirms it—not a production HTTP engine defect.

The cohesive patch introduces scripts/Wait-WindowsLoopbackHttpPort.ps1 and wires it into installed HTTP/Pause-Resume, Liquid Progress, cross-version upgrade/rollback, and the 1/4/16 benchmark/memory-profile path. The waiter requires a live server and a complete decimal port in 1..65535; the inline HTTP workflow additionally validates a loopback URI. A Python source-contract test locks shared usage and workflow trigger/scope behavior.

**Pre-build question:** can the exact-head installed HTTP flow obtain a valid endpoint, verify the 16 MiB initial SHA, close all requests on Pause, keep bytes stable, and Resume to exact 32 MiB SHA? Source tests cannot establish file-publication timing, installed IPC or real sockets, so the exact-head Windows build/E2E is required. No performance thresholds are relaxed; current-head performance, independent release recheck, and final HyperGet.exe SHA/artifact remain OPEN.


## 2026-10-28 — HG-03/HG-04 current evidence after exact-head Windows acceptance

On tested source `e273a001d254125f1443a7d3ca92f9121c5dc13b`, installed GUI/CLI identity, Chrome and Firefox native-host downloads, cross-version upgrade/rollback, Liquid Progress, and embedded icon resources passed their current Windows acceptance workflows. The GUI launcher and setup installer each contain the six approved 32-bit-alpha icon sizes (16, 32, 48, 64, 128, 256 px); see [Windows installed GUI/icon audit run 40626221366](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40626221366). The paired exact-snapshot 1/4/16 performance and memory-regression matrix also passed at this source (run [40626221330](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40626221330)).

**HG-03/HG-04 are not a final release approval.** The current installer is a test build; a standalone user-facing `HyperGet.exe` with verified SHA/artifact and an independent final Windows recheck remain open. Installed HLS process-kill/relaunch and behavioral scheduled-start/stop acceptance are also open. Keep legacy CLI/native-host/data identities compatible; Torrent remains excluded.
