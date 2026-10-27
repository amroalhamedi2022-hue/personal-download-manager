# HG-E061 — Controlled HTTP throughput and memory regression comparison

**Status: OPEN before Windows execution.** This checkpoint is committed before the paired installer builds are started.

## Pre-build question

Does the isolated production change that bounds blocking HTTP part-body copies at `PART_BODY_COPY_PARALLELISM = 48` preserve exact HTTP output and real 1/4/16 overlap while avoiding a material regression in aggregate throughput or process memory?

- **Baseline app snapshot:** parent of the bound change, `d6ea54a885277cb5b8fd545eb3f3fb0e185b6aa5`.
- **Candidate app snapshot:** `dd27731decbcd8cf1cb67feae42963be42d3bb7a`, the production commit `perf(http): bound concurrent blocking part-body copies`.
- **Harness:** the exact current PR head; the same fixture, sampler, summarizer and comparison code is used for both binaries.
- **Expected:** each snapshot completes 63 deterministic 32-MiB HTTP downloads (1, 4 and 16 distinct active tasks; three rounds each), every output's actual size and SHA-256 equals the expected fixture, all existing live-overlap gates remain strict, and nine process-tree CPU/RAM rounds are recorded per snapshot with separate app-source and harness SHAs.
- **Why source tests are insufficient:** the question depends on compiled installers, real Windows GUI/CLI IPC, socket overlap, byte transfer, the installed runtime's thread/heap/process behavior, and OS Working Set/Private Bytes. Source assertions cannot establish those facts.
- **Predeclared regression gates:** on the same Windows Server 2022 runner, the candidate's median aggregate throughput must not regress by more than 15%, and median peak Working Set or Private Bytes must not increase by more than 15%, for any of the 1/4/16 workloads. Three rounds are paired by workload and the install order alternates (baseline→candidate, candidate→baseline, baseline→candidate) to reduce time-order bias. These are version-to-version regression thresholds, not IDM parity claims.
- **Safety:** only a fresh, marker-owned `%USERPROFILE%\.personal-download-manager` test profile and unique runner-temp install path may be cleaned. The harness refuses to touch pre-existing user data, registry entries, or shortcuts. Both installers are internal test artifacts; they are not release deliverables.
- **Scope:** no production source is rewritten for this experiment, no paid service/runner/API, no main-branch write, no auto-merge, no release, and no Torrent support.

**Required result:** retain installer SHA-256 values, raw transfer manifests, nine CSVs per snapshot, comparison report, transcript and artifact digest. If any existing transfer/hash/overlap gate fails, or a predeclared regression threshold fails, report FAIL and preserve evidence; do not weaken thresholds.

**Status: OPEN — awaiting exact-snapshot Windows build and paired runtime results.**

## 2026-10-27 — Harness audit correction before the paired Windows build

The exact-head review found two defects in the comparison harness, so no performance result was accepted from it:

- The transfer SHA guard in `scripts/Compare-WindowsActiveBenchmarks.ps1` was syntactically incomplete, and an orphaned duplicate fragment remained after the report's final gate. The workflow's PowerShell parser step must reject this before any build.
- The comparison loop selected `Round -eq 1` for every round when reading both throughput manifests and resource summaries. That would duplicate round-one observations and invalidate the claimed three-round median.

The harness now checks a full 64-hex SHA-256 against the expected fixture, keeps the deterministic fixture hash consistent across all 63 outputs, rejects duplicate output names per round, and reads the actual current round for both transfer and resource records. Source-contract regression assertions cover these conditions.

**Pre-build question:** does the bounded HTTP body-copy candidate preserve byte-exact HTTP output and real 1/4/16 overlap without exceeding the predeclared 15% throughput or peak-memory regression limits against the exact baseline on the same Windows runner?

**Why source tests are insufficient:** only compiled exact-snapshot installers and a real Windows loopback workload can prove byte hashes, socket overlap, and OS process measurements. The paired Windows run is still **OPEN**; no performance conclusion is claimed until its logs and artifacts are inspected.

## 2026-10-27 — First rerun stopped before packaging; source-test syntax repaired

The repaired PowerShell comparison script passed the Windows parser gate. The run then stopped before JDK setup or either installer build because the newly added Python regression assertion had been inserted with an unterminated string literal. No baseline/candidate binary was built and no performance result was produced.

The source-contract test file has now been reconstructed as a complete valid Python module and includes explicit checks for the full SHA-256 guard, duplicate output-name guard, and current-round selection. The next exact-head run must pass these contracts and the PowerShell parser before any packaging begins.

