# HG-HTTP-EXACT-HEAD-RECHECK-01 — force the real Windows acceptance gate (2026-10-26)

## Why this work exists
The exact-head run [40241733184](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40241733184) looked green at workflow level, but its installed matrix job was **SKIPPED**. The prior real measurement is [40233832377](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40233832377) on SHA `05d63ede0590f916db1671fc80ddc07cfdb7fe1d`, not the current PR head `386f4751a29d4a7c6e6c8fe10664c4dc50693f18`.

## Root cause
The benchmark YAML already listed itself under `pull_request.paths`, but its downstream shell `case` allowlist omitted `.github/workflows/windows-installed-active-benchmark.yml`. A workflow-only repair therefore made the scope gate say `run=false`; GitHub marked the overall run successful even though the required installed benchmark never executed.

## Change made
- Add the workflow's own path to the change-scope allowlist.
- Add a Python source regression contract asserting that the workflow path maps into the real matrix job's scope.
- Keep the actual acceptance unchanged: three rounds each at 1, 4, and 16 concurrent downloads; 63 final files with exact SHA-256; nine process CPU/RAM samples; active distinct-task evidence; one purpose-built NSIS test install.
- No production downloader code or test thresholds were changed.

## Purposeful Windows test, defined before trigger
**Question:** will the exact current PR head build/install and pass the strict 1/4/16 acceptance matrix instead of silently skipping it?
**Expected:** scope gate selects the installed benchmark, installer succeeds, 63/63 file hashes match, 9/9 resource CSVs exist and validate.
**Why source tests are insufficient:** they cannot validate Windows sockets, actual GUI/CLI runtime behavior, installed state, active transfer concurrency, process-tree resource samples or the final file bytes.
**Trigger path:** the changed workflow is in the PR's existing `paths` filter and now also in the downstream scope allowlist; one exact-head Windows matrix should start automatically. No manual duplicate build.

## Acceptance result at checkpoint creation
- Browser cold wake on SHA `386f4751a29d4a7c6e6c8fe10664c4dc50693f18`: **PASS** for real Chrome and Firefox; each stopped then woke the installed GUI and verified a real 2 MiB download SHA. Run [40241733195](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40241733195); installer SHA-256 `e3cc092cc01e29f13165073caae0311ebba6443626affb4c07dcf1935825d6df`.
- Prior HTTP matrix on older SHA: 63/63 and 9/9 **PASS**, but current-head acceptance remains **NOT TESTED** until the new exact-SHA run finishes.
- FTP installed pause/restart/REST/SHA: **PASS** at the current prior head [40241733143](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40241733143).
- Release still **NOT READY**: HTTPS remote endpoint, refreshed signed/expiring URL flow, scheduler, installed HLS process-kill/relaunch, Liquid Progress visual evidence, final HyperGet branding/icon, in-place upgrade/uninstall preservation, independent Windows release recheck and final single `HyperGet.exe` remain open.
