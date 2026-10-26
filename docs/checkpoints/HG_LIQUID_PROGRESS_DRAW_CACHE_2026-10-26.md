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

- Production change and source regression are being committed together on the existing PR branch.
- Windows compile, installed visual gate, exact SHA-256, 1/4/16 matrix and JVM profile are PENDING until the exact-source runs complete and artifacts are inspected.
- No release, main-branch write, merge, paid service/runner, or Torrent functionality.
