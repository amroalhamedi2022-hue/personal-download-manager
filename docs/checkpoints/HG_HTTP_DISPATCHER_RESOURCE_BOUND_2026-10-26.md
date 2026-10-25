# HG-HTTP-DISPATCHER-RESOURCE-BOUND-01 — bound OkHttp request dispatch (2026-10-26)

## Exact baseline evidence
Installed Windows benchmark [40242626125](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40242626125), source SHA `dd86e7375e06be9b397a6b3013efc33897bd7de7`, **PASS**: 63/63 32 MiB files SHA-256 verified, 9/9 process CPU/RAM CSVs, three rounds each at 1/4/16 active downloads. Test installer SHA-256 `4e2aa04c2f207cbc1a33b6f60dd2fab3a5125e88caf8046ba11a352d6f2682b7`; artifact ZIP SHA-256 `aceb60e1f993ed9ada0792ec55c59aa1c02ce2d16f106e179f35269b75eea21a`.

The same exact report shows 16-way round 3 peak Working Set **527.09 MiB**, Private Bytes **590.309 MiB**, **336 threads** and **2,229 handles**. At 1-way the peak was 312.117 MiB Working Set / 324.797 MiB Private Bytes; at 4-way 335.957 / 358.293 MiB. No IDM baseline exists in this run.

## Production change
Replace `Int.MAX_VALUE` global and per-host OkHttp dispatcher limits with **64 / 64**. The intent is to bound the asynchronous request/callback burst, not reduce the user's 1/4/16 download count or alter per-download range settings. Add a Python source contract and wire it into source-only CI and the installed Windows benchmark preflight. The benchmark's scope gate must recognize the production DI path so it cannot silently skip the exact-head test.

## Purposeful Windows test defined before trigger
**Question:** does the finite dispatcher cap preserve 16 distinct active tasks, all 63 exact SHA-256 files, and nine process samples while reducing peak threads and/or memory?
**Expected:** strict transfer/resource evidence remains valid; lower thread count is expected but not guaranteed until measured. Source tests cannot validate real OkHttp call scheduling, Windows sockets, file contents or process-tree metrics.
**Status at checkpoint creation:** NOT TESTED on the new production SHA. Do not claim a resource improvement until the installed Windows artifact is inspected. If active-task fairness or hashes fail, tune/revert the cap without relaxing acceptance; if memory is unchanged, gather JVM heap/thread evidence before further code changes.

No main write, merge, release, paid service or Torrent.
