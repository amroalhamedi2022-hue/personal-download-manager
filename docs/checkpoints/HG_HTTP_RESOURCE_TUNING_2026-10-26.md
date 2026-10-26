# HG-HTTP-RESOURCE-TUNING-01 — restore the verified 64/64 dispatcher baseline

Date: 2026-10-26  
PR: [#3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3)  
Branch: `automation/idm-ftp-cancel-and-category-flush`  
Status: **32-cap trial rejected; 64/64 restored; exact-head revalidation pending**

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

## Next exact-head validation

**Pre-build question:** does the restored 64/64 configuration compile and pass the installed Windows matrix again, with 63/63 matching SHA-256, 1/4/16 distinct active tasks, 9/9 CPU/RAM CSVs, and durations within the established guard?

Expected: the functional gates remain green and the 16-task duration returns near the 64/64 baseline. The installed JVM profile should also run on this exact SHA so memory evidence is not inferred from an older commit.

Status until new runs are inspected:
- Source contract for 64/64: **PENDING CI**.
- Installed HTTP 1/4/16: **PENDING exact-head Windows run**.
- Installed JVM profile: **PENDING exact-head Windows run**.
- Cross-version upgrade/rollback, external HTTPS/FTP, HLS restart, final branded single `HyperGet.exe`, and independent final Windows recheck: **OPEN / NOT TESTED**.

No main write, merge, release, paid service/runner, or Torrent.
