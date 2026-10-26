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
