# HG-ARIA2-S1 — Windows aria2 viability gate (2026-10-28)

## Milestone
Complete the missing Stage 1 viability evidence for the pinned upstream aria2 1.37.0 Windows binary before starting the paired performance comparison or changing any production engine wiring.

## Pre-test declaration
**Question:** can the exact pinned binary safely handle real loopback HTTP redirects, 404/429/500 responses, a server that ignores Range, forced process interruption followed by a true nonzero-offset resume, authenticated FTP, and a local-only authenticated JSON-RPC lifecycle without corrupt output or leaking credentials?

**Expected:** each successful transfer matches the deterministic fixture byte count and SHA-256; HTTP error statuses fail rather than being accepted as completed downloads; ignored Range is either handled to exact bytes or rejected without a full-length corrupt output; resume proves a partial staged file and .aria2 control file before restart, then proves a nonzero honored Range offset and fewer requested bytes after restart; FTP records authenticated USER/PASS and completed RETR; RPC rejects missing/wrong/stale secrets, binds only to loopback, survives malformed JSON, shuts down cleanly, and leaves no secret in uploaded logs.

**Why source tests are insufficient:** only Windows process termination, actual aria2 HTTP/FTP sockets, its resume-control file, Windows listener bindings, JSON-RPC authentication and process shutdown can establish these properties. The source contracts only lock the test harness and route definitions.

## Production scope
- No production engine switch or dependency change.
- The stock upstream archive is evaluation-only. Its enabled-feature list includes BitTorrent; it must never be bundled or exposed as a HyperGet feature while Torrent is prohibited.
- The script uses the existing standard-library loopback HTTP and FTP fixtures. HTTP fixture additions are opt-in routes (/redirect, /status/{404,429,500}, /ignore-range) and bounded per-task Range telemetry; default fixture behavior remains unchanged.
- SFTP is only recorded from aria2's feature list. No local SFTP server is part of this milestone, so actual SFTP transfer remains NOT TESTED.

## Implementation in this checkpoint
- scripts/Run-WindowsAria2Viability.ps1: real Windows Stage 1 harness for redirect/status/ignored-Range handling, interrupted HTTP resume, authenticated FTP, local-only JSON-RPC auth/malformed-input/restart, and secret redaction.
- scripts/LocalHttpRangeFixture.py and scripts/test_local_http_range_fixture.py: deterministic fault routes and regression coverage for redirect, status codes, ignored Range, and requested offsets.
- scripts/source-tests/test_hyperget_active_benchmark_source.py: source contracts for the viability harness.
- .github/workflows/hyperget-source-only.yml: trigger source checks when the aria2 evaluation workflow or viability harness changes.
- The Windows evaluation workflow must run the source contracts and PowerShell parser before its purposeful test/build steps, invoke this harness only after verifying the pinned archive SHA, and upload sanitized viability evidence separately.

## Acceptance ledger
- Source/fixture checks: PENDING exact CI run.
- PowerShell parse: PENDING exact Windows run.
- HTTP redirect and status handling: PENDING.
- Ignored-Range correctness/safe rejection: PENDING.
- True interrupted HTTP resume with exact SHA: PENDING.
- Authenticated FTP transfer with exact SHA: PENDING.
- RPC loopback binding, authentication, malformed-input recovery, shutdown and stale-secret rejection: PENDING.
- Secret-free logs: PENDING.
- SFTP actual transfer: NOT TESTED.
- Stock aria2 bundle eligibility: FAIL/BLOCKED by BitTorrent-enabled upstream binary; do not package it.
- Stage 2 paired 1/4/16 performance comparison: NOT STARTED; remains separate from this viability gate.
- Final HyperGet.exe and independent Windows release recheck: OPEN.

No merge, main write, auto-merge, release, paid API/runner, or production aria2 wiring.


## 2026-10-28 — Stage 1 workflow wiring correction (pre-run checkpoint)

The audit found that the Windows workflow still ran only the small single/segmented HTTP smoke; it did **not** execute scripts/Run-WindowsAria2Viability.ps1, even though the source-contract test expected the Stage 1 gate. Therefore the existing HTTP smoke is not Stage 1 acceptance evidence.

This cohesive repair wires the real harness into the pinned-aria2 Windows job and makes source contracts, fixture unit tests, Python syntax checks, and PowerShell parsing mandatory before the viability runtime stage. The workflow will verify the exact PR source SHA, invoke the harness only after verifying the upstream archive SHA, and upload a separate sanitized viability artifact. It also strengthens the regression test to require the actual command invocation and ordering, not merely a filename mention.

**Pre-run question:** can the pinned Windows aria2 1.37.0 binary pass all ten runtime assertions—redirect, 404/429/500 rejection, ignored-Range safety, true interrupted nonzero-offset resume with exact SHA, authenticated FTP, RPC loopback/authentication/malformed-input recovery/shutdown/stale-secret rejection—without leaking a secret?

**Expected:** all ten harness results PASS, source/fixture checks PASS, PowerShell parse PASS, and a separate artifact with the CSV/report/logs. If any gate fails, preserve the exact failure and sanitized evidence; do not treat the smoke pass as a substitute.