**Status remains OPEN.** The paired 63+63 download matrix, SHA verification, real overlap, resource samples and 15% gates remain untested until the next run finishes.

## 2026-10-27 — E061 paired benchmark stopped after baseline round 1 (registry-residue isolation fix; pre-rerun)

**Observed Windows run:** [40442377970](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40442377970), harness SHA `8eb8af3fe585564a93ad8ede55fb0956970f71c2`.

- **PASS:** source contracts and PowerShell parsing; both exact-snapshot installers built.
- **Installer SHA-256:** baseline `247df866be8d0c64301f00e0189d70d450a1192fed8b8649c67653693c976964`; candidate `eb474ec951a591f2551e90a1d8350b019347632f8ffd2728531d624ee7be50ed`.
- **PASS for baseline round 1 only:** 21/21 HTTP outputs matched expected size/SHA; real peak task counts met 1/4/16; three resource CSVs were produced.
- **FAIL / blocker:** after baseline round 1, silent uninstall left `HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\DownloadManager`. The harness stopped before baseline rounds 2–3 and all candidate rounds. Therefore the paired 63+63 matrix and the 15% performance thresholds are **NOT TESTED**.
- **Evidence artifact:** [12707770712](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40442377970/artifacts/12707770712), uploaded archive digest `0c7754001905ae944fee7e53dce946a73ad6c669827cd13fe00a02e07a8ec820`. The log is the source for the exact failure; no performance conclusion is accepted.

### Controlled correction before the next build

The benchmark now records residual registry keys to `registry-residue.csv` and removes a key only when preflight proved it absent and the remaining values prove it belongs to the just-used controlled install (or it is an empty known product key). Unknown/mismatched keys remain fatal. This prevents an unrelated uninstall residue from blocking the performance experiment while retaining an explicit diagnostic; **it does not convert uninstall cleanup into a PASS**.

**Pre-build question:** can the same Windows runner complete all three alternating rounds for both exact app snapshots, 63 verified 32-MiB downloads and nine resource rounds per version, while preserving a separate uninstall-residue record?

**Expected:** 63/63 hashes per version, live 1/4/16 overlap on each round, source/harness provenance matched, and the predeclared 15% throughput/peak-memory gates reported without weakening them. If residue persists, the artifact must explicitly retain it as a separate uninstall diagnostic.

**Why source tests are insufficient:** only the built and installed Windows binaries can prove byte hashes, socket overlap, process-tree resource readings, installer behavior and actual registry values after uninstall.

**Status: OPEN.** No performance result is accepted until the next exact-head Windows artifact and comparison report are inspected.


## 2026-10-27 — E061 controlled comparison rerun: installer left an owned Start Menu shortcut (OPEN)

**Observed exact harness SHA:** `1c931ad618810f4439ce59938f44119237464158`.  
**Windows run:** [40447652721](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40447652721).  
**Failure-evidence artifact:** [12709134157](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40447652721/artifacts/12709134157), uploaded archive SHA-256 `d95df76a9763240885f682ef2ad936701645d70124a070017017964d94249c9f`.

### Evidence
- **PASS:** source contracts, PowerShell parsing and both exact-snapshot Windows installer builds.
- **Installer SHA-256:** baseline `0956f331fefa524a32d2a3f153f3c3aff34c89b424c4e18f4adcdf928c683ec1`; candidate `ab04cc26442ede75de24416690080d515494649ed64775d3c5731b04fa1f18ab`.
- **PASS for baseline round 1 only:** 1/1, 4/4 and 16/16 HTTP transfers completed with verified SHA-256; 8 process samples per workload were recorded; peak distinct active tasks met the requested 1/4/16 levels. This is 21 verified files, not the full paired matrix.
- **FAIL / blocker:** after baseline uninstall, the harness safely recorded and cleared two test-owned registry keys, then stopped because the installer left `%APPDATA%\Microsoft\Windows\Start Menu\Programs\Download Manager\Download Manager.lnk`. The log did not yet prove the link's target, so the previous harness correctly refused to delete it blindly. Candidate rounds and the paired 15% comparison did not run; performance remains **NOT TESTED**.
- The failure artifact retains the transcript, installer hashes, round-1 measurements and cleanup diagnostics. This is not a product uninstall acceptance PASS.

