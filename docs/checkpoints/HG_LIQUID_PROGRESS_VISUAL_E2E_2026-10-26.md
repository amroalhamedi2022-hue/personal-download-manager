# HG-UI-LIQUID-PROGRESS-01 — shared Liquid Progress + installed Windows visual acceptance
Date: 2026-10-26
PR: #3
Branch: automation/idm-ftp-cancel-and-category-flush
Base: codex/repair-rf00-rf01

## Previous milestone continuity
The current PR head before this milestone was 3581ad1d98c4219bf0725dd07fae170311f222e5. Exact-head checks associated with that SHA were terminal-success for source-only, installed HTTP 1/4/16, JVM memory profile, Chrome+Firefox capture, FTP Kotlin, HLS, queue/lifecycle, and installed FTP pause/REST/resume. HTTP evidence remains 63/63 SHA-verified files and 9/9 process CSVs. Memory is still not closed: the full 16-way peak Private Bytes was previously measured at 590.879 MiB, so this UI change must not be described as a memory optimization.

## Required functionality
Replace the flat status strip with a restrained Liquid Progress treatment in the main download list:
- retain determinate width and status-specific theme gradient;
- animate a soft glossy band and two small bubbles only while status is Downloading;
- share one list-level animation clock, created only while at least one download is active;
- read the shared phase in Canvas draw code, not by running one infinite transition/recomposition loop per row;
- keep the existing left-side percent/status label and do not add a detached right-side percent;
- capture a real installed Windows screenshot during a real transfer and verify the completed 16 MiB fixture by SHA-256.

## Test question, expected result, and why source-only is insufficient
Question: Does the exact PR source compile and install on Windows, visibly render the moving liquid treatment while the installed CLI and loopback fixture prove a live Downloading state, and still complete the actual file byte-for-byte?
Expected: the installed GUI is detected and foregrounded; the screenshot is captured while status is Downloading and at least one real data task is active; the 16 MiB output finishes with SHA-256 equal to the fixture; the workflow uploads liquid-progress-active.png and a JSON report containing the exact source SHA, status at capture, byte counts, and both hashes.
Source contracts cannot prove Kotlin/Compose API compatibility, Windows native GUI capture, IPC to the installed process, real network transfer, or final file integrity. One purposeful installer build is therefore justified for this visual milestone; it is not a release build.

## Implementation and regression protection
- TableDownloadItem.kt: shared phase State<Float> is consumed in the Canvas draw phase; determinate width retains a short tween; active-only glossy band and bubbles are clipped to the pill track.
- DownloadList.kt: creates one 1.8-second linear infinite transition only when at least one row is Downloading and provides the State to rows.
- test_hyperget_liquid_progress_source.py: protects shared-clock ownership, draw-phase reads, active-only rendering, live-transfer screenshot gate, SHA verification and artifact upload wiring.
- windows-installed-ui-smoke.yml: PR-triggered exact-head Windows build/install and visual E2E, guarded so docs/test-only follow-up commits skip expensive packaging.
- No new dependency was added. The implementation uses existing Compose APIs; official animation guidance is https://developer.android.com/develop/ui/compose/animation/quick-guide and the existing AndroidX progress-indicator reference implementation is Apache-2.0 at https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/compose/material/material/src/commonMain/kotlin/androidx/compose/material/ProgressIndicator.kt.

## Acceptance status at commit creation
- Source-only contract: NOT TESTED until CI completes.
- Kotlin/Compose compile and NSIS install: NOT TESTED until the Windows workflow completes.
- Live screenshot, active Downloading status, 16 MiB final SHA-256: NOT TESTED until the Windows workflow completes and its artifact is inspected.
- Existing HTTP 1/4/16, FTP, HLS, browser and memory evidence is historical exact-SHA evidence, not retroactively proof for this UI commit.
- No main write, merge, auto-merge, paid runner/API, release, or Torrent.


## 2026-10-26 04:30 Asia/Jerusalem — diagnose and repair visual-E2E false negative (Windows recheck pending)