**Why source checks are insufficient:** process interruption, active socket closure, aria2 resume-control state, actual FTP credentials/RETR, Windows listener addresses, RPC lifecycle and exact output hashes require a real Windows runtime.

**Progress before this run:** basic aria2 HTTP smoke = 2/2 PASS on the prior tested SHA; comprehensive Stage 1 runtime assertions = 0/10 verified. The stock binary remains **BLOCKED for bundling** because BitTorrent is enabled; no production engine switch or Torrent exposure is permitted. Stage 2 paired performance remains NOT STARTED.


## 2026-10-28 — First wired Stage 1 runtime attempt and harness correction

Exact tested PR source SHA: f661703efa08c5e9f7ace316b5bbbcea7385d646.

- **PASS:** exact-SHA checkout, Python syntax, all source-contract tests, local HTTP fixture unit tests, and PowerShell parsing.
- **PASS:** pinned aria2 1.37.0 archive SHA verification and the basic single/segmented 16 MiB HTTP smoke; both outputs matched SHA-256 287507f403176f1f5b22b9a4d9cb49f7d7f88ac19e406b5ae87ce109564846bd.
- **PASS 1/10 Stage 1 runtime assertions:** HTTP redirect produced the exact 16 MiB fixture and SHA-256 341aacac661ccb210720bedaa9ead5d668fe5ea41a73532fc147c71e34040df1.
- **FAIL — test harness only, before HTTP status assertions:** PowerShell interpreted the interpolated URL segment "$status?task" as a variable name. This is not an aria2 status-handling failure. HTTP 404/429/500, ignored-Range, restart/resume, FTP, and RPC cases did not run and remain NOT TESTED on this attempt.
- Evidence artifacts: basic smoke [12806888836](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40670133507/artifacts/12806888836), SHA-256 f5254e9050d30b86935de1b8b1d1cbaf4368c52ff91d3a4ca1550ef6d97b2cb5; incomplete Stage 1 logs [12807627862](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40670133507/artifacts/12807627862), SHA-256 969df994ea7178aa493239fcf24e3b10827dfaf05d778c72616ac61705865a1b.
- **Fix:** delimit the PowerShell variable as $($status) in the URL and add a regression assertion that rejects the ambiguous form. The next exact-head Windows run must continue through all ten assertions; no Stage 1 GO decision is inferred from this partial attempt.

The stock binary still reports BitTorrent enabled and is blocked from packaging. No production engine wiring, main write, merge, release, or Torrent feature.


## 2026-10-28 — Stage 1 resume gate: full redownload observed; checkpoint timing hardened

Exact tested PR source SHA: 18d25c4eb7ae5956d0192d65cd67ce4b031d32c5.

- **PASS 5/10 runtime assertions:** HTTP redirect, 404, 429, 500, and ignored-Range safety. Every successful transfer was SHA-256 checked; ignored Range fell back to exact output SHA 341aacac661ccb210720bedaa9ead5d668fe5ea41a73532fc147c71e34040df1.
- **Resume case: FAIL (resume-efficiency gate), data integrity PASS.** The restarted aria2 process completed a correct 16 MiB file, but the fixture recorded 16 MiB requested after restart and no honored nonzero offset. Therefore this is not a resume PASS.
- The first process log showed it was interrupted around 896 KiB, below aria2's default 1 MiB HTTP piece length. The official aria2 manual documents a 60-second default for saving the .aria2 control file, with --auto-save-interval controlling the cadence: https://aria2.github.io/manual/en/html/aria2c.html. The likely explanation is that the hard-kill happened before useful progress had been checkpointed; this is a hypothesis, not yet proven.
- **Harness repair:** explicitly set --auto-save-interval=1 and --always-resume=true; wait until at least 2 MiB and a control file exist, then require a newer control-file timestamp after 1.6 seconds before killing the process. Save first-attempt and restart Range telemetry as JSON and include offsets/bytes in any failure. No assertion is weakened.
- Evidence artifact [12808540829](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40670419404/artifacts/12808540829), ZIP SHA-256 6224d07ccb14a46cbac3068b34b06a878c7ae25f95f5d5a678066f622ac6a763. It confirms the second process fetched from the beginning; the next exact-head Windows run must prove a nonzero honored offset and fewer than 16 MiB requested, or keep the engine gate open.
- FTP and RPC cases were not reached and remain NOT TESTED. Stock aria2 still has BitTorrent enabled and remains blocked from packaging.


## 2026-10-28 — Resume artifact reanalysis: telemetry metric false negative

The diagnostic artifact for exact source SHA 010bca3354c894f1b1245d2dd829e81ddf8d28d3 proves that the transfer **did resume from a nonzero honored Range offset**:

- First attempt: 2,752,512 bytes actually served; partial output was 2,310,144 bytes when interrupted.
- Restart: Range starts were 0 and 2,310,144; the latter was marked range=true and honored=true.
- Restart actual bytes served: 14,532,608, below the 16,777,216-byte fixture size; final output length and SHA-256 were correct.
- The gate failed because it compared task_requested_bytes (31,244,288), which sums the *declared range lengths* of both a preliminary full-response GET that the client aborted after about 64 KiB and the resumed Range request. It did not measure actual data served.

