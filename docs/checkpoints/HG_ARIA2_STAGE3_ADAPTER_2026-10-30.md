# HG — aria2 Stage 3 real adapter milestone

Date: 2026-10-30  
PR: #3  
Branch: `automation/idm-ftp-cancel-and-category-flush`  
Base: `codex/repair-rf00-rf01`

## Implemented

- Application-owned `aria2c` lifecycle with a loopback-only `127.0.0.1` RPC endpoint, a random 256-bit secret per process session, readiness polling, graceful shutdown and bounded forced termination.
- The RPC secret is passed through a temporary owner-restricted config rather than argv, redacted from printable requests/errors, and deleted immediately after readiness. The persistent aria2 session and its directory are restricted to the owning Windows user through ACLs (POSIX owner-only permissions elsewhere).
- Strict typed JSON-RPC for `getVersion`, `addUri`, `tellStatus`, `pause`, `unpause`, `remove`, `removeDownloadResult` and `shutdown`; the client rejects proxies, redirects, non-`127.0.0.1` endpoints, malformed JSON, mismatched IDs/GIDs and unknown states.
- Atomic schema-versioned HyperGet-task ↔ aria2-GID storage that contains neither URLs nor RPC secrets, duplicate mapping rejection, stale-GID reconciliation and monotonic per-task progress with terminal-state latching.
- Serialized add/pause/unpause/cancel/remove operations, destination collision checks, cleanup after durable-write failure, and fail-closed behavior when removal or child termination cannot be proven.
- Feature flag remains off by default; startup failure returns to the existing engine. HLS, DASH, media extraction, browser/native messaging and package workloads stay on their specialized paths. Existing aria2 Torrent capability is retained behind the adapter without adding Torrent UI priority.

## Commits

- `48ac0fb` — real RPC/process/store/adapter implementation and Windows runtime workflow.
- `e817844`, `dabad42` — correct completed-result cleanup and `removeDownloadResult` response handling.
- `3e817fe` — deterministic Stage 2 overlap attribution regression fix.
- `2d56883` — persistent session restore and concurrent cancellation/add coverage.
- `2659c48`, `8172eb7`, `ae9345a`, `ddea0d1` — fail-closed shutdown/removal, orphan cleanup, immediate secret deletion, Windows ACL and per-session-secret assertions.
- `7c40b01` — evidence-backed classification of all 81 tracked requirements.

## Verified evidence

- Baseline adapter contracts and pinned Windows aria2 1.37.0 runtime passed on `dabad42`: run `40966151892`; both the Ubuntu contract job and Windows real lifecycle/transfer job succeeded.
- The Windows runtime used the official archive SHA-256 `67d015301eef0b612191212d564c5bb0a14b5b9c4796b76454276a4d28d9b288` and exercised process start/readiness, authenticated RPC, add, progress, pause, resume, exact output size/SHA-256, removal and shutdown.
- Full application regression/build passed on `dabad42` inside run `40966151890`; the later Stage 2 benchmark failed only at fixture overlap classification in active-16 round 2 after earlier exact outputs passed. Commit `3e817fe` adds the focused regression; its exact-head Stage 2 rerun remains the acceptance authority.
- Local source suites after the repair: 33/33 PASS (`test_hyperget_aria2_comparison_source` plus `test_hyperget_active_benchmark_source`); `git diff --check` PASS.

## Pending exact-head proof

The security-hardening head `ddea0d1` has been pushed. Its dedicated adapter Windows runtime and the repaired Stage 2 run must complete before these later commits are marked accepted. A source-only pass is not a substitute for that runtime evidence.

## Next

Read the exact-head CI result once, repair only evidenced failures, then connect the accepted adapter to the desktop HTTP/HTTPS selection path behind the disabled-by-default flag while preserving the existing downloader as fallback and leaving specialized engines unchanged.
