# HG-E064 — Correct variant-specific GUI discovery in controlled benchmark

Date: 2026-10-28  
PR: [#3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3)  
Branch: `automation/idm-ftp-cancel-and-category-flush`  
Prior lifecycle patch: [9e090424d8320b447cd6970db765c58cb5ec3d8e](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/9e090424d8320b447cd6970db765c58cb5ec3d8e)

## Observed benchmark harness failure

The pre-fix controlled Windows comparison [40606763940](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40606763940) built both installers and completed baseline round 1, then failed before candidate measurements with:

`candidate installed executable set is invalid: GUI=0, CLI=1.`

The harness searched only for `DownloadManager.exe`, even though the candidate is branded `HyperGet.exe`. Therefore that run provides **no candidate throughput or memory measurements** and is not a product-performance FAIL. It is a harness identity failure. The later source fix changes the part-worker stop predicate; the exact-head benchmark must include both that production fix and this harness correction before it can pass acceptance.

## Harness repair

- Select `DownloadManager.exe` for the unchanged base-branch baseline and `HyperGet.exe` for the PR candidate.
- Require exactly one expected GUI, zero opposite-name GUI files, and exactly one stable `DownloadManagerCli.exe`.
- Keep cleanup, registry/shortcut residue auditing, source provenance, SHA-256 gates and the 15% limits unchanged.
- Add a source regression test that locks the variant-specific selection and rejects stale/duplicate GUI names.

## Pre-build acceptance question

Can the controlled runner install each exact snapshot and discover its correct GUI identity (legacy baseline versus branded candidate), then complete the paired 1/4/16 transfer and resource measurements?

Expected: the candidate identity check passes; the matrix records 63 SHA-256-correct outputs per variant and nine resource sets per variant; true concurrency 1/4/16 is proven; throughput and peak Working Set/Private Bytes remain within the existing 15% limits.

Source tests can validate the name-selection logic but cannot establish the actual NSIS installed tree, process identity, live network overlap, output integrity or Windows memory values. The exact-head installed Windows benchmark is required. No paid service, main write, merge, release or Torrent.

## Current status

- Source-only contracts: expected to rerun on the combined patch.
- Stop All late-worker regression: exact-head Windows JUnit is running after the production fix.
- The queued benchmark run created from the prior head may still fail on the stale harness and must not be treated as evidence for this correction.
- Fresh benchmark on the combined production+harness fix remains the acceptance authority.
- HTTPS/signed-link refresh, native-size icon audit, independent final pass and final user-facing `HyperGet.exe` artifact/SHA remain OPEN.
