# HG-HTTP-JVM-MEMORY-PROFILE-01 — inspect the installed JVM before another memory rewrite (2026-10-26)

## Evidence prompting the profile
At exact source SHA `587fb010973a7a8358d06e10b5c64e2bdd5d1afe`, Windows installed matrix [40244684936](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40244684936) passed 63/63 SHA-verified files and 9/9 process metrics. The 64/64 OkHttp dispatcher cap reduced 16-way peak threads 336→277 and handles 2,229→2,003, but peak Private Bytes only fell 590.309→582.375 MiB. The remaining memory is too high to call optimized.

## Purposeful diagnostic question (recorded before build)
Is the remaining memory mainly committed/used Java heap, retained object classes, native thread stacks, or other JVM/native allocations? Source tests cannot reveal the running JVM's heap or object histogram.

## Planned Windows test
The new installed diagnostic workflow builds and installs one internal NSIS test package, performs one real 1/4/16 round (21 files, exact SHA-256; three process CSVs), then runs the JDK's `jcmd` against the installed GUI to capture `VM.version`, `GC.heap_info`, `GC.class_histogram`, `Thread.print`, and a process snapshot. These diagnostics run after transfer measurements so a class-histogram GC cannot contaminate the active benchmark samples.

Expected: all 21 file hashes and three CSVs pass, and profile files are uploaded. If attach fails, the artifact should retain the command's normalized output and the failure should be investigated before any further production tuning.

## Status at checkpoint creation
- Dispatcher cap exact-head matrix: PASS; artifact [12618717214](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40244684936/artifacts/12618717214).
- Source-only and browser/FTP/HLS/queue regressions: PASS on the same SHA.
- JVM profile: NOT TESTED yet on the new workflow SHA.
- Product release remains NOT READY: HTTPS remote, refreshed URL, scheduler, installed HLS kill/relaunch, Liquid Progress visuals, final branding/icon, upgrade/uninstall preservation, independent Windows release recheck and final single `HyperGet.exe` remain open.

No main write, merge, release, paid service or Torrent.


## Profile preflight failure and repair
The first profile workflow [40246220683](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40246220683) stopped in preflight because the source test compared the first textual occurrence of the script names; the profile script appeared in the parser step before the transfer command. No build/profile ran. The test now compares actual PowerShell invocation lines, and the profile workflow includes the source-contract path so the repaired preflight is revalidated before packaging. Exact-head profile result remains pending.


## JVM profile attempt: harness lifetime issue fixed
Run [40246308828](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40246308828) built and installed the app and passed **21/21 SHA hashes + 3/3 resource CSVs**, but profile collection failed because Run-WindowsActiveBenchmarkMatrix.ps1 deliberately stopped the GUI in its finally block. This is not a production app failure and no jcmd output was captured. Added opt-in -KeepGuiRunning; normal benchmark cleanup remains the default, while the diagnostic workflow now preserves the GUI for post-transfer jcmd. New exact-head run pending.


## JVM target PID correction
The second attempt [40247117339](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40247117339) passed 21/21 SHA + 3/3 metrics and preserved the GUI, but the JVM attach failed because the script targeted the NSIS native launcher (PID 552), not the HotSpot JVM. Artifact [12619336316](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40247117339/artifacts/12619336316) shows `jcmd -l` listed JVM PID 1336 as `com.abdownloadmanager.desktop.AppKt`; the launcher correctly reported `jvm.dll not loaded by target process`. Updated the profiler to parse and target the actual JVM main-class PID. New exact-head run pending.


## JVM evidence and next NMT pass
Artifact [12619073707](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40247117339), ZIP SHA-256 `484aaecc6e8d6d5720254f4393aa92b3c854d005cb0c632a38acb3ab6b18f180`, contains successful `VM.version`, `GC.heap_info`, `GC.class_histogram` and `Thread.print` outputs even though the workflow failed on a stale status-line variable. The 21/21 SHA and 3/3 metric checks passed. At post-transfer snapshot, the actual JVM process had 421.441 MiB Working Set, 462.977 MiB Private Bytes, 71 OS threads and 818 handles. G1 heap was 126,976 KiB committed / 75,859 KiB used; histogram total was 35,410,368 bytes across 649,486 objects; the JVM dump had 55 threads. Most private memory is not explained by committed heap or live object histogram, so native memory categories must be inspected before tuning further.

Fixed the final status field to `JvmProcessId=$javaPid`. The next diagnostic run enables `-XX:NativeMemoryTracking=summary` only when launching the GUI, restores the environment before CLI commands, and adds `VM.flags` + `VM.native_memory summary`. The NMT run is diagnostic and not a comparable performance benchmark.


## NMT profile result — PASS
Installed profile workflow [40249082722](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40249082722), source SHA `9f5577a2d557aed3223a04749187f316b2decb88`, **PASS**: 21/21 file hashes, 3/3 resource CSVs and all JVM profile commands. Installer SHA-256 `213de5aad39f985d0c511bc7c075e6ebd18b6d24e43f0219ef9d25e8729e19f1`; artifact ZIP SHA-256 `c52829ffa62d6bdd145dc9cb89c7228657511b02428d25eab61de68f2d4d1848`.