**Correction:** keep requested-byte telemetry for diagnostics, but gate resume on the nonzero honored Range offset plus task_bytes_sent < expectedBytes. This matches the fixture's actual socket-write counter and avoids falsely rejecting a valid resume. No runtime criterion is relaxed; the corrected exact-head Windows run must still pass before Stage 1 is closed. Artifact: [12808482015](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40671624364/artifacts/12808482015), ZIP SHA-256 21a8bb9b11e0670b3d36ee977889ef342b8d68b50cf8990d94a71b54dc6a0a53.


## 2026-10-28 — Exact-head Stage 1 runtime result and RPC diagnosis patch

Exact tested PR source SHA: `38b6f57a481c0d606fad90a97d121a0dd5ecda3a`; Windows evaluation run [40675748124](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40675748124).

- **PASS:** exact-SHA checkout; Python source/fixture preflight; PowerShell parser; pinned aria2 1.37.0 archive hash; standalone HTTP single-connection and 4-way segmented 16 MiB output with identical SHA-256 `287507f403176f1f5b22b9a4d9cb49f7d7f88ac19e406b5ae87ce109564846bd`.
- **PASS 7/10 Stage 1 runtime assertions:** redirect, HTTP 404/429/500 rejection, ignored-Range safe exact output, true restart/resume, and authenticated FTP. Resume was proven by an honored nonzero Range offset `2,768,896`, `14,073,856` actual bytes served after restart (< `16,777,216`), and final SHA-256 `341aacac661ccb210720bedaa9ead5d668fe5ea41a73532fc147c71e34040df1`. FTP recorded successful login + RETR and exact 16 MiB SHA.
- **FAIL — RPC readiness harness diagnostic gap:** `Wait-RpcReady` timed out at `Run-WindowsAria2Viability.ps1:271`. The uploaded RPC stdout only confirms that aria2 opened IPv4/IPv6 listeners on the selected TCP port. The harness did not expose HTTP status, transport exception, or JSON-RPC error from its last probe; this is **not yet evidence that aria2 RPC itself is broken**. RPC authentication, malformed-input recovery, authenticated shutdown and stale-secret rejection remain **NOT TESTED**.
- Stage 1 artifact [12809908767](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40675748124/artifacts/12809908767), ZIP SHA-256 `bc4c7893a15af660ff9a0bb02f67395c62937280c69fd51ddc0b09ed722692fa`.
- The same exact-SHA Windows app job **PASS**: 249 JUnit tests, zero failures/errors/skips; Windows installer build succeeded. Test installer `HyperGet_1.0.0-sha.g38b6f57_windows_x64.exe`, SHA-256 `10b0bc3e36ed5312c5169c587b5807cc621a88347fd8db99f316a778a4686b86`; artifact ZIP SHA-256 `119fd8290713074b6b32dbcb3c170b84ae31ffd2ddc8b4c6f23afd5036238c4f`. This remains a **test installer**, not final standalone `HyperGet.exe`.
- **Next fix:** preserve sanitized status/transport/JSON-RPC diagnostics from the final readiness probe and add a source regression, then rerun this exact Stage 1 gate. Do not start Stage 2 until RPC is resolved and all mandatory Stage 1 cases pass. The stock upstream binary advertises BitTorrent and remains ineligible for packaging; Torrent stays prohibited. Stage 2 paired 1/4/16 comparison remains **NOT STARTED**.


## 2026-10-28 — RPC probe narrowed to HTTP 200 with no parsed result

Exact tested source SHA `6c30f1fc1596e51f35ffa69a6ba90ef423192a63`; Windows evaluation run [40678234198](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40678234198).

- **PASS 7/10 Stage 1 runtime assertions** again: redirect, 404/429/500, ignored-Range safety, honored nonzero-offset restart/resume with exact SHA, and authenticated FTP with exact SHA. Resume restarted at `2,768,896` and served `14,073,856` actual bytes after restart, below the 16 MiB fixture.
- **RPC diagnostic result:** the last readiness probe received HTTP 200 with no transport exception, but the harness parsed neither a JSON-RPC `result` nor an `error`. Because the previous helper did not record response content length/type or parse errors, the root cause is still **unresolved**; do not label this an aria2 engine failure or an RPC pass.
- **Cohesive follow-up:** replace the PowerShell web-wrapper call with .NET `HttpClient`, explicitly disable inherited proxies for the hard-coded loopback endpoint (the secret must never be sent to a proxy), and record sanitized content length/type, parse-error class, and a 120-character redacted preview only on failure. Source contracts require loopback-only/no-proxy behavior and secret redaction.
- **Expected next result:** the same Windows test must successfully parse `aria2.getVersion`, then pass missing/wrong-token rejection, malformed-input recovery, authenticated shutdown/listener closure and stale-token rejection. Until the exact-head runtime passes, RPC and overall Stage 1 remain **NOT TESTED/OPEN** beyond the seven confirmed cases. No Stage 2 or production engine change yet.
