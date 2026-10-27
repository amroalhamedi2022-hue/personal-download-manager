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
