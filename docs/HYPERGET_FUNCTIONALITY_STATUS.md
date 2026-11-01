# HyperGet functionality handoff (2026-11-01)

This is the persistent, source-linked checkpoint for functionality work on `main`.
It does **not** authorize changes to Liquid Bar, themes, typography, layout, or the separate visual branch.
Never merge PR #3 wholesale or import the unrelated khodemadi/HyperGet repository without user approval.
No Torrent support.

## Verified main, as checked at this checkpoint

- `87029228`: HLS protected playlist/segment origin and redirect policy. Windows CI **PASS**: run `41195484677`.
- `ba0549ac`: HTTP hidden retry suppression, preserved Retry-After, two loopback tests. Windows CI **PASS**: run `41285976725`.
- `bc47c11a`: real loopback HLS redirect/cookie tests; **CI not completed** at checkpoint, run `41286406504`. Do not label it accepted until run completes successfully.

Main also contains prior tested fixes for HTTP pause/range/truncated writes, per-ID lifecycle serialization and Stop All, queue admission, native messaging safety, transactional persistence recovery, HLS segment identity/playlist cap, and Windows silent installer/uninstaller.

## Prepared functionality commits — NOT independently validated or pushed to main at checkpoint

The following commits form a strict linear parent chain after `bc47c11a` and should be advanced onto main **only after the previous commit's Windows tests pass**, checking latest main HEAD each time:

1. `55fdc39b` — make HTTP/HLS URL edits complete their stop/restart inside the per-ID lifecycle lock.
2. `258616cd` — CLI HTTP headers preserve embedded colon/equals; reject newline injection; seven tests.
3. `a5b2d6ae` — staged, no-clobber HLS assembly; four regression tests.
4. `8fb64bc7` — Windows FTP transfer engine, WinCred support, REST identity, six Kotlin FTP test files; not yet user-facing.
5. `e9ef0dd4` — register the FTP engine and expose secure `download add ftp` CLI with stdin-only passwords; functional monitor fallback, credential release on deletion.
6. `db3afcbe` — actual installed Windows FTP acceptance (GUI/CLI, paused restart and REST resume, sha256 and WinCred cleanup) with local loopback fixture.

If any CI fails, inspect the failing JUnit and logs and **fix exact underlying cause** before promoting subsequent commits. Earlier green branches never prove a later SHA. Windows installation CI validates packaging, NOT all runtime FTP or browser scenarios; use installed FTP E2E.

## Open functionality gates

- Exact HEAD Windows `:downloader:core:desktopTest` and desktop compilation/packaging for every promoted step.
- Installed Windows FTP authenticated `--password-stdin` capture, Pause, restart, positive REST offset, exact SHA-256 output and secrets cleanup.
- Test browser Chrome/Firefox capture with actual installed native messaging host and authenticated link cases.
- Dedicated 1/4/16 active-download resource/performance comparison on **main**; no source-independent performance claims.
- Reconcile verified safe FTP with GUI Add URL entry (functionality only; visual styling remains separate).
- External real-world FTP LAN/WAN and HTTPS, not only loopback tests.
- Final regression, true cross-version upgrade/rollback, single signed-off Windows installer package.

## Important performance block

PR #3 comparison run `41187095597` FAILED at 16 concurrent transfers with about **-42.55%** aggregate throughput and **+18.14%** peak working-set regression vs its chosen PR baseline. This does NOT establish main performance, but blocks blind migration of PR #3 engine/aria2. Do not claim Stage 2 acceptance or import aria2 into production without a passing controlled comparison.

For fresh context, first read `/branches/main`, the latest `main` Actions run and the latest commits. Skip anything already landed and tested.