**Observed exact-head result:** installed UI workflow [40257834591](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40257834591) compiled the Compose UI, built and silently installed one NSIS installer, and launched/captured the installed GUI successfully. The new visual-transfer step then failed at `scripts/Run-WindowsLiquidProgressVisualE2E.ps1:129` with `Could not prove a live data transfer with Downloading status before screenshot`. This is a real acceptance **FAIL**, not a source/compile failure. Installer SHA-256 from the run log: `949283b4eb8450d0ab6ca7f67f6a30f6751ad9166411881f4860f2343198ea8c`. The uploaded visual artifact (`12624295283`) is diagnostic-only and does not establish a captured live screenshot or final SHA.

**Root-cause analysis:** the script passed `--task-chunk-delay-ms 180` but never activated the fixture's pacing mode. `LocalHttpRangeFixture.py` applies the aggregate per-task pacer only when the `/stats` endpoint has been reset with `gate_tasks` > 0; otherwise only the legacy per-connection delay applies. The script also launched a fresh CLI process on every 250-ms poll, which added overhead and could miss the brief live window. The failure therefore points to a test-harness timing defect; it does **not** prove a Liquid Progress rendering defect or an engine download defect.

**Repair pushed:** `570cd36f5bb07581c7c5fc2b5a0134233bbf1595` enables `/stats?reset=1&gate_tasks=1` before starting the GUI/download, polls lightweight fixture counters frequently, and only launches the status CLI after actual payload bytes are moving. On failure it writes sanitized `liquid-progress-diagnostics.json` with task status and fixture counters. `0073a746fa0b78ee359bdfb9d63b4cea4b76a4e1` adds source-contract coverage for paced-mode activation, polling order and diagnostic retention.

**Next acceptance question:** on the new exact source SHA, does Windows compile/install and capture `liquid-progress-active.png` while CLI status is `Downloading` and loopback data bytes are actively moving, then finish the 16 MiB file with matching SHA-256? Expected: screenshot + JSON report with active counters and identical hashes; installer lifecycle step also must run and pass. Source checks cannot prove this. Status remains **NOT TESTED** until the new Windows run and artifact are inspected.

## 2026-10-26 04:58 Asia/Jerusalem — strengthen screenshot-time proof and prevent CI cancellation

The prior harness repair was present in source, but the subsequent exact-head workflow run was cancelled and later runs scope-skipped the expensive UI job because their newest commit only changed documentation. Therefore there is still **no passing screenshot artifact** for the repaired harness.

This follow-up makes two test-quality changes before the next purposeful Windows build:
- CLI status polling is rate-limited to at most twice per second while cheap fixture counters remain polled every 100 ms, avoiding repeated process-start overhead.
- After the 900 ms animation observation window, the test rechecks the installed CLI status, requires at least one live data task, and proves the fixture byte count advanced immediately before screenshot capture. The JSON report records this evidence and the status-poll count; failure diagnostics retain the last safe status and fixture counters.
- The installed-UI workflow no longer cancels an already-running exact-SHA acceptance job merely because a newer docs-only commit arrives. Its scope gate still skips unrelated commits.

**Pre-build question:** does this exact source compile/install on Windows, capture the real GUI while the CLI status is Downloading, at least one fixture task is active and bytes advanced during the screenshot window, then finish the 16 MiB file with matching SHA-256 and pass same-version reinstall/uninstall?

**Expected PASS:** screenshot + JSON report tied to the exact source SHA; BytesAdvancedBeforeScreenshot=true; final status Finished; expected/actual SHA-256 equal; installer lifecycle proof present. Source checks cannot prove these GUI/IPC/timing/file-byte properties. This is one targeted acceptance build, not a release. No main write, merge, auto-merge, paid service, or Torrent.


## 2026-10-26 06:00 Asia/Jerusalem — exact-head artifact diagnosis and status-parser repair (Windows recheck required)

**Observed run:** [40262112549](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40262112549) on source SHA `0edcde0598864071adc716d232046a8ec03f3010` compiled Compose, built one NSIS installer, installed it in an isolated directory, and launched the GUI. The live screenshot gate failed; installer lifecycle was skipped because that step depends on the visual step succeeding.

**Artifact inspection:** `windows-liquid-progress-visual` artifact [12625646344](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40262112549/artifacts/12625646344) contains no screenshot/report, but it does contain the fixture and completed 16 MiB output. Both files are exactly 16,777,216 bytes and have matching SHA-256 `341aacac661ccb210720bedaa9ead5d668fe5ea41a73532fc147c71e34040df1`; the downloaded file is byte-identical to the fixture. The diagnostics show `TaskId=0`, 27 status polls, 16,777,472 response bytes, and zero active requests/tasks when the loop timed out. Thus transfer integrity succeeded, but screenshot-time acceptance did not.

