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

## Verified Windows result (same milestone)

## VERIFIED — 2026-10-24 HG-HLS-RESUME-03 persisted job relocation and safe assembly

**Code + tests exact SHA:** `4707652d386b621f30fbae33d2d21dd858625d7a` on draft PR #3. **Windows PASS:** [targeted run 40128335181](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40128335181), Windows 2022, JDK 25, targeted `:downloader:core:desktopTest`; log: `BUILD SUCCESSFUL in 1m 33s`, 32 Gradle tasks executed. Acceptance gate verified **six JUnit XML suites**, **at least 27 tests**, **zero failures/errors/skips**. Includes new persisted HLSDownloadJob boot/resume fixture with redirected playlist, previously completed old stages, actual new segment downloads and SHA-256 comparison, plus safe assembly negative tests for missing stages and existing outputs. This is a real Windows loopback network/JVM test, **not** a killed/restarted installed HyperGet.exe.

**Source-only PASS:** [run 40128335196](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40128335196), same exact code SHA; new six-case structural contract and existing FTP/HLS source checks green. **JUnit evidence:** [artifact 12574555538](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40128335181/artifacts/12574555538), six XML files uploaded, 4,176-byte ZIP, artifact digest SHA-256 `836387f97cc63af3b868400b3395e26418a6a3fb87a05314c8f4362164b65ae9` (this is the test ZIP, **not HyperGet.exe**). Artifact content has not been independently re-parsed outside the runner; on-runner XML gate passed.

**Production behavior:** `SegmentedDownloadDestination` revalidates completed staged segments immediately before finalization, assembles into a same-directory temporary file, checks copied lengths, then moves into place without an overwrite flag. On failure it removes the temporary assembly and leaves existing output/staged input intact. `HLSDownloadJob` existing playlist reconciliation now has a real job-level Windows regression confirming relocated segment identity forces fresh bytes rather than old completed stage reuse.

**Remaining/risks:** No true process-kill/restart of installed EXE, NTFS hostile stage replacement, network share ACL/TOCTOU, same-URL mutated media content with trusted validators, FTP Pause/REST and strict active-socket 1/4/16, CPU/RAM, Liquid Progress, icon, extension E2E, upgrade, single HyperGet.exe or independent final-product recheck. Gradle warned about deprecated features before Gradle 10; compilation succeeded. No new dependency or paid service, no Torrent, main write, merge or release. Browser PR #1 remains separate/draft. **Overall: IN_PROGRESS.**

