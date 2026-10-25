# HG-BROWSER-CHROME-04 — installed Chrome → native add → real HTTP bytes (2026-10-25)

## Scope and pre-build acceptance question

Previous exact-head `fffa67afe0b1a288b0682b33495199f9c72640eb` has PASS for source, Windows queue, FTP, HLS, installed HTTP 1/4/16, installed FTP and real Chrome MV3 native messaging workflow [40221694624](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40221694624). That Chrome test proved 3 one-shot responses, 3 connected-port responses and 1 reconnect response, but **did not send a download or verify a downloaded file**. The browser integration PR remains pinned at `0fb5a304cbde07a9758d2b42417e7a8a85cba0f2`, preserving its existing native host identity and Apache-2.0 licensing.

**Functional question:** Can the *real Chrome MV3 extension context* call the installed Windows HyperGet native host with the production `add` protocol, cause the installed app to fetch bytes from an isolated loopback HTTP fixture, and produce a file whose size and SHA-256 match the fixture?

**Expected:** Existing one-shot/connected/reconnect assertions continue passing; Chrome invokes `add` using the extension's production AddDownloadRequest schema (`items` + `options`), with `silentAdd=true`, `silentStart=true`, and a uniquely named deterministic HTTP fixture on `127.0.0.1`. Host returns correlated `true`, fixture sees actual GET traffic (metadata probes alone insufficient), a real file under the fresh runner user's standard download directories is byte-for-byte SHA-256 verified, and the non-sensitive report records both evidence and exact source SHA/installer SHA in workflow artifacts. Reject absent/mismatched bytes and no-GET cases. Keep browser ID/host registry names and persisted data unchanged.

**Why compile/build is justified:** The source checks and mocked native API cannot prove Chrome extension-origin authorization, Windows native-host registration, GUI IPC add dispatch, network transfer, or the installed download folder. Extend the *existing* one-purpose Windows NSIS/Chrome workflow rather than launch duplicate installers; use only Node 22 built-ins (HTTP, crypto, fs) and existing repository Apache-2.0 code. The Windows runner is free; no external download endpoints or new dependencies.

**Pre-build state:** CODE PENDING, exact-head installed Chrome download **NOT TESTED**. The previously completed Chrome native ping workflow is PASS, but does not satisfy this new gate. If the default download directory is not discoverable on a clean runner, capture sanitized diagnostics and mark FAIL rather than silently downgrading to a protocol-only test.

## Outstanding release gates

Firefox real extension, cookies/headers and signed-URL privacy, link refresh, scheduler, HTTPS, installed HLS process-kill/restart, Liquid Progress UI parity, RAM optimization, safe HyperGet identity/upgrade, independent Windows recheck and single final `HyperGet.exe` remain NOT TESTED. No main write, merge, paid runner, Torrent or final release.
