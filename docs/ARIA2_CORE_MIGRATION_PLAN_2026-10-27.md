# HyperGet aria2 Core Evaluation and Migration Plan

Date: 2026-10-27
Repository: https://github.com/amroalhamedi2022-hue/personal-download-manager
Working branch: `automation/idm-ftp-cancel-and-category-flush`
Decision status: EVALUATION ONLY — no production engine replacement approved yet.

## User goal

Produce a Windows HyperGet executable now, and rigorously determine whether aria2 should become the primary transfer core while retaining HyperGet UI, its integration/domain layer, and specialized subsystems (HLS/DASH, media extraction, browser/native messaging, and package handling).

## Current-state guardrails

- Keep the existing PR #3 and its working branch; do not write to `main`, merge, publish a GitHub Release, or create a duplicate PR.
- Build the current HyperGet Windows installer as a test/delivery artifact on the exact source SHA. Record SHA-256 and test provenance. A CI artifact is not a signed public release.
- Do not change production download-engine wiring during the evaluation. No destructive migration until comparative evidence passes the gates below.
- Preserve app identity and compatibility: `HyperGet.exe`; stable `DownloadManagerCli.exe` and `DownloadManagerNativeMessagingHost.exe`; existing user data, history, credentials, settings, and browser integration.
- No torrent feature work is part of this task.

## Architecture target

```
HyperGet UI (Compose Desktop, Liquid Progress, dialogs, settings)
                    |
HyperGet domain/integration layer
  - DownloadJob / identity / queue / retry policy
  - persistence and history
  - lifecycle, progress mapping, cancellation, resume policy
  - SHA-256 verification and safe destination validation
  - browser/native messaging and CLI compatibility
                    |
        TransferEngine interface
          /                 \
  Aria2Engine             Specialized engines
  - HTTP/HTTPS            - HLS/DASH manifests, segments, assembly
  - FTP/SFTP              - media extraction/adaptive stream selection
  - segmented transfers   - package/container-specific handling
  - resume/RPC             - unsupported/edge protocols
```

The engine boundary must prevent aria2 task IDs and RPC details from leaking into UI state or persistent HyperGet identifiers. HyperGet remains the owner of stable task identity, presentation, validation, policy, and persistence. Aria2 is an implementation behind an adapter, not the application itself.

## Stage 0 — Current Windows EXE and baseline evidence

1. Checkout the exact PR head, verify the SHA, use the repository's strict Gradle dependency verification and JBR 25.
2. Run desktop JVM/JUnit suites and source contracts; create the existing Windows installer using `createReleaseFolderForCi`.
3. Verify exactly one installer EXE is emitted and compute SHA-256. Upload installer and JUnit/report artifacts separately.
4. Record current exact-head failures without mislabeling old SHA results as current.
5. Run existing installed UI/Liquid Progress, HTTP, FTP/REST resume, HLS, queue lifecycle, browser/native messaging, 1/4/16 and upgrade/uninstall checks on the same candidate where their workflows apply.

## Stage 1 — Standalone aria2 spike (no production integration)

Pin the official upstream Windows x64 aria2 1.37.0 archive and verify its SHA-256 before execution:
- URL: https://github.com/aria2/aria2/releases/download/release-1.37.0/aria2-1.37.0-win-64bit-build1.zip
- Expected SHA-256: `67d015301eef0b612191212d564c5bb0a14b5b9c4796b76454276a4d28d9b288`
- Upstream project/license: https://github.com/aria2/aria2 (GPL-2.0-or-later).

Run the binary against the repository's deterministic loopback HTTP Range fixture. Verify version, 1-connection and 4-connection downloads, output size, and SHA-256. Extend the spike before any go/no-go decision to cover:
- interruption/restart and true range resume;
- HTTP redirect, 404/429/5xx, slow server, missing/incorrect Range support;
- FTP and SFTP capability/build configuration;
- concurrent independent tasks at 1/4/16, fairness and bounded resources;
- RPC authentication, localhost-only binding, malformed input, process shutdown, stale sessions and secret redaction;
- Windows Defender/SmartScreen implications, executable provenance, child-process lifecycle and packaging footprint.