**Root cause found in the test harness:** two status-row filters used an over-escaped `\\t` regular expression inside PowerShell single quotes. That pattern searched for a literal backslash followed by `t`, not the CLI's actual tab delimiter, so the real `ID<TAB>Status` line was never recognized. This is a harness defect; it is not evidence of a production download or Compose rendering failure.

**Repair prepared for exact-head recheck:** status rows now use a literal `[char]9` prefix with ordinal `StartsWith`, and final `Finished` parsing uses the suffix after that prefix. A source regression contract prevents the over-escaped filter from returning. The 16 MiB transfer + matching hash in the failed run are recorded as useful partial evidence only; they do not retroactively pass the screenshot gate.

**Pre-build question:** does the corrected installed Windows test recognize the real CLI tab-delimited status row, capture the actual GUI while status is `Downloading` and fixture bytes are advancing, finish the 16 MiB file with matching SHA-256, then complete same-version reinstall and silent uninstall?

**Expected PASS:** exact-SHA Windows run succeeds; `liquid-progress-active.png` and `liquid-progress-report.json` exist; report shows `Downloading`, active fixture task, `BytesAdvancedBeforeScreenshot=true`, final `Finished`, and identical expected/actual hashes; installer lifecycle proof exists. Source-only checks cannot establish installed IPC/status formatting, Windows screenshot timing, or actual file bytes. One targeted build is justified; no release is being produced.


## 2026-10-26 — second exact-head result: transfer hash passes, status observation still fails

**Run:** [40266707875](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40266707875), source SHA `5e8266873bda688c0cabd20556f96bc6f0446a74`. Windows Compose build, single-installer packaging, silent install, GUI launch, and six real idle/minimized measurement CSVs **PASS**. The active Liquid Progress gate still **FAIL**; installer lifecycle did not run because it is downstream of that gate.

**Artifact:** [12626702200](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40266707875/artifacts/12626702200). The 16 MiB output and fixture are byte-identical; both SHA-256 values are `341aacac661ccb210720bedaa9ead5d668fe5ea41a73532fc147c71e34040df1`. No screenshot or passing report exists. Diagnostics still show task 0, 38 polls, and zero active requests when the status loop ends. The literal-tab prefix change alone did not fix observation; therefore the earlier regex diagnosis was incomplete.

**Next diagnostic repair:** invoke the CLI with the positional ID before `--status-lines`, and record the status command exit code plus a short sanitized output preview in failure diagnostics. This distinguishes a parser/IPC/empty-result problem from timing without logging URLs or local paths. A new Windows build is justified because source tests cannot prove the installed CLI output and live status observation.

**Pre-build question:** does the exact revised command return the real `ID<TAB>Status` row from the installed application, allow a screenshot while `Downloading` and bytes are advancing, then complete SHA verification and installer lifecycle checks? Expected PASS requires the screenshot/report and lifecycle proof, not just successful compilation or matching final bytes.


## 2026-10-26 06:23 Asia/Jerusalem — exact-head status CLI ordering repair (Windows recheck required)

**Exact failed run:** [40268376077](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40268376077), source SHA `de210ffdc3c7e112fd9dd6dbe8d9e485dd353bd6`. Compose compile, one NSIS installer build, isolated install and GUI launch **PASS**; the visual gate **FAIL**; installer lifecycle **SKIPPED** because the screenshot gate failed.

**Artifact inspected:** [12627488411](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40268376077/artifacts/12627488411), ZIP digest `sha256:e995c5d42f5968173ac47962ce15fe1d214b3ae4c1781c5d234eb02e36233d85`. The fixture and downloaded output are both 16,777,216 bytes with matching SHA-256 `341aacac661ccb210720bedaa9ead5d668fe5ea41a73532fc147c71e34040df1`. Fixture counters prove a paced transfer occurred (peak distinct tasks 1; 256 paced chunks; about 45.85 seconds in the aggregate pacer). However, no active screenshot/report was produced. The CLI status subprocess exited 0 but no accepted status row was observed; task ID was 0 and the sanitized preview was too generic to distinguish a default table from other output. This is a **FAIL for visual acceptance**, despite successful transfer integrity.

