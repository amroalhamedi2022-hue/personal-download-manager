# HG-HTTP-SHARED-IO-01 — bounded thread use and cancellation-safe HTTP part streaming (2026-10-26)

## Acceptance question defined before the targeted Windows runs

Can HyperGet replace one newly-created raw JVM thread per HTTP range part with the already-declared Kotlin coroutines Dispatchers.IO, while preserving stop semantics and the installed 1/4/16 download contract?

Expected:
- The part body copy runs on the shared blocking-I/O dispatcher; no raw thread is created per part.
- stop() closes the active response source before cancelling the part scope, so even a blocking Source that ignores Thread.interrupt() can release.
- A connection returned at the stop race edge is closed rather than leaked.
- The installed Windows benchmark still reaches 1, 4 and 16 active downloads, completes all 63 fixture files with exact SHA-256, and produces 9/9 CPU/RAM CSVs.
- Compare process-tree peak threads, handles, Working Set and Private Bytes with the prior capped-dispatcher baseline. Fewer threads are expected; memory reduction is a measured outcome, not guaranteed.

Source tests alone cannot prove that a real Windows socket unblocks on close, that all 16 tasks reach active transfer, that file contents remain exact, or that the JVM process tree uses fewer resources. A targeted Windows JUnit run plus the installed benchmark and post-transfer JVM profile are therefore justified. This is a functional/performance experiment, not a release build.

## Baseline and evidence

At exact source SHA 587fb010973a7a8358d06e10b5c64e2bdd5d1afe, installed Windows matrix [40244684936](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40244684936) passed 63/63 SHA-verified transfers and 9/9 CPU/RAM samples. After the 64/64 OkHttp dispatcher cap, 16-way round 3 peak was 277 threads, 2,003 handles, 522.113 MiB Working Set and 582.375 MiB Private Bytes. This was a thread/handle improvement, not a memory optimization claim.

The latest completed NMT profile [40249082722](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40249082722) recorded 431.551 MiB Working Set, 473.309 MiB Private Bytes, 77 OS threads and 826 handles post-transfer; heap and native categories did not explain all private bytes. The profile and active matrix must be rerun on this production change before changing heap settings or making a memory claim.

Reference used before implementation: the official [Kotlin Dispatchers.IO API](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-i-o.html) describes a shared pool for blocking I/O and recommends replacing manually created thread-backed executors with IO dispatcher views. This patch adds no dependency and copies no third-party implementation; it uses the project's existing kotlinx.coroutines dependency.

## Production change and regression

- Production commit [b811f2c](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/b811f2cd0b25adbc4115376b0db2900b909739ba): remove kotlin.concurrent.thread and the per-part Thread field; execute blocking body copy inside withContext(Dispatchers.IO); register the active Connection under a lifecycle lock; close it before cancelling the worker; close a connection returned after stop wins the registration race; remove thread-interrupt checks from the copy loop.
- Runtime regression [PartDownloaderIoLifecycleTest.kt](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/automation/idm-ftp-cancel-and-category-flush/downloader/core/src/desktopTest/kotlin/ir/amirab/downloader/part/PartDownloaderIoLifecycleTest.kt): a deliberately blocking Okio Source ignores thread interrupts and only releases when closed. The test requires stop() to close the source and join() to finish within two seconds.
- Source contract [test_hyperget_part_downloader_io_lifecycle_source.py](https://github.com/amroalhamedi2022-hue/personal-download-manager/blob/automation/idm-ftp-cancel-and-category-flush/scripts/source-tests/test_hyperget_part_downloader_io_lifecycle_source.py): guards shared-dispatcher usage, close-before-cancel ordering, connection registration race handling, and the required Windows benchmark/profile path gates.

## Exact-head verification plan

- Source-only contracts: quick Python checks; no application build.
- Windows lifecycle/JUnit: compile the production Kotlin source and run the blocking-source cancellation regression; no EXE/installer.
- Installed active HTTP matrix: one internal NSIS test installer, 63 SHA-256 transfers, 1/4/16 active task counts, 9 resource CSVs.
- Installed JVM profile: after the transfer samples, capture JVM heap/class/thread and NMT diagnostics to compare thread and native-memory categories without contaminating the active benchmark.

Current code/CI head when this checkpoint was written: 9a33736e202d34dbea5dedbc20d8b8542055d01a. The Windows runs were triggered by the production/workflow changes and are pending exact artifact inspection at checkpoint creation; do not claim resource improvement until their artifacts are inspected.

## Remaining gates

- If JUnit cancellation or connection-race test fails: fix the production lifecycle before interpreting performance results.
- If any transfer hash or active-task gate fails: revert/tune the dispatcher change without weakening thresholds.
- If threads fall but Private Bytes do not: report the result as thread reduction only and inspect NMT before further memory changes.
- HTTPS external endpoint, refreshed URL, scheduler, installed HLS kill/relaunch, full browser-to-host exact-head run, cross-version upgrade/data preservation, final HyperGet icon/installer identity, single deliverable HyperGet.exe, and independent final Windows recheck remain separate open acceptance gates.

No main write, merge, release, paid API/runner, or Torrent functionality.
