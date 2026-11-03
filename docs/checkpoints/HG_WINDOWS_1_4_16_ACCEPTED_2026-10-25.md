# HyperGet — Windows 1/4/16 installed transfer milestone — VERIFIED 2026-10-25

## Functional acceptance question and why Windows compilation/install was required
Can the actual Windows-installed downloader run **1, 4 and 16 distinct simultaneously transferring HTTP tasks**, three independent rounds each, with no silent missing transfers, correct file bytes, and process-tree CPU/RAM measurements? Expected: **63/63 SHA-256 matches**, nine rounds with 1/4/16 active tasks, and **9/9 CPU/RAM CSVs**. Python source tests alone cannot establish actual Windows installer behavior, process scheduling, network sockets, or JVM/GUI memory use. The existing milestone workflow performed one NSIS build and one clean isolated install; no user-facing release.

## Evidence (exact production SHA)
- **Source SHA:** `d82ecabd09a5d699fbb13435268899fde60ca84a`.
- **PASS:** [Windows installed benchmark run 40153251388](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40153251388), job 121049039999, completed 2026-10-25 UTC. Every step passed, including NSIS build, isolated install, real transfer matrix, report audit, artifact upload.
- **PASS:** 1 task × 3 rounds = **3/3** verified; 4 tasks × 3 rounds = **12/12** verified; 16 tasks × 3 rounds = **48/48** verified. **Total 63/63 distinct downloads**, SHA-256 equal to local HTTP fixture, with all nine expected measurement CSVs and an 8-sample measurement window per round.
- **PASS:** [Windows benchmark evidence artifact 12584301199](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40153251388/artifacts/12584301199), 13 files, 11,408 bytes, reported artifact ZIP SHA-256 `03849843fe986ca284f353d29a7dce6ec9568fc8612411c4d6a336550f87c935`; GitHub currently reports `expired=false` (retention configured to 7 days).
- **Installer SHA-256:** `5b97bc2a3b31b94cd76219a318d1fc15e1326c98c854b3033b2151ca9b3ca53d`. This was an internal NSIS test installer, **not** final `HyperGet.exe`.
- **PASS:** [HLS Windows JUnit run 40153251401](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40153251401) on the same source SHA; targeted Gradle test compile succeeded and the six-suite/29-test/zero-failure-or-skip workflow gate passed. [JUnit artifact 12583019215](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40153251401/artifacts/12583019215).

## Measured resource use (Windows Server 2022 GitHub runner; 4 logical CPUs)
- 1 concurrent: peak Working Set across 3 rounds **310.027 MiB**, peak machine CPU **15.316%**.
- 4 concurrent: peak Working Set **337.770 MiB**, peak machine CPU **23.302%**.
- 16 concurrent: peak Working Set **510.688 MiB**, peak Private Bytes **577.164 MiB**, peak machine CPU **36.611%**.
- These are runner-specific process-tree measurements, not measurements on the user's MSI laptop. CPU is percentage of total machine CPU; Working Set and Private Bytes are different metrics. No IDM performance baseline was measured, and memory optimization remains open.

## Outcome and remaining requirements
**PASS for this bounded HTTP concurrency milestone (3/3 acceptance categories):** installed Windows transfers, 63 file-integrity checks, nine process-metric CSVs. Previous 16-task fixture instability was resolved enough for this exact SHA/run; no claim that all future networks and devices are error-free.

**NOT TESTED for release:** FTP live Pause/REST/resume and crash recovery on this exact SHA; HTTPS remote servers; HLS installed GUI after forced kill/restart; download URL refresh; scheduler; browser/native-host E2E; Liquid Progress visual comparison; full HyperGet icon/installer identity and legacy upgrade; independent second Windows device recheck; single verified `HyperGet.exe`. Installer still installs `DownloadManager.exe` and `DownloadManagerCli.exe` in this run: naming migration is not complete.

## Next single milestone
**FTP live Pause/REST/resume on installed Windows**: interrupt RETR mid-stream; assert staged bytes 0 < staged < full; resume via REST at exact staged length; compare SHA-256; exercise Windows credential restore, conflicting destination, and cancellation near completion. Source tests plus real local FTP Windows run are required. No build should be repeated without a changed FTP-specific test question. Do not loosen acceptance gates to obtain green CI.

## Governance
PR #3 remains Draft on `automation/idm-ftp-cancel-and-category-flush`. No direct main writes, auto-merge, paid runners, Torrent, unrelated projects, or release. GitHub is code/evidence authority. Handoff synchronization is a separate administrative concern and is not performed by this development run.
