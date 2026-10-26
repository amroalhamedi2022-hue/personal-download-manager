# HyperGet cross-version upgrade / rollback checkpoint — 2026-10-26

**Status: FAIL on the previous exact head; harness correction committed in this checkpoint, new Windows acceptance run required.**  
**PR:** [#3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3)  
**Branch:** `automation/idm-ftp-cancel-and-category-flush`  
**Failed candidate SHA:** `91101cfaea25973a7b6979331b2e9710ff18bfa8`  
**Base SHA:** `6c386abeae95e8e0460308d17c033558cbd60f15`  
**Failed run:** [40367433598](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40367433598)  
**Failure artifact:** [hyperget-cross-version-upgrade-rollback-proof, ID 12670788928](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40367433598/artifacts/12670788928)

## Milestone and question

Prove that the exact base installer can be upgraded to HyperGet and safely rolled back without losing a real completed download-history record, persisted max-concurrency setting, user-data marker, or synthetic Windows Credential Manager entry. Also prove visible branding, registry metadata, and Start Menu/Desktop shortcuts follow each installed snapshot.

**Pre-build question:** can the two exact source snapshots build, and does a real Windows install → HTTP transfer → HyperGet upgrade → preserving-data uninstall → baseline rollback → normal uninstall preserve all state and the synthetic WinCred record?

**Expected result:** both snapshots build one internal NSIS installer; an 8 MiB loopback HTTP download completes with exact SHA-256; max-concurrent stays 4; the candidate reports `HyperGet`; rollback restores baseline name/version; the same task ID stays `Finished`; the sentinel, history, and synthetic WinCred record remain intact at each transition; cleanup removes the synthetic credential and only the harness-owned data tree.

**Why source tests are insufficient:** URL validation, NSIS replacement, registry metadata, shortcut creation/deletion, executable replacement, IPC readiness, actual download bytes, and Windows Credential Manager persistence are observable only in the installed Windows process/user profile. The harness refuses pre-existing install/data/registry/shortcut paths and removes test data only after validating its random ownership marker.

## Evidence from failed exact-head run

Run [40367433598](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40367433598) checked out baseline `6c386abeae95e8e0460308d17c033558cbd60f15` and candidate `91101cfaea25973a7b6979331b2e9710ff18bfa8`.

- **PASS:** baseline installer build; SHA-256 `4ef3c7fe2cb10b98d62e85de0076b2e19866d372d4d57f6d518de690e2c5135a`.
- **PASS:** candidate installer build; SHA-256 `07cdce6ee137ac434dd7f4cf8140538ed8dcf90b28ac09133552a2032fb51d60`.
- **FAIL:** the first real HTTP download was rejected before transfer by `HttpUrlUtils.isValidUrl` because the harness added the unnecessary query `?task=upgrade-rollback` to the local fixture URL. The installed app returned `IllegalArgumentException: url is not valid`; no upgrade/rollback acceptance was reached.
- **PASS cleanup:** the harness removed only the test-created `.personal-download-manager` tree after its ownership-marker/path check.
- Artifact archive SHA-256: `95322b99a2e7c51f86167ad9f33b6f2e56aab5545dc0edbe48fa05980523cbdd`.

## Correction and strengthened gate in this checkpoint

- Remove the unneeded query string; use the already accepted loopback URL shape `http://127.0.0.1:<port>/fixture.bin`.
- Add a synthetic, random-UUID WinCred generic credential under the existing `PersonalDownloadManager/FTP/` namespace using the same Windows Credential Manager API contract and `CRED_PERSIST_LOCAL_MACHINE`.
- Read/verify username, credential persistence mode, and exact UTF-16LE secret bytes after baseline creation, candidate upgrade, candidate uninstall, baseline rollback, and normal uninstall.
- Keep the synthetic secret and opaque target out of logs/proof; zero local byte buffers; delete the fixture and verify absence on success and best-effort cleanup on failure.
- Extend source-contract tests for the URL shape, lifecycle assertions, cleanup, and log redaction.

## Acceptance gates

| Requirement | Current state |
|---|---|
| Baseline and candidate installer builds on failed head | PASS; hashes above |
| Real HTTP transfer/history on failed head | FAIL — invalid fixture URL; fixed in current source |
| Upgrade branding/registry/shortcuts | NOT TESTED on failed head |
| Data/settings/history preserved across upgrade and rollback | NOT TESTED on failed head |
| Synthetic WinCred survives upgrade, candidate uninstall, rollback, and normal uninstall | Added to current harness; NOT TESTED until new exact-head run |
| Synthetic WinCred cleanup and secret redaction | Source guard added; Windows result pending |
| Independent final Windows recheck | NOT COMPLETE |

## Next action

Run the source-contract and PowerShell parser gates first, then build the exact base/candidate snapshots and rerun the full Windows lifecycle. Do not close this milestone until the proof artifact records `Result=PASS`, matching download SHA-256, preserved setting/task/sentinel, WinCred lifecycle PASS, and successful cleanup. Internal installers are test fixtures only, never delivery artifacts. PR #3 stays Draft; no main write, merge, release, paid runner/API, browser-repository write, or Torrent.


## 2026-10-26 follow-up — cross-version Windows gate: CLI option order

**Latest exact-head run:** [40369973960](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40369973960) on candidate `1b1b234570a96bee7202353a409f6c599de1f4d4`. **Builds PASS; lifecycle gate FAIL before transfer acceptance.** Both exact snapshots built on Windows. Baseline installer SHA-256: `4e30f106d9d7802a456648b28505c22599fc75cc3483b0d62fc089570723218a`; candidate installer SHA-256: `56cadb857a45e3aabf8cb63e3a39f207a983bfe000c6ed16d95ecfd808560e6a`. The harness then invoked `download show <ID> --status-lines`; the installed Clikt command declares options before positional IDs and rejected the trailing option (`no such option --status-lines`). This is a harness argument-order defect, not a download-engine failure. The root-directory collision and invalid loopback query are already corrected. No upgrade, rollback, WinCred lifecycle, or completed HTTP transfer is counted as PASS from this run.

**Repair in the next source commit:** invoke `download show --status-lines <ID>` and add a source regression contract that locks the option-before-ID order. No production transfer behavior changes. The test-owned data tree was removed after ownership-marker verification. Failure-proof artifact: [12671504967](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40369973960/artifacts/12671504967), archive SHA-256 `97faaa8a0aec0207159ab4409d9f3dbfb0c93d3df486daccb68c088e9c5731ed` (diagnostic only).

**Pre-build question:** after correcting CLI argument order, can the baseline installer complete a real 8 MiB loopback HTTP download with matching SHA-256, then survive baseline → HyperGet upgrade → candidate uninstall → baseline rollback → normal uninstall with task ID/status, max-concurrent=4, user-data sentinel, branding/shortcuts/registry metadata, and a synthetic Windows Credential Manager entry preserved and then safely removed?

**Expected result:** exact-head Windows run produces `Result=PASS`, matching expected/actual download SHA-256, preserved history/settings/sentinel and WinCred at each transition, clean shortcut/registry transitions, and safe cleanup. Source tests alone cannot prove Clikt parsing in the packaged CLI, NSIS replacement, IPC readiness, registry/shortcut behavior, actual bytes, or WinCred persistence. This gate remains **OPEN** until the new Windows proof artifact is inspected.
