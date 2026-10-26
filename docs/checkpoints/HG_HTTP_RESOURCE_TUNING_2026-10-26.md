# HG-HTTP-RESOURCE-TUNING-01 — restore the verified 64/64 dispatcher baseline

Date: 2026-10-26  
PR: [#3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3)  
Branch: `automation/idm-ftp-cancel-and-category-flush`  
Status: **32-cap trial rejected; 64/64 restored and exact-head Windows revalidation PASS; memory optimization remains open**

## Purpose

Evaluate whether lowering OkHttp's global and per-host asynchronous request caps from 64 to 32 reduces resource use without harming real 1/4/16 transfer behavior. The experiment was deliberately gated on an installed Windows build because source tests cannot prove dispatcher scheduling, overlapping sockets, completed file bytes, throughput, or process-tree memory.

## Evidence reviewed

### Previously verified 64/64 baseline

- Windows installed matrix: [run 40303361484](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40303361484).
- Artifact: [12641274367](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40303361484/artifacts/12641274367), ZIP SHA-256 `a37ca245d59a579e00c82e755d3ba371c6f49d240ec446e3a5b2e6d47cde9b82`.
- 63/63 transfers matched fixture SHA-256; 1/4/16 active-task gates and 9/9 CPU/RAM CSVs passed.
- Across the three 16-task rounds, the highest recorded peak was 441.617 MiB Working Set and 503.734 MiB Private Bytes. Round durations were approximately 155.7–157.0 seconds.

### Tested 32/32 experiment

- Exact source SHA: `d4dc2dcb121913b9734b5bfaf5279d24b483f49d`.
- Installed Windows matrix: [run 40321541277](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40321541277).
- Artifact: [12650625992](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40321541277/artifacts/12650625992), ZIP SHA-256 `90dcf4af3fe04d7cb39a3a0d9d84a4c91504ddf0462b071dd6f793df1dda490f`.
- Functional gates passed: 63/63 distinct SHA-256 checks, 16/16 active transfers in each 16-task round, and 9/9 resource CSVs.
- 16-task round durations were approximately 166.3, 168.3 and 166.0 seconds. These are within the 180-second guard but roughly 7% slower than the prior baseline.
- Peak memory by 16-task round:
  - Round 1: 371.520 MiB Working Set / 432.777 MiB Private Bytes.
  - Round 2: 458.734 / 509.324 MiB.
  - Round 3: 480.629 / 547.379 MiB.
- The third-round peaks were about 8.8% higher Working Set and 8.7% higher Private Bytes than the previous 64/64 baseline. This is a single-run comparison, not a controlled A/B or proof of causal attribution, but it provides no evidence of a meaningful resource benefit.

## Decision

Reject the 32/32 tuning. Restore `maxRequests = 64` and `maxRequestsPerHost = 64`; update the source regression contract to lock the verified baseline. Do not weaken any integrity, concurrency, or timing gates.

## Exact-head 64/64 revalidation — PASS

The production rollback is now validated on exact source SHA `915fe579db44b26b485d048e8f00b9487aec0a22`.

- Installed Windows HTTP matrix: [run 40333126121](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40333126121).
- Evidence artifact: [windows-active-http-1-4-16-evidence, ID 12655724146](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40333126121/artifacts/12655724146); artifact ZIP SHA-256 `24e17186a69c4aec3256fecaa5a5a6f1b32eba95762e98643eb760a2eb523520`.
- **63/63** distinct 32 MiB downloads verified against the local fixture SHA-256 `e09320c5b00b34bb704802136c599a95b3996332ba84d7c7f21112b6231b6bd0`.
- Three rounds each at 1/4/16 distinct simultaneously transferring tasks; peak distinct task counts were 1/4/16, with 128 peak HTTP connections at 16 tasks.
- **9/9** process-tree CPU/RAM measurement CSVs were produced and validated.
- Third 16-task round peak: 502.059 MiB Working Set, 562.102 MiB Private Bytes, 200 process-tree threads and 1,684 handles in the sampled process tree. This is functional acceptance, **not a resource improvement claim**; this run's memory was higher than the earlier 64/64 baseline, so memory remains open and comparisons need controlled repeatability.

## Exact-head JVM profile — PASS, diagnostic only

- Installed JVM profile: [run 40333126363](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40333126363).
- Artifact: [hyperget-installed-jvm-memory-profile, ID 12655992771](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40333126363/artifacts/12655992771); ZIP SHA-256 `d399f29e6dfbde436e544b2e88e35a10c5f0f64f470084b6324fb81c21286834`.
- **21/21** profile downloads matched SHA-256, **3/3** resource CSVs and all requested JVM diagnostic captures passed.
- Same-JVM process snapshot after the transfer: 566.922 MiB Working Set / 616.012 MiB Private Bytes, 133 threads / 1,074 handles. After 30 seconds idle: 374.961 MiB / 442.191 MiB, 121 threads / 1,032 handles. Used heap fell from 73,995 KiB to 35,384 KiB.
- Skia `CleanableImpl` objects fell from 11,249 during the transfer profile to 3,645 after stabilization; Compose `LayoutNode` count remained 1,759. These are diagnostic snapshots, not proof of a leak or its absence. The profile makes bounded body-copy scheduling a worthwhile experiment.

## Other exact-head gates

- Installed Liquid Progress + 16 MiB SHA + same-version reinstall/silent uninstall: **PASS**, [Windows UI run 40333125751](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40333125751). Internal installer SHA-256 `af6fb7805d79a933d4d2ca8f32ac3d19184e052a205a330348d21ad3f5bfdc7f`; not a deliverable.
- HLS guarded redirect/segment/assembly JUnit: **29 tests PASS**, [run 40333125794](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40333125794).
- FTP Windows Kotlin/JUnit: **29 tests PASS**, [run 40333126023](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40333126023).
- Source-only safety checks: **PASS**, [run 40333125785](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40333125785).

## Remaining gates

Cross-version upgrade/rollback and existing-data migration; HTTPS/external FTP and HLS restart; controlled CPU/RAM optimization and IDM comparison; full branded single `HyperGet.exe`; and independent final Windows recheck remain **OPEN / NOT TESTED**.

No main write, merge, release, paid service/runner, or Torrent.
