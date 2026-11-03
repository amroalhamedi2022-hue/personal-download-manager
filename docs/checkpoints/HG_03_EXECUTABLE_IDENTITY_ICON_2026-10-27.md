# HG-03 / HG-04 — Windows launcher identity and icon migration

Date: 2026-10-27  
Status at implementation commit: **SOURCE PATCH PREPARED; WINDOWS ACCEPTANCE PENDING**  
Pre-change PR head: 12766f448cf351ab82e97af20c63ae64fee00b5c

## Functional milestone

Make the installed Windows GUI launcher and product icon visibly HyperGet while preserving the established internal identity needed by upgrades and browser IPC.

- Windows app-image directory and main GUI launcher become HyperGet/HyperGet.exe.
- The setup executable is HyperGetSetup.exe so NSIS process cleanup cannot kill the installer when it closes the GUI.
- Stable identifiers remain unchanged: Gradle root project / NSIS APP_NAME / install path / HKCU registry keys / data directory / CLI name / native-messaging host name / extension IDs and framing.
- Installer cleanup removes both the new and legacy GUI filenames, and cross-version tests assert that only the expected one exists.
- Start Menu and Desktop shortcut targets are inspected, not merely checked for existence.
- A new local SVG source and six-size alpha ICO (256, 128, 64, 48, 32, 16) replace the Windows icon. No external image or runtime dependency is introduced.

## Pre-build question and expected result

**Question:** Does the real Windows jpackage + NSIS output use HyperGet.exe as the only GUI launcher, keep the CLI/native host discoverable, and survive baseline → candidate upgrade → candidate uninstall → baseline rollback without losing data, credentials, shortcuts, or browser native-messaging registration?

**Expected:** source contracts pass before packaging; candidate installer and installed GUI have distinct safe names; baseline uses DownloadManager.exe while candidate uses HyperGet.exe; no installation contains both GUI names; both branded shortcuts target HyperGet.exe; CLI/native host names and HKCU manifest paths remain stable; the real HTTP fixture completes with matching SHA-256; user data and synthetic WinCred survive the transition; icon parser confirms all six alpha sizes.

**Why source tests alone are insufficient:** jpackage derives the main launcher from nativeDistributions.packageName, NSIS expands template parameters and runs process/shortcut/registry cleanup, and the browser native host reads the installed helper path. Those behaviors require a real Windows build and installed-process tests.

## Required evidence

- [ ] Source-only Python contracts: icon structure, Gradle/NSIS naming, stable IDs, and resolver rules.
- [ ] Windows GUI smoke: build, install, launch, screenshot, real Liquid Progress transfer, reinstall and uninstall.
- [ ] Cross-version Windows acceptance: baseline → HyperGet → candidate uninstall → baseline rollback; verify GUI name, shortcut targets, real HTTP SHA-256, settings/history/user sentinel and WinCred.
- [ ] Installed Chrome + Firefox native-host E2E: manifests still point to DownloadManagerNativeMessagingHost.exe and real extension requests complete.
- [ ] Inspect icon at native 256/128/64/48/32/16 entries; verify transparent corners/no white surround.
- [ ] Record exact candidate SHA, installer SHA-256, proof artifact SHA-256, logs, and all failures/skips after workflows finish.

## Explicit non-claims

- Performance was not changed intentionally by this branding/packaging milestone. E061 remains accepted only at its tested source c99b6f627de5f1ef1cba9e79f4f8e1baba20ffe1 (Run 40494020066); do not treat that as a new benchmark for this candidate.
- The previous PR head 12766f448cf351ab82e97af20c63ae64fee00b5c had successful workflow summaries with expensive jobs scope-skipped on its docs-only commit. Those are not fresh Windows/browser acceptance for the launcher migration.
- No auto-merge, main-branch write, release, paid service/runner, browser-repository write, or Torrent feature.
