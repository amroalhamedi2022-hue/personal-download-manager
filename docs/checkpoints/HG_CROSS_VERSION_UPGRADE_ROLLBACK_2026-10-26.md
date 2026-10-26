## 2026-10-27 — follow-up: Windows table border hid the requested ID (OPEN)

**Exact candidate SHA:** `e986936bb5ffd7f4fa6183e7cf50a53281b8afd2`.  
**Windows run:** [40410263260](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40410263260).  
**Proof artifact:** [12692738726](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40410263260/artifacts/12692738726), archive SHA-256 `163b3d3e98b44d92a45ac5d182c187044b6130e5aff70818f1dddf0f53240381`.

### Evidence
- **PASS:** source contracts passed before packaging.
- **PASS:** both exact-snapshot Windows installers built. Baseline installer SHA-256: `c3b7a333e51980cbea96aab4febaaf4b5be778e63436c40ba382eebfc1f06d9a`; candidate installer SHA-256: `34442e65f317d7e90dcb2e416241ab387a766140ae6233d7ac3e4f3ee595506d`.
- **PASS:** the baseline CLI created the loopback download. The file reached exactly 8,388,608 bytes and its SHA-256 matched the fixture; fixture counters were 9 requests and 8,388,864 bytes sent.
- **FAIL (harness only, after transfer):** status remained `UNPARSED`. The actual row began `? 0 ? Finish? ...`: the leading table-border glyph had been decoded as `?`, so the numeric-ID row regex never matched. The prior parser change handled the clipped status token but not this leading border. Upgrade, rollback and WinCred lifecycle assertions did not run; test-owned data cleanup passed.

### Next controlled fix — recorded before the next build
Trim only leading/trailing table-border replacement characters from each captured line, then parse the requested numeric ID and its first status cell. Preserve the internal `Finish?` marker so it normalizes to `Finished`. Extend the regression to the complete observed row, including the leading border, and retain the no-neighbor-row invariant.

**Pre-build question:** does normalizing the outer table border let the baseline row ID and truncated `Finished` status parse correctly, so the full cross-version lifecycle can execute?  
**Expected:** source contracts pass; on Windows the real 8 MiB transfer is observed as `Finished`, then upgrade → candidate uninstall → rollback → normal uninstall completes with history/settings/shortcuts/registry and synthetic WinCred preserved and safely cleaned up.  
**Why source tests are insufficient:** only the packaged baseline process reproduces the native Windows table formatting, actual file transfer and Windows installer/credential-store transitions.

**Acceptance remains OPEN.** No production downloader source changed. Do not mark upgrade/rollback/WinCred PASS until a new exact-head artifact records `Result=PASS`. No merge, release or final EXE delivery.

---

## 2026-10-27 — cross-version Windows gate: real transfer succeeded; status parser false-negative (OPEN)

**Prior exact candidate SHA:** `69d6120442e7abbe092e1af95a146579f6a0a42a`.  
**Windows run:** [40401156337](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40401156337).  
**Proof artifact:** [12689113834](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40401156337/artifacts/12689113834), archive SHA-256 `4cbc9fb96fa5b410ef7b824b0253e220fe454c29a6cc082d6f2c86dfe50df8c8`.

### Evidence from the failed gate
- **PASS:** baseline installer built from exact base SHA `6c386abeae95e8e0460308d17c033558cbd60f15`; installer SHA-256 `363aa5751ba15574a856c361d5e4a38244996371105680ca7cd4785c9e8a2691`.
- **PASS:** candidate installer built from exact PR SHA `69d6120442e7abbe092e1af95a146579f6a0a42a`; installer SHA-256 `ec6b0f4b5cef89694fb3748fbab5b8ddda6f9997bfd81d805d7c7fe89314661e`.
- **PASS:** direct baseline CLI invocation added the loopback task; the fixture saw 9 requests and 8,388,864 response bytes. The final file was exactly 8,388,608 bytes and its SHA-256 matched the expected fixture.
- **FAIL (test harness, after transfer):** status remained `UNPARSED`. The baseline CLI's fixed-width table appeared in the transcript as `0 ? Finish? ...`; the parser only recognized the full word `Finished`. The script stopped before upgrade, rollback and WinCred lifecycle assertions. Test-owned data cleanup passed.
- This run disproves the earlier URL-validation suspicion for this attempt: the URL passed and the real transfer completed. Do not mark upgrade/rollback or WinCred preservation as PASS.

