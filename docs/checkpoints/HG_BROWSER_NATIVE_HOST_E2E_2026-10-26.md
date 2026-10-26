# HG-BROWSER-NATIVE-HOST-E2E — exact-head Windows acceptance

Date: 2026-10-26  
PR: [#3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3)  
Branch: `automation/idm-ftp-cancel-and-category-flush`  
Base: `codex/repair-rf00-rf01`  
Tested source SHA: `0d803d2bcf449cd810f4bfbf4b8e67676159d1ed`

## Acceptance question

Can the exact installed Windows build register the native host for Chrome and Firefox, pass real extension-origin native messages, cold-wake the GUI from a stopped state, capture actual browser-origin downloads, and verify their final bytes by SHA-256? Source-only checks cannot establish these browser/registry/process/file behaviors.

## Result: PASS

- Workflow: [40313688358](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40313688358)
- Artifact: [hyperget-installed-native-host-evidence, ID 12645862959](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40313688358/artifacts/12645862959)
- Artifact ZIP SHA-256: `596e5ad99319380173730aea4d1bd6cdf1acd956b8136a10bc2a471d066f85ca`
- Internal NSIS test installer SHA-256: `ba29b47f587d773e65284dbf9c21f3530329bedd41b93cba875065619337d2ed`
- Pinned browser integration source: `0fb5a304cbde07a9758d2b42417e7a8a85cba0f2` (separate repository; unchanged).

### Verified evidence

- Installed host: Chrome + Firefox manifests point at the installed native host; two framed ping replies, correlated safe generic error, and clean EOF passed.
- Chrome MV3: correlated pings/safe error; connected port 3/3 replies, reconnect 1/1; GUI cold-wake; real 2 MiB browser-origin download SHA-256 verified; five fixture GETs.
- Firefox WebExtension: Firefox 158.0.1 + geckodriver v0.37.1; add-on install, native ping/safe error, GUI cold-wake, real 2 MiB browser-origin download SHA-256 passed; five fixture GETs.
- Shared fixture SHA-256: `1e075c8d478ad21844e33e830a695ef03a4d2488b69ee275bd8947618bb1be1`.
- Chrome-for-Testing archive SHA-256: `a4e0d57245e167e98a9d465af1e3774d2b4ec9f04e483d19bcd97a6f63cf46dd`.
- Firefox installer SHA-256: `3259a2f8508e58cf2e383b6c99bfc64d6ddc86d1407b7c2f86f660af955b5d10`.
- Geckodriver archive SHA-256: `dfed9315abe8d2fbc1b6161a2ee8002452e79cf05ee92fdc653a4e26bc35edd8`.

The Firefox report preserves a transient WebDriver `GET STATUS / UNAVAILABLE` diagnostic, but final stage is `COMPLETE`, `failureCode=null`, and `verified=true`; the actual browser download/SHA gates passed.

## Root cause fixed

Initial run [40312425370](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40312425370) passed installed host protocol and Chrome E2E but could not start Firefox because the unauthenticated GitHub releases API hit the hosted runner's shared-IP rate limit. The workflow now follows GitHub's official `/mozilla/geckodriver/releases/latest` redirect and validates HTTPS, final repository/tag path, and exact versioned win64 asset. Source contract rejects the old API endpoint. Source-only safety checks PASS: [40313688214](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40313688214).

## Scope limits

This is an internal test installer, not the final product deliverable. Cross-version upgrade/data migration, external HTTPS/FTP, HLS restart, controlled CPU/RAM comparison, full IDM visual parity, final HyperGet.exe branding/icon and independent final Windows verification remain open. HTTP 1/4/16, JVM memory, installed UI and installed FTP workflows were scope-skipped on this workflow-focused commit and are not claimed as new passes. No main write, merge, release, paid service, or Torrent.
