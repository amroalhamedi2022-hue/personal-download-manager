# HG-HTTP-EXACT-HEAD-RECHECK-01 — exact Windows acceptance (2026-10-26)

## Root cause and fix
The previous exact-head workflow [40241733184](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40241733184) reported green while its real transfer matrix was skipped. The workflow YAML already included its own path under `pull_request.paths`, but the shell `change-scope` allowlist omitted that same path.

Commit [dd86e7375e06be9b397a6b3013efc33897bd7de7](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/dd86e7375e06be9b397a6b3013efc33897bd7de7) added the workflow path to the allowlist, a source regression test, and an acceptance checkpoint before triggering the real matrix. No production downloader source or thresholds were changed.

## Exact-head acceptance result — PASS
Run [40242626125](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40242626125), exact source SHA `dd86e7375e06be9b397a6b3013efc33897bd7de7`:
- Internal NSIS test installer built and installed on Windows Server 2022.
- **63/63** 32 MiB transfers completed and matched the fixture SHA-256.
- **9/9** process CPU/RAM CSVs validated; three rounds at each workload 1/4/16.
- Real distinct active tasks reached 1, 4 and 16; the 16-task fixture observed 128 simultaneous HTTP connections.
- Installer SHA-256: `4e2aa04c2f207cbc1a33b6f60dd2fab3a5125e88caf8046ba11a352d6f2682b7`.
- Artifact [12617872801](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40242626125/artifacts/12617872801); downloaded ZIP SHA-256: `aceb60e1f993ed9ada0792ec55c59aa1c02ce2d16f106e179f35269b75eea21a`.

## Performance observation
Correctness passed, but memory/thread use remains a real follow-up: 16-way round 3 peak Working Set **527.09 MiB**, peak Private Bytes **590.309 MiB**, **336 threads**, **2,229 handles**. 1-way peak was 312.117 MiB Working Set / 324.797 MiB Private Bytes; 4-way was 335.957 / 358.293 MiB. This is not an IDM comparison and does not prove a memory target. Investigate the combination of per-part blocking copy threads and the OkHttp dispatcher before tuning; retain the strict 63/63, 9/9 and 16-distinct-task acceptance.

## Other exact-head checks
- Source-only [40242626137](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40242626137): PASS.
- FTP Kotlin [40242626094](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40242626094): PASS.
- HLS Kotlin [40242626114](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40242626114): PASS.
- Queue/lifecycle [40242626138](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40242626138): PASS.
- Installed FTP restart/REST/SHA [40242626099](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40242626099): PASS.
- Chrome+Firefox cold-wake/download/SHA was independently inspected from artifact at prior code SHA `386f4751a29d4a7c6e6c8fe10664c4dc50693f18`, run [40241733195](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40241733195): PASS for both browsers. The present commit touched only benchmark gating, source tests and evidence docs, so browser packaging correctly skipped rather than repeating an unchanged build.

## Still open
HTTPS remote endpoint, refresh URL, scheduler, installed HLS kill/relaunch, Liquid Progress visual comparison, final HyperGet icon/branding, in-place upgrade/uninstall preserving old data, independent Windows release recheck, and final single `HyperGet.exe`. No main write, merge, release, paid service or Torrent.
