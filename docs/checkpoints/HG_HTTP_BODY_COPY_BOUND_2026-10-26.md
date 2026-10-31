# HG-HTTP-BODY-COPY-BOUND-01 — bounded blocking part-body copies

Date: 2026-10-26  
PR: [#3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3)  
Branch: `automation/idm-ftp-cancel-and-category-flush`  
Status at commit creation: **PRE-BUILD — source experiment proposed; exact-head Windows acceptance pending**

## Evidence motivating this experiment

The restored 64/64 HTTP dispatcher configuration passed exact-head Windows acceptance at source SHA `915fe579db44b26b485d048e8f00b9487aec0a22`:

- 63/63 distinct 32 MiB downloads SHA-256 verified; three rounds each at 1/4/16, 16/16 distinct active tasks, 128 peak HTTP connections at 16 tasks, and 9/9 resource CSVs. [Run 40333126121](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40333126121), artifact ZIP SHA-256 `24e17186a69c4aec3256fecaa5a5a6f1b32eba95762e98643eb760a2eb523520`.
- Third 16-task round peak: 502.059 MiB Working Set / 562.102 MiB Private Bytes, 200 process-tree threads and 1,684 handles in the sampled process tree.
- The installed JVM profile passed 21/21 SHA-verified transfers and collected thread/heap/NMT evidence. [Run 40333126363](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40333126363), artifact ZIP SHA-256 `d399f29e6dfbde436e544b2e88e35a10c5f0f64f470084b6324fb81c21286834`. A thread dump contained many `DefaultDispatcher-worker` threads; used heap fell after idle but process Private Bytes remained elevated. This motivates an experiment, not a leak diagnosis.

## Production experiment

Use one process-wide `Semaphore(48)` for blocking response-body copies and acquire the permit **before** switching to `Dispatchers.IO`. Waiting part coroutines should suspend instead of occupying blocking-I/O workers. This does not change user task concurrency, the independent 64/64 OkHttp request caps, or transfer split settings. Keep the connection scoped with `use` while waiting so Pause/cancellation closes the response even if the copy never begins.

The cap is 48 rather than 32: the rejected 32/32 experiment changed OkHttp request dispatch, not body-copy scheduling, and showed no resource win. This is a separate hypothesis and must stand or fall on its own Windows evidence.

## Purposeful Windows build question (recorded before execution)

**Question:** does the bounded body-copy path compile and preserve the existing HTTP/FTP/queue cancellation behavior, while the installed Windows HTTP matrix still passes all 63 SHA-256 checks, all 1/4/16 overlap gates and 9/9 CPU/RAM samples?

**Expected:** all 63 files verified; peak distinct tasks 1/4/16; no regression in the existing blocking-source cancellation test; all 16-task rounds complete within the workflow's 300-second per-round guard; fewer blocking worker threads and/or lower peak Working Set/Private Bytes than the prior exact-head profile. The follow-up JUnit change will explicitly saturate all 48 permits, prove the 49th copy waits, and prove a thrown copy releases its permit. Do not mark the experiment accepted until that test also passes.

**Why source tests are insufficient:** only a real Windows build and installed transfer run can measure blocking Okio reads, response lifetime while waiting for permits, socket overlap, throughput, GC/JIT effects, process-tree threads/handles, memory and file integrity. Source checks only protect the intended structure.

## Required validation

- Existing source contracts and targeted Windows queue/part lifecycle JUnit on the production commit.
- Installed Windows HTTP 1/4/16 matrix: 63 distinct 32 MiB SHA-verified downloads and 9 CPU/RAM CSVs.
- Installed JVM profile on the same production SHA: compare active and 30-second stabilized thread count, heap, Skia cleanables, Working Set and Private Bytes.
- Follow-up test-only commit: saturation and permit-release JUnit tests plus source assertions; because production source remains unchanged, this follow-up should not rerun the expensive HTTP matrix or JVM package gate.

## Status before build

No result is claimed in this file yet. The source/compile pass is not a performance result. If active-task overlap or SHA integrity fails, or throughput materially regresses without a measurable resource benefit, revert the production cap rather than weakening tests. No paid service/runner, new dependency, main write, auto-merge, release or Torrent.


## Post-build evidence — 2026-10-26

**Production source tested:** \`dd27731decbcd8cf1cb67feae42963be42d3bb7a\` (the bounded \`Semaphore(48)\` implementation).  
**Current PR head at follow-up:** \`2859588b6056f478b46cd7c189db5164b161b3ad\`. Comparing \`dd27731...319c6aa\` shows only the lifecycle JUnit and source-contract test files changed; the production \`PartDownloader.kt\` blob is identical (\`5e1e4cbde0d3bd33c802ca7013b1ee281d141cea\`). The later commits \`b0b6e5c1\` and \`2859588b\` strengthen only the test and its source contract.

### Installed Windows results — PASS

- **HTTP 1/4/16 matrix:** [run 40339095923](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40339095923). Installed one internal NSIS test installer; 63/63 distinct 32 MiB downloads matched the fixture SHA-256; three rounds each at 1/4/16, 16/16 distinct active tasks, and 9/9 process-tree CPU/RAM CSVs validated. Every round completed within the 300-second per-round guard.
- Evidence artifact [12659995827](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40339095923/artifacts/12659995827), ZIP SHA-256 \`4c7c1d5b1f7dc2e0179df76a82de7ef737c662a45b8aa4670f79c2774f15e5cf\`.
- Third 16-task round peak: **481.914 MiB Working Set / 553.676 MiB Private Bytes**. The previous 64/64 acceptance run recorded 502.059 / 562.102 MiB. This is a favorable single-run comparison, not a controlled A/B or proof of causality.
- **Installed JVM profile:** [run 40339095875](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40339095875), 21/21 SHA-verified transfers, 3/3 resource CSVs and JVM diagnostics captured. Artifact [12659296456](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40339095875/artifacts/12659296456), ZIP SHA-256 \`6267fdaf8dd01fe95725442af4df1f551276a0a1834fbc08b86efac791c80cc4\`. Internal test installer SHA-256: \`4d63cbff6010f4d6f0764acfc359fa269efa10bd0caf51478545f079aa36e380\` (not a release deliverable).
- Same-JVM snapshots: idle baseline 217.715 MiB WS / 243.234 MiB Private Bytes, 51 threads / 668 handles; after transfer 387.211 / 454.027 MiB, 114 threads / 1,008 handles; after 30 seconds idle 385.852 / 452.402 MiB, 87 threads / 904 handles. Heap used decreased from 39,692 KiB to 35,114 KiB. This indicates fewer threads than the earlier profile, but retained process memory remains material; do not claim a leak or a proven memory win from one-run comparisons.
- **Windows lifecycle JUnit at \`319c6aa\`:** [run 40339189560](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40339189560) — Gradle BUILD SUCCESSFUL; \`PartDownloaderIoLifecycleTest\` 3/3 PASS, including permit saturation and release-after-failure. FTP 29/29 and HLS 29/29 also passed on that test head.

### Stronger overflow regression — PASS

Review found the first saturation test asserted a peak of 48 but did not explicitly observe whether a 49th coroutine remained waiting while all permits were held. Added a contender-ready latch plus overflow signal and bounded wait assertion in [\`b0b6e5c1\`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/b0b6e5c14ffd51ee071bda97392ab962a0fb3cb4), with source-contract guard in [\`2859588b\`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/2859588b6056f478b46cd7c189db5164b161b3ad).

- **PASS — exact-head Windows lifecycle JUnit:** [run 40350134896](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40350134896), checkout SHA \`2859588b6056f478b46cd7c189db5164b161b3ad\`; Gradle BUILD SUCCESSFUL. \`PartDownloaderIoLifecycleTest\` 3/3, including the explicit assertion that the 49th contender stays suspended while all 48 permits are held, and release-after-copy-failure. Related suites: lifecycle context 4/4, queue batch resume 7/7, response deadline 4/4.
- **PASS — exact-head source contract:** [run 40350134795](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40350134795).
- The current PR head subsequently advanced for documentation only; the production \`PartDownloader.kt\` blob remains identical to the tested production SHA \`dd27731decbcd8cf1cb67feae42963be42d3bb7a\`.

The strengthened lifecycle regression is accepted. Keep memory tuning OPEN: the measurements show fewer JVM threads and somewhat lower active memory in this run, but are not a controlled A/B.

### Decision

The production body-copy bound has passed installed HTTP integrity/concurrency and the JVM profiling workload on its production SHA, and the initial lifecycle test passed. Keep the \`Semaphore(48)\` experiment pending the strengthened exact-head JUnit result. Memory optimization remains open; no IDM parity claim, final branded \`HyperGet.exe\`, cross-version upgrade/rollback, external HTTPS/FTP, HLS restart, or independent final Windows recheck is claimed. No main write, merge, release, paid service/runner, or Torrent.


## Controlled A/B decision — 2026-10-27

The later paired run [40485436333](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40485436333) is stronger than the earlier standalone PASS: it completed 126 SHA-verified downloads and 18 resource rounds, then rejected `Semaphore(48)` because 16-way throughput fell `22.45%` (3.728 → 2.891 MiB/s), beyond the predeclared 15% limit. Peak Working Set and Private Bytes fell 5.57% and 5.36%, respectively, but the throughput gate takes precedence. Artifact [12727621253](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40485436333/artifacts/12727621253), SHA-256 `339eb61ee276cedae563cc4524c5702a3cc04d5943b727814433dc1c0065a267`.

Decision: do not accept the 48-copy cap. The next bounded trial uses `PART_BODY_COPY_PARALLELISM=60` in production commit `c99b6f627de5f1ef1cba9e79f4f8e1baba20ffe1`, with saturation tests adjusted for the 61st waiter. Exact-head Windows A/B and the same 15% gates remain **NOT TESTED** for this trial. If cap 60 still fails throughput, revert the cap rather than relaxing acceptance.