Post-transfer process snapshot: 431.551 MiB Working Set, 473.309 MiB Private Bytes, 77 OS threads, 826 handles. G1 heap 122,880 KiB committed / 70,204 KiB used; class histogram 34,716,776 bytes / 636,147 objects. NMT total committed 339,598 KiB (~331.6 MiB), leaving about 141.7 MiB of process Private Bytes outside the NMT committed total in this sample. NMT categories: Java Heap 120 MiB; Metaspace 76,766 KiB (also represented under Class, so don't double count); Code 46,821 KiB; GC 45,611 KiB; Symbol 19,541 KiB; Thread 4,949 KiB. The JVM reserved 4 GiB heap address space but had only 120 MiB committed, so Xmx reduction is not yet justified. Skia cleanable wrappers are numerous, but one snapshot cannot prove a leak.

Next: capture an NMT-enabled idle baseline and compare with a fresh post-transfer process to distinguish startup/native graphics allocations from transfer-related retained objects.


## HG-UI-RELATIVE-TIME-CHURN-01 — production change queued for measured validation

The idle/post NMT profile [40250121436](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40250121436) passed 21/21 SHA + 3/3 metrics. Private Bytes grew 247.590→501.965 MiB; NMT committed 216,238→338,879 KiB; heap used 45,341→90,541 KiB; Skia CleanableImpl 1,402→9,250; Compose LayoutNode 308→1,725. The source has an avoidable once-per-second relative-date refresh loop in visible desktop download rows.

Changed the shared refresh helper to wake at the next display-unit boundary and avoid state writes when the formatted label is unchanged. Added four deterministic commonTest cases and a Python source contract. The new targeted workflow runs `:shared:app:desktopTest` without packaging; installed Windows HTTP acceptance and NMT before/after profiling are also triggered for these production files. Result is pending at checkpoint creation; do not claim a memory improvement before comparing the artifacts.


## Relative-time test expectation corrected
The first targeted desktop test run [40251458528](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40251458528) compiled the code and ran 32 tests; 31 passed. One new test incorrectly expected a 2-second wait at exactly 58 seconds, where the next seconds label boundary is 1 second. Corrected the expected value to 1 second and added an explicit 500 ms boundary assertion. This is a test expectation fix, not a production compile defect; rerun pending.


## Relative-time optimization — exact runtime outcome
The common desktop test workflow [40251917222](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40251917222) passed after correcting the one incorrect expected delay. Installed HTTP matrix [40251458455](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40251458455) passed 63/63 SHA and 9/9 CPU/RAM. Artifact ZIP SHA-256 `f1ff63ef8447e5d70e4f3d57f9f3b0d5c63aa6228ddb6eff9efdccc0d3b92ea3`.

CPU decreased in final rounds at 1/4/16 workloads, but the full 16-way peak Private Bytes rose slightly 582.375→590.879 MiB. The NMT one-round profile [40251917240](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40251917240) passed 21/21 SHA + 3/3 metrics; post Private Bytes 462.977 MiB vs prior 501.965 MiB, NMT committed 317,587 KiB vs 338,879 KiB. Yet Skia CleanableImpl count increased 10,538→20,710 while total histogram bytes stayed close (~35 MB). This is mixed evidence: retain the lower-frequency relative-date updates for reduced periodic CPU work, but memory is still NOT CLOSED and requires repeatable profiling/retention analysis.

## Memory profile workflow scope-gate repair
Run [40252512178](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40252512178) failed with no runnable jobs because the change-scope block in the workflow was malformed. Rebuilt the complete jobs block and switched from `git diff HEAD^ HEAD` (requires parent history) to `git show --format= --name-only HEAD`, which works with the default shallow checkout. Added source assertions for the complete conditional and `needs` dependency. Current fix changes workflow/tests/docs only; expensive profile should skip while source-only CI validates the gate.


## Scope gate aligned with the working Windows benchmark
The profile workflow validation run [40253378123](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40253378123) failed before creating jobs. Replaced the scope block with the same known-good structure used by `windows-installed-active-benchmark.yml`: checkout `fetch-depth: 2`, diff only `HEAD^..HEAD`, and a shell `case` allowlist. The scope test now asserts those pieces. This commit changes workflow/tests/docs only, so no expensive package should run.


## Duplicate job removed from profile workflow
The previous scope-gate rewrite had preserved a duplicate second `installed-jvm-profile` job and an orphaned shell tail after the first job's artifact-upload step. Removed everything after the first complete artifact-upload retention line and strengthened source checks to assert exactly one installed profile job and no trailing shell fragment. This is workflow-only cleanup; no app code changed.


## Source-contract assertion aligned to case allowlist
Source-only run [40254048011](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40254048011) failed because the profile source test still expected `else/fi` after the workflow scope gate had been changed to a `case/esac` allowlist. The workflow scope gate itself succeeded and skipped the expensive job for a docs/test-only commit. Updated the assertion to match the actual case block; this is test-only.


## Relative-time unit workflow scope gate
Added the same tested latest-commit scope pattern to the targeted desktop unit-test workflow: checkout depth 2, diff `HEAD^..HEAD`, case allowlist for TimeUtil, its commonTest, and TableDownloadItem; manual dispatch still forces a run. This avoids repeated Gradle compile/test jobs for docs-only follow-up commits. The source contract validates the gate.
