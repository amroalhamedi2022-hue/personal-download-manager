# HG-E077 — Replace rejected dynamic Range fixtures with static HTTPS test file (2026-10-28)

## Exact-head evidence

HG-E076 built a Windows installer, but all three dynamic test endpoints returned HTTP 400 before the independent reference was downloaded. The sanitized artifact [12801608851](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40656136841/artifacts/12801608851) has ZIP SHA-256 `9a6ad1f74998d4fa07b89ef62eb8ae507659ebccf07c1801d6525b5fe8008598` and records `EndpointAttempts=httpbin.org:400,httpbingo.org:400,httpbin.io:400`. No Range result or installed HyperGet download was reached; this remains a fixture/network failure, not a product acceptance result.

## Cohesive test-fixture adjustment

- Use the publicly documented static OVH test file `https://bom.proof.ovh.net/files/1Mb.dat` first, with `https://proof.ovh.net/files/1Mb.dat` as a mirror. OVH documents the file as exactly 1,048,576 bytes. The fixture is now 1 MiB; this does not change product limits or performance thresholds.
- Retain the dynamic HTTPBin endpoints as fallback candidates, each using the same 1 MiB expected size.
- For every candidate, require an exact full-reference size, normal HTTPS certificate validation, query-bearing Range request, HTTP 206, `Content-Range: bytes 0-1023/1048576`, exactly 1,024 bytes and matching prefix SHA-256 before selecting the host.
- Keep sanitized host/status evidence and remove partial files before trying the next candidate. No production downloader code changed.

Reference for the static test file and exact size: [OVH test-file listing](https://bom.proof.ovh.net/files/).

## Acceptance question before the next Windows build

Can the exact PR-head installed HyperGet download the 1 MiB static HTTPS file from an approved host and produce a stable file with the independently fetched SHA-256, after the same host passes the 206/Content-Range/prefix-hash probe?

Expected PASS: source contract and PowerShell parser pass before packaging; one test installer is built from the exact PR SHA; the selected static or fallback endpoint passes reference + Range checks; installed HyperGet output matches the reference SHA and remains stable.

**Status at checkpoint creation:** source patch staged on the existing PR branch; exact-head source and Windows reruns pending. Signed-token provider expiry remains **NOT TESTED**. No paid API/runner, merge, `main` write, auto-merge, release, Torrent, or browser-repository change.
