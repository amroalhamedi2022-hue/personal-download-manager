# HyperGet — HG-HLS-RESUME-03 — persisted job resume and safe output publication
Date: 2026-10-24. PR #3 head baseline: `f56714cff7a643a0421005730ef1d12f4db34ec7`.

## One functional milestone
A paused/crashed HLS job with persisted completed segment records must re-fetch its redirected playlist, reject old completed segments after relocation, download the new bytes using the real DownloadManager/HLSDownloadJob worker stack, and publish exactly the expected SHA-256. Finalization must never expose a truncated output when a completed stage file disappeared or the assembly copy fails; an existing output must not be overwritten.

## Production change and regression scope
- `SegmentedDownloadDestination.onAllPartsCompleted` revalidates every completed staged segment before assembly.
- `assemble` builds in a temporary file beside the output, verifies byte counts and stage-file attributes, then moves into place without replace. Failures clean only the temporary assembly and retain staged inputs.
- `HlsDownloadJobResumeLoopbackTest`: local HTTP redirect and two media bodies, previously completed old stages, persisted DB and real job boot/resume; SHA-256 + new-server request assertions.
- `HlsSegmentAssemblySafetyTest`: successful assembly, missing stage, existing destination preservation.
- Six Python structural checks and targeted Windows Kotlin/JUnit suites; no new runtime dependencies.

## PRE-BUILD QUESTION (recorded before Windows CI)
Does the actual persisted HLS job discard completed old media after a playlist redirect, redownload, and assemble the exact new bytes without corrupting output on stage failure?
**Expected:** six JUnit suites, >=27 tests, zero errors/failures/skips, source contracts green; exact PR SHA in logs and downloadable XML. Source tests alone cannot validate Kotlin APIs, OkHttp sockets, coroutine/job lifecycle, database persistence and filesystem publication. **Authorized build:** targeted free Windows `:downloader:core:desktopTest` only; no EXE, installer or packaging.

## Known limitations and pending gates
- Same URL serving changed bytes without trusted validators remains an open content-integrity risk.
- Stage files can be maliciously swapped during an open read; basic attribute/size checks reduce, not eliminate, TOCTOU. NTFS hardlinks, ACLs, network shares need installed Windows review.
- True OS process kill + restart, FTP Pause/REST, strict 1/4/16 active sockets, CPU/RAM, Liquid Progress, icon/identity/upgrade, extension and final HyperGet.exe are NOT TESTED by this milestone.
- Apache-2.0 existing project + OkHttp/JDK only, no new dependency, paid API, Torrent, main write, merge or release.

**Status at preflight:** PENDING exact-SHA CI. Record PASS/FAIL only after examining logs.
