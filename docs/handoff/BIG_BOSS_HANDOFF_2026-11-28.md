# HyperGet — Big Boss Handoff
Snapshot: 2026-11-28. Source: repository README and open-PR listing inspected on this date; active HEAD/CI not independently certified.

## Canonical source
README.md; docs/roadmaps/HYPERGET_FUNCTIONALITY_MASTER_PLAN.md; PRs #65/#66 and #64/#63/#62/#59/#58; browser companion repository.

## Observed baseline
Windows Kotlin/Compose; HTTP/HLS/FTP queues, resume, integrity, installer. README states aria2 experimental only, not active on main.

## Execution sequence / acceptance
Reconcile open PR dependencies; validate range/ETag/retry/disk safety and HLS; compare exact installed Windows 1/4/16 benchmarks; integrate browser native messaging; gate aria2 migration with rollback and approved torrent capability; test DPI/RTL/Explorer drops; publish verified Windows installer.

## Operational handoff rules
- Master functionality in a masterpiece app. Maximize meaningful tested work per batch; avoid repeated work and unnecessary builds.
- Before every continuation: fetch origin, inspect default branch and active PR heads, latest commits, Actions and existing plans; record exact SHA, branch, tests and remaining gates. Do not assume README reflects latest code. Never overwrite another active branch or merge competing PRs blindly.
- Recovery: clone the repository, inspect README and documented toolchain, check out the verified intended branch/commit, restore dependencies from lockfiles, run relevant focused tests, then build only at meaningful milestones. No final release without same-SHA evidence. Record exact commands, artifact hash, version, device acceptance, and rollback procedure.
- Secrets, credentials, keystores, personal data, private asset archives and private Drive URLs never belong in GitHub (including private archive). Recover from owner-controlled restricted Drive or configured secret manager; verify permissions, signer fingerprint and checksums without exposing secrets. Never change production identity or signing key silently.
- Update handoff after meaningful changes; mark implemented, CI-verified, device-verified and blocked separately. This document is an index/continuation supplement, not a replacement for detailed source plans. No builds or device tests were run as part of this handoff update.
