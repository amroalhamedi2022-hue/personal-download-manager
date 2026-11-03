# HG-E075 — External HTTPS endpoint failover and diagnosability (2026-10-28)

## Why this milestone exists

HG-E074's installed Windows gate built the exact-PR-head test installer successfully, but two runtime attempts failed before HyperGet was installed/launched. Both stopped while fetching the independent reference file from `https://httpbin.io/range/10485760` with PowerShell `HttpResponseException`. Neither attempt obtained a reference SHA, executed the Range assertion, or tested HyperGet's external download. These are **external-fixture failures**, not evidence of a HyperGet HTTPS regression.

- Attempt 1: [Windows run 40648777183](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40648777183), artifact [12799511871](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40648777183/artifacts/12799511871), ZIP SHA-256 `9972f72ce9f9e140c95d9ff2a2d4fef728b19d573458ef592e6abcf53797a297`; test installer SHA-256 `e93bdac79e0b45f45dae977a3d81d533a1a744a0cfed9e8e1271711410b6f54f`.
- Attempt 2 (rerun): same run, artifact [12800962533](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40648777183/artifacts/12800962533), ZIP SHA-256 `fde07b61b2519ea5ff7c8a3504360b05344a442a876458c52525f718d00f9e12`; test installer SHA-256 `bb8827ee57b6653249bd1997046610d1ce295aa2e8e2b720ea40a8cc5138e3aa`.

The prior evidence format recorded only the exception type and hid the HTTP status. Therefore it could not distinguish an endpoint's 4xx/5xx response from a client/network exception.

## Cohesive test-harness repair

- Try two allow-listed public HTTPS Range endpoints in order: `httpbingo.org`, then `httpbin.io`.
- For each candidate, require the full reference to be exactly 10,485,760 bytes and the query-bearing Range request to return 206, `Content-Range: bytes 0-1023/10485760`, exactly 1,024 bytes, and a prefix-matching SHA-256 before selecting that endpoint.
- If a candidate fails, remove its partial files and try the next. The installed HyperGet download uses the endpoint that passed both independent reference and Range checks.
- Record only `host:HTTP-status-or-exception-type` attempts; never write a full URL or query value to evidence. Continue normal TLS certificate validation; no trust bypass.
- Added source-contract regression for endpoint allow-list, fallback and sanitized diagnostics. No production downloader source, performance threshold, browser repository, base branch, or release artifact changed.

## Acceptance question before the next Windows build

Can one exact-head Windows run select an available public HTTPS endpoint, verify normal TLS, full reference size, query-bearing HTTP 206 Range and matching prefix, then install HyperGet and download the same 10 MiB resource with stable exact-size/SHA-256 output?

Expected PASS: source contract and PowerShell parser pass before packaging; exactly one test installer is built from the exact PR SHA; the chosen endpoint passes reference + Range checks; installed HyperGet output matches the independent reference hash and remains stable.

Source-only tests cannot establish live DNS/TLS, public endpoint response semantics, installed IPC or actual downloaded bytes, so a targeted Windows build is still necessary. If both endpoints fail, the artifact now records each host's sanitized status/type and leaves the gate OPEN rather than misclassifying the failure.

**Status at checkpoint creation:** implementation staged in the existing PR branch; exact-head source and Windows reruns pending. Provider-specific signed-token expiry remains **NOT TESTED** by this public Range gate. No paid API/runner, merge, `main` write, auto-merge, release, Torrent, or browser-repository change.
