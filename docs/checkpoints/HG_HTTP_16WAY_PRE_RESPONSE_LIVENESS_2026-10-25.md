# HyperGet HTTP 16-way pre-response liveness milestone — 2026-10-25

## Real Windows failure at exact previous head
Installed [run 40191413066](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40191413066) on `e037a04330e77f5f24eaa8c8c7824c029c9e8e21` **FAIL** at 16-way. Real NSIS installer compile/install succeeded; 1-way and 4-way: **15/15 verified SHA-256**, **6/9 CPU/RAM CSVs**. Artifact 12598273742: all 16 jobs report `Downloading`, but task **8 (ID 22)** has only **two 256-byte probe GETs**, zero data GETs; other 15 tasks are actively transferring. The new sanitized diagnostic field is `-` for every task, because none was terminal. The 32-MiB preallocated file length is NOT evidence of successful transfer. Do not infer that this proves a particular cause (OkHttp dispatcher, OS socket cap, queue, or worker).

## Functional change / expected behavior
A pre-response `connectAndVerify()` could remain suspended indefinitely while the parent job reports `Downloading`. Add a **45-second response-header deadline to the part connection phase only**, leaving full body streaming unlimited. Convert an *inner* response deadline to retryable `SocketTimeoutException` so the existing retry budget can act; preserve caller/outer cancellation as cancellation (no spurious retry); close any acquired connection on the cancellation race. No new dependency, storage migration, IPC changes, torrent, or license change. This is a liveness repair, **not a claimed proven fix** for task 8.

## Purposeful Windows build/test question, specified before CI
Does the changed production Kotlin compile and do the four new JUnit regressions pass, preserving the existing 11 queue/lifecycle cases? Source contracts alone cannot prove Kotlin type checking or cancellation semantics. Run PR-scoped Windows `:downloader:core:desktopTest` and require **>=15 JUnit, zero failure/error/skip**. Because the change affects installed HTTP connection admission, the existing Windows NSIS+installed 1/4/16 benchmark is also triggered once to check **63/63 SHA-256 verified transfers, all 16 live data tasks for each 16-way round, and 9/9 real CPU/RAM CSVs**. The installer is **test-only**, not the final HyperGet.exe.

## Acceptance state at commit
- **PASS at prior SHA:** real Windows NSIS compile/install; 15/15 one/four-way SHA transfers; FTP/HLS/queue JUnit workflows; prior source-only checks.
- **FAIL at prior SHA:** strict 16-way live task gate (15/16), 63/63 transfer gate, 9/9 CPU/RAM.
- **NOT TESTED at new SHA:** new Kotlin/JUnit deadline tests, source CI, installed 1/4/16 benchmark, browser E2E, Liquid Progress visual comparison, upgrade compatibility, final single HyperGet.exe, independent Windows recheck.
- **Next if FAIL:** inspect exact-head artifact diagnostics for whether the missing job transitions to retry/error, and instrument the per-part Connecting/IDLE phase if necessary. Do not increase benchmark timeout or lower strict concurrency gate to hide failure.

References: official [Kotlin coroutine timeout and cancellation docs](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/with-timeout.html), existing Apache-2.0 downloader source. PR #3 remains Draft. No main write, auto-merge, release, paid service, or unrelated project modification.