### Controlled fix staged before the next Windows build
The parser now reads only the status cell after the requested numeric ID, recognizes the truncated `Finish?`/ellipsis representation as `Finished`, and retains the existing row-boundary guard so a neighboring download cannot satisfy the poll. A source regression exercises full, truncated, wrapped and unrelated status cells.

**Pre-build question:** can the baseline CLI's narrow Windows table be parsed as `Finished` after the verified 8 MiB transfer, allowing the full upgrade → candidate uninstall → rollback → normal uninstall and synthetic WinCred lifecycle to execute?  
**Expected:** source contracts pass; on Windows the baseline status becomes `Finished`, then settings/history/shortcuts/registry and synthetic WinCred remain valid through upgrade and rollback, with final SHA and cleanup checks passing.  
**Why source tests are insufficient:** only the packaged baseline process on Windows reproduces the actual CLI table width, native output decoding and real Windows install/uninstall/credential-store transitions.

**Acceptance remains OPEN.** The new Windows run must build both exact snapshots and record `Result=PASS` before this milestone can close. No production downloader code was changed in this parser-only fix; no merge, release or final EXE delivery is authorized by this checkpoint.

---

## 2026-10-26 — latest exact-head cross-version gate: HTTP setup still fails (OPEN)

**Exact candidate SHA:** 78c830d4005c64c347073fb96a7dfa88666e9e70.  
**Run:** [40397222265](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40397222265).  
**Proof artifact:** [12687572255](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40397222265/artifacts/12687572255), archive SHA-256 `94703509c35388c42cc130b3955b7e69d559f17233a57fd1b793e3be3f4f16ce`.

### What passed / failed
- **PASS:** all other exact-head gates completed successfully: source safety [40397222487](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40397222487), installed HTTP 1/4/16 [40397222283](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40397222283), FTP Kotlin/WinCred [40397222194](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40397222194), relative-time tests [40397222409](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40397222409), JVM profile [40397222210](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40397222210), installed FTP E2E [40397222391](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40397222391), Chrome/Firefox capture [40397222328](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40397222328), GUI smoke [40397222303](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40397222303), queue/I/O lifecycle [40397222290](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40397222290), and HLS [40397222279](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40397222279).
- **PASS:** both cross-version installers built from the exact source snapshots. Baseline installer SHA-256: `b9d2d40068bc0b7dc7e8d268a5d1dec87795dc0965a571c4842064feb7d3782f`; candidate installer SHA-256: `92416aab86f9d3a961d49749261410407cf9776a6d7c68369ede57cc68202388`.
- **FAIL:** the baseline CLI's add-download request was rejected by the GUI-side `HttpDownloadCredentials.validate` with `url is not valid` before the HTTP fixture sent any bytes. The run never reached the upgrade/rollback or WinCred lifecycle assertions. Test-owned data cleanup passed.
- **Important correction:** the current script already uses `http://127.0.0.1:<port>/fixture.bin` with no query. The earlier hypothesis that the `?task=upgrade-rollback` query alone caused this failure is **not supported** by the latest run and must not be repeated as the root cause.

### Next controlled experiment
Before the next Windows build, add a safe loopback-URI preflight (log scheme/host/port/path and length only, never arbitrary URLs/secrets) and invoke the baseline CLI directly with the same native argument form used by the previously passing installed HTTP E2E, instead of the wrapper/splatting helper. Add source regression assertions for that contract.

**Pre-build question:** does the direct invocation pass the exact loopback URL through the installed CLI/GUI IPC and complete the 8 MiB download with the expected SHA-256?  
**Expected:** preflight shows host 127.0.0.1, a nonzero port, path /fixture.bin, no query; CLI adds the task; 8,388,608 bytes and matching SHA-256 are proven, then the full upgrade → candidate uninstall → rollback → normal uninstall and synthetic WinCred checks continue.  
**Why source tests are insufficient:** only the packaged baseline process on Windows can reveal the actual argument/IPC behavior; URL shape assertions cannot prove bytes or Windows state transitions.