### Controlled correction before the next Windows build
Add a separate `shortcut-residue.csv` audit. Record whether each known shortcut's parent existed at preflight. After uninstall, use Windows Shell link metadata and permit removal only when the link was absent at preflight and its **absolute target exactly equals the GUI or CLI executable discovered inside this controlled install**. Record link path, target and working directory before removal. Remove a parent directory only if it did not exist at preflight and is empty; never recursively delete Start Menu/Desktop directories. Unknown target or unreadable link remains fatal. Keep shortcut residue separate from registry residue and performance acceptance.

**Pre-build question:** does ownership-checked shortcut cleanup let the same Windows runner finish all six alternating baseline/candidate rounds without touching pre-existing user shortcuts?  
**Expected:** 63 SHA-verified HTTP downloads per snapshot, nine CPU/RAM rounds per snapshot, real active 1/4/16 overlap on every round, matching source/harness provenance and unchanged throughput/peak Working Set/Private Bytes regression limits of 15%. Any uninstall residue remains explicitly recorded and does not become an uninstall PASS.  
**Why source tests are insufficient:** only the packaged Windows installer creates the actual `.lnk`; the runner must inspect its Shell target, perform real HTTP transfers, measure processes and observe post-uninstall filesystem/registry state.

**Status: OPEN.** No production downloader source changed in this harness fix. Do not close E061 until a new exact-head artifact contains all 126 verified transfers, 18 resource-round records and `Result=PASS` from the paired comparison. No merge, release or final EXE delivery.


## 2026-10-27 — controlled benchmark stopped before measurements: preflight map initialization (OPEN)

**Harness candidate SHA:** `aa4db9ef0569d6a658f80e765603ca99d51fdfc5`.  
**Windows run:** [40453504060](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40453504060).  
**Run result:** both exact-snapshot installers built, but the comparison script failed before installing either variant. No HTTP measurements or performance conclusion were produced; the workflow had no evidence artifact to upload.

- **PASS:** source contracts and PowerShell parser gate.
- **PASS:** baseline installer built; SHA-256 `9f94fcb21512b7801bf648e23d7f27e276ded288a4bae06845225ffd64a0402c`.
- **PASS:** candidate installer built; SHA-256 `0f54ba71982020eedf5936821bf5d1482afb2baf60bf4ba8acf64dae03d655cf`.
- **FAIL (harness preflight):** StrictMode reported that `$script:ShortcutParentExistedAtPreflight` had not been set. The preflight loop wrote to the map before its later initialization. This is a test-harness ordering bug, not evidence of an app performance regression.

### Controlled correction before the next Windows build

Initialize the shortcut-parent ownership map before checking the known shortcut paths, record each parent's pre-existing state, and do not reset that map after preflight. Add a source regression requiring the single initialization to precede the first preflight loop.

**Pre-build question:** does the ownership map remain available from preflight through uninstall cleanup, allowing the real installed benchmark to finish without touching any pre-existing shortcut or parent folder?

**Expected:** both exact installers build; each variant completes three rounds across concurrency 1/4/16, with 63 verified 32-MiB downloads per snapshot, nine resource-round summaries per snapshot, matching source/harness provenance, and unchanged 15% throughput/peak-memory gates.

**Why source tests are insufficient:** only a real Windows install creates the shortcuts and parent folders; the benchmark must exercise Shell link inspection, installer/uninstaller behavior, HTTP transfers and OS process measurements.

**Status: OPEN.** This fix changes only the benchmark harness, regression test and evidence logs—not production downloader behavior. Do not mark E061 PASS until a new exact-head Windows artifact contains the full transfer/resource evidence and `Result=PASS`. No merge, release or final EXE delivery.


## 2026-10-27 — E061 reached baseline round 1; install executable residue exposed (OPEN)

**Exact harness SHA:** `6ab806912382f614491a0ba03825315a990bdca4`.  
**Windows run:** [40459096700](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40459096700).  
**Failure-evidence artifact:** [12714490990](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40459096700/artifacts/12714490990), GitHub archive digest `sha256:c0d056987c2c265a2dcc5f5a3abd79eb2e93018dd0ab58931c72051c8fd6c28e`.

