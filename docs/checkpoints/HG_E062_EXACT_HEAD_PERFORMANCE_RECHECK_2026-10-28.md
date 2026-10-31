# HG-E062 — Exact PR-head Windows performance recheck

Date: 2026-10-28
PR: [#3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3)
Branch: `automation/idm-ftp-cancel-and-category-flush`

## Previous-milestone continuity

The latest controlled comparison run before this checkpoint, [40591897721](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40591897721), reported PASS for a historical fixed candidate `c99b6f627de5f1ef1cba9e79f4f8e1baba20ffe1` against baseline `d6ea54a885277cb5b8fd545eb3f3fb0e185b6aa5`; its harness was `78804425f4b979338b7b8865d5cfd30ed1a639b7`. That result is valid for those two snapshots, but it does not independently measure the later HTTP resource-identity and Windows destination-handle fix at `b3ff66a82a5f0cf5264184b849cf4624f0eb289a`.

The workflow is corrected to build the candidate from `${{ github.event.pull_request.head.sha || github.sha }}` and to trigger the controlled comparison when the HTTP lifecycle/destination/connection source paths change. A regression contract prevents returning to the pinned historical candidate.

## Pre-build acceptance question

Does the exact current PR-head candidate, including HTTP resource replacement and Windows file-handle lifecycle changes, pass the existing paired installed-Windows 1/4/16 throughput and memory regression gate against the unchanged baseline?

## Expected result and why a build is required

Expected: baseline SHA is exactly `d6ea54a885277cb5b8fd545eb3f3fb0e185b6aa5`; candidate SHA equals the exact PR head; both installers build on `windows-2022`; 63 deterministic HTTP outputs per version match SHA-256; nine resource measurement sets per version are recorded; true 1/4/16 concurrency is proven; throughput regression is no worse than 15%; peak Working Set and Private Bytes increase are each no worse than 15%.

Source tests can verify the SHA selection, workflow trigger paths, comparison gates and report contracts, but cannot prove installed process behavior, actual socket overlap, output integrity or OS memory samples. Therefore an exact-head Windows build/install/benchmark is required.

## Status before the run

**OPEN / NOT YET TESTED** on the corrected exact-head candidate. Do not infer success from the historical `c99b6f...` comparison, do not relax thresholds, and do not release/merge. No paid runner/API, main write, auto-merge, or Torrent.