**Acceptance remains OPEN.** No production downloader code changed in this diagnostic experiment. Do not mark transfer, upgrade, rollback, or WinCred lifecycle PASS until the new exact-head artifact records `Result=PASS`.

---

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


## 2026-10-26 correction — baseline CLI predates machine-readable status option

**Correction to the immediately preceding note:** moving `--status-lines` before the positional ID was not sufficient. Windows run [40374748042](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40374748042) built both exact installers successfully (baseline SHA-256 `ea724188c7ff52c3f52e3d2116dd8c2863c59d45709e826a7681fac0ff1ca1c9`; candidate SHA-256 `60839abd1f780a08df48bec8f8ea843119322ac61efe3c7ffb79fa41afa71cbb`) and failed at the first status poll while the *baseline* app was installed. Inspecting `ShowDownload.kt` at exact base `6c386abeae95e8e0460308d17c033558cbd60f15` confirms it defines only `--watch`, not `--status-lines`. The option is absent in the baseline binary, so option ordering was not the root cause. No accepted transfer, upgrade, rollback or WinCred result is claimed.

**Repair:** the harness now calls the baseline-compatible `download show <ID>` default table, strips ANSI CSI colors and vertical table separators, and matches the ID against the explicit supported status set (`Downloading`, `Finished`, `Error`, `Paused`, `PreparingFile`, `Resuming`, `Retrying`). Source contracts prohibit relying on `--status-lines` and require the sanitizing parser. The base snapshot remains unchanged.

**Pre-build question:** can this baseline-compatible parser observe the real 8 MiB HTTP download reach Finished with exact SHA-256, then preserve task/history, max-concurrent=4, sentinel and synthetic WinCred through baseline → HyperGet upgrade → candidate uninstall → baseline rollback → normal uninstall, including expected branding/shortcut/registry transitions and safe cleanup?

**Expected:** a new exact-head Windows proof with `Result=PASS`, matching expected/actual download SHA-256, preserved task/status and settings, WinCred verified at each stage then removed with absence confirmed. Source tests alone cannot prove packaged table formatting, ANSI handling, real HTTP bytes, NSIS replacement, IPC, registry/shortcuts or WinCred persistence. Previous installer SHA values are internal test evidence only, not a release artifact.


## 2026-10-26 — cross-version baseline transfer diagnostic gate

**Exact-head Windows run:** [40376651762](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40376651762), candidate `46da163a6827530b37a5de41c30f27072653ae18`, base `6c386abeae95e8e0460308d17c033558cbd60f15`. Source-contract/PowerShell parsing passed and both installers built successfully. Baseline installer SHA-256: `ef2f88052063922f1ef3eaa966e96ff1355b050cb67ecdfd2305bde7b3de8663`; candidate installer SHA-256: `4e82e513e6fa3df9d2b47f842fd77d0784ce277a0d047923ea910406c526f18c`. Baseline transfer gate **FAIL**ed after 180 seconds: CLI returned download ID `0`, but the harness did not observe `Finished` with the expected 8 MiB file/SHA. No upgrade, rollback, WinCred preservation, or branding transition is accepted from this run. Proof artifact [12678125429](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40376651762/artifacts/12678125429), ZIP digest `df21fe9a597ec4ddae32101755f85fdd0998e8f88741117243749345b78a3a45`. The harness-owned data directory was removed after ownership-marker verification.

**Diagnostic patch:** the next exact-head run will log only sanitized status transitions, distinguish “no matching download row” from a recognized but stalled state, and query the loopback fixture’s `/stats` endpoint for request/byte counters. Failure proof will include the last recognized state, safe file-size/hash outcome, and fixture request/byte totals without URLs, file paths, WinCred targets, or secrets. A 30-second unparseable status or 20-second Paused/Downloading-with-zero-bytes condition will fail fast rather than consume the full timeout.

**Pre-build question:** does the installed baseline CLI expose the task/status row, and does the real transfer send bytes to the loopback fixture? The next Windows run must separate IPC/status parsing, a baseline task stuck Paused, zero network requests, and output-path/hash mismatch. Source tests cannot answer that because the behavior depends on the exact baseline installer, live IPC and socket transfer. Expected result: a precise diagnostic on any failure, then a passing 8 MiB SHA gate before upgrade/rollback proceeds. **Status: OPEN; no functional upgrade/rollback PASS claimed.**

