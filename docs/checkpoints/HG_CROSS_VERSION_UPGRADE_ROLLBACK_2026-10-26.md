# HyperGet cross-version upgrade / rollback checkpoint — 2026-10-26

**Status: IN PROGRESS — no functional PASS claimed yet.**  
**PR:** [#3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3)  
**Branch:** `automation/idm-ftp-cancel-and-category-flush`  
**Acceptance candidate at test trigger:** `98fcbec764f66ec7fc84e6348a17ca754c142202`  
**Base:** exact `pull_request.base.sha` (or the explicit base ref on manual dispatch).

## Milestone and question

Prove that an installer built from the existing base snapshot can be upgraded to the HyperGet candidate and safely rolled back without losing the existing user-data directory, a real completed download-history record, or a persisted max-concurrency setting. Also prove that the visible branding, registry version and Start Menu/Desktop shortcuts follow the installed snapshot.

**Pre-build question:** does the real Windows installer preserve the existing `.personal-download-manager` data and app state across base → HyperGet, then across a data-preserving HyperGet uninstall → base reinstall, and remove stale branding shortcuts in both directions?

**Expected result:** both exact source snapshots build one internal NSIS installer; an 8 MiB loopback HTTP download completes with exact SHA-256; max-concurrent is set to 4; candidate install reports `HyperGet`; rollback restores the baseline display name/version; the same task ID remains `Finished`; setting and sentinel remain unchanged; normal uninstall removes the app/registry/shortcuts but leaves user data until the harness removes its own marked test tree.

**Why source tests are insufficient:** NSIS section order, registry metadata, shortcut creation/deletion, executable replacement, IPC readiness and persisted application state are observable only after both installers run against the same Windows profile. The harness refuses to start if its install directory, expected data directory, product registry keys or product shortcuts already exist. It deletes only a data tree created by this run and guarded by a random ownership marker.

## Changes in this milestone

- Installer now removes only this product's known legacy `Download Manager` and branded `HyperGet` shortcuts before creating the selected brand's shortcuts, and cleans both names on uninstall.
- Added `scripts/Run-WindowsCrossVersionUpgradeRollbackE2E.ps1` for real install → HTTP download/history → upgrade → verify → candidate uninstall preserving data → baseline reinstall/verify → normal uninstall.
- Added `scripts/source-tests/test_hyperget_cross_version_upgrade_source.py` to guard the stable app/package/data identities, shortcut cleanup ordering, harness safety gates and workflow evidence policy.
- Added `.github/workflows/windows-cross-version-upgrade-rollback.yml`. It builds the exact base and candidate snapshots on the standard `windows-2022` runner, uses no paid service/API, and uploads only text proof/transcript—not installers.

## Acceptance gates

| Requirement | Current state |
|---|---|
| NSIS shortcut cleanup implementation | Implemented; Windows verification pending |
| PowerShell syntax + source contracts before builds | Pending latest-head run |
| Base installer build + SHA-256 | Pending latest-head run |
| Candidate installer build + SHA-256 | Pending latest-head run |
| Real HTTP 8 MiB output + SHA-256 | Pending latest-head run |
| Persisted max-concurrent=4 across upgrade and rollback | Pending latest-head run |
| Same download ID/status survives upgrade and rollback | Pending latest-head run |
| Registry display name/version changes and restores | Pending latest-head run |
| Start Menu/Desktop shortcut transitions | Pending latest-head run |
| Normal uninstall preserves user data | Pending latest-head run |
| Independent final Windows recheck | NOT COMPLETE |

## Failure handling / evidence discipline

- An earlier workflow dispatch for the first harness draft was triggered before its syntax/route corrections. It is tied to the older workflow SHA and must not be used to accept this milestone.
- Only the run whose checkout log records the corrected candidate SHA can close this gate.
- A source-only PASS is not installer acceptance; a build PASS without the state-preservation checks is not upgrade acceptance.
- The internal installers are test fixtures only. They are not release artifacts and must not be delivered as `HyperGet.exe`.
- PR #3 remains Draft. No merge, direct `main` write, release, Torrent, or browser-repository write is part of this milestone.
