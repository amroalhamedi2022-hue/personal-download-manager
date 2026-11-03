# HG-BROWSER-GUI-WARMUP-01 — make installed Chrome/Firefox acceptance reach the browser APIs (2026-10-25)

## Failure evidence
- Exact source SHA: `5104b1da9743eea660161c85b159d0fb36f833af`.
- Run [40238268700](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40238268700) **FAIL** at step 15, before actual Chrome nativeMessaging calls and before Firefox was started.
- Node syntax checks, eight Firefox source contracts, NSIS build/install, installed native-host protocol acceptance and Chrome/Firefox extension packaging all **PASS**.
- Test installer SHA-256: `df3084e4267c9ce7042ab83453aac39ba052ed636c7a4296ab53e7698cf6e933`. Evidence artifact [12616365138](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40238268700/artifacts/12616365138), archive SHA-256 `bf5cf66e87de91ad6843002448291275170061ce3ebc47f00b8c282c2021e098`.
- The failure was `Installed HyperGet GUI/IPC readiness check failed`; the previous harness discarded the distinction between spawn error, timeout, non-zero exit and unexpected stdout. Root cause was **not proven**, and Firefox diagnostics from the preceding commit were **not exercised**.

## Change
- Start the app explicitly with the installed CLI's existing `gui start-if-not-started` command, then perform the existing read-only `download show --status-lines <nonexistent ID>` IPC probe before any browser-origin add.
- On either warmup failure, write a small JSON artifact containing only fixed stage, safe error class, numeric exit code and booleans for output presence. Never persist raw stdout/stderr, paths, URLs, headers or exception text.
- Add source regression checks for ordering and sanitized failure evidence.
- Add a latest-commit scope gate to the installed HTTP benchmark and disable cancellation of a running evidence round. A browser-only commit will no longer launch another 63-download matrix or cancel the currently useful run.

## Purposeful next Windows build question
**Question:** Does the installed CLI's explicit start command make GUI IPC ready, allowing real Chrome capture and then the pending Firefox WebDriver diagnostic run to proceed?
**Expected:** source/Node checks pass; the next exact-head NSIS install succeeds; GUI start and status-only IPC probe pass; Chrome real extension completes the 2 MiB SHA-verified download; then Firefox either completes its own SHA gate or produces stage/status/allowlisted-error JSON.
**Why source checks are insufficient:** only installed Windows execution can establish the CLI start command, named-pipe/IPC readiness, Chrome origin/host policy, Firefox WebDriver session/add-on installation and actual downloaded bytes.

## Current acceptance state
- HTTP 1/4/16: **PASS** on prior exact head (63/63 hashes, 9/9 CPU/RAM samples); one newer repeat remains in progress from before this follow-up.
- FTP installed pause/restart/REST/SHA: **PASS** on prior head.
- Chrome real download: **PASS on earlier head**, but current exact-head retest blocked at GUI/IPC preflight.
- Firefox real download: **NOT TESTED on the diagnostic head** because Chrome preflight failed first.
- Refresh URL, scheduler, HTTPS remote, installed HLS process-kill/relaunch, Liquid Progress/visual match, safe HyperGet rename/upgrade, independent Windows release check and final single `HyperGet.exe`: **NOT TESTED / INCOMPLETE**.
- No main write, merge, release, paid service or Torrent.
