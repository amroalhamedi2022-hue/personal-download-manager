# HG-E078 — External HTTPS final-file stability acceptance (2026-10-28)

## Milestone
Close the external HTTPS + Range acceptance gate without treating a transient Windows file-read lock as a product transfer failure or weakening the byte/hash requirement.

## Pre-build declaration
**Question:** after a public HTTPS reference and query-bearing Range request pass normal TLS validation, HTTP 206, exact Content-Range, 1 KiB bytes and prefix SHA-256, can the installed HyperGet GUI/CLI download the same 1 MiB resource and keep the complete output readable with the expected SHA-256 across two samples separated by two seconds?

**Expected:** source guard and PowerShell parser pass before packaging; one test installer from the exact source SHA; full reference size/hash pass; Range status 206 with 1,024 bytes and matching prefix; installed output equals the reference SHA and remains stable in two samples within a bounded 30-second read retry.

**Why source tests are insufficient:** only an installed Windows run exercises external DNS/TLS, real HTTP Range semantics, GUI/CLI IPC, file publication and Windows sharing/read locks after a completed transfer.

## Failure evidence that motivated the change

- Previous exact source SHA: `32da786bb7bfe4388d89f1c7149bb225cb1accf5`.
- Run [40657023659](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40657023659) reached `bom.proof.ovh.net`, passed the 1 MiB reference and HTTP 206 Range/prefix checks, and recorded matching full output/reference SHA-256 `30e14955ebf1352266dc2ff8067e68104607e750abb9d3b36582b8af909fcb58`.
- It then failed on a single final file read with `IOException`, despite the earlier exact hash match. Evidence artifact [12802058569](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40657023659/artifacts/12802058569), ZIP SHA-256 `5ab8772b4cd751b88032e874d3c8541a686296f44ca0f22687b53b819ab974fa`.

## Cohesive fix

- Source SHA `8bfa11c0d6eac78efb73ed7c9b6939c254f98f9d` adds a bounded 30-second final-read retry, requires two consecutive exact size/SHA-256 samples two seconds apart, and records attempts, last read exception type, and stable sample count.
- A transient `IOException` is retried, never counted as success. Persistent unreadability, a wrong length, a wrong hash, or failure to produce two stable samples still fails the gate.
- Test-only source-contract follow-up: `968524a80e67bb6b879eb161a27ff070b916d779`. It does not change the tested PowerShell harness or production application.
- No production downloader logic, dependencies, UI, or browser integration changed in this milestone.

## Exact-head Windows result — PASS

- Run: [40658785902](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40658785902), exact tested source SHA `8bfa11c0d6eac78efb73ed7c9b6939c254f98f9d`.
- Test installer SHA-256: `e42ffd235c69f0667a4f3d2ba393f677225087bb2e562b3307f1bf056bd919b0`.
- Evidence artifact: [12803022839](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40658785902/artifacts/12803022839), ZIP SHA-256 `1670f0654de894a289e9983cdd70a1442d816933eeeddd7027a35ebdbc51b0ae`.
- Endpoint: `bom.proof.ovh.net`, HTTPS, default TLS validation.
- Range: HTTP 206; 1,024 bytes; range SHA-256 equals reference-prefix SHA-256 `5f70bf18a086007016e948b04aed3b82103a36bea41755b6cddfaf10ace3c6ef`.
- Full output: expected/actual size `1,048,576` bytes; expected/actual SHA-256 both `30e14955ebf1352266dc2ff8067e68104607e750abb9d3b36582b8af909fcb58`.
- Stability diagnostics: `FinalReadAttempts=4`; last transient read exception `IOException`; `StableReadSamples=2`; `Result=PASS`.
- Provider-specific signed-token expiry remains `NOT_TESTED`; this gate proves external HTTPS + Range transport, not a real provider's token-expiry policy.

## Remaining acceptance gates

- Independent final Windows recheck on the final source SHA.
- Complete the 1/4/16 active benchmark and JVM memory profile; keep measured results separate from source-only checks.
- Full aria2 comparison remains Stage 2 / PENDING. The pinned stock aria2 binary's enabled-feature list includes BitTorrent, so it is evaluation-only and must not be bundled while Torrent is prohibited. A no-Torrent build and GPL distribution review are mandatory before any adapter packaging.
- Final standalone user-facing `HyperGet.exe` delivery with verified SHA-256 and artifact link; the current installer is a test artifact, not the final standalone deliverable.
- No merge, main write, auto-merge, release, paid API/runner, or Torrent feature work.
