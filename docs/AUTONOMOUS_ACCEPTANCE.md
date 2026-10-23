## HyperGet — 2026-10-24: browser outbound native-messaging limit and strict encoding (SOURCE + REGRESSION; NO APP BUILD)

**Production commit:** [`02ee41e8`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/02ee41e816a8165a06a85954419e98a4fa3d57ce); **test commit:** [`8282b547`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/8282b5477e33012d19d34930a5b8a0e51c8831cc); existing draft [PR #3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3), head `automation/idm-ftp-cancel-and-category-flush` → base `codex/repair-rf00-rf01`. No main writes, merge, workflow trigger, release or paid service.

**Verified protocol discrepancy:** Existing `StdioProtocol.writeMessage` allowed a 4-MiB **host-to-browser** payload even though both [Microsoft Edge](https://learn.microsoft.com/en-us/microsoft-edge/extensions-chromium/developer-guide/native-messaging) and [Firefox/MDN](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/Native_messaging) document a **1-MB host-to-browser limit**. The existing 4-MiB **browser-to-host local allocation cap** is retained separately. Existing `String.toByteArray(UTF_8)` also silently replaced malformed UTF-16 surrogates on outbound responses. Reused the existing Apache-2.0 project protocol implementation and JDK `CharsetEncoder` with `CodingErrorAction.REPORT`; no dependency added, no extension ID, IPC name, Registry key, credentials or stored download migration.

| Gate | Result | Direct evidence |
| --- | --- | --- |
| Source change and regression committed | **PASS** | Production Git blob `37d3040fd9b02217460354830c9ec689ecca8df5`; test blob `c8fc58658e706183ec57cb31026b462911f519c8`. Both GitHub-fetched blobs matched `git hash-object` of the exact files compiled locally. |
| Before-fix regression | **FAIL reproduced (expected)** | Original GitHub-blob-matched source `5a25a27a036fc97fa23fea9f8f9dc62cc0b346e6` compiled with the new regression; it wrongly accepted a 1-MiB+1 outbound message. Test stopped at case 4. |
| After-fix Kotlin/JVM regression | **PASS 10/10** | `kotlinc StdioProtocol.kt NativeMessagingFinished.kt HyperGetNativeMessagingOutboundSmoke.kt -d after-classes && kotlin -classpath after-classes HyperGetNativeMessagingOutboundSmokeKt`. Checks ASCII and Arabic/emoji round-trip framing, exact 1-MiB boundary, 1-MiB+1 ASCII and multibyte rejection **before any output bytes**, lone high/low surrogate rejection, valid surrogate pair, 2-MiB inbound frame still accepted, and subsequent valid output. Isolated real Kotlin/JVM source test; not Gradle or installed Windows. |
| Static/source invariants | **PASS 12/12** | Checked independent inbound/outbound caps, strict encoder, error reporting, output-before-rejection ordering, no silent encoding, regression cases and retained inbound allowance. |
| GitHub Actions / commit statuses at test SHA `8282b547` | **NOT TESTED / 0 reported** | 0 PR workflow runs and 0 commit statuses when queried. Existing Windows packaging workflow push trigger excludes the isolated PR head; **no Actions build was requested**. |
| Browser integration and real Windows host | **NOT TESTED** | No browser process or Windows install executed; the extension repository was read-only in this round and its old README still uses upstream branding. Cross-repo identity/manifest compatibility remains open. |
| HyperGet.exe, Gradle/desktop compilation, installer, real FTP/HTTP(S)/HLS, 1/4/16, SHA-256, CPU/RAM, Liquid Progress, upgrade, icon and independent double recheck | **DEFERRED / NOT TESTED on this SHA** | Binding NO-BUILD gate remains active until all source/readiness requirements are complete. No EXE or installer generated. Torrent remains excluded. |

**Next source-only action:** Audit native-host error handling after a partial stdout write (do not retry into a broken stream); inspect the separate browser extension protocol/branding and HG-01..HG-05 readiness without changing its repository from this task. Continue download/FTP/queue source fixes and tests. Do not begin first final Windows build until all source/readiness gates are met; do not claim final delivery or disable the recurring task.

---

## HyperGet 2026-10-23 — Strict UTF-8 native browser-message decoding (SOURCE + TEST; NO BUILD)

**Production SHA:** [`09abfe24`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/09abfe24137589d6e374b44af3d61797bd6365fd). **Regression SHA:** [`32a4127d`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/32a4127d9b3a2118df06131ed9b264ae1469af1a). Existing draft [PR #3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3), head `automation/idm-ftp-cancel-and-category-flush`, base `codex/repair-rf00-rf01`; no merge or main write.

**Reproduced defect:** `StdioProtocol.readMessage()` used `String(bytes, UTF_8)`, which silently converted malformed browser-supplied UTF-8 into replacement character U+FFFD. An isolated Kotlin/JVM reproduction on the **unmodified, GitHub-blob-matched production source** confirmed that bytes `C3 28` were accepted as `[FFFD, 28]`. Native messaging requires UTF-8; silently changing request bytes is not safe. The source now uses JDK's strict UTF-8 `CharsetDecoder` (`CodingErrorAction.REPORT`) and throws a distinct **recoverable** `InvalidNativeMessagingPayloadException : IllegalArgumentException` after consuming the complete frame. Invalid *length* prefixes remain fatal via `InvalidNativeMessagingFrameException`, preserving the prior host-loop safety behavior. Existing host catch path treats a bad complete payload as recoverable and can read the next frame. No new dependency; existing repository `LICENSE` is Apache-2.0 and JDK decoder is built in.

| Gate | Status | Evidence |
| --- | --- | --- |
| Production source changed and regression committed | **PASS** | Commits `09abfe24` and `32a4127d`; GitHub returned source blob `5a25a27a036fc97fa23fea9f8f9dc62cc0b346e6` and test blob `d7c90fd65b2cb6ef52d7830e102d934f576734cb`, exactly matching locally tested `git hash-object` results. |
| Isolated Kotlin/JVM source test | **PASS 15/15** | `kotlinc StdioProtocol.kt NativeMessagingFinished.kt HyperGetNativeMessagingFramingSmoke.kt -d /tmp/hyperget-post-fix-classes && kotlin -classpath /tmp/hyperget-post-fix-classes HyperGetNativeMessagingFramingSmokeKt`. 12 existing cases + malformed continuation, truncated multibyte, UTF-8 surrogate. Each invalid payload is framed between two valid messages; rejection and subsequent successful frame read verified. No Gradle or app build. |
| Source-only static guards | **PASS 11/11** | Checked strict UTF-8 decoder/error policy, recoverable payload exception vs fatal frame exception, complete-frame-before-decode ordering, unchanged 4-MiB cap, and three malformed UTF-8 variants with next-frame recovery. Static only, not a Windows/Gradle result. |
| GitHub PR workflow runs and commit statuses on test SHA `32a4127d` | **NOT TESTED (0 reported)** | Connector returned 0 PR runs and 0 statuses; existing Windows packaging workflow does not push-trigger on this PR head. No CI requested. |
| Full desktop app, Windows, browser extension and socket integration | **NOT TESTED** | User's NO-BUILD gate remains binding. No Gradle, app compilation, EXE, installer or executable-producing Actions invoked. |
| FTP/REST, HTTP(S)/HLS, 1/4/16 active downloads, SHA-256, CPU/RAM, UI/identity, HyperGet.exe, migration, independent double recheck | **NOT TESTED on current SHA** | Historical acceptance below remains; no final delivery. Torrent remains excluded. |

**Next bounded action:** Review outbound native-messaging UTF-8 encoding and browser host error-path handling, then continue source-only HG-01–HG-05/functional tests. When all source/readiness requirements are complete, permit first final Windows build and installed acceptance; do not mark VERIFIED or disable the recurring task.

---

## HyperGet 2026-10-23 — Native browser-messaging frame desynchronization repair (SOURCE + TEST)

**Production SHAs:** `94114937c1d2e67af121ef699bbb46f9b0c668ef` (`StdioProtocol.kt`), `e8929d0b2116a5b269ea0acfcd5214c22db52f9c` (`NativeMessagingHost.kt`). **Regression source SHA:** `6a66b4a20d567fc0a382dc7857f908f35bc6a9c6` (`scripts/tests/HyperGetNativeMessagingFramingSmoke.kt`). Existing draft [PR #3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3); no merge or direct main changes.

**Defect:** The original Apache-2.0-based native-messaging parser rejected a zero/negative/>4-MiB frame length as `IllegalArgumentException`; the browser host treated that exception as recoverable and read the next four bytes as a new length. Those bytes may actually be the malformed frame's payload, causing a desynchronized error loop. **Fix:** A dedicated `InvalidNativeMessagingFrameException` terminates the host read loop for unrecoverable framing errors, without consuming or reinterpreting attacker-controlled payload. Ordinary JSON serialization/argument errors after a valid frame retain the previous recoverable behavior. Existing native host names, extension IDs, registry keys, IPC, stored downloads and credential keys are unchanged. Reviewed the existing upstream AB Download Manager v1.11.1 implementation and this repository's Apache-2.0 LICENSE; reused existing framing code, no new dependency.

| Gate | Status | Exact evidence |
| --- | --- | --- |
| Kotlin/JVM isolated source framing test | **PASS 12/12** | `kotlinc StdioProtocol.kt NativeMessagingFinished.kt HyperGetNativeMessagingFramingSmoke.kt -include-runtime -d /tmp/hyperget-native-framing-test.jar && java -jar /tmp/hyperget-native-framing-test.jar`; cases cover valid ASCII/UTF-8, consecutive frames, output framing, invalid 0/-1/>4-MiB prefixes, exact 4-MiB boundary, outbound oversize, truncated header/body, clean EOF. Compiled source Git blob hashes `e18907902697ab70fb9ac5f15814796dcafa5a67`, `2b9958f9e5625cf8069fde7cd1b75b88c61b30d9`, test `d744b276d98bfe0a102387f285c6bdee6b4510f3` match the GitHub files exactly. This is NOT full app compilation. |
| GitHub-source guard/static assertions | **PASS 8/8** | Re-fetched exact production/host/test and checked fatal-frame propagation, recoverable JSON behavior, 4-MiB limit, test presence and branch workflow trigger scope. Static only; host as a whole was NOT compiled. |
| GitHub Actions on exact source+test SHA `6a66b4a2` | **NOT TESTED** | 0 PR workflow runs and 0 commit statuses at check. Existing `private-windows-validation.yml` push trigger lists base branch but NOT the isolated PR branch; no Actions requested. |
| Browser extension on real Chrome/Firefox, native-host end-to-end | **NOT TESTED** | No real browser/Windows execution; separate browser repository unchanged. |
| Full Gradle/JUnit, app compile, installer, HyperGet.exe | **DEFERRED / NOT TESTED** | Binding user NO-BUILD gate. No Gradle, packaging, EXE, installer or executable-producing Actions invoked. The temporary isolated JVM test JAR is not a HyperGet application build. |
| HTTP(S)/HLS, FTP/REST, 1/4/16, SHA-256, CPU/RAM, Liquid Progress, upgrade/rollback, icon, independent double recheck | **NOT TESTED on this SHA** | Existing historical failures/gates below remain open. No change to Torrent exclusion. |

**Next bounded action:** Continue HG-01–HG-05 and functional source fixes without building; add a source-only host-loop integration regression when the full serialization dependency is available, then verify extension manifest/identity and Windows migration readiness. After ALL source/readiness gates pass, perform the first final Windows build and strict installed acceptance, including browser framing and actual sockets. Do not mark HyperGet complete, merge, publish, or disable the recurring task.

---

## HyperGet renaming acceptance gates — 2026-10-23

**Approved brand:** HyperGet. This is an administrative plan update only, **no production code/icon/installer changed yet**. [Detailed plan](HYPERGET_REBRAND_PLAN.md). All earlier acceptance/evidence applies to this same project under the new name, without implying rename was implemented.

| Gate | Status | Proof required |
|---|---|---|
| HG-01 User-visible HyperGet name across Windows app/UI, settings, dialogs and system integration | NOT TESTED | Source changes + exact-head Windows screenshots and string audit |
| HG-02 Upgrade-safe persistence: settings, credentials, downloads, database and Windows registrations | NOT TESTED | Previous-to-new version migration/rollback and data integrity tests |
| HG-03 HyperGet.exe + installer/uninstaller/shortcuts + separately maintained browser integration | NOT TESTED | Genuine EXE + clean/upgrade Windows UI, functional extension IPC tests |
| HG-04 Original modern HyperGet icon, not IDM/other app copy | NOT TESTED | Actual embedded ICO resources and Windows icon review |
| HG-05 Full independent final regression (1/4/16 download SHA, CPU/RAM, FTP/HTTP/HLS, resumed installs) | NOT TESTED | Exact-SHA Windows CI, real device/process tests and recheck report |

# Personal Download Manager — autonomous acceptance ledger

AUTOMATION_COMPLETE: IN_PROGRESS

Updated: 2026-10-23 (Asia/Jerusalem)
Base branch: `codex/repair-rf00-rf01`
Base SHA inspected: `6c386abeae95e8e0460308d17c033558cbd60f15`
Change branch: `automation/idm-ftp-cancel-and-category-flush`
Current change commit before this ledger: `610d804a1d39e82a8b4043c832b16ae4d598f8ae`

## This development round — production source changed

1. `FtpTransferClient.kt`: FTP Pause now cancels the **active RETR data InputStream** as well as the control socket. An independent per-transfer cancellation flag is checked after connection/login, during buffered reads and before final commit. Closing the data stream is supported by the official Apache Commons Net 3.13.0 `FTPClient.retrieveFileStream` contract. Stream cleanup now also covers sidecar-creation failures. This addresses the observed Windows FTP E2E defect where Pause left a full 32-MiB `.ftp-part` file instead of a genuine partial transfer.
2. `FtpTransferClientTest.kt`: a real local Apache FTP integration regression test cancels an in-flight transfer from another thread, requires a partial file and no final commit, then REST-resumes it and verifies exact bytes.
3. `CategoryRestartPersistenceTest.kt`: replaced a timing-dependent two-second sample/poll with `CategoryManager.close()` (existing production shutdown flush) followed by an exact disk assertion and restart. This exercises deterministic persistence instead of Windows runner scheduling.

**Verification in this automation runtime:** Source files were fetched from GitHub, compared to the observed failures and modified on an isolated branch. **No Gradle compilation, JUnit execution, Windows install or FTP/HTTP transfer was executed here.** Local environment cannot clone GitHub (network/DNS blocked), and the existing Windows Actions workflows only auto-trigger on the approved base branch, not this PR branch. A passing source inspection is **not** a passing test.

## Evidence and outstanding gates

| Requirement | Status | Direct evidence / next gate |
| --- | --- | --- |
| Clikt header option + persistent IPC compilation | HISTORICAL PASS, CURRENT NOT TESTED | Fixed in base source after `51a4658`; Windows validation [39926396165](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39926396165) passed on `40caada3`. The latest base SHA and this PR SHA have no verified Windows build yet. |
| Category restart persistence | FAIL on older SHA; PATCH UNTESTED | [39929246766](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39929246766) on `46a17b9e`: `CategoryRestartPersistenceTest.restartRestoresCategoriesAndRepairsDuplicateMemberships` timed out, 26 tests/1 failed. New deterministic close/flush test requires rerun. |
| FTP authenticated Pause → restart → REST Resume | FAIL on older SHA; PATCH UNTESTED | [39929246815](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39929246815) on `46a17b9e`: `FTP pause staging invariant failed: partial=33554432 final=False`. New data-stream cancellation and regression test require Windows acceptance. |
| FTP real external LAN/WAN, GUI Add URL/OK | NOT TESTED | Loopback tests do not prove external connectivity, GUI behavior or firewall/NAT handling. |
| 1/4/16 simultaneous installed HTTP + SHA256 + CPU/RAM | FAIL / PARTIAL | [39929246748](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39929246748) on `46a17b9e`: `active-16` round 1 verified 16/16; round 2 observed only 13/16 in-flight tasks. Do not weaken the strict active-socket assertion or modify normal concurrency default of 3. Nine complete rounds remain unverified. |
| HTTP/HTTPS/HLS, signed links, Refresh URL, browser extension | NOT FULLY TESTED | Require current SHA integration tests and real-browser/real-network acceptance. Browser extension remains a separate repository. |
| Liquid Progress, full IDM parity, settings, 16 themes | NOT TESTED | Require actual screenshot comparisons to approved assets on Windows. |
| One Windows EXE, reinstall/uninstall, data safety | HISTORICAL PASS / CURRENT NOT TESTED | [39918464707](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39918464707) packaged exactly one EXE on older SHA; [39919442198](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39919442198) validated same-version lifecycle. Cross-version upgrade and current SHA still open. |
| Torrent | EXCLUDED | Explicitly cancelled by user; never reintroduce. |

## Exact next test commands (NOT executed in this round)

On an authorized Windows runner, at this PR's exact head SHA:

```bash
./gradlew --no-daemon --stacktrace --dependency-verification strict :downloader:core:desktopTest :downloader:monitor:desktopTest :shared:app:desktopTest :desktop:app:compileKotlin
./gradlew --no-daemon --stacktrace --dependency-verification strict createReleaseFolderForCi
```

Then execute the existing installed Windows FTP E2E workflow and the installed 1/4/16 benchmark workflow on that same source SHA, retaining JUnit XML, artifact hashes and full failure logs. Do not spend CI minutes re-running older SHAs. Existing workflows are configured to run on `codex/repair-rf00-rf01` after reviewed integration; no workflow/credentials were changed by this PR.

## Next bounded development action

1. Review/merge this production-code PR into the authorized development branch, or run equivalent exact-SHA Windows validation if workflow permissions permit.
2. If FTP cancellation test/E2E fails, inspect the data-stream close/pause handshake and fix without weakening the staging or REST-offset assertions.
3. Investigate why 16-task round 2 reached only 13 live tasks, preserving per-user concurrency settings and real-socket measurements.
4. Continue FTP GUI acceptance, external network, browser integration and Liquid Progress parity.
5. Only when all requirements have actual end-to-end evidence, mark CANDIDATE and run a separate independent recheck; do not self-certify VERIFIED.

Cost: no OpenAI API or paid CI was enabled; this branch changes no workflows, credentials, repository visibility or releases.


## 2026-10-23 — Follow-up autonomous development: atomic 1/4/16 batch admission (UNTESTED)

Production commits on this same existing draft PR (no new PR, no merge):
- `e5e6ff746b4318e2a8a68a49b2ee8af8e3ffcda5`: `ManualDownloadQueue.resumeAll(Collection<Long>)` now deduplicates and admits the complete user-requested list in one lock-protected queue update and one immediate scheduler pass; existing `resume(id)` delegates to it. The existing user `maxConcurrent` cap is **not** bypassed.
- `010fc6c2106e1cf20818cc78088bffe92358ee07`: `DownloadSystem.userManualResume(ids)` exposes the batch operation while preserving the single-ID entry point.
- `1e68b0cca03419e315155930e1f310633575dc6e`: desktop IPC `resumeDownload(ids)` now calls the batch facade once instead of N sequential admissions.
- `12f42f9728474551ce0b0f5abddacec58752e757`: new `ManualDownloadQueueBatchResumeTest` exercises 16 requested IDs (including duplicates), verifies only 4 are admitted under a 4-task cap, then verifies all 16 are admitted once when the existing cap is raised to 16. Concurrent completion order is not assumed.
- `105f33859868bd5214871a2851bdc2525e151697`: Windows active benchmark now writes `failure-diagnostics.txt` (live fixture counters, persisted limit, installed CLI task status, output sizes) when the exact 1/4/16 active-socket gate fails. **No assertion, concurrency setting, SHA-256 check, CPU/RAM measurement, or sample duration was relaxed.**

**Motivation, not proof of a root cause:** Windows [run 39929246748](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39929246748), source `46a17b9e`, showed active-16 round 1 passing 16/16 and round 2 reaching only 13/16 simultaneously. Previously the desktop IPC admitted each of 16 IDs individually, re-shaking the scheduler after every ID. Atomic batch admission removes that unnecessary repeated scheduling, but the 13/16 failure may have additional causes. The new diagnostic artifact will help identify them if it recurs.

**Verification status:** The new source and test were written to the existing PR branch and their GitHub blob/commit SHAs returned. **No Gradle, JUnit, Windows process measurement, FTP socket test, installed-app test, or PowerShell parser was executed on these commits in this automation runtime.** GitHub Actions currently has no run on this isolated PR head. Status remains **PATCH UNTESTED / FAIL historically**; do not claim 16/16, SHA-256, CPU/RAM or installer success on this head.

**Next gates (run on exact PR head after review/authorized CI):**
1. `./gradlew --no-daemon --dependency-verification strict :downloader:core:desktopTest :downloader:monitor:desktopTest :shared:app:desktopTest :desktop:app:compileKotlin` — including `ManualDownloadQueueBatchResumeTest`, `FtpTransferClientTest` and category restart.
2. `./gradlew --no-daemon --dependency-verification strict createReleaseFolderForCi`, strict JUnit XML verification and one-EXE check.
3. Installed Windows FTP Pause → REST Resume + exact SHA-256; installed 1/4/16 x 3 HTTP concurrency benchmark with real process-tree CPU/RAM CSV and 63 verified outputs. Inspect `failure-diagnostics.txt` if the strict 16-task gate still fails.
4. Preserve the separation of the browser-integration repository, exclude Torrent, and continue the remaining original plan/visual acceptance. Only after all acceptance gates pass may a separate agent perform the final independent recheck.

No paid API, new workflow, public release, repository visibility change, automatic merge or other-project modification was made.


## 2026-10-23 — Follow-up: refill a vacated manual queue slot (PATCH UNTESTED)

**Production change:** [`f8b81550`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/f8b81550f275eff8c01588572b83dfd3dd815c78) changes `ManualDownloadQueue.removeFromQueue(ids)`: when a queued/active ID is removed, the queue now schedules a **debounced** admission pass after releasing `admissionLock`. Before this fix, the `OnJobRemoved` listener simply removed the ID without calling `shake()`; if that ID occupied an active slot, pending jobs could remain stranded indefinitely until an unrelated queue event. Existing completion/cancellation flows continue to work and the configured concurrency cap is unchanged. Empty/repeated removal is idempotent.

**Regression test:** [`38e40674`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/38e40674679e683839f417824de5acc19e03fd48) extends `ManualDownloadQueueBatchResumeTest.kt` with `removingAnActiveJobAdmitsNextPendingJobWithoutCompletionEvent`: start three IDs with max concurrent=1, remove the active ID without a completion event, require admission of the next queued ID, confirm duplicate removal does not double-start, then remove that ID and require the third. This test would time out against the prior source because no scheduling was triggered.

**Actual verification in this run:** Source and test were written to existing draft PR #3. The GitHub connector returned the above commit SHAs. No Kotlin compiler, Gradle/JUnit or Windows benchmark was executed on this patch; `fetch_commit_workflow_runs` returned **zero PR workflow runs** for test commit `38e40674` and the combined commit status had **zero reported checks** when queried. Mark **PATCH UNTESTED**, not PASS. The historical active-16 13/16 failure remains unresolved, and this slot-refill fix must not be represented as a proven root-cause fix for it.

**Required exact-SHA verification:** Run `./gradlew --no-daemon --dependency-verification strict :downloader:core:desktopTest :desktop:app:compileKotlin` including both queue tests; then Windows 1/4/16 x 3 installed HTTP concurrency + 63 SHA-256 files + process-tree CPU/RAM and FTP pause/REST resume, using a build of this PR head. Do not relax socket-count assertions or the user's max-concurrent setting. Keep the browser extension in its own repository, exclude Torrent and do not release/merge automatically. No paid API or new workflow was created.


## 2026-10-23 — Manual queue start-failure slot recovery (production patch; CI pending)

**Source commit:** `f30f0d56939ff7c01a84ef398bc20bea0e343fbf`
**Regression test commit:** `db1281f8528abc4047ae705bd13443374a667708`
**Existing draft PR:** [#3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3), `automation/idm-ftp-cancel-and-category-flush` → `codex/repair-rf00-rf01`. Base SHA reported by PR: `6c386abeae95e8e0460308d17c033558cbd60f15`.

**Observed source-level defect and correction:** `ManualDownloadQueue.downloadAQueueItemIfPossible()` reserved an active concurrency slot before launching suspendable `downloadEvents.startJob(...)`. When that call threw before the manager emitted any lifecycle event, the reserved ID remained in `activeItemsFlow`, preventing the next pending job from starting. The new code catches ordinary `Exception`, removes the failed ID from the manual queue and lets the existing debounced refill admit the next pending task; `CancellationException` is rethrown so scope cancellation is not mistaken for a task failure. No global concurrency bypass, new storage, change of the default cap (3), or automatic retry loop was introduced.

**Regression test:** `ManualDownloadQueueBatchResumeTest.failedAsynchronousStartReleasesItsSlotAndAdmitsTheNextJob` injects an `IOException` from the first start with **no lifecycle event**, asserts the second ID starts under a max-concurrent limit of **1**, asserts no failed-ID spin and no over-admission of the third, then removes the active second ID and checks that the third starts. This is a separate failure mode from the earlier missing removal-slot refill test.

| Verification gate | Status | Evidence |
|---|---|---|
| Production + test code committed on isolated PR | PASS | Source `f30f0d5`, test `db1281f8` |
| GitHub source-level static invariants | PASS (static only) | Re-fetched both files at `db1281f8`; seven targeted source/test presence assertions passed. **Not a compiler/JUnit result.** |
| Actual `:downloader:core:desktopTest` including new regression | NOT TESTED | No Gradle dependency cache / full project available in this automation environment. |
| Windows `:desktop:app:compileKotlin` and JUnit XML gate | NOT TESTED | GitHub `fetch_commit_workflow_runs` on exact test SHA `db1281f8` returned **zero** PR workflow runs; existing validation workflow is scoped to the base branch. |
| Windows installed active 1/4/16 × 3, 63 SHA-256 files and CPU/RAM CSV | FAIL historically / CURRENT NOT TESTED | [39929246748](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39929246748) on older source: 16-task round 2 only 13/16 in flight. This patch handles one potential stranded-slot cause; it **does not prove** the benchmark fixed. |
| FTP Pause → REST Resume and GUI Add URL/OK | FAIL historically / CURRENT NOT TESTED | Older [39929246815](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39929246815) failure; prior FTP cancellation patch on PR #3 still awaits exact-SHA Windows validation. |
| Single Windows EXE, UI parity, credentials, upgrade and independent full recheck | NOT TESTED on this SHA | No new Windows artifact or GUI/device evidence. |

**Next action:** Obtain an authorized **exact PR-head SHA** Windows test run (without relaxing socket/CRC/SHA assertions or altering normal concurrency settings); first run `:downloader:core:desktopTest` and `:desktop:app:compileKotlin`, then the strict installed FTP and 1/4/16 benchmarks. Inspect any failure-diagnostics file and only then diagnose remaining active-16 discrepancies. Do not merge PR #3 automatically. Browser extension repository untouched, torrent excluded, no API fees or public release.


## 2026-10-23 — Pending manual Pause must not start on later queue refill (SOURCE + REGRESSION COMMITTED)

**Production SHA:** [`650f6cd650cf1d8c25e73bb1464c73e740fb4cf1`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/650f6cd650cf1d8c25e73bb1464c73e740fb4cf1)
**Regression test SHA:** [`62713b1d152a6da845250619aadbd711719d25ac`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/62713b1d152a6da845250619aadbd711719d25ac)
**PR:** [#3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3), existing draft; no merge or workflow edits.

**Bug and production repair:** `ManualDownloadQueue.pause(id)` used to call `stopJob(id)` without removing an ID that was still **pending** in the manual queue. A pending download may never emit an `OnJobCanceled` event because it has not started, so when another active task vacated its slot the supposedly paused download could start without another user Resume. The fix atomically removes only pending IDs from `totalItemsFlow` under the existing `admissionLock`, schedules the normal debounced refill and then invokes the existing stop path. **Already-active IDs keep their reserved slots until their stop lifecycle event**, preserving the user's concurrency limit. No new permissions, default changes or Torrent code.

**Repository regression:** `ManualDownloadQueueBatchResumeTest.pausingPendingJobPreventsUnexpectedStartAfterSlotIsFreed` queues IDs 71/72/73 under maxConcurrent=1, pauses pending 72 with a manager that emits no stop event, frees active 71, requires 73 (not 72) to start, then explicitly resumes 72 and requires it to start only after 73 leaves.

**Executed verification, carefully scoped:**
- PASS **isolated Kotlin/JVM behavioral harness** using the installed Kotlin compiler and real bundled kotlinx-coroutines runtime. Reproduced **before** fix: started IDs `[71,72]` (wrong); reproduced **after** fix: `[71,73]` (correct), then explicit Resume started `72` after slot release. This harness reproduces the relevant queue state transitions with minimal shims; **it does not compile the whole actual project and is not JUnit**.
- PASS **7 GitHub-source static assertions** against the committed production and test files: pending removal under lock, active reservation, ordering before stop, refill, test presence and expected sequences. These are source assertions, not unit-test execution.
- **NOT TESTED:** project `:downloader:core:desktopTest` and `:desktop:app:compileKotlin`, because this runner cannot clone the repository or download Gradle dependencies. `fetch_commit_workflow_runs` on test SHA `62713b1d` returned **zero PR workflow runs** and combined commit status returned **zero statuses**. Do not mark Windows compile/JUnit PASS.
- **FAIL historically / CURRENT NOT TESTED:** 16-active Windows benchmark (13/16 in round 2 on [39929246748](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39929246748)), FTP Pause/REST resume ([39929246815](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39929246815)), external FTP and GUI, SHA-256 + CPU/RAM matrix, one-EXE packaging and full visual parity. This specific pause fix is **not** claimed to solve the separate active-16 issue.

**Next mandatory gate:** validate PR #3's exact head (including this patch) on authorized Windows CI: `./gradlew --no-daemon --dependency-verification strict :downloader:core:desktopTest :desktop:app:compileKotlin`; then installed FTP pause/resume and strict 1/4/16 × 3 benchmarks with SHA-256 and CPU/RAM artifacts, without weakening assertions. Inspect the actual output and failures before any merge. Keep browser extension in its own repository, and exclude Torrent.


## 2026-10-23 — Lowering concurrency must not reuse a slot before asynchronous stop completes

**Production commit:** [`2f542a76`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/2f542a76c28e817fc57d0bc630a61eaf14ded0a2)  
**Regression test commit:** [`767c2275`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/767c227503a045d841b4ae654929b18c6f03553a)  
**PR:** [#3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3) (existing Draft, no merge). **Production source changed: YES.**

**Failure mode:** `ManualDownloadQueue.trim()` previously called `removeActiveItem(id)` immediately before launching suspendable `stopJob(id)`. The socket could still be transferring when that slot was declared free. If another active download then finished, `shake()` could admit a new job even though the configured concurrency limit had already been reduced (e.g. 3 → 1). The fix keeps each stop-requested ID in `activeItemsFlow` until a confirmed `OnJobCanceled`/`OnJobCompleted` or explicit removal, and tracks it in `trimmedItems` so repeat shakes do not send duplicate stop requests. It does not change the user's persisted limit or add a new queue implementation.

**Regression:** `ManualDownloadQueueBatchResumeTest.reducingConcurrencyDoesNotReuseSlotsBeforeAsynchronousStopsFinish` starts three jobs at maxConcurrent=3, reduces the limit to 1, records two asynchronous stop requests that intentionally emit **no** cancellation events, removes the only untrimmed job and confirms no fourth job starts while stopping sockets still occupy reservations. After explicit removals confirm the remaining stops, the fourth job may start. The recording fixture now tracks `stopJob` calls.

| Gate | Result | Evidence |
| --- | --- | --- |
| Production source + JUnit regression committed to isolated PR | PASS | `2f542a76` + `767c2275` |
| Re-fetch source/test and check eight exact invariants | PASS — STATIC ONLY | 8/8 checks; confirmed no `removeActiveItem` inside trim request, pending cancellation path still releases reservation |
| Isolated Kotlin/JVM trim-state behavioral reproduction | PASS — ISOLATED MODEL ONLY | `kotlinc /tmp/idm_trim_regression.kt -include-runtime -d /tmp/idm_trim_regression.jar && java -jar /tmp/idm_trim_regression.jar`: reproduced pre-fix premature admission of ID 84 and verified post-fix no early admission/duplicate stop; **not actual production compilation or JUnit** |
| `:downloader:core:desktopTest` and `:desktop:app:compileKotlin` on exact PR SHA | NOT TESTED | No full Gradle project/dependency cache here. `fetch_commit_workflow_runs` returned zero PR runs and `get_commit_combined_status` returned zero statuses for `767c2275`. |
| Installed Windows 1/4/16 × 3 + SHA-256 + CPU/RAM | HISTORICAL FAIL / CURRENT NOT TESTED | Prior [39929246748](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39929246748) recorded 13/16 in one round; this trim fix **does not prove** that issue resolved. |
| FTP Pause/REST Resume, external FTP, GUI OK, browser/HTTP/HTTPS/HLS, Liquid Progress and one Windows EXE | CURRENT NOT TESTED | No Windows runner, real transfer, screenshot comparison or packaging performed on new SHA. |

**Research:** Compared with the official Apache-2.0 `kotlinx.coroutines` Semaphore/withPermit model: a permit is held until work actually completes. A wholesale Semaphore replacement is inappropriate for this dynamically resizable existing queue; preserve its architecture and enforce the same lifecycle-based reservation principle.

**Next action:** Authorize/obtain an exact-PR-SHA Windows CI run of `./gradlew --no-daemon --dependency-verification strict :downloader:core:desktopTest :desktop:app:compileKotlin`, then run installed strict 1/4/16 × 3 benchmark and FTP pause/REST resume. Investigate any failed JUnit first. **No workflow edit, paid API, browser repo change, Torrent, merge or release.** Independent Windows recheck remains mandatory.


## 2026-10-23 — Revoke queued-but-undispatched starts (production + regression; full CI pending)

**Production SHA:** `562301225517d74f08ba362285da553688e9f529`
**Regression SHA:** `237ca04e77dcf7586fd613e708731967ac6e099e`
**PR:** #3, `automation/idm-ftp-cancel-and-category-flush` → `codex/repair-rf00-rf01` (draft; not merged).
**License/dependency review:** Existing project LICENSE is Apache-2.0; repair uses only its already-installed Kotlin coroutines/StateFlow/collections, with no copied third-party code, new dependency, new paid service, workflow edit or security permission.

**Production defect:** `ManualDownloadQueue` reserves an active slot before dispatching the coroutine that calls `startJob`. If the dispatcher is occupied, a user Pause could call `stopJob` **before** the scheduled start executes; the stale coroutine could then start a supposedly paused download. Similarly, lowering max concurrency while starts are still queued could call `stopJob` on nonexistent jobs, leaving races and phantom stop requests. Existing active jobs must keep their slots until actual lifecycle cancellation; only never-dispatched reservations may be released early.

**Patch:** `scheduledStarts` holds per-admission identity tokens under `admissionLock`. The coroutine claims its own token immediately before `startJob` and skips a revoked/superseded start. Pending Pause revokes the token and removes its never-started reservation; trim revokes undispatched starts without issuing phantom `stopJob`; removal/clear also invalidates tokens. Running transfers still use the existing stop/cancellation lifecycle and user max-concurrent cap. Fresh tokens prevent stale coroutines from stealing a later re-admission of the same ID. This addresses the pre-dispatch race, **not** every possible pause-vs-startJob in-flight race.

**Repository regression tests added:** `ManualDownloadQueueBatchResumeTest.pausingReservedButNotYetDispatchedStartCannotLaunchAfterPause` and `trimmingUndispatchedStartsDoesNotStopPhantomJobsOrResurrectOldTokens`. Both deliberately occupy a single-thread coroutine dispatcher to force the race, then assert which IDs actually start and that undispatched IDs do not receive phantom stops.

| Gate | Status | Evidence |
| --- | --- | --- |
| Production and tests committed on existing PR #3 | PASS | Source `56230122`, tests `237ca04e`; GitHub re-fetch returned expected blobs and 10/10 source/test structural checks passed. |
| Isolated Kotlin/JVM coroutines behavioral harness | PASS (isolated model only) | Executed `kotlinc -cp kotlinx-coroutines-core-jvm.jar /tmp/idm_queue_admission_harness.kt -include-runtime -d /tmp/idm_queue_admission_harness.jar` and `java -cp ... Idm_queue_admission_harnessKt` locally. Before/after: paused ID `[91]` → `[92]`; trim `[101,102,103]` → `[101]`. This model is **not** the full repository source/JUnit. |
| Repository Gradle/JUnit and desktop compile | NOT TESTED | Runtime lacks GitHub clone/DNS and full Gradle dependency graph; actual new JUnit tests are committed but not executed. |
| GitHub PR Actions and commit status on test SHA `237ca04e` | NOT TESTED | `fetch_commit_workflow_runs` returned 0 PR runs; `get_commit_combined_status` returned 0 statuses. No exact-SHA Windows build. |
| Installed 1/4/16 real sockets, 63 SHA-256 files, CPU/RAM | FAIL historically / CURRENT NOT TESTED | Prior Windows [39929246748](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39929246748) had 13/16 on active-16 round 2. This race patch is **not** proven to fix that result. |
| FTP Pause → REST Resume, HTTP/HTTPS/HLS, browser integration | FAIL/PARTIAL historically; CURRENT NOT TESTED | Existing FTP and protocol patches require exact-head Windows CI and real network/browser acceptance. Browser extension remains separate and untouched. |
| Liquid Progress parity, single EXE, Windows install/upgrade and independent recheck | NOT TESTED | No current Windows artifact, screenshots, device/process-tree measurements or final package delivery. |

**Next bounded action:** Execute the two new tests in `:downloader:core:desktopTest` and `:desktop:app:compileKotlin` on PR #3's **exact source SHA** with JUnit XML, then strict Windows installed FTP Pause/REST and 1/4/16 × 3 socket/SHA/CPU/RAM acceptance. Investigate actual failures rather than relaxing limits. Follow with real-browser, visual, upgrade and independent security recheck. Do not mark CANDIDATE/VERIFIED, merge, release or disable this recurring task.

**Scope:** Torrent excluded. No other repository, main branch, ChatGPT Library, paid API or workflow changed.


## 2026-10-23 — HyperGet HG-01 display brand, first non-breaking source slice

**Product name:** HyperGet only. **Existing draft PR:** [#3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3), base `codex/repair-rf00-rf01`, head `automation/idm-ftp-cancel-and-category-flush`. **Production source changed: YES.**

- **Source commit:** [`1a84fee4`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/1a84fee483351e4d21dbb0a3857c6facc684bb5e). In `shared/app/build.gradle.kts`, only the generated `APP_DISPLAY_NAME` is now `HyperGet`. This flows through `SharedConstants.appDisplayName` → desktop `AppInfo.displayName` → `HomeWindow` title, tray tooltip and other existing display-name consumers. No storage migration or executable rename was attempted.
- **Regression source:** [`90668d62`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/90668d6278156dfcaca69233266a773d1bd86ffa) adds `HyperGetBrandingCompatibilityTest` under `shared/app/src/commonTest`. It asserts the visible name and keeps technical runtime/storage IDs distinct from the display brand.
- **Upstream/license/security:** Existing codebase is derived from AB Download Manager under Apache-2.0 (repository `LICENSE`). Reused the already-present BuildConfig → SharedConstants → AppInfo display-name pipeline; no third-party code copied, no new dependencies, no credential/IPC/registry mutations, no new service or cost.

| HyperGet acceptance gate | Status | Exact evidence / remaining work |
| --- | --- | --- |
| HG-01: main Windows window/tray name wiring | **PASS STATIC ONLY; WINDOWS NOT TESTED** | Re-fetched committed build config and `AppInfo`/`HomeWindow`: 10/10 static invariants passed. Actual rendered window/tray/About/settings/notifications need Windows inspection. |
| HG-02: preserve existing technical identity | **PASS SOURCE DIFF; UPGRADE NOT TESTED** | `APP_NAME`, `PACKAGE_NAME`, `DATA_DIR_NAME`, `getAppName()`, NSIS `upgradeUuid`, installer package name and data-dir configuration unchanged by this round. Existing downloads, credentials, registry and rollback need old→new Windows E2E. |
| HG-03: HyperGet.exe, installer, shortcuts and separate browser extension | **NOT TESTED / NOT IMPLEMENTED** | Deliberately retained legacy binary, host and registry identities until migration tests; extension repo untouched. |
| HG-04: original liquid-style HyperGet icon | **NOT IMPLEMENTED** | No icon/image was generated or installed in this round. |
| HG-05: complete feature/Windows/security/independent double recheck | **NOT TESTED** | Exact PR test SHA `90668d6278156dfcaca69233266a773d1bd86ffa` had **0 PR workflow runs and 0 commit statuses** when checked. No Gradle/JUnit, Windows build, GUI, FTP/HTTP/HLS, 1/4/16 SHA-256 or CPU/RAM measurements were executed on this source. Historical 13/16 benchmark and FTP failures are unresolved for current SHA. |

**Next bounded action:** Run `./gradlew --no-daemon --dependency-verification strict :shared:app:desktopTest :downloader:core:desktopTest :desktop:app:compileKotlin` and inspect JUnit XML on this PR's exact head; then run Windows installed FTP REST and strict 1/4/16 x3 socket/SHA/resource matrix. Proceed with HG-01 Settings/About/notifications and HG-03 NSIS/shortcut/browser compatibility only with old-version upgrade/rollback evidence. Do not mark CANDIDATE or VERIFIED, change `main`, auto-merge, publish a release, rename the repo/IPC/credentials, reintroduce Torrent, or use paid APIs.


## 2026-10-23 — HyperGet Windows launcher compatibility + branded dialog titles (SOURCE-ONLY, NO APP BUILD)

**User's latest binding gate:** no full app compile/build, Gradle/Compose packaging, NSIS/MSI/EXE or packaging Actions during development. Source/static tests only until all features, icon, UI, upgrade design, code review and delivery readiness are complete. Historical instructions above to run Windows packaging next are **superseded**. No CI or Windows build was requested/triggered in this round.

**PR:** [#3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3), head `automation/idm-ftp-cancel-and-category-flush`, base `codex/repair-rf00-rf01` (no main write/merge).
**Production commits:**
- [`56855f68`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/56855f688043f6cb3fd77d18b475e1fdccc28e1e): added `MainExecutableResolver.kt`. It resolves the main GUI next to legacy CLI/native-messaging helpers, preserves the old main executable when both names exist, accepts `HyperGet` when it is the only GUI, and never mutates disk/registry/IPC/data.
- [`fc4892ae`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/fc4892ae7dbebc05c2700f7dddcd78dbf318de24): `AppInfo.mainExeFile` uses this resolver on Windows only; other platforms and existing CLI/native-host filenames remain unchanged.
- [`7a82ffd4`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/7a82ffd410ded102eb080fb782857906c9215664), [`ffe59103`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/ffe59103d9d6d1324a49a1416df312e733c0ed6d), [`93f9f11c`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/93f9f11cdfcc335f0bd7dd746c19bd3133018673): Settings, About and error dialog titles now include the `AppInfo.displayName` HyperGet brand; localized Settings/About labels are retained.

**Regression source:** [`60ab3e0c`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/60ab3e0cb63fea7993d08d75aac5d067fdce2616), `scripts/source-tests/HyperGetExecutableResolverSmoke.kt`. Covers null/relative path, historical fallback, legacy-only, side-by-side preference, direct old/new GUI, new-only install, non-executable directory and case-insensitive launcher name. Uses temporary `.bin` fixtures, **not executable builds**.

| Gate | Status | Evidence / limitation |
| --- | --- | --- |
| New production resolver + GUI wiring + branded dialog source | **PASS SOURCE COMMITTED** | Six production commits above; re-fetched on existing PR #3. |
| Actual isolated Kotlin source regression | **PASS (10 cases, no app build)** | Ran `kotlinc /tmp/hyperget-source-check/MainExecutableResolver.kt /tmp/hyperget-source-check/HyperGetExecutableResolverSmoke.kt -d /tmp/hyperget-source-check/classes && kotlin -classpath /tmp/hyperget-source-check/classes com.abdownloadmanager.desktop.utils.HyperGetExecutableResolverSmokeKt`. Output: `PASS HyperGet executable path source smoke: 10 cases`. Before execution, `git hash-object` on both local copies matched GitHub blob SHAs `cc122d8462bedf0b6d39efededa4963e354b24a5` (production) and `c6eb5f5df6c10248435502c22a39764b8cf4fc03` (test), so these were exact committed sources, not a mock. |
| GitHub source/static checks | **PASS 13/13 (static only)** | Re-fetched resolver, `AppInfo`, smoke test, three dialogs, native-messaging manifest/host and BuildConfig. Verified brand wiring, safe old/new precedence, Windows-only change, no file mutation, legacy IPC/registry keys. No Gradle/JUnit/Windows assertion. |
| No packaging during this round | **PASS (actions not invoked)** | Inspected workflow triggers. Existing packaging workflows are push-scoped to `codex/repair-rf00-rf01`/main or manually dispatched, not PR #3 head. No workflows were edited or dispatched. This is **not** a guarantee about external actors/manual triggers. |
| CI on exact source SHA `93f9f11c` | **NOT TESTED / 0 checks** | `fetch_commit_workflow_runs`: 0 PR runs; combined commit status: 0 statuses. Deliberately did not trigger build workflows. |
| HG-01 visible dialog titles | **PASS SOURCE ONLY; WINDOWS NOT TESTED** | Settings/About/error now use HyperGet; Windows screenshot/rendered UI inspection deferred. |
| HG-02 legacy data/credentials/registry/IPC | **PASS NO-MUTATION SOURCE REVIEW; UPGRADE NOT TESTED** | No changes to IDs, storage, native-host names or registry. Actual old-to-new upgrade, rollback and credential preservation still unverified. |
| HG-03 `HyperGet.exe`/installer/extension | **NOT IMPLEMENTED / NOT TESTED** | This round only prepares runtime path selection; binary rename and packaging intentionally deferred. Separate extension repo main HEAD `491c2d64589e6697c53a6bdb15ccce38a6fc860f`, no open PR at inspection; no cross-repo write. |
| HG-04 custom icon and Liquid Progress parity | **NOT IMPLEMENTED / NOT TESTED** | Original brand icon, DPI checks and approved screenshot parity remain open. |
| HG-05 FTP/HTTP(S)/HLS, 1/4/16, SHA-256, CPU/RAM, Windows installed acceptance and independent double recheck | **NOT TESTED on current SHA** | Historical FTP and 13/16 active transfer failures remain unresolved as exact-head Windows tests are intentionally deferred. No final EXE/artifact. |

**Open-source/license/security review:** Inspected existing AB Download Manager-derived source, `AppInfo.calcExecutable`, jpackage path usage, and repo `LICENSE` (Apache-2.0). Adapted existing Java/Kotlin `File` path behavior with no new dependency, no downloaded executable and no third-party code copied. Conservative legacy-first selection prevents silently switching an existing helper to a side-by-side branded executable; final installer/upgrade must explicitly resolve how an old GUI binary is retired while keeping IPC/credentials stable.

**Next source-only round:** Audit remaining user-visible HyperGet strings and Windows installer/shortcut/native-host migration logic without building; add source-level compatibility tests and icon/readiness checks; continue FTP/HTTP/HLS/queue security and logic fixes with unit/static tests. Keep `AUTOMATION_COMPLETE: IN_PROGRESS`. Do not package until all source/readiness gates are met, then do first final build, actual Windows acceptance, independent double recheck and verified delivery. No Torrent, paid API, auto-merge, release or ChatGPT Library access.