- **PASS:** exact baseline/candidate checkouts, source contracts, PowerShell parser, and both exact-snapshot installer builds.
- **Installer SHA-256:** baseline `ed30e704cfed26c1c65ad2ace19df7645156b70cc3567c587ad81e88525f3721`; candidate `c170af172f1578fec70e94d0783c7aac03b083fc71cb4a8aa2ab018930ebaaba`.
- **PASS for baseline round 1 only:** 21/21 HTTP outputs matched expected size/SHA; real active overlap reached 1/1, 4/4, and 16/16; three resource CSVs were emitted. Peak Working Set was 281.336 / 336.035 / 421.797 MiB and peak Private Bytes 293.504 / 346.551 / 427.172 MiB for the 1/4/16 workloads. These are one baseline round only, not a paired performance conclusion.
- **FAIL / blocker:** after silent uninstall, ownership-checked registry and shortcut cleanup succeeded, but `DownloadManager.exe` remained in the unique controlled install directory. The runner stopped at `Assert-NoProductResidue`; baseline rounds 2–3, all candidate rounds, and the paired 15% comparison did not run. The log does not yet distinguish a still-running/locked process from uninstaller deletion behavior. Uninstall acceptance remains **OPEN/FAIL evidence**, not PASS.
- **Pre-build question for the next exact-head run:** can the harness SHA-256-inventory any leftover install files, prove the directory was absent at preflight and the active marker still matches, verify no process remains under that path, then clear only that exact runner-temp install directory so the performance matrix can continue without masking the uninstall defect?
- **Expected:** 63 SHA-verified 32-MiB transfers per app snapshot; nine resource rounds per snapshot; strict live 1/4/16 overlap; matching app/harness provenance; unchanged 15% throughput and peak-memory regression gates. Any leftover file must be recorded in `install-file-residue.csv`; cleaning this isolated test directory must **not** be reported as uninstall acceptance PASS.
- **Why source tests are insufficient:** only the installed Windows binaries can reveal real process lifetime, locked files, NSIS uninstall behavior, transferred bytes, active socket overlap, and OS Working Set/Private Bytes.
- **Status: OPEN.** No paired performance conclusion, merge, release, or final EXE delivery. The harness correction is isolated to test cleanup/evidence; production downloader snapshots remain unchanged.

## 2026-10-27 — E061 inventory TOCTOU race after baseline round 1 (OPEN)

- **Exact harness SHA:** 97de7973a2be4bac84db83d02a3f2cb944fe2d96.
- **Windows run:** [40466299367](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40466299367).
- **Evidence artifact:** [12716968288](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40466299367/artifacts/12716968288), archive SHA-256 6dae9a2764f1ee85f281d85af207c0fe14471bb8c63cb2099620a5fc62e6468c (downloaded and independently re-hashed; digest matched).

### Results

- **PASS:** exact baseline/candidate source snapshots, source-contract tests, PowerShell parser, and both installer builds.
- **Installer SHA-256:** baseline 44bf25f26b3424117078913c6470d739770a4a84fc946dc959fd939ddbe58a4c; candidate 9d713d2649b29eda66eeed1454d0d3896a04fc5b670c00565240ba6ec52d6704.
- **PASS for baseline round 1 only:** 21/21 32-MiB HTTP outputs matched expected size and SHA-256; live distinct-task overlap reached 1/1, 4/4 and 16/16; three resource CSVs were recorded.
- Baseline round-1 peak Working Set: **281.434 / 383.492 / 467.449 MiB** at 1/4/16; peak Private Bytes: **293.395 / 455.875 / 465.727 MiB**. Aggregate transfer rates were **0.893 / 3.588 / 3.785 MiB/s**. These are one baseline round, not paired medians or an optimization claim.
- **FAIL — harness race:** after uninstall, the harness recorded and safely cleared two test-owned registry keys and two test-owned shortcuts. The install-directory preflight check had passed, but the path disappeared during the process-quiescence/inventory window; Get-ChildItem then raised a path-not-found error for the controlled install directory. The workflow stopped before baseline rounds 2–3 and all candidate rounds.
- **Uninstall remains OPEN:** registry and shortcut residue were observed. Harness cleanup is not product uninstall acceptance. No paired performance result or 15% gate result exists.

### Pre-build question for the next run

Can the harness safely handle the controlled install directory disappearing after process quiescence—or between the final path check and file enumeration—without masking access/ownership errors or deleting any path that is not the exact test-owned runner-temp directory?

**Expected:** if the path is absent after stopping processes, record a clear diagnostic and continue; if it disappears during enumeration, tolerate only a confirmed Test-Path = false; if the directory still exists, rethrow the original enumeration error. All six alternating rounds must complete with 63 SHA-verified 32-MiB downloads per snapshot, nine resource rounds per snapshot, strict live 1/4/16 overlap, exact app/harness provenance and unchanged 15% throughput/peak Working Set/Private Bytes gates.

