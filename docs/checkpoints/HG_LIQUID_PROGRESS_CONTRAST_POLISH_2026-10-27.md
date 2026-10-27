# HG-UI-LIQUID-PROGRESS-CONTRAST — stronger but restrained liquid treatment
Date: 2026-10-27
PR: #3
Branch: automation/idm-ftp-cancel-and-category-flush
Base: codex/repair-rf00-rf01

## Prior visual proof (PASS)
- Exact source SHA `77f8c7e637b0fc6430484249af148588cc01837f`.
- Windows run [40425830147](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40425830147) succeeded; source tests [40425830410](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40425830410) passed 17/17.
- Visual artifact [12699952296](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40425830147/artifacts/12699952296), ZIP SHA-256 `110dd6ae8a51bf619b093707a4e3e39fe29784666ee73ba310ab2eb5c2babc3d`.
- Screenshot captured at 33.2% fill while status was `Downloading`, one task was active, and bytes advanced. The 16 MiB output finished with matching expected/actual SHA-256 `341aacac661ccb210720bedaa9ead5d668fe5ea41a73532fc147c71e34040df1`.
- Visual inspection: the glossy band and two bubbles are present but too faint at normal window scale.

## Purposeful pre-build gate
Question: does a 7dp track, stronger cached shimmer, more legible bubbles, and a narrow top-edge gloss improve visibility without changing the progress algorithm or creating per-row animation loops?
Expected:
- Source regression keeps one list-level clock, active-only animation, draw-phase reads and cached gradient allocation.
- Exact-head Windows Compose build/install succeeds.
- Screenshot captured at >=25% of the real 16 MiB transfer, with status `Downloading`, one active task, and advancing bytes.
- Final output size is 16,777,216 bytes and expected/actual SHA-256 match.
- Inspect the screenshot at native scale before accepting visual parity.

Source tests cannot prove actual pixel contrast, Compose rendering, IPC, or transfer integrity; one targeted Windows visual build is justified. No release build, main write, merge, paid runner/API, or Torrent.
