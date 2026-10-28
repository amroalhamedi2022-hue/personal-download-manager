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
