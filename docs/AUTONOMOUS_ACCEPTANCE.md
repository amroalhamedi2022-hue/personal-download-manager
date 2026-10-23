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