**Source diagnosis and patch:** the visual script used `download show $taskId --status-lines`, while the existing 1/4/16 benchmark and its source contract use the stable option-first form `download show --status-lines <IDs>`. The current evidence does not prove the option-order mismatch is the sole cause, so the next run is a targeted falsification test, not a presumed fix. All three visual status probes now use the option-first form. Failure diagnostics classify status rows, no-downloads output, formatted-table output, and CLI usage/error output without retaining raw table rows or paths; preview arrays are flattened correctly and line counts are recorded. Regression assertions protect both command order and privacy-safe diagnostics.

**Pre-build question:** does this exact source return a parseable `ID<TAB>Status` row from the installed CLI while the fixture is moving, capture the real GUI during `Downloading`, complete the 16 MiB transfer with matching SHA-256, and then pass same-version reinstall/silent uninstall?

**Expected PASS:** screenshot and exact-SHA JSON report; `StatusAtCapture=Downloading`; active fixture task and `BytesAdvancedBeforeScreenshot=true`; final CLI status `Finished`; matching expected/actual hashes; installer lifecycle proof. Source tests cannot prove installed CLI argument parsing, IPC timing, Windows screen capture or actual file bytes, so one more purposeful Windows build is justified. No production rendering change is claimed in this harness-only repair; no main write, merge, release, paid service or Torrent.


## 2026-10-26 07:04 Asia/Jerusalem — Liquid Progress status output normalization (Windows recheck required)

**Exact-head failure:** Windows installed GUI run [40269580569](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40269580569) on source SHA `4f5b74ba187c5bedc24393799a24dbbfd9a31a33`. Compose/installer build, isolated silent install, and GUI launch **PASS**; active Liquid Progress screenshot gate **FAIL**; same-version reinstall/silent uninstall **SKIPPED** behind the failed gate. Installer lifecycle evidence was not produced.

**Artifact inspected:** [windows-liquid-progress-visual ZIP](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40269580569/artifacts/12628195357), artifact ZIP SHA-256 `4103acfe10a7019a7b36ad53dcc781aa81bb6654de31808c91b42f3025435a59`. The fixture and downloaded file are both 16,777,216 bytes and byte-identical, SHA-256 `341aacac661ccb210720bedaa9ead5d668fe5ea41a73532fc147c71e34040df1`. Fixture counters prove one paced task, 256 chunks and 45.884 seconds of aggregate pacing. But the report shows task ID 0, 40 status polls, exit code 0, and one output line summarized only as 16 characters; no active screenshot was produced. This is **FAIL for visual acceptance**, not a download-integrity failure.

**Patch on the existing PR branch:** normalize ANSI escapes, BOM and zero-width prefixes before parsing; split the tab-delimited row into columns and validate the requested ID/status instead of relying on a raw prefix; reconstruct only a safe ID/status diagnostic; and classify known generic task-lookup messages without persisting raw output. Source-contract assertions cover normalization, field parsing, all three status probes and redaction. This does **not** assume the hidden 16-character output is the root cause; the next exact-SHA run is the falsification test.

**Pre-build question:** does this exact source recognize the installed CLI's actual status row during a paced transfer, capture the visible GUI while status is `Downloading` and bytes advance, complete the 16 MiB download with matching SHA-256, and then pass same-version reinstall plus silent uninstall? Source tests cannot prove installed CLI output, IPC, Windows screenshot timing, or file bytes. Expected evidence is the active screenshot, exact-SHA JSON report, final `Finished` status, matching hashes, and lifecycle record.

No production renderer change is claimed in this harness-only repair. No main write, merge, release, paid API or Torrent.

**Follow-up CI scope guard:** source-only contract changes remain in the workflow path trigger, but no longer independently trigger an expensive installer/UI build. The workflow-file change in this commit intentionally causes one exact-SHA Windows recheck; future test-only edits continue through fast source CI without rebuilding the app.

**Fast source-gate correction:** the first source-only run on the normalization patch caught a stale contract assertion that still expected a hard-coded `FinalStatus = 'Finished'` after the report was correctly changed to use the parser-derived `$finalStatus`. No PowerShell/Compose compile failure was involved. Updated the contract to require the parsed value and the explicit Finished gate; this is a test-only correction and is excluded from the expensive installer scope.

## 2026-10-26 — latest exact-head CI checkpoint (visual gate still running)

