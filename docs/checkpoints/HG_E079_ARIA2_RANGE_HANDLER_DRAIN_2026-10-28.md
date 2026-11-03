# HG-E079 — aria2 HTTP range-handler drain and segmentation gate

Date: 2026-10-28  
PR: [#3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3)  
Branch: `automation/idm-ftp-cancel-and-category-flush`  
Exact source SHA before harness repair: `76911e2c701c5d0808df8de29ce5d61b3165d306`

## Observed result

The purposeful Windows run [40689694857](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40689694857) built and installed the exact-head HyperGet candidate. The HyperGet side completed 21/21 expected-size/SHA-256 HTTP outputs at concurrency 1/4/16 and recorded three CPU/RAM measurement CSVs. That is a HyperGet workload PASS, not an aria2 comparison result.

The aria2 side downloaded one 32 MiB output with the expected size and SHA-256, then failed at the final loopback-counter assertion. Its diagnostic snapshot showed 8 started GET handlers (one initial full request plus seven Range requests), 32 MiB served, 60 MiB requested across the overlapping request headers, and 8 server handlers still active when the aria2 process had already exited. The script sampled the counters immediately after process exit. Therefore this run **did not prove that the Range handlers had finished**, and no paired throughput or memory decision can be made from it.

Evidence artifact: [hyperget-aria2-stage2-comparison](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40689694857/artifacts/12816853328). The GitHub artifact archive digest is `sha256:245acc7f6785464cc603ad1e95d42c5dfd2a14ae3ac3725c2094bbefbe398dc8`; the installer SHA recorded by the job was `7babd9f2653ebcf329204d42c4d78ac0c4e8ad14836a0fdbf9476d648b4166ce`. These are different files and must not be conflated.

## Controlled repair

- Wait up to 20 seconds for loopback GET handlers and active-task counters to drain after aria2 exits.
- Require each task's started GET count to reconcile exactly with completed plus interrupted handlers.
- Require each task to serve exactly the fixture byte count, and require the expected number of honored Range requests plus completed range GETs for the selected `--split` value.
- Keep exact output-size/SHA-256 checks, true 1/4/16 task overlap, process CPU/RAM measurements, and the existing Stage 2 thresholds unchanged.
- Add source-contract assertions for the new drain and segmentation gates.

## Pre-build acceptance question

On the exact updated PR head, does the Windows runner finish the loopback handler lifecycle and prove the actual requested Range transfers before recording each aria2 measurement, then complete all three paired 1/4/16 rounds and the 1/4/8 single-file sweep?

**Expected:** the source contracts and PowerShell parser pass; each output matches the fixture SHA-256; each task's HTTP byte count and completed/interrupted counters reconcile; actual range requests complete; all 63 outputs per engine and all nine resource sets per engine are present; the unchanged performance thresholds produce a clear GO or NO-GO.

**Why source checks are insufficient:** static assertions cannot prove Windows socket shutdown, response-body completion, actual HTTP Range behavior, file integrity, concurrent task overlap, or process resource usage. A fresh exact-head Windows run is required.

## Status

- HyperGet installed HTTP workload from the previous run: **PASS** for 21 outputs at 1/4/16 and three resource samples.
- aria2 single HTTP output: **PASS** for expected size/SHA-256, but segmentation lifecycle: **NOT TESTED / previous gate failed before it could be accepted**.
- Paired Stage 2 throughput/memory decision: **NOT TESTED**.
- Production engine migration and final `HyperGet.exe` delivery: **NOT AUTHORIZED by this evidence**.
- Torrent remains prohibited; stock aria2 is evaluation-only and must never be bundled.
