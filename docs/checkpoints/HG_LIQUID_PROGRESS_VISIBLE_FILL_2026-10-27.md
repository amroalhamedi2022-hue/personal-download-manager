# HG-UI-LIQUID-PROGRESS-VISIBLE-FILL — meaningful screenshot acceptance
Date: 2026-10-27
PR: #3
Branch: automation/idm-ftp-cancel-and-category-flush
Base: codex/repair-rf00-rf01

## Prior exact-head evidence
- Windows installed GUI run [40423929424](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40423929424) passed and uploaded [artifact 12699058510](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40423929424/artifacts/12699058510).
- Independently inspected report: source SHA `f023a0e07d4680cd998fda434c4ca6a21275551f`, status `Downloading`, one active task, bytes advancing, final 16 MiB file and SHA-256 match `341aacac661ccb210720bedaa9ead5d668fe5ea41a73532fc147c71e34040df1`.
- Screenshot captured after only 1,048,832 / 16,777,216 bytes (~6.25%). Because the renderer draws bubbles only after the filled width exceeds 14 dp, that screenshot did not prove the bubble treatment was visibly rendered.

## Purposeful acceptance gate
Question: can a new exact-head Windows build/install capture Liquid Progress at a sufficiently visible fill while the download is still live, then verify the complete output?
Expected:
- wait for at least 25% of the 16 MiB fixture bytes before starting the screenshot observation window;
- retain the live-status, active-task, and bytes-advanced checks;
- recheck active state and Downloading status after the 900 ms observation window;
- record minimum threshold, bytes, and fraction in the JSON proof;
- finish with exact size and matching expected/actual SHA-256;
- preserve diagnostics if the visible-fill threshold is not reached.

The 25% gate is a test-quality requirement, not a change to the production progress algorithm. Source tests cannot prove the rendered bubbles are visible, Compose still compiles, the installed IPC is live, or the final file is intact; one targeted Windows UI build is justified.

## Pre-build state
The current UI screenshot proved transfer integrity but was insufficient for bubble visibility. The new harness/source contract is being prepared; visual acceptance remains **NOT TESTED** until its exact-head artifact is inspected. No release, merge, main write, paid runner/API, or Torrent.


## 2026-10-27 — exact-head visible-fill proof PASS

- Run [40425830147](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40425830147) succeeded on source SHA `77f8c7e637b0fc6430484249af148588cc01837f`.
- Artifact [12699952296](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40425830147/artifacts/12699952296), ZIP SHA-256 `110dd6ae8a51bf619b093707a4e3e39fe29784666ee73ba310ab2eb5c2babc3d`; report rehashed independently.
- Screenshot capture: `Downloading`, 1 active task, 5,570,816/16,777,216 bytes (33.2%), above the 4,194,304-byte threshold; bytes advanced before capture. Final file size and SHA-256 matched the 16 MiB fixture, and final status was `Finished`.
- Same-version reinstall and silent uninstall PASS; installer SHA-256 `b8b113af3e7e38b928fda49ca9762a84efcb2b16ffe2ab34bce5d3065dec3bf2`.
- The bubble path is now exercised, but screenshot review found the glossy band and bubbles too subtle at normal scale. This closes the live-transfer/visible-fill test gap, not final visual parity.
- Next gate: strengthen the visual contrast and rerun the exact-head Windows visual acceptance. **PASS for visible-fill evidence; overall UI parity remains OPEN.**
