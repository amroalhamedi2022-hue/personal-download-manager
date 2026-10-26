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