## 2026-10-26 — exact-head baseline CLI row split correction

**Exact-head Windows run:** [40379630588](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40379630588), candidate `5fd509d89b40b02196867a387c94d86e3d96a9d3`, base `6c386abeae95e8e0460308d17c033558cbd60f15`. PowerShell parsing/source contracts and both installer builds passed. Baseline installer SHA-256: `9a912cc3d8ecf6fe8adaa43a4b3b835fa42b95fbd876f87b0f2971830704b829`; candidate installer SHA-256: `eb2f0d7621383a9c66fa07f5b6c38a7565fefd47bd15f8c0729c70595747b981`. The baseline HTTP gate **FAIL**ed before upgrade: status remained unparseable. Fixture evidence shows `requests=10`, `bytesSent=8913152`, `peakRequests=8`, `peakDistinctTasks=1`, so real loopback transfer activity occurred; however, this is not proof that the 8 MiB output matched its expected SHA. No download-hash, upgrade, rollback, WinCred or branding acceptance is claimed. Proof artifact [12679210598](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40379630588/artifacts/12679210598), ZIP SHA-256 `7de476f4ed022d90c751e5892b7ec679ca8b33a5c03b25e01e0f2981990b0361`. The harness-owned data tree was removed after ownership-marker verification.

**Correction:** the baseline CLI table can arrive through PowerShell as a multiline output object. `Read-DownloadStatus` now splits each captured entry into physical lines before ANSI/separator normalization and status matching. The early unparseable-status failure now also reports only safe output-file length/hash-match state plus fixture counters, without printing URL, path, credential target or secret. A source contract guards the explicit line split.

**Pre-build question:** will explicit line splitting expose a recognized status on the installed baseline CLI and let the 8 MiB loopback transfer pass exact size/SHA-256? Expected: status transitions become observable, the output file matches the deterministic fixture, then the full upgrade → candidate uninstall → rollback → normal uninstall sequence verifies settings/history, shortcuts/registry and synthetic WinCred. Source tests cannot prove the installed CLI's native-output shape, actual transfer bytes, file placement/hash or NSIS transitions. **Status: OPEN; exact-head Windows recheck required.**

## 2026-10-26 — exact-head status-row parser failure and correction plan