**Why source tests are insufficient:** only the packaged Windows installer/uninstaller can reproduce the timing of NSIS self-removal, process exit, file locks, real transfer bytes and OS resource samples. The new source regression checks ordering and the disappearance-only exception, but Windows execution is still required to prove the race is resolved.

**Status: OPEN.** No production downloader source changed in this correction. Do not claim performance acceptance or uninstall acceptance from the baseline round. No merge, release, main write or Torrent.


## 2026-10-27 — E061 full matrix completed; validator rejected nested round labels (OPEN)

**Exact harness SHA:** `62d4987ce89fdb0b220b2307b0adf1d789e5f451`.  
**Windows run:** [40468354887](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40468354887).  
**Evidence artifact:** [12718594292](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40468354887/artifacts/12718594292), archive SHA-256 `21b3e94d1ccfbea74d9d9a83cfdd092351c4f54f5bf7243b3945f2e3308bd0d4` (downloaded and independently re-hashed; digest matched).

### Exact-head results

- **PASS:** source contracts and PowerShell parsing; both exact app snapshots built.
- **Installer SHA-256:** baseline `cfd688d9cfc2b000a4349a67fa10df3bd24e77aa1d56929812686135d57a7cb7`; candidate `16fefdb465d06d4ab064dea34de4d4807a2f03caf28ad5f0a2e494732d9705b8`.
- **PASS — transfer matrix produced:** 63 distinct 32-MiB HTTP downloads per snapshot (126 total), all expected sizes/SHA-256 verified; each variant has 9 resource summaries and 9 measurement CSVs; real active-task overlap reached 1/4/16 in every round.
- **FAIL — comparator metadata contract:** after all six rounds finished, validation stopped with `baseline active-1 has 0 files, expected 1`. The controlled runner invoked the inner matrix with `-Rounds 1` for each outer install round, so the manifest and summary in outer round-2/round-3 directories still labelled their local round as `1`. The comparator correctly selected the enclosing round and rejected the mismatch. This is a harness evidence-schema defect, not a product transfer failure.
- **Preliminary artifact-only calculation (not yet official gate acceptance):** throughput deltas candidate vs baseline were 0.00% / +0.20% / +0.68% for 1/4/16; median peak Working Set deltas were -1.44% / +2.03% / -1.27%; median peak Private Bytes deltas were -1.03% / +1.99% / -1.44%. All are within the predeclared 15% limits, but the automated comparison must pass its exact provenance/round checks before E061 can close.
- **Separate uninstall diagnostic remains OPEN:** 12 registry-residue and 12 shortcut-residue records were produced, with no remaining install-file records. The shortcut CSV had blank target fields. Code review found `Normalize-RegistryValuePath` swallowed normalization exceptions into empty strings, permitting an unsafe empty-vs-empty ownership comparison. The next correction must fail closed on non-empty path normalization errors and reject blank shortcut targets; cleanup must never be reported as uninstall acceptance.

### Next controlled correction

Normalize each nested one-round output to its enclosing paired-comparison round after the inner summarizer has validated it: stamp `Round` in the transfer manifest and resource summary, rename round-labelled output/measurement files, update resource `RunLabel`, and re-hash files after rename. The comparator will explicitly reject mismatched round metadata. Fix path normalization with explicit separator characters and throw on invalid non-empty paths; refuse to remove a shortcut with an empty target.

**Pre-build question:** can the exact Windows pair complete again with truthful round 1/2/3 provenance and fail-closed cleanup ownership, producing an official comparison report without weakening any acceptance gate?  
**Expected:** both exact installers build; 63 SHA-verified 32-MiB transfers per version; nine resource rounds per version; strict active 1/4/16 overlap; source/harness SHAs and round labels match; all paired throughput/peak-memory deltas remain within 15%, or the gate reports a genuine regression. Unknown/empty ownership targets must fail safely and remain recorded rather than being deleted.

**Why source tests are insufficient:** the result depends on installed Windows binaries, NSIS cleanup, Windows Shell shortcut metadata, actual HTTP bytes, process-tree resource sampling and exact artifact provenance. Source assertions cannot replace the installed run.

**Status: OPEN.** No production downloader code changed in this correction. This artifact is evidence for the harness fix only, not a release artifact or uninstall PASS. No merge, release, main write or Torrent.
