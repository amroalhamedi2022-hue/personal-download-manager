# HG-UI-LIQUID-DRAW-CACHE-01 — reduce per-frame progress rendering allocations

Date: 2026-10-26  
PR: [#3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3)  
Branch: automation/idm-ftp-cancel-and-category-flush  
Base: codex/repair-rf00-rf01

## Prior evidence and unresolved cost

- The installed Liquid Progress gate previously passed on source SHA 2fe6a20e46bfe2f71035a1892951198aaf15cb70: real screenshot while Downloading, bytes advancing, 16 MiB output verified by SHA-256, and same-version reinstall/silent uninstall. This proved the basic live visual behavior, not full IDM visual parity.
- The shared-I/O production slice b811f2cd0b25adbc4115376b0db2900b909739ba passed the installed Windows HTTP matrix: 63/63 SHA-verified transfers and 9/9 resource CSVs. The third 16-task round peaked at 515.672 MiB Working Set / 574.410 MiB Private Bytes. This is a measured sample, not a claim of improvement versus IDM or a controlled memory win.
- The prior NMT investigation observed Skia CleanableImpl counts rising substantially during active downloads (1,402→9,250 in one profile and later 10,538→20,710). One snapshot cannot prove a leak. The progress renderer, however, was creating Brush.horizontalGradient inside each row's per-frame draw lambda and reading the animated width in composition. This is a concrete allocation/recomposition candidate worth testing before any JVM heap tuning.

## Acceptance question written before the Windows build

Question: Can the main-list Liquid Progress renderer cache size-dependent gradient brushes at a stable full-track size and read animation values in the draw phase, while preserving the installed screenshot and exact file bytes?

Expected PASS:
- Source contract verifies one shared list clock, no row-level infinite transition, cached shimmer brush, stable track size, draw-phase reads, and rounded/filled clipping.
- Windows Compose compilation and isolated installation succeed.
- Installed screenshot shows an active Downloading row with moving liquid treatment while fixture bytes advance.
- The 16 MiB fixture completes with matching expected/actual SHA-256 and final status Finished.
- Installed HTTP matrix remains 63/63 SHA matches with 9/9 resource CSVs.
- JVM profile captures before/after class histogram, heap, NMT and process metrics so the Skia/Private Bytes hypothesis can be evaluated.

Source-only checks cannot prove Compose API compatibility, actual pixels, Windows drawing behavior, final file integrity, or the live JVM's allocation profile; a single purposeful Windows build/profile is justified.

## Production implementation

- Replace the width-animated Canvas node with a full-track Spacer using Modifier.drawWithCache.
- Keep the track's draw size stable; scale the cached status gradient to the determinate fill width and restore rounded caps after the moving band/bubbles are clipped to the fill.
- Keep animateFloatAsState as a State<Float> and read .value only inside onDrawBehind; read the shared liquid phase in the same draw phase.
- Build the shimmer Brush once per stable cache size, rather than constructing a gradient brush and shader on every frame for every visible active row.
- Add source regression assertions for the cache boundary and deferred state reads. No new dependency or copied implementation is introduced.

Implementation follows the official Compose guidance to cache size-dependent Brush/Shader allocations with drawWithCache: [Android Developers — graphics and rendering performance](https://developer.android.com/develop/ui/compose/graphics/draw/overview).

## Acceptance status

- **PASS on exact tested production SHA `869af00b39ba1977321fadb7ffbc8b022bd78148`:** source contracts, Windows Compose build/install, inspected active Liquid Progress screenshot, 16 MiB SHA-256 integrity, same-version reinstall/silent uninstall, 63/63 HTTP 1/4/16 transfers with 9/9 resource CSVs, JVM profile collection, FTP JUnit, HLS guarded redirect/segment SHA, queue lifecycle and installed FTP pause/REST-resume.
- **Measured but not causally proven:** third-round 16-task peak Working Set/Private Bytes fell versus the prior same-harness run; CPU peak increased, and stabilized Private Bytes remain elevated. Do not claim an overall performance win or a confirmed leak.
- **Still NOT TESTED / NOT COMPLETE:** current-head full browser-to-host E2E, cross-version upgrade/rollback and existing-data migration, external HTTPS/FTP and HLS restart, controlled IDM parity, final single deliverable `HyperGet.exe`, and independent final Windows recheck.
- Detailed evidence and artifact hashes are recorded below. This closes the renderer-cache acceptance slice only; it does not accept the full product.
- No release, main-branch write, merge, paid service/runner, or Torrent functionality.


## 2026-10-26 — exact-head acceptance results

Tested source SHA: `869af00b39ba1977321fadb7ffbc8b022bd78148`.

### PASS — source, compile, live UI, transfer integrity and installer lifecycle

- Source-only safety checks PASS: [run 40303361309](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40303361309).
- Installed Windows GUI smoke PASS: [run 40303361243](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40303361243). The job compiled Compose, built one NSIS installer, installed into an isolated directory and launched the real GUI.
- Visual artifact [windows-liquid-progress-visual](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40303361243/artifacts/12640789718), ZIP SHA-256 `3659673d0cb11ea66bd9458769242d2df6feb69b79629ab76406fe9bcefa8187`. Independently inspected the PNG and report. The report source SHA matches this exact tested head; status at capture was `Downloading`, one active task, 1,048,832 fixture bytes sent and advancing before capture. Final output is 16,777,216 bytes and byte-identical to the fixture; independently computed expected and actual SHA-256 both equal `341aacac661ccb210720bedaa9ead5d668fe5ea41a73532fc147c71e34040df1`; final status `Finished`, `Verified=true`.
- Lifecycle artifact [windows-installer-lifecycle](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40303361243/artifacts/12642191429), ZIP SHA-256 `ec62ed7eed273c48c43857248324c2d90938f5d8055427ee96913b275bd96598`. Internal installer SHA-256: `0477986D2D341CC52906380EEE1C8EED968C992EB2C50E6AB6E45EDDD8E0733F`. Exact source SHA is recorded. Same-version reinstall, silent uninstall and registry removal PASS; cross-version upgrade remains NOT TESTED.
- Real Windows FTP Kotlin/JUnit PASS: [run 40303361331](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40303361331), 29 tests. HLS guarded redirect/segment SHA tests PASS: [run 40303361477](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40303361477). Queue/part-I/O lifecycle regression PASS: [run 40303361199](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40303361199). Installed authenticated FTP pause/restart/REST/SHA PASS: [run 40303361314](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40303361314).

### PASS — full installed HTTP 1/4/16 matrix

- Run [40303361484](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40303361484) completed successfully on the same exact source SHA.
- Artifact [windows-active-http-1-4-16-evidence](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40303361484/artifacts/12641274367), ZIP SHA-256 `a37ca245d59a579e00c82e755d3ba371c6f49d240ec446e3a5b2e6d47cde9b82`.
- Independently inspected CSVs: 63/63 files verified, each 33,554,432 bytes with the expected SHA-256; all 9 resource CSVs present; measured peak distinct active tasks reached 1/4/16 and peak HTTP connections reached 128 at 16 tasks.
- Compared the third round against the prior 63-transfer baseline at `b811f2cd0b25adbc4115376b0db2900b909739ba` ([prior run 40297341776](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40297341776)). At 16 tasks, peak Working Set was 441.617 MiB vs 515.672 MiB (-14.4%); peak Private Bytes 503.734 MiB vs 574.410 MiB (-12.3%). At 4 tasks, peak Working Set was 324.203 MiB vs 371.352 MiB (-12.7%); peak Private Bytes 364.535 MiB vs 415.234 MiB (-12.2%). At 1 task, peaks were effectively flat. This is a favorable same-harness observation, **not a controlled A/B or proof that the shader-cache change alone caused the reduction**; peak CPU at 16 tasks was higher in the current third round (35.693% vs 29.779%), so CPU/allocation behavior remains worth profiling.

### JVM profile — evidence, no leak or memory-win claim

- Profile run [40303361524](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40303361524) PASS; artifact [hyperget-installed-jvm-memory-profile](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40303361524/artifacts/12641069695), ZIP SHA-256 `12d1948fee63b9286dbdc736ded65e73b8e60403fb2393e2c75038b70c460ab7`.
- 21/21 measured profile transfers had matching SHA-256. In this one round, peak Working Set / Private Bytes were 286.531/301.387 MiB at 1, 334.969/344.473 MiB at 4, and 464.488/462.082 MiB at 16.
- The sampled Skia `CleanableImpl` count was 1,402 before the active round, 13,188 during it, and 3,509 after the 30-second idle stabilization. Heap used fell from 78.8 MiB at the active snapshot to 34.9 MiB after idle, while Private Bytes stayed around 444 MiB. Baseline and active snapshots used different JVM PIDs, so this is diagnostic evidence, not a strict same-process leak test. A retained graphics/native-memory question remains open.

### Remaining gates

- Cross-version upgrade/rollback and migration of existing user data: NOT TESTED.
- Full Chrome/Firefox browser-to-host E2E on this exact renderer SHA: NOT TESTED; [run 40303361312](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40303361312) passed its scope gate but skipped the expensive browser job for this renderer-only change.
- HTTPS/external FTP and HLS restart acceptance: still needs exact-head evidence.
- Full IDM visual parity and controlled CPU/RAM comparison: NOT TESTED.
- Final product branding/icon and a single deliverable `HyperGet.exe`, plus independent final Windows recheck: NOT COMPLETE.

No main write, merge, release, paid service/runner, or Torrent. This checkpoint records test evidence only; it does not mark the whole product accepted.
