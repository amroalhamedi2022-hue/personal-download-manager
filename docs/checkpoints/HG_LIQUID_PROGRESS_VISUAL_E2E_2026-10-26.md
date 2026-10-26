# HG-UI-LIQUID-PROGRESS-01 — shared Liquid Progress + installed Windows visual acceptance
Date: 2026-10-26
PR: #3
Branch: automation/idm-ftp-cancel-and-category-flush
Base: codex/repair-rf00-rf01

## Previous milestone continuity
The current PR head before this milestone was 3581ad1d98c4219bf0725dd07fae170311f222e5. Exact-head checks associated with that SHA were terminal-success for source-only, installed HTTP 1/4/16, JVM memory profile, Chrome+Firefox capture, FTP Kotlin, HLS, queue/lifecycle, and installed FTP pause/REST/resume. HTTP evidence remains 63/63 SHA-verified files and 9/9 process CSVs. Memory is still not closed: the full 16-way peak Private Bytes was previously measured at 590.879 MiB, so this UI change must not be described as a memory optimization.

## Required functionality
Replace the flat status strip with a restrained Liquid Progress treatment in the main download list:
- retain determinate width and status-specific theme gradient;
- animate a soft glossy band and two small bubbles only while status is Downloading;
- share one list-level animation clock, created only while at least one download is active;
- read the shared phase in Canvas draw code, not by running one infinite transition/recomposition loop per row;
- keep the existing left-side percent/status label and do not add a detached right-side percent;
- capture a real installed Windows screenshot during a real transfer and verify the completed 16 MiB fixture by SHA-256.

## Test question, expected result, and why source-only is insufficient
Question: Does the exact PR source compile and install on Windows, visibly render the moving liquid treatment while the installed CLI and loopback fixture prove a live Downloading state, and still complete the actual file byte-for-byte?
Expected: the installed GUI is detected and foregrounded; the screenshot is captured while status is Downloading and at least one real data task is active; the 16 MiB output finishes with SHA-256 equal to the fixture; the workflow uploads liquid-progress-active.png and a JSON report containing the exact source SHA, status at capture, byte counts, and both hashes.
Source contracts cannot prove Kotlin/Compose API compatibility, Windows native GUI capture, IPC to the installed process, real network transfer, or final file integrity. One purposeful installer build is therefore justified for this visual milestone; it is not a release build.

## Implementation and regression protection
- TableDownloadItem.kt: shared phase State<Float> is consumed in the Canvas draw phase; determinate width retains a short tween; active-only glossy band and bubbles are clipped to the pill track.
- DownloadList.kt: creates one 1.8-second linear infinite transition only when at least one row is Downloading and provides the State to rows.
- test_hyperget_liquid_progress_source.py: protects shared-clock ownership, draw-phase reads, active-only rendering, live-transfer screenshot gate, SHA verification and artifact upload wiring.
- windows-installed-ui-smoke.yml: PR-triggered exact-head Windows build/install and visual E2E, guarded so docs/test-only follow-up commits skip expensive packaging.
- No new dependency was added. The implementation uses existing Compose APIs; official animation guidance is https://developer.android.com/develop/ui/compose/animation/quick-guide and the existing AndroidX progress-indicator reference implementation is Apache-2.0 at https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/compose/material/material/src/commonMain/kotlin/androidx/compose/material/ProgressIndicator.kt.

## Acceptance status at commit creation
- Source-only contract: NOT TESTED until CI completes.
- Kotlin/Compose compile and NSIS install: NOT TESTED until the Windows workflow completes.
- Live screenshot, active Downloading status, 16 MiB final SHA-256: NOT TESTED until the Windows workflow completes and its artifact is inspected.
- Existing HTTP 1/4/16, FTP, HLS, browser and memory evidence is historical exact-SHA evidence, not retroactively proof for this UI commit.
- No main write, merge, auto-merge, paid runner/API, release, or Torrent.
