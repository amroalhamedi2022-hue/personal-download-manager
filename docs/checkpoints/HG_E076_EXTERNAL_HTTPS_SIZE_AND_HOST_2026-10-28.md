# HG-E076 — Bound external HTTPS fixture size and add canonical httpbin fallback (2026-10-28)

## Evidence driving this adjustment

HG-E074's two exact-head Windows attempts failed at the independent reference fetch before HyperGet installation or launch. HG-E075 then tried two public HTTPS hosts, but both returned HTTP 400 for the 10 MiB fixture request. The evidence confirms the status code; the prior harness did not preserve response bodies, so the exact server-side rejection reason is **not proven**.

- HG-E074 attempt 1 artifact [12799511871](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40648777183/artifacts/12799511871).
- HG-E074 attempt 2 artifact [12800962533](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40648777183/artifacts/12800962533).
- HG-E075 artifact [12802001432](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40655328610/artifacts/12802001432), ZIP SHA-256 `085c894eebb136e6c0f0693629dbdda7fc005a91c8720ab99de5c6eddc923422`. Its sanitized report recorded `EndpointAttempts=httpbingo.org:400,httpbin.io:400` and `FailureType=400`; no reference hash, Range result, or installed-app download was produced.

## Cohesive fixture repair

- Reduce the external reference to **8 MiB (8,388,608 bytes)**, below common 10 MB response ceilings. This is a test-fixture adjustment, not a downloader size limit.
- Try the canonical `httpbin.org` range endpoint first, then `httpbingo.org`, then `httpbin.io`. All three hosts are explicitly allow-listed HTTPS endpoints.
- Select a host only if the full reference is exactly 8,388,608 bytes and a query-bearing Range request returns HTTP 206, `Content-Range: bytes 0-1023/8388608`, exactly 1,024 bytes, and a matching prefix SHA-256.
- Keep normal TLS validation; record only sanitized host/status-or-exception-type attempts; remove partial files before trying another host.
- Extend the source contract to lock the exact byte count, endpoint allow-list, fallback order and sanitized diagnostics. No production downloader code or performance thresholds changed.

The 10 MB ceiling is a plausible explanation for the prior 400 responses, not a confirmed server diagnosis. The next artifact will show each host's actual status/type so a repeated failure can be classified without guessing.

## Acceptance question before the next Windows build

Can the exact PR-head Windows installer use a normal trusted HTTPS connection to one approved public Range endpoint, verify the 8 MiB reference and query-bearing 206 prefix, then install HyperGet and download the same bytes with a stable exact-size/SHA-256 match?

Expected PASS: source contracts and PowerShell syntax pass before packaging; one test installer is built from the exact PR SHA; the selected endpoint passes full-reference + Range checks; installed HyperGet output matches the independent reference SHA and remains stable.

**Status at checkpoint creation:** source patch staged in the existing PR branch; exact-head source and Windows reruns pending. Provider-specific signed-token expiry remains **NOT TESTED**. No paid API/runner, merge, `main` write, auto-merge, release, Torrent, or browser-repository change.
