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