Current branch head before this ledger update: `bdf68673ec28fe44adb6b9b43718ac90d78dd59a`.

- **Source-only contracts:** PASS — [run 40273299476](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40273299476). This includes the corrected Liquid Progress source contract.
- **Real Windows FTP Kotlin/JUnit:** PASS — [run 40273299464](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40273299464).
- **HLS guarded redirect/segment SHA milestone:** PASS — [run 40273299535](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40273299535).
- **Queue lifecycle regression:** PASS — [run 40273299422](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40273299422).
- **Installed authenticated FTP pause/restart/REST/SHA:** IN PROGRESS — [run 40273299503](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40273299503).
- **Installed Liquid Progress visual E2E:** IN PROGRESS on the harness-change SHA `d8e8f557693c7db398bc4b3e4cb57d4df8867c58` — [run 40273070311](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40273070311). The Windows installer build is the active step; no screenshot verdict is available yet.
- **HTTP 1/4/16 matrix and Chrome/Firefox E2E on this docs/test-only head:** scope-skipped — [run 40273299650](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40273299650) and [run 40273299511](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40273299511). These are not new PASS results. Prior exact-head functional evidence remains recorded above.
- **Installed JVM memory profile:** scope-skipped — [run 40273299427](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40273299427); no new memory improvement is claimed.

The source/test/docs commits do not change the production downloader or Compose renderer. Do not close HG-UI-LIQUID-PROGRESS-01 until the active screenshot, `Downloading` status at capture, byte advancement, final SHA-256 equality, `Finished` status, and reinstall/uninstall proof are all present in an inspected exact-SHA artifact.

## 2026-10-26 — second installed visual result and suffix-normalization repair

**Run:** [40273070311](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40273070311), source SHA `d8e8f557693c7db398bc4b3e4cb57d4df8867c58`. Compose/installer build, silent isolated install and GUI launch **PASS**; screenshot-time status gate **FAIL**; same-version reinstall/uninstall **SKIPPED**.

**Artifact:** [windows-liquid-progress-visual](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40273070311/artifacts/12628404908), ZIP SHA-256 `db7c80523b297810dfeac319dc222fadfd83ef44e7ba64afa124a773784490ab`. The fixture and downloaded output remain byte-identical at 16,777,216 bytes, SHA-256 `341aacac661ccb210720bedaa9ead5d668fe5ea41a73532fc147c71e34040df1`. Fixture counters prove one paced task, 256 chunks and 45.869 seconds aggregate pacing. The parser-normalization patch did not resolve the status row: task ID 0, 23 polls, exit 0, one unclassified 16-character line, and no screenshot/report.

**New targeted change:** the prior normalizer removed invisible Unicode prefixes but not suffixes. Extend trimming to both ends for BOM/zero-width characters, and include a sanitized plain-ASCII message only when output contains letters/spaces (the runner uses a fresh isolated install); all arbitrary rows, names, paths and URLs remain redacted. This is a diagnostic hypothesis, not a confirmed root cause.

**Pre-build question:** does the two-sided normalization recover a valid ID/status row, capture the installed GUI while `Downloading` and fixture bytes advance, verify the completed 16 MiB SHA-256, and pass same-version reinstall plus silent uninstall? The prior artifact proves that source tests alone cannot establish this Windows CLI-output behavior or screenshot timing. The next build is justified because the script changes based on inspected exact-SHA evidence.

## 2026-10-26 — source-contract failure caught and corrected before next UI verdict

Source-only run [40274122171](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40274122171) failed two assertions because the previous generated script blob accidentally duplicated the visual harness body. The assertions correctly caught the duplicate CLI probes (6 instead of 3) and parser calls (4 instead of 2); this was a patch-composition defect, not a product or Windows compile failure.

**Correction:** reconstructed `Run-WindowsLiquidProgressVisualE2E.ps1` from the exact known-good harness source at `d8e8f557693c7db398bc4b3e4cb57d4df8867c58`, then applied only the intended two-sided invisible-character normalization and safe plain-ASCII diagnostic. The expected script shape is again one server start, three CLI status probes, two active probes and one final-status probe. The latest source-only rerun must pass before accepting the next Windows visual result.

The suffix-normalization hypothesis remains unverified until a fresh exact-SHA Windows run produces the screenshot/report or a more specific safe diagnostic.