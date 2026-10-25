# HG-BROWSER-FIREFOX-DIAGNOSTICS-01 — identify real Firefox E2E failure safely (2026-10-25)

## Exact evidence before this change
- Main PR #3 head: `05d63ede0590f916db1671fc80ddc07cfdb7fe1d`.
- Windows Chrome + Firefox run [40233832473](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40233832473): **FAIL** in the real Firefox capture step (job `121291311525`, step 18). Windows NSIS install, installed native-host protocol checks, real Chrome MV3 download capture, source contracts, HTTP 1/4/16, FTP restart/resume, HLS targeted JUnit and queue lifecycle gates passed at this SHA.
- Firefox's only surfaced error was `WEBDRIVER_COMMAND_FAILED` at the generic response handler. The old harness suppressed the W3C error code, HTTP status and route class, and did not write its JSON report until success. The root cause is therefore **UNCONFIRMED**; no assumption is made that Firefox, the extension, or the native host is at fault.
- HTTP run [40233832377](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40233832377) passed 9 workload rounds and **63 distinct SHA-256-verified downloads**, with 9 process-resource CSVs. Current observed peak was about **507 MiB Working Set / 563 MiB Private Bytes**. Artifact [12614572483](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40233832377/artifacts/12614572483), archive SHA-256 `f2e904dc2b97851725f70a188f72eceaad4d8caa6c9e268a10233be6542ebeb6`.
- Installed FTP [40233832428](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40233832428) passed; artifact [12613938645](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40233832428/artifacts/12613938645).

## Functional engineering change
- Added safe WebDriver route classes and allowlisted W3C error normalization. The report records only HTTP status, method, route class and normalized error code; raw WebDriver messages, browser URLs, session IDs, download URLs, headers and payloads are not emitted.
- Added explicit stage markers from session creation through add-on install, extension-page navigation, native ping/error/add and SHA verification.
- The JSON evidence report is now written on both success and failure, so a failed run leaves actionable evidence. The exit error carries only a bounded stage/code token.
- Added three source regression tests for safe diagnostic classification, failure artifact creation and stage coverage. These source tests do not claim a browser pass.

## Purposeful build question recorded before CI
**Question:** On the next exact-source Windows run, which WebDriver stage and normalized W3C error actually cause Firefox capture to fail, and does the richer evidence preserve all privacy guards?
**Expected:** source contracts pass; one installed Windows NSIS build; if Firefox still fails, a JSON artifact and log identify a fixed stage, HTTP status, route class and allowlisted error code without raw browser data. If it passes, the existing strict acceptance remains: correlated ping, safe error response, real HTTP GET, 2 MiB output and exact SHA-256.
**Why source tests are insufficient:** only the real Windows Firefox/geckodriver/temporary-extension/browser-native-host combination can expose the failing WebDriver command; source inspection cannot validate session creation, add-on installation or actual file capture.

## Status at checkpoint creation
- Diagnostic fix: committed for exact-SHA CI; Windows result **PENDING**.
- Chrome installed download + SHA: **PASS**.
- HTTP 1/4/16 installed matrix: **PASS** (63/63, 9/9 resource samples).
- FTP installed pause/restart/REST/SHA: **PASS**.
- Firefox installed download: **FAIL on previous head; retest pending**.
- HTTPS remote endpoint, refresh URL, scheduler, cold browser-triggered GUI wake, Liquid Progress/visual match, safe HyperGet branding/upgrade, independent release recheck and final single `HyperGet.exe`: **NOT TESTED / INCOMPLETE**.
- No main write, merge, release, paid service or Torrent.