A simple standalone download/hash pass proves only basic viability; it does not prove production readiness or superiority.

## Stage 2 — Controlled apples-to-apples performance comparison

Compare current HyperGet and aria2 on the same Windows runner, fixture, bytes, network, and number of rounds. Use 1, 4, and 16 independent tasks, and single-file segmentation levels 1, 4, and 8 where supported. Capture:
- actual output bytes and SHA-256 for every task;
- aggregate and per-task throughput, completion time, failure/retry counts;
- active request overlap, CPU, peak Working Set and Private Bytes;
- pause duration, bytes-after-pause, resume correctness, restart recovery;
- installer and extracted-engine footprint.

Use at least 3 paired rounds per workload and alternate order to reduce time-order bias. Never infer speed from a single run or compare different files/servers.

Provisional gates:
- zero incorrect/truncated/corrupt outputs;
- zero leaked or duplicate tasks and no unauthorized RPC exposure;
- no loss of required HyperGet features;
- no >10% median throughput regression in any critical workload unless a documented trade-off is explicitly accepted;
- no >15% median peak-memory regression, and no material idle-resource regression;
- repeatable pause/resume and restart recovery with exact hashes;
- clean uninstall/upgrade preserving user data and stable browser/CLI identities;
- license/compliance and security review accepted before bundling.

## Stage 3 — Thin adapter prototype on an isolated branch

Only if Stage 1 and 2 justify investment, create a small adapter prototype behind a feature flag. Keep the existing engine as fallback. Prototype:
- one stable HyperGet task ID ↔ aria2 GID mapping persisted safely;
- structured progress/status mapping and monotonic progress;
- pause/resume/cancel/remove and shutdown ordering;
- error classification, retry ownership, duplicate prevention and queue caps;
- path/name validation, temporary files, final commit, SHA-256 and cleanup;
- local-only RPC with a strong per-session secret and strict lifecycle management;
- no raw RPC or secrets in logs or browser-facing messages.

Do not replace HLS/DASH, media extraction, browser/native messaging, or package handling just because aria2 supports HTTP.

## Stage 4 — Functional parity and failure injection

Run the complete existing regression suite against the adapter and run dedicated integration cases for:
- parallel jobs and concurrency cap changes while jobs are queued/running/stopping;
- pause-before-start, pause-during-transfer, cancel races, remove and queue refill;
- crash/restart, duplicate recovery, stale GIDs, destination collisions, disk-full and permission errors;
- server ignores Range, changes ETag/length, redirects across origins, authentication, rate limiting;
- HLS/DASH manifest/segment refresh, byte assembly, media extraction and package flows stay on their specialized engines;
- Chrome/Firefox native messaging and actual browser-captured downloads;
- installer, upgrades, rollback, uninstall, settings/history/credentials preservation;
- security tests for RPC isolation and filesystem/path traversal.

## Stage 5 — Decision

- **GO:** only when all mandatory functional/security/license gates pass and paired benchmarks show a meaningful overall benefit (or a clearly justified operational simplification with no unacceptable regressions).
- **NO-GO:** retain the current engine if aria2 fails correctness, resume, RPC security, license/distribution, resource, feature parity or performance gates.
- **PARTIAL GO:** use aria2 only for protocols/workloads where it demonstrates clear value, while preserving the existing and specialized engines behind the same interface.

## Explicit non-goals

No main-branch writes, merge, public release, Torrent implementation, removal of the current engine before acceptance, or claims of successful tests without exact-SHA artifacts. Keep this plan and the acceptance ledger updated with the actual run IDs, source SHA, test outcome, artifact URL and artifact/file SHA-256.
