# M09 — Installed Windows Resource & Transfer Acceptance Protocol

**Status at authoring (2026-10-23):** instrumentation, deterministic local
server, Python socket contract, PowerShell syntax gate and Windows Actions
harness are committed. The 1/4/16 full Windows benchmark is **NOT certified**
until the linked Windows Actions run finishes successfully and its artifacts
are inspected. Do not confuse an Actions build starting with transfer acceptance.

## Why we measure

The user requested a downloader with IDM-like behavior **and** controlled
CPU/RAM usage. Merely adding a CPU/RAM monitor is not an optimization.
Real process-tree measurements establish what can be optimized later.

## Existing known-good source evidence

- Windows Kotlin/JUnit gate: 150/150 tests across 17 JUnit suites on E054
  source. Production duplicate terminal-completion race fixed with CAS.
- Installed Windows GUI smoke: isolated silent NSIS install and actual GUI.
- Installed HTTP E2E: real loopback Range server, actual installed GUI and CLI,
  16 MiB downloaded byte-for-byte. New Windows sharing-lock retry succeeded
  at [run 39917907039](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39917907039).
- Local HTTP fixture contract (3 Python socket tests + PowerShell AST audit)
  passed at [run 39918260441](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39918260441)
  before the additional Pause/Resume script was included in the same audit.
- No published real FTP LAN/WAN, media-capture, clean upgrade, or full visual
  parity acceptance should be inferred from these results.

## Idle sample protocol

`.github/workflows/windows-installed-ui-smoke.yml` installs the EXE,
explicitly starts the GUI, captures real screen pixels, then
`Run-WindowsIdleBenchmarkMatrix.ps1` measures **three 20-second rounds**
each of warm idle and minimized idle on the installed app.

`Measure-WindowsDownloadManager.ps1` samples the full application process
tree: WorkingSetMiB, PrivateBytesMiB, CpuMachinePercent normalized over
logical processors, handles, threads, root/child process count and OS label.
These are separate metrics, never a fabricated "private working set".

**Installer invariant:** `/S` must not auto-launch the GUI; otherwise
there can be two root app processes and a misleading resource baseline.
Interactive finish-page launch remains supported.

## Active 1/4/16 workload protocol

The `windows-installed-active-benchmark.yml` workflow builds exactly one
NSIS EXE from its checked-out source SHA and installs it on Windows Server
2022. It discovers the real GUI and CLI launchers and runs
`Run-WindowsActiveBenchmarkMatrix.ps1`:

- One real process-tree sample **per second**, 8 seconds per round.
- Three independent rounds per workload: **1, 4, 16** download tasks.
- 32 MiB deterministic binary fixture; local HTTP server bound only to
  `127.0.0.1` with 500 ms/chunk throttle so in-flight overlap is observable.
- 3 × (1 + 4 + 16) = **63 distinct outputs**, totaling 2,016 MiB of
  downloaded payload, each checked by expected size **and SHA-256**.
- `LocalHttpRangeFixture.py` `/stats` endpoint records active HTTP request
  bodies, peak simultaneous requests and **distinct task identifiers**.
  Benchmark must observe at least the requested number of distinct active
  task IDs simultaneously *before resource sampling*. This avoids calling
  16 tasks "concurrent" when the queue silently limited them to four.
- Repeat SHA check after a 2-second settle to catch late output writes and
  terminal completion races. Sharing violations while the downloader
  still owns the file are retried within a strict 150-second deadline.
- Nine raw CSVs, a task manifest, round summary and a Markdown report are
  uploaded. The large binaries are intentionally excluded from GitHub
  artifacts; their hashes are preserved in the manifest.

`Summarize-WindowsActiveBenchmark.ps1` fails closed on missing rounds,
missing hash results, incorrect source SHA, duplicate output names, invalid
CPU/memory numbers, inconsistent OS/CPU count or missing overlap evidence.

## Separate installed Pause/Resume gate

`Run-WindowsInstalledResumeE2E.ps1` starts a slowed 32 MiB HTTP download
via the installed Windows CLI, observes bytes on real GET sockets, issues a
real Pause IPC command, verifies sockets close and byte counters stop
changing, resumes the same task ID and checks full SHA-256 and post-finish
stability. It is invoked in `windows-installed-http-e2e.yml` and produces a
separate `installed-resume-e2e.txt` proof.

**This is NOT yet a GUI click-through test** for the Pause/Resume buttons;
it tests the installed CLI-to-application engine path and socket semantics.

## Comparability and limitations

All numbers are bound to the exact Actions run/source SHA and GitHub-hosted
runner. The localhost fixture is deliberately reproducible; it is not WAN,
real CDN/media, SFTP, torrent, or an actual IDM competitive throughput
benchmark. Only compare versions measured on the same runner family with
the same fixture, segment settings, workload and CPU normalization.
Hosted machines may vary; absolute values are not guarantees for a user's PC.

M09 performance optimization is not "closed" until bottlenecks are
identified from verified data, the code is changed, and before/after runs
are repeated. The next acceptance gates remain M04 real FTP GUI/network,
M06-M08 approved UI screenshot/function parity, M10 browser/media handling,
and M11 upgrade/uninstall and stable one-EXE release. Torrent excluded.