**Latest exact-head Windows run:** [40381964443](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40381964443), candidate \`1001d3bf3655a8b25e75d1ec5d9a7d5d5cc769a0\`, base \`6c386abeae95e8e0460308d17c033558cbd60f15\`.

- **PASS:** source contracts and PowerShell parse; both exact-snapshot Windows installers built.
- Baseline installer SHA-256: \`492fe456b15859ffc4ab586e1da587eb715abd57efb59751c150e12bed2c1756\`.
- Candidate installer SHA-256: \`e0fdcb4bc9d5ae53d7b364a65099d317e72e38ffc1f7f3aea1e73a78979689a9\`.
- **PASS:** the installed baseline performed real loopback HTTP transfer; fixture reported \`requests=10\`, \`bytesSent=8913152\`, \`peakRequests=8\`, \`peakDistinctTasks=1\`; output was exactly 8,388,608 bytes and its SHA-256 matched the deterministic fixture.
- **FAIL:** the harness could not parse the baseline CLI's status row within 30 seconds (\`id=0\`, \`lastProbe=matching ID row without recognized status\`). The script then cleaned only the harness-owned data tree. Upgrade, rollback, branding transitions, settings/history preservation, and WinCred lifecycle were **not reached / NOT TESTED**. Do not label this a product transfer failure: the exact file size/hash and fixture counters prove the transfer bytes, but status/history assertions are still required.
- Proof artifact [12679702562](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40381964443/artifacts/12679702562), ZIP SHA-256 \`a857f76dae3c88cd9cde717e561f83dfda9a788fdc6d494d9d7ed9f89eca14b3\`.

**Diagnosis and repair:** the parser currently requires the numeric ID and status token on the same physical table line. The observed matching-ID-without-status result, despite a verified completed file, is consistent with Mordant's table cells wrapping onto separate lines in the captured Windows CLI output. Replace it with a row-scoped parser: detect the requested ID at the start of a table row, accept a recognized status on that line or following wrapped cell lines, and stop searching when another numeric-ID row begins. Strip ANSI and box separators before parsing. Never infer \`Finished\` from bytes alone and never accept a status belonging to another task.

**Pre-build question:** does the row-scoped parser read the actual packaged baseline CLI's \`Finished\` state, and then allow the full baseline → HyperGet upgrade → candidate uninstall → baseline rollback → normal uninstall flow to pass?

**Expected:** the 8 MiB transfer remains SHA-verified; the same task ID/status and max-concurrency=4, user-data sentinel, registry/shortcut branding, and synthetic WinCred survive every transition; normal uninstall preserves user data; the synthetic credential is deleted and absence verified. Source tests cannot establish the installed CLI's actual table wrapping or Windows NSIS/registry/Credential Manager behavior, so rerun the exact two-snapshot Windows gate after the parser patch. This is an internal acceptance test, not a release build or deliverable.


## 2026-10-26 — row-boundary safety guard and exact-head rerun

**Current parser/test commit:** [e235ee2e465e79b2ca2669821a0cc8c616837e95](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/e235ee2e465e79b2ca2669821a0cc8c616837e95).

A follow-up review caught an important false-positive risk in the first row-scoped parser draft: it checked for a status token before checking whether the line began a *different* numeric download row. The order is now corrected so a new row closes the target-row window before any status can be accepted. The source contract explicitly locks that ordering. This is an acceptance-harness correction only; no downloader production behavior changed.

**Exact-head checks observed so far:**
- **PASS:** source-only safety suite [40392955570](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40392955570).
- **PASS:** FTP Kotlin/JUnit [40392955073](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40392955073), queue lifecycle [40392955147](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40392955147), and HLS guarded-redirect unit [40392955040](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40392955040).
- **NOT RERUN (scope-gated/skipped):** installed HTTP 1/4/16 matrix, installed GUI smoke, browser capture, JVM resource profile and installed FTP E2E on this exact commit. Their older successful runs remain historical evidence only.
- **IN PROGRESS:** [cross-version Windows run 40392955509](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40392955509). PowerShell parsing and all cross-version source contracts passed; the baseline installer build is running. Candidate installer build and real lifecycle have not started at the last check.

**Next gate:** inspect both installer hashes, then the real 8 MiB transfer/status parser and full upgrade → candidate uninstall → rollback → uninstall lifecycle. Do not count the milestone complete until the proof artifact records \`Result=PASS\` and the WinCred preservation/cleanup assertions.


## 2026-10-27 follow-up — pre-build regression for the exact wrapped Windows row

The last exact-head Windows attempt [40410263260](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40410263260) built both snapshots and proved the baseline output file was exactly 8,388,608 bytes with matching SHA-256, but the harness did not reach upgrade/rollback: native table border bytes appeared as `? 0 ? Finish?`, and the parser failed to recognize the ID row. This is a harness false-negative after a real transfer, not evidence of a transfer failure.

**Current candidate at pre-build:** `d1cc6e31064facc93ced65380ae8b5ff3f7e41a7`. The edge-marker trim and a source regression for the outer-border row are present; the latest test commit corrected the Python regex escaping. The next regression now mirrors the *wrapped* two-line transcript rather than only a single-line approximation.

**Pre-build question:** does the exact installed baseline parse the wrapped row as `Finished` and proceed through the complete upgrade → preserving-data uninstall → rollback → normal uninstall flow?

**Expected:** exact source checks and PowerShell parse pass; both installers build; 8 MiB size/SHA passes; the same task ID/status, max-concurrency=4, sentinel, registry/shortcut branding and synthetic WinCred survive each transition; cleanup verifies only harness-owned data and the synthetic credential are removed.

**Why source tests are insufficient:** Python/source assertions cannot establish the packaged Windows CLI's actual output encoding, file bytes, installer replacement behavior, or real Credential Manager persistence. Run the Windows lifecycle gate after the wrapped-output regression commit. Status remains OPEN until the exact-head artifact says `Result=PASS`.
