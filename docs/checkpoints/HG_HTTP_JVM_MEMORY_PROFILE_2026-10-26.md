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
