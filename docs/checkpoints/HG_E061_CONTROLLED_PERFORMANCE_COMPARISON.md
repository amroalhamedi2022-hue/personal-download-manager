# HG-E061 — Controlled HTTP throughput and memory regression comparison

**Status: OPEN before Windows execution.** This checkpoint is committed before the paired installer builds are started.

## Pre-build question

Does the isolated production change that bounds blocking HTTP part-body copies at `PART_BODY_COPY_PARALLELISM = 48` preserve exact HTTP output and real 1/4/16 overlap while avoiding a material regression in aggregate throughput or process memory?

- **Baseline app snapshot:** parent of the bound change, `d6ea54a885277cb5b8fd545eb3f3fb0e185b6aa5`.
- **Candidate app snapshot:** `dd27731decbcd8cf1cb67feae42963be42d3bb7a`, the production commit `perf(http): bound concurrent blocking part-body copies`.
- **Harness:** the exact current PR head; the same fixture, sampler, summarizer and comparison code is used for both binaries.
- **Expected:** each snapshot completes 63 deterministic 32-MiB HTTP downloads (1, 4 and 16 distinct active tasks; three rounds each), every output's actual size and SHA-256 equals the expected fixture, all existing live-overlap gates remain strict, and nine process-tree CPU/RAM rounds are recorded per snapshot with separate app-source and harness SHAs.
- **Why source tests are insufficient:** the question depends on compiled installers, real Windows GUI/CLI IPC, socket overlap, byte transfer, the installed runtime's thread/heap/process behavior, and OS Working Set/Private Bytes. Source assertions cannot establish those facts.
- **Predeclared regression gates:** on the same Windows Server 2022 runner, the candidate's median aggregate throughput must not regress by more than 15%, and median peak Working Set or Private Bytes must not increase by more than 15%, for any of the 1/4/16 workloads. Three rounds are paired by workload and the install order alternates (baseline→candidate, candidate→baseline, baseline→candidate) to reduce time-order bias. These are version-to-version regression thresholds, not IDM parity claims.
- **Safety:** only a fresh, marker-owned `%USERPROFILE%\.personal-download-manager` test profile and unique runner-temp install path may be cleaned. The harness refuses to touch pre-existing user data, registry entries, or shortcuts. Both installers are internal test artifacts; they are not release deliverables.
- **Scope:** no production source is rewritten for this experiment, no paid service/runner/API, no main-branch write, no auto-merge, no release, and no Torrent support.

**Required result:** retain installer SHA-256 values, raw transfer manifests, nine CSVs per snapshot, comparison report, transcript and artifact digest. If any existing transfer/hash/overlap gate fails, or a predeclared regression threshold fails, report FAIL and preserve evidence; do not weaken thresholds.

**Status: OPEN — awaiting exact-snapshot Windows build and paired runtime results.**
