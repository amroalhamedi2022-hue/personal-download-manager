# HG-E080 — aria2 segmented HTTP socket-write accounting

Date: 2026-10-28  
PR: [#3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3)  
Base: `codex/repair-rf00-rf01`  
Working head: `automation/idm-ftp-cancel-and-category-flush`  
Parent SHA: `b91ee130a77f79068d3c2a0c4d1915ae3345e7d2`

## Purposeful Windows test question

**Can the exact-head Windows run complete the full paired HyperGet-versus-aria2 comparison (three alternating rounds at 1/4/16, 63 SHA-256-verified outputs per engine) and the 1/4/8 single-file segmentation sweep without rejecting a correct aria2 transfer because the local fixture's socket-write counter is not a peer-delivery counter?**

Expected: all full data Range responses complete and are honored; lifecycle counters reconcile; output size and SHA-256 match; true concurrent-task overlap is proved; resource CSVs are present; the unchanged throughput/memory thresholds yield a defensible GO or NO-GO.

Source-only tests cannot prove Windows socket teardown, HTTP Range response completion, final downloaded bytes, actual task overlap, or process CPU/memory. A fresh Windows build/install/run is therefore justified; it is not a release build.

## Exact-head evidence that exposed the defect

Windows evaluation run [40695776974](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40695776974) tested source `b91ee130a77f79068d3c2a0c4d1915ae3345e7d2`.

- **PASS:** pinned aria2 archive verification and Stage 1 runtime gate.
- **PASS:** exact-head HyperGet desktop/downloader regression suites, JUnit XML gate (zero failures/errors/skips), and Windows installer build. Installer SHA-256: `194d0303f190ee44e5fdb1bc6ea08ce7f33c27e6826beb0ec32ba472dbfd26b3`. This is a test installer, not the final standalone deliverable.
- **PASS:** HyperGet's first paired round produced 21/21 expected-size/SHA-256 HTTP outputs at 1/4/16.
- **FAIL — acceptance harness metric, not a proven engine defect:** aria2 produced the exact 32 MiB file with fixture SHA-256 `e09320c5b00b34bb704802136c599a95b3996332ba84d7c7f21112b6231b6bd0`, but the script rejected `33,619,968` socket-write bytes against an exact `33,554,432` payload expectation. The difference is exactly one 64-KiB chunk. The fixture observed eight GETs: one initial non-Range full GET that was interrupted and seven honored Range requests; seven GETs completed and one was interrupted. The artifact shows the requested Range bytes overlap the initial full GET, which is expected while aria2 switches to segmented transfer.
- The fixture increments `bytes_sent` after `wfile.write/flush`; on Windows, a final chunk can be accepted into the local socket buffer even after the client has canceled that response. That counter is therefore **socket-writer accounting**, not proof that the peer consumed every counted byte. Exact byte equality was an invalid gate. The failed run stopped in round 1 at concurrency 1, so it produced no accepted paired performance result and did not complete the 1/4/8 sweep.

Evidence artifact [hyperget-aria2-stage2-comparison](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40695776974/artifacts/12819633427), archive digest `sha256:d606a8204ae2bcae45eed4053d1783558358143d9fa183161db7bf9c524d9f1d`. The installer artifact is separate: [hyperget-windows-x64-installer](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40695776974/artifacts/12819727691), archive digest `sha256:9713496acba7b2ee72db1744184925d00717504f52a0901668e8238499b3e8a6`.

## Controlled repair in this commit

- The loopback fixture records per-request `data_transfer`, `completed`, and local socket-write bytes, so tiny metadata probes cannot count as completed data segments.
- The PowerShell gate requires the expected number of **completed and honored data Range** requests and rejects any incomplete data Range response.
- The byte counter now permits at most one 64-KiB buffered-write overcount per interrupted GET; undercount or larger unexplained overfetch still fails. Final file size/SHA, active overlap, lifecycle reconciliation, CPU/RAM sampling, and the existing performance thresholds are unchanged.
- Source regression tests assert these semantics. No production download engine was changed.

## Remaining acceptance

- Re-run the exact-head Windows workflow. The required outcome is all three paired rounds, all 63 verified outputs per engine, and the 1/4/8 segmentation sweep with resource data.
- Apply the existing decision policy: no workload throughput regression worse than 10%, memory regression at most 15%, and median throughput gain at least +5% for `STAGE2_PERFORMANCE_GO`; otherwise retain HyperGet's current engine.
- aria2's stock evaluation binary has BitTorrent capability, but no Torrent workload is invoked and the binary is not bundled. **Torrent is prohibited in HyperGet.** No production migration until a no-BitTorrent build, GPL-2.0-or-later distribution review, security review, and adapter parity are separately verified.
- Final standalone `HyperGet.exe`, independent Windows recheck, SHA-256, and release-quality delivery remain **NOT TESTED**.
