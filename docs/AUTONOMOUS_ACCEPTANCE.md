## HyperGet — 2026-10-24 HG-HLS-AUTH-01 evidence checkpoint (no release)

**Current functional slice:** Guarded same-origin authenticated redirects are wired into HLS manifest refresh, UI preview and media segment workers. They preserve explicit headers on same-origin hops and reject cross-origin/downgrade/port/userinfo/loop/over-limit hops. No Torrent, new paid dependency or user-data schema change.

**PASS — exact PR head a998156e3d74bc719960f772de931355407c9459:** [source-only CI 40115162196](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40115162196) completed successfully, including 88 Python source/FTP fixture tests across 13 test batches; these are structural/fixture checks, not Windows network evidence. No EXE/installer was produced.

**PASS — earlier exact PR head 68874b2cf56968726ad9119790f4d3bc614f3ad9:** [Windows Kotlin milestone 40114985141](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40114985141) built the relevant Kotlin desktop test target and validated 10/10 fake-transport redirect tests via JUnit XML; no executable distribution was produced. This does not cover the two new real loopback tests added after that SHA.

**IN PROGRESS / NOT TESTED at checkpoint:** [Windows loopback milestone 40115162273](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40115162273) was still running when checked. It must produce two JUnit XML files and >=12 tests with zero failures/skips before real localhost redirect behavior can be marked PASS. No conclusion is inferred from a running job.

**Observed earlier failure and repair:** [Windows 40114659012](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40114659012) failed during root Gradle configuration, not HLS tests, because a synthetic PR merge SHA yielded invalid SemVer `1.0.0-sha.03710`. Production build version now uses `sha.g<7-hex>`, and CI checks out the exact PR head. That repair passed subsequent Windows Kotlin compilation on 68874b2c.

**Remaining acceptance:** Actual full HLS transfer and resume/byte verification, HTTP/HTTPS/FTP Windows E2E, 1/4/16 concurrent sockets and SHA-256, CPU/RAM, Liquid Progress, icon/branding, installer/upgrades, browser extension and final HyperGet.exe plus independent recheck are still NOT TESTED at this head. PR #3 stays Draft, no main write/merge, no packaging or paid runner.

---

## HyperGet — 2026-10-24 HG-HLS-AUTH-01 real loopback HTTP acceptance extension

Added two Windows Kotlin tests using JDK HttpServer and production OkHttpHttpDownloaderClient (not a fake network): a same-origin 302 must carry the explicit Cookie to the final endpoint without leaking an ambient CookieJar cookie, and a cross-port redirect must be rejected before the second server sees any request. No internet, paid runner or additional dependency required. The targeted Windows workflow now requires both JUnit XML suites and at least 12 passing tests; results remain NOT TESTED until the exact new SHA completes. This is a justified network milestone compile/test, not EXE packaging. Real installed-GUI and public HTTPS E2E remain open.

---

## HyperGet — 2026-10-24 HLS Windows gate failure and SemVer prerequisite repair

**Observed FAIL (not a Kotlin test result):** Windows guarded-HLS workflow Run 40114659012 on the first head b1c1a8e could not configure the root Gradle build. The exact error at build.gradle.kts:33 was `Invalid version: 1.0.0-sha.03710`; the PR synthetic merge commit happened to start with a numeric identifier containing a leading zero, which violates SemVer. No JUnit XML or executable was produced, so the HLS Kotlin tests remain NOT TESTED on that run.

**Repair:** Prefix the commit-hash prerelease token with `g` and use seven characters, e.g. `1.0.0-sha.g03710ab`, preserving tagged release versions and existing persistence identifiers. Both PR source CI and targeted Windows JUnit workflow now check out the exact PR head instead of GitHub's synthetic merge ref; add a source regression for leading-zero SHA. This is a build configuration fix motivated by an observed failure, not a new EXE packaging request. A subsequent exact-head Windows test result is required before marking the HLS milestone PASS.

---

## HyperGet — 2026-10-24 HG-HLS-AUTH-01 protected HLS redirect milestone

**Functional acceptance:** A protected HLS manifest, preview and media segment may follow at most five HTTP 301/302/303/307/308 redirects on the same scheme/canonical host/effective port while retaining explicit Cookie/Authorization/Basic credentials. Reject cross-origin, HTTPS downgrade, port change, URL-embedded credentials, missing Location, loops and excess hops before issuing a second request. Close every intermediate response. Anonymous HLS keeps OkHttp's existing redirect handling. This completes a functional compatibility slice after earlier security-only guards.

**Production:** New HlsGuardedRedirects.kt implements one shared request policy used by HLSDownloadJob, HLSPartDownloader and HLSLinkChecker. No storage, extension protocol, user identity or new runtime dependencies changed. Reviewed existing Apache-2.0 OkHttp implementation and RFC 9110 redirect guidance; no borrowed code or new license obligations.

**Regression:** 10 Kotlin desktopTest scripted-transport cases test same-origin chains and secret retention, cross-host, scheme/port changes, URL userinfo, missing Location, cycle, hop limit, anonymous compatibility and protected segments. A Python source-contract suite checks all three production call sites, origin checks, socket closure and bounds; the previous manifest source contract was updated to reflect shared policy. **Status pending exact-SHA CI; no PASS is claimed before results.**

**Purposeful compile gate (specified before triggering):** Does real Kotlin compile and do the 10 redirect JUnit tests run on Windows with zero failures, errors and skips? Source tests cannot answer that. Expected result: targeted :downloader:core:desktopTest with XML >=10 tests; new PR-only Windows workflow compiles/tests this milestone only, no EXE/installer. If it fails, the milestone is blocked and the log must be investigated.

**Still NOT TESTED:** live local two-host HTTP/HTTPS with actual OkHttp; custom interceptors/authenticators; staged segment content identity; HTTP/FTP/HLS end-to-end; 1/4/16 concurrent sockets and SHA-256; CPU/RAM; Liquid Progress; full HyperGet UI/icon, upgrade and browser E2E; single HyperGet.exe and independent Windows recheck. No main writes, merge, paid API, Torrent or unrelated-project changes. PR #3 remains Draft.

---

## HyperGet — 2026-10-24: guarded HLS CookieJar isolation (SOURCE ONLY)

**Status: IN_PROGRESS; NO APP BUILD.** Reviewed the existing HLS manifest/segment origin policy and the shared OkHttp transport. The guarded `noRedirectClient` inherited the application's configured `CookieJar` from `singleAttemptClient`; that meant an explicit-header origin policy did not also control implicit ambient cookies. No evidence establishes that the current configured jar actually contains cookies, so this is a defense-in-depth isolation fix rather than a demonstrated exfiltration exploit.

**Production** [`c27627e5`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/c27627e56125a7b3caa587de9bf5ce2439767ccc): guarded HLS calls now derive their OkHttp client with `CookieJar.NO_COOKIES`, while keeping `followRedirects(false)` and `followSslRedirects(false)`. Explicit Cookie/Authorization headers supplied by the user still go through `newCall`; ordinary HTTP and anonymous HLS continue using the original `singleAttemptClient` configuration. No new dependencies, persistent schema, migration, IPC, extension or licensing changes.

**Source regression** [`d6da810b`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/d6da810b6d8ec4c0e5c54f00e6e488b1e95f3638): three additional Python source-contract cases check guarded CookieJar isolation, normal client preservation, and explicit Cookie compatibility. **PASS:** [source-only CI Run 40109441035](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40109441035) on exact production+test SHA `d6da810b6d8ec4c0e5c54f00e6e488b1e95f3638`; all 11 substantive source/FTP steps passed. Independent post-push inspection matched exact GitHub blobs and passed 7/7 structural assertions. The prior source-only Run 40109427336 also passed at the production-only SHA. **NOT RUN:** Kotlin/Gradle tests, app compilation, Windows network tests, installer, EXE.

**Residual risks:** Custom OkHttp application/network interceptors or authenticators could still add request headers; the source-only contracts do not simulate live cookie redirects or prove runtime isolation. Guarded HLS requests intentionally cannot rely on implicit CookieJar cookies; users must supply explicit Cookie headers for those flows. Same-origin authenticated redirects remain conservatively rejected. A local two-host redirect fixture and Windows runtime verification are required before security acceptance. Remaining functional/visual gates (HTTP/HTTPS/FTP/HLS E2E, 1/4/16, SHA-256, CPU/RAM, Liquid Progress, branding, icon, upgrade, browser extension, final `HyperGet.exe`, independent recheck) remain **NOT TESTED** on this head. PR #3 remains Draft; no main write, merge, packaging, release, paid API or Torrent.

---

## HyperGet — 2026-10-24: authenticated HLS manifest redirect isolation (SOURCE ONLY)

**Status: IN_PROGRESS; NO APP BUILD.** The previous HLS segment credential policy prevented cross-origin leakage from segment requests, but the *manifest itself* was still fetched with `client.connect(downloadItem, ...)` (and the UI preview with `client.connect(credentials, ...)`). OkHttp's default redirect behavior could follow a 302/307 to a foreign host while carrying user-supplied Cookie, Authorization, Referer or Basic credentials.

**Production:** [`3bbba4f1`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/3bbba4f1d54ead8860465c827a603498724b4e3c) introduces `credentialsForHlsPlaylist(IHLSCredentials)` using a request-local `HttpDownloadCredentials`: strips Host, Proxy-Authorization and hop-by-hop/Range headers; retains required headers/Basic auth on the initial URL; **disables automatic redirects whenever any allowed user header or complete Basic credentials are present**. Anonymous playlists still follow normal redirects. [`e38c909f`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/e38c909f73e1a216323871634c75f5906bc07034) applies the policy to resume/playlist refresh; [`ce2f2e93`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/ce2f2e93545b18e08b0579364e0d556e406b2aca) applies it to new/edit HLS UI link previews. No stored schema, native IPC, extension ID, download database, or migration changes.

**Verification:** Seven Kotlin desktopTest regression cases were added in [`3895f0e9`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/3895f0e991d029ffea2597d0826ad12347e513bb), **NOT RUN** because full Gradle/app compilation remains forbidden. Eight Python source-contract tests were added in [`488516f4`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/488516f41635cdf3d024c9280f21145c6f2f3f21) and wired to PR-only source CI in [`aa42645c`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/aa42645cdb0aedbd2117f58afdbdc3ac51e310b8). **PASS:** [GitHub source-only Run 40105237869](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40105237869) on exact source SHA `aa42645cdb0aedbd2117f58afdbdc3ac51e310b8`; all **11 substantive source/FTP steps passed**, including the eight new manifest source tests. Independently re-fetched exact GitHub blobs and checked 10/10 structural assertions. No Gradle, packaging, EXE, installer, release or paid API.

**Residual limits:** Authenticated manifest redirects (including safe same-origin redirects) are conservatively rejected rather than followed; an explicit origin-checked redirect handler would be needed to support them without exposing credentials. Source contracts cannot prove runtime redirect safety. Custom OkHttp cookie-jar/interceptor/authenticator behavior requires separate review and real local cross-origin server tests on Windows. HLS segment integrity under unchanged URLs, concurrent staged-file mutation, HTTP/HTTPS/FTP E2E, 1/4/16, SHA-256 full transfers, CPU/RAM, Liquid Progress, Windows icon/branding, old-version upgrade, browser integration, final `HyperGet.exe` and independent recheck remain **NOT TESTED** on this head. No main write, merge, Torrent or other-project changes. PR #3 remains Draft.

---

## HyperGet — 2026-10-24: HLS segment credentials and redirect guard (SOURCE ONLY)

**Status: IN_PROGRESS; NO APP BUILD.** Previously, HLS segment workers created `HttpDownloadCredentials(url)` and lost the playlist's user-supplied Cookie, Authorization, Referer, Basic credentials and User-Agent. A naive copy to every segment would leak private headers to arbitrary hosts listed in a playlist.

**Production source change:** `HlsSegmentCredentialPolicy.kt` now forwards user-supplied headers and Basic credentials **only when original playlist URL, final (possibly redirected) playlist URL, and segment URL share the exact scheme, canonical host and effective port**. It drops transport/hop-by-hop headers and does not forward `downloadPage`. User-Agent may be forwarded independently. Authenticated segment requests disable automatic OkHttp redirects so a 3xx cannot silently forward user-supplied headers to a different origin. Unauthenticated segment redirects retain existing behavior. The request-only redirect flag is `@Transient` and defaults to true for other HTTP downloads, preserving stored credential schemas. This deliberately prefers failing a redirected authenticated segment over leaking its secrets; supporting same-origin authenticated redirect chains safely remains future work.

**Verification:** Six Python source-contract cases added to source-only PR CI; seven Kotlin desktopTest cases added but **NOT RUN** due the user's prohibition on application compilation/Gradle. **PASS:** [source-only CI Run 40101015521](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40101015521) on exact code SHA `3f85b091277875db83616d3568307c70ad23bed2`; all ten substantive Python/FTP steps succeeded, including the six new HLS credential source-contract tests. An earlier attempt [Run 40100955530](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40100955530) failed because its Git tree accidentally omitted pre-existing PR source tests; a subsequent restoration commit restored the PR's original tree, and a comparison against pre-change SHA `16312166e408e0ed2b6c1b3b30e00c047a110350` confirms **only the nine intended files** differ. No prior PR source files remain deleted. The successful CI tested the restored code SHA. The Python tests are structural and do not establish runtime network security. No new dependency, license change, persistent schema migration, IPC change, extension protocol change or paid service. Reference: RFC 8216 §4.1 for playlist-relative URIs and OkHttp client redirect settings.

**Still NOT TESTED:** actual authenticated HLS server, cross-origin redirect on Windows, Kotlin JUnit, HTTP/FTP/HLS end-to-end, 1/4/16, SHA-256 full transfers, CPU/RAM, Liquid Progress, icon/branding, browser integration, migration, final HyperGet.exe and independent recheck. **Residual risk:** original *playlist* request still uses the existing OkHttp redirect policy and may forward custom headers across origins; that path needs its own reviewed guard. Default cookie-jar/interceptor behavior also needs end-to-end review. PR #3 remains Draft; no main write/merge/build/installer/Torrent.

---

## HyperGet — 2026-10-24: redirected HLS playlist segment-base fix (SOURCE ONLY)

**Status: IN_PROGRESS; no application build.** The HLS response parser already resolves the first segment against `responseInfo.requestUrl` (the final OkHttp response URL), but `HLSDownloadJob.getOrCreatePartDownloader` previously passed `downloadItem.link` (the pre-redirect playlist URL) to segment workers. For a playlist redirected to a different host or directory, relative `.ts` segment links could therefore resolve to the wrong resource.

**Production:** [`971aade7`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/971aade7b53b7a5f1733d715a3093d0250b827be) tracks the final manifest response URL as `segmentBaseUrl`, updates it after retiring old workers and before reconciling segments, and passes it to new `HLSPartDownloader` instances. The original link remains the initial fallback until a successful refresh. No persistence schema, IPC protocol, extension ID, credential, or user data migration changed.

**Regression:** [`6593e05c`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/6593e05c10f3fd4e5207f23dc857820fb6141b52) adds seven Python source-contract/URL-resolution cases covering the final response URL, update ordering, worker wiring, changed host/path, and absolute segment URLs. [`38b27808`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/38b27808653fdd8f100ca01cdea4dd5aad4e7297) wires them into the source-only CI. **PASS** 9/9 independent post-push source assertions. **PASS** [GitHub source-only Run 40096795128](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40096795128) on exact code SHA `38b27808653fdd8f100ca01cdea4dd5aad4e7297`: all nine substantive Python/FTP steps succeeded, including the new redirect test. This is source-level verification, **not** a live Kotlin HTTP redirect test.

**NOT TESTED:** Kotlin/Gradle JUnit, live HLS redirect with signed/authenticated segments, Windows HLS/FTP/HTTP E2E, 1/4/16 transfers, SHA-256 full downloads, CPU/RAM, Liquid Progress, browser integration, installer/upgrade, final `HyperGet.exe`, independent recheck. **Residual risk:** segment requests currently construct `HttpDownloadCredentials(url)` without propagating playlist credentials; authenticated HLS segments and cross-origin redirect credential isolation need a separate reviewed change. Same-URL changed-content detection and concurrent staged-file replacement remain open. No full compile, Gradle, package/EXE/installer Actions, paid API, release, main write, merge or Torrent. PR #3 remains Draft.

---

## HyperGet — 2026-10-24: serialize HLS preview edits against active playlist refresh (SOURCE ONLY)

**Status: IN_PROGRESS; NO APP BUILD.** On an existing HLS job, `extraConfigsReceived` previously applied a UI link-checker's `hlsManifest` directly to `parts`, even while segment workers and listeners were active. That could replace in-memory `MediaSegment` objects without retiring old workers, allowing stale status callbacks and staged segment reuse during active transfer.

**Production change:** [`259355bc`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/259355bc4e76140679a626a5b49626fb392944d4) introduces a shared `playlistMutationLock` around remote refresh and preview replacement. UI preview manifests are **ignored while the job or active scope is running**; resume still fetches the authoritative remote playlist. For an inactive job, the preview path now stops/awaits old workers, retires listeners and cached downloader objects, then replaces and saves segments. Explicit `Mutex.lock()/unlock()` in `try/finally` supports suspending stop/join and persistence calls without using `withLock`'s non-suspending action.

**Source verification:** New `test_hyperget_hls_preview_lifecycle_source.py` contains **6 source-contract tests** covering shared lock, remote refresh transaction, preview transaction, active-job guard, old-worker retirement ordering and save ordering. [Source-only CI Run 40092490941](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40092490941) **PASS** at exact code SHA `259355bc4e76140679a626a5b49626fb392944d4`: all **8 substantive Python/FTP source-only steps** succeeded, including the new preview test. Workflow remains PR-scoped, Python-only, without Gradle, app compilation, EXE, installer, artifact upload or paid API. No runtime dependencies or licensing changes; Apache-2.0 baseline unchanged.

**NOT TESTED:** Kotlin/Gradle JUnit, actual HLS simultaneous edit/resume race, HTTP/HTTPS/FTP Windows E2E, 1/4/16, SHA-256 full transfers, CPU/RAM, Liquid Progress, branding/icon migration, browser native messaging, final `HyperGet.exe`, independent recheck. Source contracts are structural and **do not prove all concurrency races are eliminated**; callbacks and playlist updates outside these two paths require later audit. PR #3 stays Draft; no main write, merge, build, installer, release or Torrent.

---

## HyperGet — 2026-10-24: persisted HLS playlist refresh + stale downloader retirement (SOURCE ONLY)

**Status: IN_PROGRESS; NO APP BUILD.** On restart the HLS job previously fetched a remote playlist only when the persisted part list was empty. Thus a fully persisted old playlist could be assembled after staged-file size validation **without checking whether the server's segment URI/duration changed**. Moreover, `clearPartDownloaderList()` iterated current `parts`, missing cached workers/listeners for segments removed by reconciliation.

**Production** [`653a18a5`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/653a18a5d3d9de5ceb0bf27ef1a45ebff31dba07): on each HLS resume, fetch and reconcile the current remote playlist before the all-complete shortcut; await old part workers, cancel all status listeners, and clear cached downloader instances before replacing segment objects. Existing sequence+URI+duration reconciliation retains progress only for matching identities, and existing staged-file checks still run before final assembly. **Remote playlist failure now prevents completion based only on stale persisted metadata**, intentionally preferring correctness to offline assembly.

**Regression** [`1342af31`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/1342af314086992632e2ccea1f6c3a519ff71858): six new Python source-contract tests check unconditional refresh, ordering before fast completion, stopping workers before cache retirement, full listener/cache cleanup, reconciliation wiring, and staged-file revalidation. **CI wiring** [`c0b5313d`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/c0b5313df137e95b215a8c3287d8c92c02067cb5): source-only Python workflow includes new test; no Gradle, EXE, installer, artifact upload or paid API.

**Verification:** **PASS** 8/8 independent remote-source assertions against exact GitHub blobs; **PASS** [GitHub source-only Run 40088136056](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40088136056) at `c0b5313df137e95b215a8c3287d8c92c02067cb5`: all seven substantive Python/FTP steps passed, including the new HLS lifecycle test. The acceptance-document-only commit below is not itself a new CI-tested code SHA. **NOT TESTED:** full Kotlin/Gradle JUnit, live HLS restart, Windows app, browser E2E, 1/4/16, SHA-256 end-to-end, CPU/RAM, GUI, migration, final EXE and independent recheck.

**Residual risks:** Source contracts are structural, not proof of full HLS runtime correctness. Playlist identity by URI/duration cannot detect changed bytes served at an unchanged URL; robust validators/hash and real HTTP fixture tests remain required. Playlist refresh and worker cleanup are not yet proven under every concurrent `extraConfigsReceived` path; concurrent file replacement between stage check and assembly also remains open. PR #3 stays Draft; no main write, auto-merge, release, build or Torrent.

---

## HyperGet — 2026-10-24: persisted HLS completed-part safety (NO APP BUILD)

**Status: IN_PROGRESS.** On resume, the old HLS fast path finalized a job whenever every persisted part carried `isCompleted=true`, **before** checking whether the corresponding staged files still existed or had the expected length. A deleted, empty or truncated segment could therefore be assembled or cause a late failure.

**Source repair:** `HLSDownloadJob.resumeWithNewScope` now invokes `fetchDownloadInfoAndValidate` before the all-complete fast path. Invalid completed segments lose their completion flag, current-byte counter and completed status-flow state so they can be redownloaded. `SegmentedDownloadDestination.isDownloadPartValid` reads NOFOLLOW_LINKS file attributes, rejects non-regular/zero-byte files and compares actual length with a positive known HLS segment length. Unknown server lengths remain accepted only for nonempty regular files.

**Verification:** The isolated pure Kotlin validation helper and eight standalone regression cases passed locally with `kotlinc`/`kotlin` (8/8); this did not compile or package the application. Six Python source-contract checks were added and **PASS 6/6** in source-only [Run 40084292413](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40084292413) at source SHA `50a81a93bc8a32eaf0a27e38a1e7e34068eeab71`. All CI steps passed (FTP stage, HLS reconciliation, persisted completion, bounded playlist body, MIME and local FTP fixture). This CI ran Python only, with no application build or installer. No Windows/HLS runtime, JUnit, packaging or EXE test was performed.

**Known limits:** This prevents premature completion for missing/truncated staged segments but does **not** establish that a saved segment's bytes match the latest remote playlist. The existing HLS saved-playlist refresh and stale downloader-instance references remain open. Attribute inspection is not an atomic open/assembly guarantee against concurrent file replacement. Real HLS resume and SHA-256 acceptance remain NOT TESTED. No paid API, new runtime dependency, main change, auto-merge, installer, or Torrent.

## HyperGet — 2026-10-24: HLS Content-Type parsing compatibility and source-only regression

**Status: IN_PROGRESS; NO APP BUILD.** Existing draft [PR #3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3) on `automation/idm-ftp-cancel-and-category-flush`, targeting `codex/repair-rf00-rf01`. The previous `HLSResponseInfo.expectSuccess` compared `supportedMime.startsWith(rawContentType)`, which rejected a valid HLS `Content-Type: application/vnd.apple.mpegurl; charset=utf-8` when the length was unknown, yet treated a partial MIME prefix as valid. Both cases matter for chunked/unknown-length playlists.

**Production change:** New dependency-free `HlsContentType.kt` compares the trimmed, lowercased MIME token before any semicolon with the two supported HLS MIME types. `HLSResponseInfo.kt` calls the helper, preserving the existing bounded 2 MiB playlist body reader and unconditional declared-length guard. The pre-existing small-body fallback for unexpected MIME types remains unchanged: **this is MIME compatibility/validation, not an authentication or content-security boundary**. The HLS parser still validates the playlist itself.

| Gate | Result | Evidence |
| --- | --- | --- |
| Isolated Kotlin/JVM source regression | **PASS 12/12** | Compiled **only** `HlsContentType.kt` + `HyperGetHlsContentTypeSmoke.kt` with local `kotlinc` and ran the standalone `kotlin` main. Covered both supported types, charset, mixed case, whitespace, false prefixes/suffixes, missing/empty/unrelated/multiple values. **No application compile or packaging.** |
| Tested-source GitHub blob identity | **PASS 3/3** | `git hash-object` for the locally tested helper `1be7be858690c9bf6ca21b66fe5a436ccf5be13b`, smoke `045564bb9fb5dfcff86ba63de7b9993e16a0c0e1`, and Python source contract `5cfd28b72518a8907dd51d6e892cf996f39eb6b8` matched the uploaded GitHub blobs. |
| Exact remote source static review | **PASS 11/11** | Verified production helper wiring, exact MIME equality, parameter normalization, removal of legacy prefix check, preservation of declared/actual 2 MiB limits, source test and Python-only CI wiring. |
| Source-only GitHub CI at `d22383fd6aceedb11143717bbe7d1d68a926fedd` | **PASS** | [Run 40080682643](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40080682643): FTP stage contract, HLS reconciliation, HLS bounded-body, **new HLS MIME** contract and local FTP fixture all succeeded. Python-only, no Gradle, no EXE or installer. |
| Earlier CI regression and correction | **FIXED** | [Run 40080642252](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40080642252) failed because the pre-existing bounded-body *test* searched for the deleted local variable `val hlsPossibleHeaders`. The test was corrected to anchor on the actual `Content-Type` lookup without weakening the declared-length check; subsequent run passed. |
| Real HLS HTTP server / Windows, persisted-playlist refresh, stale downloader references, 1/4/16, SHA-256, CPU/RAM, Liquid Progress, HyperGet.exe, migration, browser integration and independent recheck | **NOT TESTED / DEFERRED** | User's mandatory NO-BUILD rule remains active until complete source/UI/security readiness. |

**Scope and risk:** No new dependency, license, credentials, data migration, IPC, packaging, release, main write, merge, paid API or Torrent. Existing Apache-2.0/JDK/Kotlin primitives only. Source-only validation cannot prove runtime HLS/Windows behavior. Incomplete persisted HLS playlist revalidation and cached downloader lifecycle remain open and must be reviewed before the final build. Keep PR Draft and the development task active until verified delivery.

---

## HyperGet — 2026-10-24: bounded HLS playlist response (SOURCE ONLY; NO APP BUILD)

**Status: IN_PROGRESS.** PR #3 remains Draft on `automation/idm-ftp-cancel-and-category-flush` against `codex/repair-rf00-rf01`. Security review found `HLSResponseInfo.fromConnection` called unbounded `readUtf8()` on the response body; its 2 MiB content-length check applied only when the Content-Type was unexpected. A server with a correct HLS Content-Type, a missing Content-Length (chunked), or a misleading declared length could exhaust memory before parsing.

**Production fix:** `HlsPlaylistBodyReader.kt` reads a maximum of 2 MiB of actual response bytes, probing one additional byte and rejecting oversize with `IOException`, regardless of Content-Type or declared size. `HLSResponseInfo` now rejects oversized declared lengths unconditionally and uses the bounded reader for all playlist responses. No change to stored download records, credentials, browser IPC, app identity, licenses, dependencies, or Torrent scope.

| Gate | Status | Evidence |
| --- | --- | --- |
| Isolated Kotlin/JVM reader regression | **PASS 10/10** | Compiled **only** `HlsPlaylistBodyReader.kt` and `HyperGetHlsBodyReaderSmoke.kt` with `kotlinc -J-Xmx384m` and ran the standalone main; exact-size acceptance, 1-byte oversize, actual 2 MiB boundary and +1 byte, >8KiB stream, UTF-8, empty body, invalid limit, and zero-byte read. **No full app compile or packaging.** |
| Exact GitHub blob identity | **PASS** | `git hash-object` of both locally compiled source files matched remote blobs: helper `cf1f12ed03594888f97f4d5ad4b3e0b9db532ca2`, smoke `def73a40654681ff1ec6c6b769bacac002c8d907`. |
| Source-only static contracts | **PASS 9/9 local checks** | Confirmed production wiring, declared/actual caps, oversize rejection, smoke coverage and Python-only CI configuration against fetched GitHub blobs. New Python unittest `test_hyperget_hls_body_source.py` is wired to `.github/workflows/hyperget-source-only.yml`. |
| Source-only GitHub CI at source+acceptance SHA `c295c5112e0f5ba23c3f1dcc9bbaa0bc5145dbbd` | **PASS** | [Run 40076471566](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40076471566) completed successfully: FTP source, HLS reconciliation, new bounded HLS response source, and local FTP fixture. The subsequent acceptance-document commit changes no source. CI invokes Python only, no Gradle or installers. |
| Full HLS integration/Windows memory, HTTP/HTTPS/FTP, 1/4/16, SHA-256, CPU/RAM, Liquid Progress, HyperGet.exe/icon, migration, browser, independent recheck | **NOT TESTED** | Binding NO-BUILD gate. |

**Residual risk:** The source reader has been tested in isolation, not compiled with Okio/HLS parser or exercised on Windows. The response-size cap does not prove full memory bounds for segment downloads or the entire application. HLS persisted-playlist refresh, stale part downloader references, and installed Windows acceptance remain open. Do not declare delivery or disable the developer automation.

---

## HyperGet — 2026-10-24: HLS playlist reconciliation safety (SOURCE ONLY; NO APP BUILD)

**Status: IN_PROGRESS.** Draft PR #3, branch `automation/idm-ftp-cancel-and-category-flush` targeting `codex/repair-rf00-rf01`; no main write or merge. The prior `HLSDownloadJob.updateParts` constructed `newPartsToSave` but never called `setParts` on it. It also reused a completed segment by sequence/duration alone even when the segment URI changed. This could leave refreshed playlists unapplied and mark different bytes as already downloaded.

**Production fix:** `HlsPlaylistReconciliation.kt` provides dependency-free reconciliation by sequence + **exact URI** + duration, preserving completion/length/progress only for matching identity. `HLSDownloadJob.updateParts` now applies the entire reconciled playlist in incoming order, including removal of obsolete segments. Changed URI/duration resets stale completion and byte metadata. No new dependencies, credentials, IPC, registry, identity migration or Torrent code.

| Gate | Status | Evidence |
| --- | --- | --- |
| Isolated Kotlin/JVM source regression | **PASS 11/11** | JDK/Kotlin compiler: compiled **only** `HlsPlaylistReconciliation.kt` and `HyperGetHlsReconciliationSmoke.kt`; checked same identity, signed URI change, duration change, sequence change, incoming order, empty/new playlists, completion/length/progress. No full app compile or installer. |
| Exact GitHub blob identity of tested source | **PASS** | Helper `4ad1ac4968a7ee85df5b3b9a3c286601a3e30445`; smoke `0fe383ef8320194ecee0e9f760644e7f20cf6a61` matched local `git hash-object` of compiled files. |
| HLS source-only Python contract in CI | **PASS** | [Run 40072872187](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40072872187) on source/acceptance SHA `d39034f4`: HLS, FTP source and local FTP fixture steps completed successfully; workflow invokes Python stdlib only, no Gradle or executable artifacts. |
| Desktop JUnit `HlsPlaylistReconciliationTest` | **NOT TESTED** | Three methods committed for eventual full application validation, explicitly deferred by NO-BUILD rule. |
| Real HLS refresh/restart, HTTPS, FTP Windows Pause/Resume, 1/4/16, SHA-256, CPU/RAM, UI, HyperGet.exe/icon, upgrade, browser integration and independent recheck | **NOT TESTED** | Binding NO-BUILD rule remains active. |

**Residual risk:** This repairs `updateParts` when called; it does **not** change the existing `fetchDownloadInfoAndValidate` behavior that fetches a playlist only if no segments are persisted. A separate policy and test are needed for revalidating persisted playlists, particularly sliding live HLS windows and signed URL refresh. `HLSDownloadJob` itself was not compiled, and Windows runtime behavior is unverified. Existing 13/16 active transfer failure is not resolved by this change. Continue source-only feature/security/UI review; first Windows build only after complete readiness.

**Commits:** `6d25cc56` (helper), `167b095f` (integration), `8a97247a` (smoke), `007592cd` (source contract), `374fabe8` / `7152bec9` (source-only CI), `a3065b86` (deferred JUnit). No paid API, EXE/installer, workflow packaging dispatch, auto-merge or main write.

---

## HyperGet — 2026-10-24: bounded, symlink-safe FTP resume metadata (SOURCE + TEST; NO APP BUILD)

**Status: IN_PROGRESS.** Draft [PR #3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3), head `automation/idm-ftp-cancel-and-category-flush`, base `codex/repair-rf00-rf01`. This round changed production code and tests, without touching `main` or merging.

**Finding:** The old `loadIdentity` used `Files.isRegularFile(path)` and `Files.newInputStream(path)`, which follow symbolic links. A local `.ftp-part.properties` link could therefore make FTP resume parse an unrelated file. The parser also had no input-size bound. **Fix:** new `loadFtpResumeIdentity` checks a regular file without following links, opens with `NOFOLLOW_LINKS`, limits reads to 16 KiB (+1 byte for rejection), checks file-key continuity when supported, and converts malformed Java-properties Unicode escapes into a fixed `IOException`. A truly missing sidecar still returns null; existing unsafe sidecars fail closed without deleting staged download data. `FtpTransferClient` uses the guarded reader before REST resume. No schema, credentials, IPC, registry, remote FTP protocol, dependency or paid service changes.

| Gate | Status | Evidence |
| --- | --- | --- |
| Isolated Kotlin/JVM sidecar regression | **PASS 11/11** | JDK 21 / kotlinc; compiled only `FtpResumeIdentity.kt` and `HyperGetFtpResumeIdentitySmoke.kt`. Missing/valid sidecar, symlink and dangling link, directory, oversized and malformed metadata; unrelated file preserved. **Not full-app compilation.** |
| Exact source/test blob identity | **PASS** | Local `git hash-object` matches GitHub: helper `7f754047f3fc33c394d8490113f65ac936682b9e`; smoke `5848b2464c6f2e875b5ff75b8525e83a8d5751df`. |
| GitHub source-only CI at source/test SHA `2c14dc13bc2518b074f2b8ae226724894ad94c64` | **PASS** | [Run 40068779167](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40068779167): 8/8 Python source-contract checks, 3/3 local FTP fixture checks. Workflow contains no Gradle, packaging or executable artifact upload. |
| New repository JUnit test `FtpResumeIdentityTest` | **NOT TESTED** | Five JUnit methods added but not run under the no-full-build rule. |
| Windows FTP real Pause/Resume, HTTP/HTTPS/HLS, 1/4/16, SHA-256, CPU/RAM, Liquid Progress, HyperGet.exe/icon, upgrade, browser integration and independent recheck | **NOT TESTED / DEFERRED** | User's binding NO-BUILD restriction; no Windows executable, installer or release produced. |

**Residual risk:** A hostile process with write access to the same directory can still mutate a regular sidecar in place, including during reading; `fileKey` may be null on some filesystems. This guard is not a cross-process locking scheme. Windows provider behavior and FTP resume integration require final exact-SHA tests. The earlier 13/16 active transfer and FTP installed regressions remain unproven fixed.

**Commits:** helper `dae9b070`; transport wiring `29ff4324`; isolated smoke `3ceccd6d`; deferred JUnit `29c7691a`; source CI checks `2c14dc13`. No full Gradle/Compose compilation, EXE/NSIS/MSI, packaging workflow dispatch, auto-merge, Torrent or paid API. Next: source-only HTTP/HLS/FTP/UI/identity review; final build only after complete readiness, followed by Windows acceptance and independent recheck.

---

## HyperGet — 2026-10-24: FTP staged-output symlink and resume-offset guard (SOURCE + REGRESSION; NO BUILD)

**Status: IN_PROGRESS.** Existing draft [PR #3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3) on `automation/idm-ftp-cancel-and-category-flush`, targeting `codex/repair-rf00-rf01`; no main write or merge. Production commits: [`bae58037`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/bae580374194ee35948abaf8f2282a4ba2fbbd68) (new guarded staged-file helper), [`16cfdbd5`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/16cfdbd594426bc44de3f422f6a91f5f10396b68) (FTP transport integration); source regression [`f63f379b`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/f63f379b90783263cb0f8d7a9105cf3f240ab474); desktop integration regression [`ad668625`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/ad6686257759282ea88c54600aaf39cfd847875e).

**Reproduced defect:** The previous `Files.newOutputStream(part, CREATE, WRITE, APPEND)` follows symbolic links. On JDK 21/Linux, a `.ftp-part` symlink to `user-data.txt` appended `-FTP` to the unrelated target, reproducing `USER-DATA-FTP`. A swapped/changed staged file could also be opened after its previously measured REST offset, causing unsafe append. The new `ftpStagedBytes` rejects symbolic links, directories and other non-regular staged paths using `NOFOLLOW_LINKS`; `openFtpStagedOutput` opens with `NOFOLLOW_LINKS` and verifies channel size equals the expected REST offset before writing. On mismatch, it closes the channel and preserves the staged bytes. No extra dependency or paid API; existing Apache-2.0 project/JDK APIs reused.

| Gate | Status | Evidence |
| --- | --- | --- |
| Pre-fix symlink append reproduction | **FAIL reproduced (expected)** | JShell/JDK 21 on Linux, real temporary files; legacy output stream appended to the unrelated symlink target. |
| Isolated Kotlin/JVM source regression | **PASS 13/13** | `kotlinc -J-Xmx384m FtpStagedOutput.kt HyperGetFtpStagedOutputSmoke.kt -d classes && kotlin -classpath classes ir.amirab.downloader.ftp.HyperGetFtpStagedOutputSmokeKt` on JDK 21/Linux; covers new/continued append, offset mismatch, valid/dangling symlinks, directory staging and untouched user file. **Only two isolated source files compiled; no Gradle or full application compile.** |
| Committed-source identity | **PASS** | Production helper Git blob `803e25d7e9a7abd42c050515575cb6ee7e1974be` and smoke Git blob `cb5e3c006e31465c11164375238b0c541b07cf9f` match local `git hash-object` of files actually compiled and run. |
| Cross-file static source checks | **PASS 16/16** | Re-fetched helper, production transport, source smoke and desktop test from GitHub. Verified guarded size/open integration, no old unsafe writer, REST/MDTM identity, cancellation, final no-replace publish, and regression coverage. Static checks do not establish runtime FTP correctness. |
| New desktop FTP integration test | **NOT TESTED** | `FtpTransferClientTest.nonFileFtpStagingPathCannotBeUsedAsOutput` exercises rejection of a directory at the staged path against a local Apache FTP fixture; deferred with all desktop tests until permitted final build. |
| CI for `ad668625` | **0 PR workflow runs, 0 combined statuses at query time** | Read-only GitHub CI inspection. Packaging workflows remain push-scoped to other branches, tags or manual dispatch; no Actions triggered deliberately. This is **not** a global CI disablement. |
| Windows FTP Pause/Resume and symlink behavior, HTTP/HTTPS/HLS, 1/4/16, SHA-256, CPU/RAM, Liquid Progress, HyperGet.exe/icon, upgrade, browser extension, independent recheck | **NOT TESTED / DEFERRED** | Binding user NO-BUILD gate; no new EXE, installer, release or paid service. |

**Residual risk:** `NOFOLLOW_LINKS` guards the open against symbolic links but does not provide a global cross-process lock against a same-size regular-file replacement or hard-link manipulation. Windows file-system/provider behavior and actual FTP/REST semantics require later exact-SHA tests. Do not claim this is a complete adversarial filesystem sandbox.

**Next:** Continue source-only review of HTTP/HLS/FTP, UI/brand/icon and migration readiness, with isolated regressions and independent security/license review. After all feature/source/UI gates are ready, allow the first final Windows build and full installed acceptance/recheck. Do not disable the recurring task until a verified `HyperGet.exe` is delivered and accepted.

---

## HyperGet — 2026-10-24: FTP final publish must not clobber a concurrent destination (SOURCE ONLY; NO BUILD)

**Status: IN_PROGRESS.** Existing draft PR #3, branch `automation/idm-ftp-cancel-and-category-flush`, base `codex/repair-rf00-rf01`. Source change: the FTP final publish now calls `publishFtpStagedFile` instead of `Files.move(part, destination, ATOMIC_MOVE)`. On Linux, the old ATOMIC_MOVE call was **reproduced overwriting an existing destination** without REPLACE_EXISTING (JShell/JDK 21, actual temp files); the initial `Files.exists` check is not sufficient when another process creates the target during RETR.

**Fix:** Try an exclusive hard-link publish in the same directory (atomic destination creation on supporting file systems), then remove the staged name. If hard links are unavailable (e.g., FAT/exFAT or some shares), fall back to `Files.move` without ATOMIC_MOVE or REPLACE_EXISTING. The fallback is not a universal cross-process atomicity guarantee; do not overstate it. The sidecar's atomic REPLACE_EXISTING update remains unchanged. No credentials, IPC, Registry, user-data schema or download identity changes. Existing Apache-2.0 source and Java NIO reused; no new dependencies or paid service.

| Gate | Status | Evidence |
| --- | --- | --- |
| Before-fix overwrite reproduction | **FAIL reproduced (expected)** | JDK 21 JShell, existing target `user-data` was overwritten by `Files.move(part, target, ATOMIC_MOVE)`, result `downloaded`. |
| Isolated Kotlin/JVM source test | **PASS 9/9** | `kotlinc -J-Xmx384m FtpStagedFileCommit.kt HyperGetFtpStagedFileCommitSmoke.kt -d classes && kotlin -classpath classes ir.amirab.downloader.ftp.HyperGetFtpStagedFileCommitSmokeKt`. Tests complete publish, stage cleanup, existing-target preservation, missing-part rejection and symlink-target protection on Linux. **Not full app compilation.** |
| Integrated FTP call site | **SOURCE REVIEW ONLY** | `FtpTransferClient.download` now delegates final publish to the helper after completion/size/cancellation checks; no changes to REST or speed limiter. |
| GitHub CI at new SHA | **NOT TESTED until queried** | Existing workflows were audited: packaging workflows are push-scoped to `main` / `codex/repair-rf00-rf01`, tag or manual dispatch; no workflow requested. Do not equate this with disabling all automatic Actions globally. |
| Windows FTP live, cross-volume/network share fallback, concurrent 1/4/16, HTTP/HTTPS/HLS, CPU/RAM, SHA-256, Liquid Progress, HyperGet.exe, upgrade, browser integration, independent recheck | **NOT TESTED / DEFERRED** | User's NO-BUILD gate remains binding until source/UI/security readiness. |

**Follow-up integration regression (added, NOT EXECUTED under NO-BUILD):** `FtpTransferClientTest.destinationCreatedWhileRetrIsRunningCannotBeOverwrittenAtCommit` uses the existing local Apache FTP fixture. It creates a conflicting destination from `onProgress` *after* RETR begins, expects `FileAlreadyExistsException`, verifies user bytes remain unchanged and the fully received staged bytes survive. This is a future desktopTest gate, **NOT TESTED** until the final build stage.

**Next:** test Windows hard-link/fallback behavior and actual race after final readiness; continue HTTP/HLS and identity/UI source work. Do not merge, write main, run packaging or disable recurring development.

---

## HyperGet — 2026-10-24: FTP RETR Pause/stream-registration cleanup race (SOURCE + TEST; NO APP BUILD)

**Status: IN_PROGRESS. Production source changed: YES.** Existing draft [PR #3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3), head `automation/idm-ftp-cancel-and-category-flush` → base `codex/repair-rf00-rf01`. Production commits: [`05b88c4d`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/05b88c4df4924e3b9411d022c99da9f268c67026) (small stream-lifecycle helper) and [`ee598b4e`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/ee598b4e9596f4ca002259a06c9e3d172e82ac56) (FTP transport integration); regression commit [`f8f3a808`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/f8f3a8081fd3b39faa192104f201e56eae96570d).

**Defect:** After a successful `retrieveFileStream`, `FtpTransferClient` registered the new data stream and called `ensureNotCancelled()` *before* entering its stream-closing `try/finally`. A concurrent Pause in that narrow interval could throw without closing the just-opened RETR data socket, despite closing the control connection. The new `withFtpDataStream` helper checks cancellation **inside** the guarded region and always unregisters/closes the stream, including when cancellation, body execution or unregistering fails. FTP REST identity checks, staged-file semantics and credential storage are unchanged.

| Gate | Status | Exact evidence |
| --- | --- | --- |
| Isolated real Kotlin/JVM source test | **PASS 4/4** | `kotlinc FtpDataStreamLifecycle.kt HyperGetFtpDataStreamLifecycleSmoke.kt -d classes && kotlin -classpath classes HyperGetFtpDataStreamLifecycleSmokeKt` (in a temporary source-only folder). Cases: cancellation before body prevents work and closes stream, normal completion, body failure, unregister failure still closes. **Not a full-app compile or live FTP test.** |
| Exact committed helper/test source identity | **PASS** | GitHub blob SHA `55dcb4b0122b3f8393c6df951a46740a7a86c7f2` and test blob `b4dc98d89190700539f9cbc8779ac87d24fc86ab` match local `git hash-object` on tested source files. Integrated transport blob `f7967951d0b7a340c8d6bdecc401567205a82970` was re-fetched for static review. |
| Source-only integration invariants | **PASS 10/10** | Re-fetched transport/helper/test; checked RETR registration before guarded check, unconditional close/unregister in `finally`, pre-body cancellation regression, preservation of completion check before final commit, and REST identity guard. Static checks are **not** runtime FTP or Windows evidence. |
| CI at regression SHA `f8f3a808` | **0 runs, 0 statuses** | Queried commit-associated PR workflow runs and combined status. Existing packaging workflows are push-scoped to `codex/repair-rf00-rf01` or `main`, or manual/tag-triggered; no Actions dispatched and no packaging attempted. |
| FTP live Pause/REST, HTTP/HTTPS/HLS, Windows build/upgrade, browser integration, 1/4/16 SHA-256 and CPU/RAM, Liquid Progress, HyperGet.exe, icon, independent recheck | **NOT TESTED / DEFERRED** | Binding NO-BUILD rule remains active. No new executable/installer or Windows acceptance evidence. Historical FTP and 13/16 active-benchmark failures remain unresolved for this SHA. |

**Open-source/license/security:** Continued the repository's existing Apache Commons Net FTP transport (Apache-2.0) and Kotlin/JDK primitives; no new dependency, paid API, user data/credential/IPC/registry mutation or Torrent. The helper does not claim FTP server behavior is validated; source-level race closure still needs exact-SHA full build and installed FTP E2E after readiness.

**Next bounded source-only action:** Review remaining FTP cancellation/commit races, HTTP/HLS resume correctness and HG-01–HG-05 installer/identity/icon readiness; add source-only regressions. After *all* source and UI readiness gates, permit first final Windows build, live install/upgrade/FTP/1-4-16/security acceptance and independent double recheck. No direct `main` write, merge, ChatGPT Library update or task disablement.

---

## HyperGet — 2026-10-24: privacy-safe browser errors, correlated replies, fatal input EOF (SOURCE + TEST; NO BUILD)

**Status:** IN_PROGRESS; source readiness only. Existing draft [PR #3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3), head `automation/idm-ftp-cancel-and-category-flush`, base `codex/repair-rf00-rf01`. **Production source changed:** YES. Last source commit before this ledger entry: [`ef82fba2`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/ef82fba2ad729ec36f0bc373c6503069ea4af448). No main write, merge, build, installer, workflow dispatch, paid API or Torrent.

**Findings from current source and separate extension protocol:** `NativeMessagingMessage.Content.error(throwable)` previously returned raw exception class names and `localizedMessage` to the browser, including potential URLs, signed query strings, request headers or credentials in parse/handler exceptions. `NativeMessagingHost.replyError` did the same. The host/router also logged exception objects; a truncated input frame (EOF/other IO) could prompt an unsolicited error response. Finally, router/send failures were answered with a new `N_` ID instead of the decoded request's `B_` ID, while the separate extension resolves pending requests by exact message ID, potentially leaving the request to time out.

**Repair:** Added a tiny, dependency-free `safeBrowserError` policy returning fixed HyperGet error codes/messages without exception text; routed both handler and host errors through it; stopped logging raw exception objects and stack traces on the browser host path; treated input `IOException`/EOF as fatal without writing another response; retained the browser's original request ID for router/send errors, using a new ID only if no decoded request exists. Existing IPC names, extension IDs, credentials, Registry and user data remain untouched. Reused existing Apache-2.0 project code/JDK; no new external library. Browser extension repository inspected read-only; separate extension branding/integration PR remains pending.

| Gate | Status | Exact evidence |
| --- | --- | --- |
| Isolated Kotlin/JVM source tests | **PASS 17/17** | `kotlinc NativeMessagingErrorPolicy.kt HyperGetNativeMessagingErrorPrivacySmoke.kt -d /tmp/hyperget-source-smoke/classes && kotlin -classpath /tmp/hyperget-source-smoke/classes HyperGetNativeMessagingErrorPrivacySmokeKt`; four secret-bearing error samples × three exception categories, deterministic safe responses, preserved browser request ID without generating another, and fallback generated ID. **Only the two isolated Kotlin source files were compiled, not the app.** |
| Exact uploaded source/test blob identity | **PASS** | Production policy blob `d552fbbaca76c3a447568f6e3137f16dddb04f61` and regression blob `fe3fdcf52aaf5a20c370611b797c7ee9b8e2b63b` match local `git hash-object` for the tested files. |
| Cross-file source-only invariants | **PASS 14/14** | Re-fetched production host/router/model/policy/test and extension transport from GitHub. Verified no raw exception text/class in browser error payloads, two request-ID-correlated error paths, no raw exception/stacktrace host logging, fatal receive IO precedes any reply, and extension `pendingRequests.get(message.id)`. **Static checks do not replace app compilation/integration.** |
| PR CI/commit status at `8f8cc8f5` before this patch | **0 reported** | Queried PR workflow runs and combined commit statuses; no runs/statuses reported. **No Actions run requested** for this change. |
| Gradle/full Kotlin desktop compile, Windows host/browser behavior, install/upgrade, real HTTP/HTTPS/HLS, FTP REST, 1/4/16 active downloads, SHA-256, CPU/RAM, Liquid Progress, icon, HyperGet.exe, independent double recheck | **NOT TESTED / DEFERRED** | User's binding 2026-10-23 NO-BUILD rule. Source-only evidence is not Windows acceptance; no executable or installer delivered. |

**Next bounded source-only work:** audit extension's separate-repository native transport disconnect/pending-request lifecycle and HyperGet branding in its own PR, then continue FTP/HTTP queue correctness and HG-01..HG-05 UI/icon/migration readiness. Do not trigger packaging workflows; preserve source-only/packaging CI separation. First final Windows build only after source/UI/security readiness, then real installed acceptance and independent double recheck. Do not disable the recurring task.

---

## HyperGet — 2026-10-24: abort browser native-messaging stdout after a partial write (SOURCE + TEST; NO APP BUILD)

**Production commits:** [`d52063bc`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/d52063bc111e8abeac6f021150d2856b096f961a) (poison broken stdout under the existing frame lock), [`df6da92e`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/df6da92e41895245af9c35fc9c7310dfb9ee4b78) (native host propagates write/flush `IOException` instead of sending a second reply); **regression:** [`b98c14e1`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/b98c14e13654f110cf8a75396ba95cfdbd6aa820). Existing draft PR #3, head `automation/idm-ftp-cancel-and-category-flush`, base `codex/repair-rf00-rf01`. No new dependency; existing Apache-2.0/JDK implementation reused. No change to extension IDs, Registry, IPC, user data or credentials.

**Defect:** `StdioProtocol.writeMessage` serialized concurrent frames but a failed prefix/body write or flush left stdout eligible for later frames. `NativeMessagingHost` also attempted `replyError` on a failed response send, which can append a second frame after an incomplete first frame. A native browser pipe has no resynchronization mechanism. **Repair:** store the original `IOException` while holding `outputFrameLock`, refuse every subsequent write without touching the stream, and terminate the native host on outbound `IOException`, including error-reply failure. Recoverable pre-write JSON/argument errors retain their existing behavior.

| Gate | Result | Direct evidence |
| --- | --- | --- |
| Production and test files on existing PR | **PASS** | Source Git blobs `95dc3977fe24fb7cfbb21c7c9a3be645e31fbf81` and `5b187768168b9a34f5d552bd6298444ead51830a`; test blob `551720e68f1d53d8318e93cbd6e557cd7f2537c1`. Re-fetched from GitHub; local `git hash-object` matched source and test. |
| Before-fix Kotlin/JVM reproduction | **FAIL reproduced (expected)** | Isolated prior `StdioProtocol` source accepted a second frame after simulated partial PREFIX write; regression exited 1 with `Broken PREFIX stream accepted another frame`. |
| After-fix Kotlin/JVM test | **PASS 10/10** | `kotlinc StdioProtocol.kt NativeMessagingFinished.kt HyperGetNativeMessagingBrokenPipeSmoke.kt -d /tmp/hyperget_broken_pipe_test_classes && kotlin -classpath /tmp/hyperget_broken_pipe_test_classes HyperGetNativeMessagingBrokenPipeSmokeKt`. Real GitHub-blob-matched production protocol. Tests partial prefix, partial body, failed flush; no retry, no additional bytes or flush, preserved original cause, and a fresh healthy connection. **Isolated test, not full application build.** |
| Source/host static guards | **PASS 9/9** | 3 protocol guards + 2 native-host fatal-send guards + 4 regression-presence checks. **Host class was not compiled or integration-tested.** |
| GitHub Actions on test SHA `b98c14e1` | **NOT TESTED / 0 reported** | 0 PR workflow runs and 0 commit statuses; no CI/build/installer job requested. |
| Real Windows/browser host, HTTP/HTTPS/HLS, FTP REST, 1/4/16 and CPU/RAM, SHA-256, Liquid Progress, upgrade, `HyperGet.exe`, independent recheck | **NOT TESTED / DEFERRED** | Binding NO-BUILD restriction applies until source/UI/security readiness. No Windows run or final artifact was created. |

**Next bounded source-only action:** Inspect and test the browser extension's native-host error/disconnect handling in its **separate repository** via its own authorized branch/PR; then continue HTTP/FTP and HyperGet identity/UI readiness on the main application's existing PR. Do not run Gradle/packaging/Actions producing installers before final readiness. Do not merge or disable the recurring task. Torrent remains excluded.

---

## HyperGet — 2026-10-24: browser outbound native-messaging limit and strict encoding (SOURCE + REGRESSION; NO APP BUILD)

**Production commit:** [`02ee41e8`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/02ee41e816a8165a06a85954419e98a4fa3d57ce); **test commit:** [`8282b547`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/8282b5477e33012d19d34930a5b8a0e51c8831cc); existing draft [PR #3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3), head `automation/idm-ftp-cancel-and-category-flush` → base `codex/repair-rf00-rf01`. No main writes, merge, workflow trigger, release or paid service.

**Verified protocol discrepancy:** Existing `StdioProtocol.writeMessage` allowed a 4-MiB **host-to-browser** payload even though both [Microsoft Edge](https://learn.microsoft.com/en-us/microsoft-edge/extensions-chromium/developer-guide/native-messaging) and [Firefox/MDN](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/Native_messaging) document a **1-MB host-to-browser limit**. The existing 4-MiB **browser-to-host local allocation cap** is retained separately. Existing `String.toByteArray(UTF_8)` also silently replaced malformed UTF-16 surrogates on outbound responses. Reused the existing Apache-2.0 project protocol implementation and JDK `CharsetEncoder` with `CodingErrorAction.REPORT`; no dependency added, no extension ID, IPC name, Registry key, credentials or stored download migration.

| Gate | Result | Direct evidence |
| --- | --- | --- |
| Source change and regression committed | **PASS** | Production Git blob `37d3040fd9b02217460354830c9ec689ecca8df5`; test blob `c8fc58658e706183ec57cb31026b462911f519c8`. Both GitHub-fetched blobs matched `git hash-object` of the exact files compiled locally. |
| Before-fix regression | **FAIL reproduced (expected)** | Original GitHub-blob-matched source `5a25a27a036fc97fa23fea9f8f9dc62cc0b346e6` compiled with the new regression; it wrongly accepted a 1-MiB+1 outbound message. Test stopped at case 4. |
| After-fix Kotlin/JVM regression | **PASS 10/10** | `kotlinc StdioProtocol.kt NativeMessagingFinished.kt HyperGetNativeMessagingOutboundSmoke.kt -d after-classes && kotlin -classpath after-classes HyperGetNativeMessagingOutboundSmokeKt`. Checks ASCII and Arabic/emoji round-trip framing, exact 1-MiB boundary, 1-MiB+1 ASCII and multibyte rejection **before any output bytes**, lone high/low surrogate rejection, valid surrogate pair, 2-MiB inbound frame still accepted, and subsequent valid output. Isolated real Kotlin/JVM source test; not Gradle or installed Windows. |
| Static/source invariants | **PASS 12/12** | Checked independent inbound/outbound caps, strict encoder, error reporting, output-before-rejection ordering, no silent encoding, regression cases and retained inbound allowance. |
| GitHub Actions / commit statuses at test SHA `8282b547` | **NOT TESTED / 0 reported** | 0 PR workflow runs and 0 commit statuses when queried. Existing Windows packaging workflow push trigger excludes the isolated PR head; **no Actions build was requested**. |
| Browser integration and real Windows host | **NOT TESTED** | No browser process or Windows install executed; the extension repository was read-only in this round and its old README still uses upstream branding. Cross-repo identity/manifest compatibility remains open. |
| HyperGet.exe, Gradle/desktop compilation, installer, real FTP/HTTP(S)/HLS, 1/4/16, SHA-256, CPU/RAM, Liquid Progress, upgrade, icon and independent double recheck | **DEFERRED / NOT TESTED on this SHA** | Binding NO-BUILD gate remains active until all source/readiness requirements are complete. No EXE or installer generated. Torrent remains excluded. |

**Next source-only action:** Audit native-host error handling after a partial stdout write (do not retry into a broken stream); inspect the separate browser extension protocol/branding and HG-01..HG-05 readiness without changing its repository from this task. Continue download/FTP/queue source fixes and tests. Do not begin first final Windows build until all source/readiness gates are met; do not claim final delivery or disable the recurring task.

---

## HyperGet 2026-10-23 — Strict UTF-8 native browser-message decoding (SOURCE + TEST; NO BUILD)

**Production SHA:** [`09abfe24`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/09abfe24137589d6e374b44af3d61797bd6365fd). **Regression SHA:** [`32a4127d`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/32a4127d9b3a2118df06131ed9b264ae1469af1a). Existing draft [PR #3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3), head `automation/idm-ftp-cancel-and-category-flush`, base `codex/repair-rf00-rf01`; no merge or main write.

**Reproduced defect:** `StdioProtocol.readMessage()` used `String(bytes, UTF_8)`, which silently converted malformed browser-supplied UTF-8 into replacement character U+FFFD. An isolated Kotlin/JVM reproduction on the **unmodified, GitHub-blob-matched production source** confirmed that bytes `C3 28` were accepted as `[FFFD, 28]`. Native messaging requires UTF-8; silently changing request bytes is not safe. The source now uses JDK's strict UTF-8 `CharsetDecoder` (`CodingErrorAction.REPORT`) and throws a distinct **recoverable** `InvalidNativeMessagingPayloadException : IllegalArgumentException` after consuming the complete frame. Invalid *length* prefixes remain fatal via `InvalidNativeMessagingFrameException`, preserving the prior host-loop safety behavior. Existing host catch path treats a bad complete payload as recoverable and can read the next frame. No new dependency; existing repository `LICENSE` is Apache-2.0 and JDK decoder is built in.

| Gate | Status | Evidence |
| --- | --- | --- |
| Production source changed and regression committed | **PASS** | Commits `09abfe24` and `32a4127d`; GitHub returned source blob `5a25a27a036fc97fa23fea9f8f9dc62cc0b346e6` and test blob `d7c90fd65b2cb6ef52d7830e102d934f576734cb`, exactly matching locally tested `git hash-object` results. |
| Isolated Kotlin/JVM source test | **PASS 15/15** | `kotlinc StdioProtocol.kt NativeMessagingFinished.kt HyperGetNativeMessagingFramingSmoke.kt -d /tmp/hyperget-post-fix-classes && kotlin -classpath /tmp/hyperget-post-fix-classes HyperGetNativeMessagingFramingSmokeKt`. 12 existing cases + malformed continuation, truncated multibyte, UTF-8 surrogate. Each invalid payload is framed between two valid messages; rejection and subsequent successful frame read verified. No Gradle or app build. |
| Source-only static guards | **PASS 11/11** | Checked strict UTF-8 decoder/error policy, recoverable payload exception vs fatal frame exception, complete-frame-before-decode ordering, unchanged 4-MiB cap, and three malformed UTF-8 variants with next-frame recovery. Static only, not a Windows/Gradle result. |
| GitHub PR workflow runs and commit statuses on test SHA `32a4127d` | **NOT TESTED (0 reported)** | Connector returned 0 PR runs and 0 statuses; existing Windows packaging workflow does not push-trigger on this PR head. No CI requested. |
| Full desktop app, Windows, browser extension and socket integration | **NOT TESTED** | User's NO-BUILD gate remains binding. No Gradle, app compilation, EXE, installer or executable-producing Actions invoked. |
| FTP/REST, HTTP(S)/HLS, 1/4/16 active downloads, SHA-256, CPU/RAM, UI/identity, HyperGet.exe, migration, independent double recheck | **NOT TESTED on current SHA** | Historical acceptance below remains; no final delivery. Torrent remains excluded. |

**Next bounded action:** Review outbound native-messaging UTF-8 encoding and browser host error-path handling, then continue source-only HG-01–HG-05/functional tests. When all source/readiness requirements are complete, permit first final Windows build and installed acceptance; do not mark VERIFIED or disable the recurring task.

---

## HyperGet 2026-10-23 — Native browser-messaging frame desynchronization repair (SOURCE + TEST)

**Production SHAs:** `94114937c1d2e67af121ef699bbb46f9b0c668ef` (`StdioProtocol.kt`), `e8929d0b2116a5b269ea0acfcd5214c22db52f9c` (`NativeMessagingHost.kt`). **Regression source SHA:** `6a66b4a20d567fc0a382dc7857f908f35bc6a9c6` (`scripts/tests/HyperGetNativeMessagingFramingSmoke.kt`). Existing draft [PR #3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3); no merge or direct main changes.

**Defect:** The original Apache-2.0-based native-messaging parser rejected a zero/negative/>4-MiB frame length as `IllegalArgumentException`; the browser host treated that exception as recoverable and read the next four bytes as a new length. Those bytes may actually be the malformed frame's payload, causing a desynchronized error loop. **Fix:** A dedicated `InvalidNativeMessagingFrameException` terminates the host read loop for unrecoverable framing errors, without consuming or reinterpreting attacker-controlled payload. Ordinary JSON serialization/argument errors after a valid frame retain the previous recoverable behavior. Existing native host names, extension IDs, registry keys, IPC, stored downloads and credential keys are unchanged. Reviewed the existing upstream AB Download Manager v1.11.1 implementation and this repository's Apache-2.0 LICENSE; reused existing framing code, no new dependency.

| Gate | Status | Exact evidence |
| --- | --- | --- |
| Kotlin/JVM isolated source framing test | **PASS 12/12** | `kotlinc StdioProtocol.kt NativeMessagingFinished.kt HyperGetNativeMessagingFramingSmoke.kt -include-runtime -d /tmp/hyperget-native-framing-test.jar && java -jar /tmp/hyperget-native-framing-test.jar`; cases cover valid ASCII/UTF-8, consecutive frames, output framing, invalid 0/-1/>4-MiB prefixes, exact 4-MiB boundary, outbound oversize, truncated header/body, clean EOF. Compiled source Git blob hashes `e18907902697ab70fb9ac5f15814796dcafa5a67`, `2b9958f9e5625cf8069fde7cd1b75b88c61b30d9`, test `d744b276d98bfe0a102387f285c6bdee6b4510f3` match the GitHub files exactly. This is NOT full app compilation. |
| GitHub-source guard/static assertions | **PASS 8/8** | Re-fetched exact production/host/test and checked fatal-frame propagation, recoverable JSON behavior, 4-MiB limit, test presence and branch workflow trigger scope. Static only; host as a whole was NOT compiled. |
| GitHub Actions on exact source+test SHA `6a66b4a2` | **NOT TESTED** | 0 PR workflow runs and 0 commit statuses at check. Existing `private-windows-validation.yml` push trigger lists base branch but NOT the isolated PR branch; no Actions requested. |
| Browser extension on real Chrome/Firefox, native-host end-to-end | **NOT TESTED** | No real browser/Windows execution; separate browser repository unchanged. |
| Full Gradle/JUnit, app compile, installer, HyperGet.exe | **DEFERRED / NOT TESTED** | Binding user NO-BUILD gate. No Gradle, packaging, EXE, installer or executable-producing Actions invoked. The temporary isolated JVM test JAR is not a HyperGet application build. |
| HTTP(S)/HLS, FTP/REST, 1/4/16, SHA-256, CPU/RAM, Liquid Progress, upgrade/rollback, icon, independent double recheck | **NOT TESTED on this SHA** | Existing historical failures/gates below remain open. No change to Torrent exclusion. |

**Next bounded action:** Continue HG-01–HG-05 and functional source fixes without building; add a source-only host-loop integration regression when the full serialization dependency is available, then verify extension manifest/identity and Windows migration readiness. After ALL source/readiness gates pass, perform the first final Windows build and strict installed acceptance, including browser framing and actual sockets. Do not mark HyperGet complete, merge, publish, or disable the recurring task.

---

## HyperGet renaming acceptance gates — 2026-10-23

**Approved brand:** HyperGet. This is an administrative plan update only, **no production code/icon/installer changed yet**. [Detailed plan](HYPERGET_REBRAND_PLAN.md). All earlier acceptance/evidence applies to this same project under the new name, without implying rename was implemented.

| Gate | Status | Proof required |
|---|---|---|
| HG-01 User-visible HyperGet name across Windows app/UI, settings, dialogs and system integration | NOT TESTED | Source changes + exact-head Windows screenshots and string audit |
| HG-02 Upgrade-safe persistence: settings, credentials, downloads, database and Windows registrations | NOT TESTED | Previous-to-new version migration/rollback and data integrity tests |
| HG-03 HyperGet.exe + installer/uninstaller/shortcuts + separately maintained browser integration | NOT TESTED | Genuine EXE + clean/upgrade Windows UI, functional extension IPC tests |
| HG-04 Original modern HyperGet icon, not IDM/other app copy | NOT TESTED | Actual embedded ICO resources and Windows icon review |
| HG-05 Full independent final regression (1/4/16 download SHA, CPU/RAM, FTP/HTTP/HLS, resumed installs) | NOT TESTED | Exact-SHA Windows CI, real device/process tests and recheck report |

# Personal Download Manager — autonomous acceptance ledger

AUTOMATION_COMPLETE: IN_PROGRESS

Updated: 2026-10-23 (Asia/Jerusalem)
Base branch: `codex/repair-rf00-rf01`
Base SHA inspected: `6c386abeae95e8e0460308d17c033558cbd60f15`
Change branch: `automation/idm-ftp-cancel-and-category-flush`
Current change commit before this ledger: `610d804a1d39e82a8b4043c832b16ae4d598f8ae`

## This development round — production source changed

1. `FtpTransferClient.kt`: FTP Pause now cancels the **active RETR data InputStream** as well as the control socket. An independent per-transfer cancellation flag is checked after connection/login, during buffered reads and before final commit. Closing the data stream is supported by the official Apache Commons Net 3.13.0 `FTPClient.retrieveFileStream` contract. Stream cleanup now also covers sidecar-creation failures. This addresses the observed Windows FTP E2E defect where Pause left a full 32-MiB `.ftp-part` file instead of a genuine partial transfer.
2. `FtpTransferClientTest.kt`: a real local Apache FTP integration regression test cancels an in-flight transfer from another thread, requires a partial file and no final commit, then REST-resumes it and verifies exact bytes.
3. `CategoryRestartPersistenceTest.kt`: replaced a timing-dependent two-second sample/poll with `CategoryManager.close()` (existing production shutdown flush) followed by an exact disk assertion and restart. This exercises deterministic persistence instead of Windows runner scheduling.

**Verification in this automation runtime:** Source files were fetched from GitHub, compared to the observed failures and modified on an isolated branch. **No Gradle compilation, JUnit execution, Windows install or FTP/HTTP transfer was executed here.** Local environment cannot clone GitHub (network/DNS blocked), and the existing Windows Actions workflows only auto-trigger on the approved base branch, not this PR branch. A passing source inspection is **not** a passing test.

## Evidence and outstanding gates

| Requirement | Status | Direct evidence / next gate |
| --- | --- | --- |
| Clikt header option + persistent IPC compilation | HISTORICAL PASS, CURRENT NOT TESTED | Fixed in base source after `51a4658`; Windows validation [39926396165](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39926396165) passed on `40caada3`. The latest base SHA and this PR SHA have no verified Windows build yet. |
| Category restart persistence | FAIL on older SHA; PATCH UNTESTED | [39929246766](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39929246766) on `46a17b9e`: `CategoryRestartPersistenceTest.restartRestoresCategoriesAndRepairsDuplicateMemberships` timed out, 26 tests/1 failed. New deterministic close/flush test requires rerun. |
| FTP authenticated Pause → restart → REST Resume | FAIL on older SHA; PATCH UNTESTED | [39929246815](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39929246815) on `46a17b9e`: `FTP pause staging invariant failed: partial=33554432 final=False`. New data-stream cancellation and regression test require Windows acceptance. |
| FTP real external LAN/WAN, GUI Add URL/OK | NOT TESTED | Loopback tests do not prove external connectivity, GUI behavior or firewall/NAT handling. |
| 1/4/16 simultaneous installed HTTP + SHA256 + CPU/RAM | FAIL / PARTIAL | [39929246748](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39929246748) on `46a17b9e`: `active-16` round 1 verified 16/16; round 2 observed only 13/16 in-flight tasks. Do not weaken the strict active-socket assertion or modify normal concurrency default of 3. Nine complete rounds remain unverified. |
| HTTP/HTTPS/HLS, signed links, Refresh URL, browser extension | NOT FULLY TESTED | Require current SHA integration tests and real-browser/real-network acceptance. Browser extension remains a separate repository. |
| Liquid Progress, full IDM parity, settings, 16 themes | NOT TESTED | Require actual screenshot comparisons to approved assets on Windows. |
| One Windows EXE, reinstall/uninstall, data safety | HISTORICAL PASS / CURRENT NOT TESTED | [39918464707](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39918464707) packaged exactly one EXE on older SHA; [39919442198](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39919442198) validated same-version lifecycle. Cross-version upgrade and current SHA still open. |
| Torrent | EXCLUDED | Explicitly cancelled by user; never reintroduce. |

## Exact next test commands (NOT executed in this round)

On an authorized Windows runner, at this PR's exact head SHA:

```bash
./gradlew --no-daemon --stacktrace --dependency-verification strict :downloader:core:desktopTest :downloader:monitor:desktopTest :shared:app:desktopTest :desktop:app:compileKotlin
./gradlew --no-daemon --stacktrace --dependency-verification strict createReleaseFolderForCi
```

Then execute the existing installed Windows FTP E2E workflow and the installed 1/4/16 benchmark workflow on that same source SHA, retaining JUnit XML, artifact hashes and full failure logs. Do not spend CI minutes re-running older SHAs. Existing workflows are configured to run on `codex/repair-rf00-rf01` after reviewed integration; no workflow/credentials were changed by this PR.

## Next bounded development action

1. Review/merge this production-code PR into the authorized development branch, or run equivalent exact-SHA Windows validation if workflow permissions permit.
2. If FTP cancellation test/E2E fails, inspect the data-stream close/pause handshake and fix without weakening the staging or REST-offset assertions.
3. Investigate why 16-task round 2 reached only 13 live tasks, preserving per-user concurrency settings and real-socket measurements.
4. Continue FTP GUI acceptance, external network, browser integration and Liquid Progress parity.
5. Only when all requirements have actual end-to-end evidence, mark CANDIDATE and run a separate independent recheck; do not self-certify VERIFIED.

Cost: no OpenAI API or paid CI was enabled; this branch changes no workflows, credentials, repository visibility or releases.


## 2026-10-23 — Follow-up autonomous development: atomic 1/4/16 batch admission (UNTESTED)

Production commits on this same existing draft PR (no new PR, no merge):
- `e5e6ff746b4318e2a8a68a49b2ee8af8e3ffcda5`: `ManualDownloadQueue.resumeAll(Collection<Long>)` now deduplicates and admits the complete user-requested list in one lock-protected queue update and one immediate scheduler pass; existing `resume(id)` delegates to it. The existing user `maxConcurrent` cap is **not** bypassed.
- `010fc6c2106e1cf20818cc78088bffe92358ee07`: `DownloadSystem.userManualResume(ids)` exposes the batch operation while preserving the single-ID entry point.
- `1e68b0cca03419e315155930e1f310633575dc6e`: desktop IPC `resumeDownload(ids)` now calls the batch facade once instead of N sequential admissions.
- `12f42f9728474551ce0b0f5abddacec58752e757`: new `ManualDownloadQueueBatchResumeTest` exercises 16 requested IDs (including duplicates), verifies only 4 are admitted under a 4-task cap, then verifies all 16 are admitted once when the existing cap is raised to 16. Concurrent completion order is not assumed.
- `105f33859868bd5214871a2851bdc2525e151697`: Windows active benchmark now writes `failure-diagnostics.txt` (live fixture counters, persisted limit, installed CLI task status, output sizes) when the exact 1/4/16 active-socket gate fails. **No assertion, concurrency setting, SHA-256 check, CPU/RAM measurement, or sample duration was relaxed.**

**Motivation, not proof of a root cause:** Windows [run 39929246748](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39929246748), source `46a17b9e`, showed active-16 round 1 passing 16/16 and round 2 reaching only 13/16 simultaneously. Previously the desktop IPC admitted each of 16 IDs individually, re-shaking the scheduler after every ID. Atomic batch admission removes that unnecessary repeated scheduling, but the 13/16 failure may have additional causes. The new diagnostic artifact will help identify them if it recurs.

**Verification status:** The new source and test were written to the existing PR branch and their GitHub blob/commit SHAs returned. **No Gradle, JUnit, Windows process measurement, FTP socket test, installed-app test, or PowerShell parser was executed on these commits in this automation runtime.** GitHub Actions currently has no run on this isolated PR head. Status remains **PATCH UNTESTED / FAIL historically**; do not claim 16/16, SHA-256, CPU/RAM or installer success on this head.

**Next gates (run on exact PR head after review/authorized CI):**
1. `./gradlew --no-daemon --dependency-verification strict :downloader:core:desktopTest :downloader:monitor:desktopTest :shared:app:desktopTest :desktop:app:compileKotlin` — including `ManualDownloadQueueBatchResumeTest`, `FtpTransferClientTest` and category restart.
2. `./gradlew --no-daemon --dependency-verification strict createReleaseFolderForCi`, strict JUnit XML verification and one-EXE check.
3. Installed Windows FTP Pause → REST Resume + exact SHA-256; installed 1/4/16 x 3 HTTP concurrency benchmark with real process-tree CPU/RAM CSV and 63 verified outputs. Inspect `failure-diagnostics.txt` if the strict 16-task gate still fails.
4. Preserve the separation of the browser-integration repository, exclude Torrent, and continue the remaining original plan/visual acceptance. Only after all acceptance gates pass may a separate agent perform the final independent recheck.

No paid API, new workflow, public release, repository visibility change, automatic merge or other-project modification was made.


## 2026-10-23 — Follow-up: refill a vacated manual queue slot (PATCH UNTESTED)

**Production change:** [`f8b81550`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/f8b81550f275eff8c01588572b83dfd3dd815c78) changes `ManualDownloadQueue.removeFromQueue(ids)`: when a queued/active ID is removed, the queue now schedules a **debounced** admission pass after releasing `admissionLock`. Before this fix, the `OnJobRemoved` listener simply removed the ID without calling `shake()`; if that ID occupied an active slot, pending jobs could remain stranded indefinitely until an unrelated queue event. Existing completion/cancellation flows continue to work and the configured concurrency cap is unchanged. Empty/repeated removal is idempotent.

**Regression test:** [`38e40674`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/38e40674679e683839f417824de5acc19e03fd48) extends `ManualDownloadQueueBatchResumeTest.kt` with `removingAnActiveJobAdmitsNextPendingJobWithoutCompletionEvent`: start three IDs with max concurrent=1, remove the active ID without a completion event, require admission of the next queued ID, confirm duplicate removal does not double-start, then remove that ID and require the third. This test would time out against the prior source because no scheduling was triggered.

**Actual verification in this run:** Source and test were written to existing draft PR #3. The GitHub connector returned the above commit SHAs. No Kotlin compiler, Gradle/JUnit or Windows benchmark was executed on this patch; `fetch_commit_workflow_runs` returned **zero PR workflow runs** for test commit `38e40674` and the combined commit status had **zero reported checks** when queried. Mark **PATCH UNTESTED**, not PASS. The historical active-16 13/16 failure remains unresolved, and this slot-refill fix must not be represented as a proven root-cause fix for it.

**Required exact-SHA verification:** Run `./gradlew --no-daemon --dependency-verification strict :downloader:core:desktopTest :desktop:app:compileKotlin` including both queue tests; then Windows 1/4/16 x 3 installed HTTP concurrency + 63 SHA-256 files + process-tree CPU/RAM and FTP pause/REST resume, using a build of this PR head. Do not relax socket-count assertions or the user's max-concurrent setting. Keep the browser extension in its own repository, exclude Torrent and do not release/merge automatically. No paid API or new workflow was created.


## 2026-10-23 — Manual queue start-failure slot recovery (production patch; CI pending)

**Source commit:** `f30f0d56939ff7c01a84ef398bc20bea0e343fbf`
**Regression test commit:** `db1281f8528abc4047ae705bd13443374a667708`
**Existing draft PR:** [#3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3), `automation/idm-ftp-cancel-and-category-flush` → `codex/repair-rf00-rf01`. Base SHA reported by PR: `6c386abeae95e8e0460308d17c033558cbd60f15`.

**Observed source-level defect and correction:** `ManualDownloadQueue.downloadAQueueItemIfPossible()` reserved an active concurrency slot before launching suspendable `downloadEvents.startJob(...)`. When that call threw before the manager emitted any lifecycle event, the reserved ID remained in `activeItemsFlow`, preventing the next pending job from starting. The new code catches ordinary `Exception`, removes the failed ID from the manual queue and lets the existing debounced refill admit the next pending task; `CancellationException` is rethrown so scope cancellation is not mistaken for a task failure. No global concurrency bypass, new storage, change of the default cap (3), or automatic retry loop was introduced.

**Regression test:** `ManualDownloadQueueBatchResumeTest.failedAsynchronousStartReleasesItsSlotAndAdmitsTheNextJob` injects an `IOException` from the first start with **no lifecycle event**, asserts the second ID starts under a max-concurrent limit of **1**, asserts no failed-ID spin and no over-admission of the third, then removes the active second ID and checks that the third starts. This is a separate failure mode from the earlier missing removal-slot refill test.

| Verification gate | Status | Evidence |
|---|---|---|
| Production + test code committed on isolated PR | PASS | Source `f30f0d5`, test `db1281f8` |
| GitHub source-level static invariants | PASS (static only) | Re-fetched both files at `db1281f8`; seven targeted source/test presence assertions passed. **Not a compiler/JUnit result.** |
| Actual `:downloader:core:desktopTest` including new regression | NOT TESTED | No Gradle dependency cache / full project available in this automation environment. |
| Windows `:desktop:app:compileKotlin` and JUnit XML gate | NOT TESTED | GitHub `fetch_commit_workflow_runs` on exact test SHA `db1281f8` returned **zero** PR workflow runs; existing validation workflow is scoped to the base branch. |
| Windows installed active 1/4/16 × 3, 63 SHA-256 files and CPU/RAM CSV | FAIL historically / CURRENT NOT TESTED | [39929246748](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39929246748) on older source: 16-task round 2 only 13/16 in flight. This patch handles one potential stranded-slot cause; it **does not prove** the benchmark fixed. |
| FTP Pause → REST Resume and GUI Add URL/OK | FAIL historically / CURRENT NOT TESTED | Older [39929246815](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39929246815) failure; prior FTP cancellation patch on PR #3 still awaits exact-SHA Windows validation. |
| Single Windows EXE, UI parity, credentials, upgrade and independent full recheck | NOT TESTED on this SHA | No new Windows artifact or GUI/device evidence. |

**Next action:** Obtain an authorized **exact PR-head SHA** Windows test run (without relaxing socket/CRC/SHA assertions or altering normal concurrency settings); first run `:downloader:core:desktopTest` and `:desktop:app:compileKotlin`, then the strict installed FTP and 1/4/16 benchmarks. Inspect any failure-diagnostics file and only then diagnose remaining active-16 discrepancies. Do not merge PR #3 automatically. Browser extension repository untouched, torrent excluded, no API fees or public release.


## 2026-10-23 — Pending manual Pause must not start on later queue refill (SOURCE + REGRESSION COMMITTED)

**Production SHA:** [`650f6cd650cf1d8c25e73bb1464c73e740fb4cf1`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/650f6cd650cf1d8c25e73bb1464c73e740fb4cf1)
**Regression test SHA:** [`62713b1d152a6da845250619aadbd711719d25ac`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/62713b1d152a6da845250619aadbd711719d25ac)
**PR:** [#3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3), existing draft; no merge or workflow edits.

**Bug and production repair:** `ManualDownloadQueue.pause(id)` used to call `stopJob(id)` without removing an ID that was still **pending** in the manual queue. A pending download may never emit an `OnJobCanceled` event because it has not started, so when another active task vacated its slot the supposedly paused download could start without another user Resume. The fix atomically removes only pending IDs from `totalItemsFlow` under the existing `admissionLock`, schedules the normal debounced refill and then invokes the existing stop path. **Already-active IDs keep their reserved slots until their stop lifecycle event**, preserving the user's concurrency limit. No new permissions, default changes or Torrent code.

**Repository regression:** `ManualDownloadQueueBatchResumeTest.pausingPendingJobPreventsUnexpectedStartAfterSlotIsFreed` queues IDs 71/72/73 under maxConcurrent=1, pauses pending 72 with a manager that emits no stop event, frees active 71, requires 73 (not 72) to start, then explicitly resumes 72 and requires it to start only after 73 leaves.

**Executed verification, carefully scoped:**
- PASS **isolated Kotlin/JVM behavioral harness** using the installed Kotlin compiler and real bundled kotlinx-coroutines runtime. Reproduced **before** fix: started IDs `[71,72]` (wrong); reproduced **after** fix: `[71,73]` (correct), then explicit Resume started `72` after slot release. This harness reproduces the relevant queue state transitions with minimal shims; **it does not compile the whole actual project and is not JUnit**.
- PASS **7 GitHub-source static assertions** against the committed production and test files: pending removal under lock, active reservation, ordering before stop, refill, test presence and expected sequences. These are source assertions, not unit-test execution.
- **NOT TESTED:** project `:downloader:core:desktopTest` and `:desktop:app:compileKotlin`, because this runner cannot clone the repository or download Gradle dependencies. `fetch_commit_workflow_runs` on test SHA `62713b1d` returned **zero PR workflow runs** and combined commit status returned **zero statuses**. Do not mark Windows compile/JUnit PASS.
- **FAIL historically / CURRENT NOT TESTED:** 16-active Windows benchmark (13/16 in round 2 on [39929246748](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39929246748)), FTP Pause/REST resume ([39929246815](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39929246815)), external FTP and GUI, SHA-256 + CPU/RAM matrix, one-EXE packaging and full visual parity. This specific pause fix is **not** claimed to solve the separate active-16 issue.

**Next mandatory gate:** validate PR #3's exact head (including this patch) on authorized Windows CI: `./gradlew --no-daemon --dependency-verification strict :downloader:core:desktopTest :desktop:app:compileKotlin`; then installed FTP pause/resume and strict 1/4/16 × 3 benchmarks with SHA-256 and CPU/RAM artifacts, without weakening assertions. Inspect the actual output and failures before any merge. Keep browser extension in its own repository, and exclude Torrent.


## 2026-10-23 — Lowering concurrency must not reuse a slot before asynchronous stop completes

**Production commit:** [`2f542a76`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/2f542a76c28e817fc57d0bc630a61eaf14ded0a2)  
**Regression test commit:** [`767c2275`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/767c227503a045d841b4ae654929b18c6f03553a)  
**PR:** [#3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3) (existing Draft, no merge). **Production source changed: YES.**

**Failure mode:** `ManualDownloadQueue.trim()` previously called `removeActiveItem(id)` immediately before launching suspendable `stopJob(id)`. The socket could still be transferring when that slot was declared free. If another active download then finished, `shake()` could admit a new job even though the configured concurrency limit had already been reduced (e.g. 3 → 1). The fix keeps each stop-requested ID in `activeItemsFlow` until a confirmed `OnJobCanceled`/`OnJobCompleted` or explicit removal, and tracks it in `trimmedItems` so repeat shakes do not send duplicate stop requests. It does not change the user's persisted limit or add a new queue implementation.

**Regression:** `ManualDownloadQueueBatchResumeTest.reducingConcurrencyDoesNotReuseSlotsBeforeAsynchronousStopsFinish` starts three jobs at maxConcurrent=3, reduces the limit to 1, records two asynchronous stop requests that intentionally emit **no** cancellation events, removes the only untrimmed job and confirms no fourth job starts while stopping sockets still occupy reservations. After explicit removals confirm the remaining stops, the fourth job may start. The recording fixture now tracks `stopJob` calls.

| Gate | Result | Evidence |
| --- | --- | --- |
| Production source + JUnit regression committed to isolated PR | PASS | `2f542a76` + `767c2275` |
| Re-fetch source/test and check eight exact invariants | PASS — STATIC ONLY | 8/8 checks; confirmed no `removeActiveItem` inside trim request, pending cancellation path still releases reservation |
| Isolated Kotlin/JVM trim-state behavioral reproduction | PASS — ISOLATED MODEL ONLY | `kotlinc /tmp/idm_trim_regression.kt -include-runtime -d /tmp/idm_trim_regression.jar && java -jar /tmp/idm_trim_regression.jar`: reproduced pre-fix premature admission of ID 84 and verified post-fix no early admission/duplicate stop; **not actual production compilation or JUnit** |
| `:downloader:core:desktopTest` and `:desktop:app:compileKotlin` on exact PR SHA | NOT TESTED | No full Gradle project/dependency cache here. `fetch_commit_workflow_runs` returned zero PR runs and `get_commit_combined_status` returned zero statuses for `767c2275`. |
| Installed Windows 1/4/16 × 3 + SHA-256 + CPU/RAM | HISTORICAL FAIL / CURRENT NOT TESTED | Prior [39929246748](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39929246748) recorded 13/16 in one round; this trim fix **does not prove** that issue resolved. |
| FTP Pause/REST Resume, external FTP, GUI OK, browser/HTTP/HTTPS/HLS, Liquid Progress and one Windows EXE | CURRENT NOT TESTED | No Windows runner, real transfer, screenshot comparison or packaging performed on new SHA. |

**Research:** Compared with the official Apache-2.0 `kotlinx.coroutines` Semaphore/withPermit model: a permit is held until work actually completes. A wholesale Semaphore replacement is inappropriate for this dynamically resizable existing queue; preserve its architecture and enforce the same lifecycle-based reservation principle.

**Next action:** Authorize/obtain an exact-PR-SHA Windows CI run of `./gradlew --no-daemon --dependency-verification strict :downloader:core:desktopTest :desktop:app:compileKotlin`, then run installed strict 1/4/16 × 3 benchmark and FTP pause/REST resume. Investigate any failed JUnit first. **No workflow edit, paid API, browser repo change, Torrent, merge or release.** Independent Windows recheck remains mandatory.


## 2026-10-23 — Revoke queued-but-undispatched starts (production + regression; full CI pending)

**Production SHA:** `562301225517d74f08ba362285da553688e9f529`
**Regression SHA:** `237ca04e77dcf7586fd613e708731967ac6e099e`
**PR:** #3, `automation/idm-ftp-cancel-and-category-flush` → `codex/repair-rf00-rf01` (draft; not merged).
**License/dependency review:** Existing project LICENSE is Apache-2.0; repair uses only its already-installed Kotlin coroutines/StateFlow/collections, with no copied third-party code, new dependency, new paid service, workflow edit or security permission.

**Production defect:** `ManualDownloadQueue` reserves an active slot before dispatching the coroutine that calls `startJob`. If the dispatcher is occupied, a user Pause could call `stopJob` **before** the scheduled start executes; the stale coroutine could then start a supposedly paused download. Similarly, lowering max concurrency while starts are still queued could call `stopJob` on nonexistent jobs, leaving races and phantom stop requests. Existing active jobs must keep their slots until actual lifecycle cancellation; only never-dispatched reservations may be released early.

**Patch:** `scheduledStarts` holds per-admission identity tokens under `admissionLock`. The coroutine claims its own token immediately before `startJob` and skips a revoked/superseded start. Pending Pause revokes the token and removes its never-started reservation; trim revokes undispatched starts without issuing phantom `stopJob`; removal/clear also invalidates tokens. Running transfers still use the existing stop/cancellation lifecycle and user max-concurrent cap. Fresh tokens prevent stale coroutines from stealing a later re-admission of the same ID. This addresses the pre-dispatch race, **not** every possible pause-vs-startJob in-flight race.

**Repository regression tests added:** `ManualDownloadQueueBatchResumeTest.pausingReservedButNotYetDispatchedStartCannotLaunchAfterPause` and `trimmingUndispatchedStartsDoesNotStopPhantomJobsOrResurrectOldTokens`. Both deliberately occupy a single-thread coroutine dispatcher to force the race, then assert which IDs actually start and that undispatched IDs do not receive phantom stops.

| Gate | Status | Evidence |
| --- | --- | --- |
| Production and tests committed on existing PR #3 | PASS | Source `56230122`, tests `237ca04e`; GitHub re-fetch returned expected blobs and 10/10 source/test structural checks passed. |
| Isolated Kotlin/JVM coroutines behavioral harness | PASS (isolated model only) | Executed `kotlinc -cp kotlinx-coroutines-core-jvm.jar /tmp/idm_queue_admission_harness.kt -include-runtime -d /tmp/idm_queue_admission_harness.jar` and `java -cp ... Idm_queue_admission_harnessKt` locally. Before/after: paused ID `[91]` → `[92]`; trim `[101,102,103]` → `[101]`. This model is **not** the full repository source/JUnit. |
| Repository Gradle/JUnit and desktop compile | NOT TESTED | Runtime lacks GitHub clone/DNS and full Gradle dependency graph; actual new JUnit tests are committed but not executed. |
| GitHub PR Actions and commit status on test SHA `237ca04e` | NOT TESTED | `fetch_commit_workflow_runs` returned 0 PR runs; `get_commit_combined_status` returned 0 statuses. No exact-SHA Windows build. |
| Installed 1/4/16 real sockets, 63 SHA-256 files, CPU/RAM | FAIL historically / CURRENT NOT TESTED | Prior Windows [39929246748](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39929246748) had 13/16 on active-16 round 2. This race patch is **not** proven to fix that result. |
| FTP Pause → REST Resume, HTTP/HTTPS/HLS, browser integration | FAIL/PARTIAL historically; CURRENT NOT TESTED | Existing FTP and protocol patches require exact-head Windows CI and real network/browser acceptance. Browser extension remains separate and untouched. |
| Liquid Progress parity, single EXE, Windows install/upgrade and independent recheck | NOT TESTED | No current Windows artifact, screenshots, device/process-tree measurements or final package delivery. |

**Next bounded action:** Execute the two new tests in `:downloader:core:desktopTest` and `:desktop:app:compileKotlin` on PR #3's **exact source SHA** with JUnit XML, then strict Windows installed FTP Pause/REST and 1/4/16 × 3 socket/SHA/CPU/RAM acceptance. Investigate actual failures rather than relaxing limits. Follow with real-browser, visual, upgrade and independent security recheck. Do not mark CANDIDATE/VERIFIED, merge, release or disable this recurring task.

**Scope:** Torrent excluded. No other repository, main branch, ChatGPT Library, paid API or workflow changed.


## 2026-10-23 — HyperGet HG-01 display brand, first non-breaking source slice

**Product name:** HyperGet only. **Existing draft PR:** [#3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3), base `codex/repair-rf00-rf01`, head `automation/idm-ftp-cancel-and-category-flush`. **Production source changed: YES.**

- **Source commit:** [`1a84fee4`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/1a84fee483351e4d21dbb0a3857c6facc684bb5e). In `shared/app/build.gradle.kts`, only the generated `APP_DISPLAY_NAME` is now `HyperGet`. This flows through `SharedConstants.appDisplayName` → desktop `AppInfo.displayName` → `HomeWindow` title, tray tooltip and other existing display-name consumers. No storage migration or executable rename was attempted.
- **Regression source:** [`90668d62`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/90668d6278156dfcaca69233266a773d1bd86ffa) adds `HyperGetBrandingCompatibilityTest` under `shared/app/src/commonTest`. It asserts the visible name and keeps technical runtime/storage IDs distinct from the display brand.
- **Upstream/license/security:** Existing codebase is derived from AB Download Manager under Apache-2.0 (repository `LICENSE`). Reused the already-present BuildConfig → SharedConstants → AppInfo display-name pipeline; no third-party code copied, no new dependencies, no credential/IPC/registry mutations, no new service or cost.

| HyperGet acceptance gate | Status | Exact evidence / remaining work |
| --- | --- | --- |
| HG-01: main Windows window/tray name wiring | **PASS STATIC ONLY; WINDOWS NOT TESTED** | Re-fetched committed build config and `AppInfo`/`HomeWindow`: 10/10 static invariants passed. Actual rendered window/tray/About/settings/notifications need Windows inspection. |
| HG-02: preserve existing technical identity | **PASS SOURCE DIFF; UPGRADE NOT TESTED** | `APP_NAME`, `PACKAGE_NAME`, `DATA_DIR_NAME`, `getAppName()`, NSIS `upgradeUuid`, installer package name and data-dir configuration unchanged by this round. Existing downloads, credentials, registry and rollback need old→new Windows E2E. |
| HG-03: HyperGet.exe, installer, shortcuts and separate browser extension | **NOT TESTED / NOT IMPLEMENTED** | Deliberately retained legacy binary, host and registry identities until migration tests; extension repo untouched. |
| HG-04: original liquid-style HyperGet icon | **NOT IMPLEMENTED** | No icon/image was generated or installed in this round. |
| HG-05: complete feature/Windows/security/independent double recheck | **NOT TESTED** | Exact PR test SHA `90668d6278156dfcaca69233266a773d1bd86ffa` had **0 PR workflow runs and 0 commit statuses** when checked. No Gradle/JUnit, Windows build, GUI, FTP/HTTP/HLS, 1/4/16 SHA-256 or CPU/RAM measurements were executed on this source. Historical 13/16 benchmark and FTP failures are unresolved for current SHA. |

**Next bounded action:** Run `./gradlew --no-daemon --dependency-verification strict :shared:app:desktopTest :downloader:core:desktopTest :desktop:app:compileKotlin` and inspect JUnit XML on this PR's exact head; then run Windows installed FTP REST and strict 1/4/16 x3 socket/SHA/resource matrix. Proceed with HG-01 Settings/About/notifications and HG-03 NSIS/shortcut/browser compatibility only with old-version upgrade/rollback evidence. Do not mark CANDIDATE or VERIFIED, change `main`, auto-merge, publish a release, rename the repo/IPC/credentials, reintroduce Torrent, or use paid APIs.


## 2026-10-23 — HyperGet Windows launcher compatibility + branded dialog titles (SOURCE-ONLY, NO APP BUILD)

**User's latest binding gate:** no full app compile/build, Gradle/Compose packaging, NSIS/MSI/EXE or packaging Actions during development. Source/static tests only until all features, icon, UI, upgrade design, code review and delivery readiness are complete. Historical instructions above to run Windows packaging next are **superseded**. No CI or Windows build was requested/triggered in this round.

**PR:** [#3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3), head `automation/idm-ftp-cancel-and-category-flush`, base `codex/repair-rf00-rf01` (no main write/merge).
**Production commits:**
- [`56855f68`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/56855f688043f6cb3fd77d18b475e1fdccc28e1e): added `MainExecutableResolver.kt`. It resolves the main GUI next to legacy CLI/native-messaging helpers, preserves the old main executable when both names exist, accepts `HyperGet` when it is the only GUI, and never mutates disk/registry/IPC/data.
- [`fc4892ae`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/fc4892ae7dbebc05c2700f7dddcd78dbf318de24): `AppInfo.mainExeFile` uses this resolver on Windows only; other platforms and existing CLI/native-host filenames remain unchanged.
- [`7a82ffd4`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/7a82ffd410ded102eb080fb782857906c9215664), [`ffe59103`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/ffe59103d9d6d1324a49a1416df312e733c0ed6d), [`93f9f11c`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/93f9f11cdfcc335f0bd7dd746c19bd3133018673): Settings, About and error dialog titles now include the `AppInfo.displayName` HyperGet brand; localized Settings/About labels are retained.

**Regression source:** [`60ab3e0c`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/60ab3e0cb63fea7993d08d75aac5d067fdce2616), `scripts/source-tests/HyperGetExecutableResolverSmoke.kt`. Covers null/relative path, historical fallback, legacy-only, side-by-side preference, direct old/new GUI, new-only install, non-executable directory and case-insensitive launcher name. Uses temporary `.bin` fixtures, **not executable builds**.

| Gate | Status | Evidence / limitation |
| --- | --- | --- |
| New production resolver + GUI wiring + branded dialog source | **PASS SOURCE COMMITTED** | Six production commits above; re-fetched on existing PR #3. |
| Actual isolated Kotlin source regression | **PASS (10 cases, no app build)** | Ran `kotlinc /tmp/hyperget-source-check/MainExecutableResolver.kt /tmp/hyperget-source-check/HyperGetExecutableResolverSmoke.kt -d /tmp/hyperget-source-check/classes && kotlin -classpath /tmp/hyperget-source-check/classes com.abdownloadmanager.desktop.utils.HyperGetExecutableResolverSmokeKt`. Output: `PASS HyperGet executable path source smoke: 10 cases`. Before execution, `git hash-object` on both local copies matched GitHub blob SHAs `cc122d8462bedf0b6d39efededa4963e354b24a5` (production) and `c6eb5f5df6c10248435502c22a39764b8cf4fc03` (test), so these were exact committed sources, not a mock. |
| GitHub source/static checks | **PASS 13/13 (static only)** | Re-fetched resolver, `AppInfo`, smoke test, three dialogs, native-messaging manifest/host and BuildConfig. Verified brand wiring, safe old/new precedence, Windows-only change, no file mutation, legacy IPC/registry keys. No Gradle/JUnit/Windows assertion. |
| No packaging during this round | **PASS (actions not invoked)** | Inspected workflow triggers. Existing packaging workflows are push-scoped to `codex/repair-rf00-rf01`/main or manually dispatched, not PR #3 head. No workflows were edited or dispatched. This is **not** a guarantee about external actors/manual triggers. |
| CI on exact source SHA `93f9f11c` | **NOT TESTED / 0 checks** | `fetch_commit_workflow_runs`: 0 PR runs; combined commit status: 0 statuses. Deliberately did not trigger build workflows. |
| HG-01 visible dialog titles | **PASS SOURCE ONLY; WINDOWS NOT TESTED** | Settings/About/error now use HyperGet; Windows screenshot/rendered UI inspection deferred. |
| HG-02 legacy data/credentials/registry/IPC | **PASS NO-MUTATION SOURCE REVIEW; UPGRADE NOT TESTED** | No changes to IDs, storage, native-host names or registry. Actual old-to-new upgrade, rollback and credential preservation still unverified. |
| HG-03 `HyperGet.exe`/installer/extension | **NOT IMPLEMENTED / NOT TESTED** | This round only prepares runtime path selection; binary rename and packaging intentionally deferred. Separate extension repo main HEAD `491c2d64589e6697c53a6bdb15ccce38a6fc860f`, no open PR at inspection; no cross-repo write. |
| HG-04 custom icon and Liquid Progress parity | **NOT IMPLEMENTED / NOT TESTED** | Original brand icon, DPI checks and approved screenshot parity remain open. |
| HG-05 FTP/HTTP(S)/HLS, 1/4/16, SHA-256, CPU/RAM, Windows installed acceptance and independent double recheck | **NOT TESTED on current SHA** | Historical FTP and 13/16 active transfer failures remain unresolved as exact-head Windows tests are intentionally deferred. No final EXE/artifact. |

**Open-source/license/security review:** Inspected existing AB Download Manager-derived source, `AppInfo.calcExecutable`, jpackage path usage, and repo `LICENSE` (Apache-2.0). Adapted existing Java/Kotlin `File` path behavior with no new dependency, no downloaded executable and no third-party code copied. Conservative legacy-first selection prevents silently switching an existing helper to a side-by-side branded executable; final installer/upgrade must explicitly resolve how an old GUI binary is retired while keeping IPC/credentials stable.

**Next source-only round:** Audit remaining user-visible HyperGet strings and Windows installer/shortcut/native-host migration logic without building; add source-level compatibility tests and icon/readiness checks; continue FTP/HTTP/HLS/queue security and logic fixes with unit/static tests. Keep `AUTOMATION_COMPLETE: IN_PROGRESS`. Do not package until all source/readiness gates are met, then do first final build, actual Windows acceptance, independent double recheck and verified delivery. No Torrent, paid API, auto-merge, release or ChatGPT Library access.


## 2026-10-24 — HyperGet native-messaging concurrent outbound framing (SOURCE-ONLY; NO APP BUILD)

**Existing PR:** [#3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3), head `automation/idm-ftp-cancel-and-category-flush` → base `codex/repair-rf00-rf01`. **Production source changed: YES.**

- **Production SHA:** [`ec8f5081`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/ec8f5081bde80746e99bf9326bbc9734be661daf). `StdioProtocol.writeMessage` previously wrote a four-byte native-messaging length prefix, then payload, then flush without guarding the three operations. If two callers shared the protocol, the second could write its complete frame after the first header but before its body, corrupting the browser's framing. Introduced a per-instance `outputFrameLock` guarding the full prefix/body/flush sequence. UTF-8 encoding and 1 MiB outbound validation remain outside the lock. The inbound 4 MiB limit and existing framing semantics are unchanged.
- **Regression SHA:** [`cae20018`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/cae20018a2e9e03ab42c1d6d625db3ef072c2a47), `scripts/tests/HyperGetNativeMessagingConcurrentSmoke.kt`. A gated `OutputStream` pauses the first writer immediately after its prefix and starts a second writer. The test requires the second writer to wait and the final stream to contain two contiguous valid frames.
- **Actual source-only verification — PASS:** Fetched the pre-patch production blob (`37d3040fd9b02217460354830c9ec689ecca8df5`), matched it with `git hash-object`, compiled it with `kotlinc` and ran the new test: **FAIL reproduced** (`Second sender overtook the first incomplete frame`). Applied the minimal lock fix, compiled **the exact committed production source and committed test** using `kotlinc StdioProtocol.kt NativeMessagingFinished.kt HyperGetNativeMessagingConcurrentSmoke.kt -d after-classes && kotlin -classpath after-classes HyperGetNativeMessagingConcurrentSmokeKt`: **PASS**. `git hash-object` matched GitHub blobs `ee58e3b663671dc9e98cdac8f237ab7be82d03c9` (production) and `fc33bee7748b98e02f648910c2d9ef5dca2f5b9e` (regression). Six source invariants also **PASS** (locking scope, byte limits, strict UTF-8, no new dependencies, unchanged reader).
- **Open-source/license/security:** Inspected existing repository `LICENSE` (Apache-2.0) and reused the existing Java/Kotlin monitor primitive; no external code copied, no new dependency, API, credential, registry key, IPC name or browser extension change. The lock prevents interleaved stdout frames; it does not claim to repair partial writes caused by OS pipe failure.
- **CI at regression SHA `cae20018`: NOT TESTED** — `fetch_commit_workflow_runs` returned **0 PR runs** and combined commit status **0 checks**. Deliberately did not dispatch packaging workflows.
- **Windows/Gradle/Compose desktop, FTP/REST, HTTP(S)/HLS, 1/4/16, SHA-256, CPU/RAM, browser E2E, Liquid Progress, HyperGet.exe, upgrade/installer and independent recheck: NOT TESTED** on this SHA. Historical 13/16 active benchmark and FTP failures remain unproven fixed. No EXE or installer created.
- **Next bounded source-only action:** Audit `NativeMessagingHost` handling of failed stdout writes (do not attempt to continue a broken frame), and the separate browser extension compatibility without changing its repository from this PR. Continue engine and UI source fixes; run source-only tests, static checks and code review. Full app build and packaging remain **PROHIBITED until feature/readiness gates are complete**, then perform one final build and real Windows acceptance with independent recheck. Keep `AUTOMATION_COMPLETE: IN_PROGRESS`.

No `main` write, auto-merge, Torrent, public release, paid API/service, packaging Action, other-project edit or ChatGPT Library access.


## 2026-10-24 — HyperGet browser companion: separate PR #1 (SOURCE-ONLY, NO BUILD)

**Cross-repository work only:** Main HyperGet PR [#3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3) remained at `3ab8526c7bbba79f41e9bcadc3c427c03f97c17c` before this ledger update. No main application production source was modified in this round. The browser companion was changed in its **separate** repository/branch, not copied into this repo.

- **Browser PR:** [HyperGet browser #1](https://github.com/amroalhamedi2022-hue/personal-download-manager-browser-integration/pull/1), draft; branch `automation/hyperget-browser-lifecycle-branding` → `main`, head `4ccec15512cd7b15180d13d0b61daa97d0fdb4ea`.
- **Production change:** [`49ab03b1`](https://github.com/amroalhamedi2022-hue/personal-download-manager-browser-integration/commit/49ab03b1fed599048da8ebbb33f0e52396fcc72a): reject pending native requests immediately on explicit disconnect; ignore stale disconnect and message events from old ports after reconnect; stop logging arbitrary native payloads. User-visible package/manifest/English locale now say HyperGet. Native host name, Firefox ID, Chrome public key and protocol are preserved.
- **Source regression:** [`e358ac07`](https://github.com/amroalhamedi2022-hue/personal-download-manager-browser-integration/commit/e358ac07fa38e087c2114911e0732aef691271c1), corrected at [`e405399a`](https://github.com/amroalhamedi2022-hue/personal-download-manager-browser-integration/commit/e405399aeb03c4bcdf6d2e10fab9c158365b0b48): `scripts/tests/HyperGetNativeTransportSourceSmoke.cjs`, Node/TypeScript in-memory source-only test harness.
- **PASS 8/8 isolated source-model checks:** Fetched the production TypeScript from GitHub, stripped TS-only syntax in memory (no app build), executed with mocked browser native ports; checked explicit disconnect, stale disconnect, stale response, multiple pending requests, timeout, secret-free logging, one-shot request and preserved identities. **Not** an npm test run or TypeScript typecheck.
- **NOT TESTED:** Node test harness execution with repo dependencies, TypeScript typecheck, Chrome/Edge/Firefox browser runtime, Windows host↔extension E2E, signed links/cookies, HTTP fallback, upgrade, installer, real downloads. Browser PR exact head has **0 workflow runs and 0 commit statuses**.
- **License/security:** No new dependency; Apache-2.0 license and upstream attribution preserved. No API charges, app/extension build, Vite packaging, EXE/installer, release, main write, merge or auto-merge.
- **HG-01/HG-03 browser branding:** **PASS source-only; installed extension NOT TESTED**. **HG-02:** legacy extension IDs and host preserved by source review; migration/upgrade NOT TESTED. **HG-04/HG-05:** icon, visual parity, engine acceptance and independent recheck NOT TESTED.
- **Next:** Continue production engine/Windows UI source fixes on main PR #3 and execute browser source-only Node tests when dependencies are available. Only after all source/readiness gates may final Windows build/real browser acceptance begin. `AUTOMATION_COMPLETE: IN_PROGRESS`.


## 2026-10-24 — HyperGet FTP staged-file identity + isolated source-only CI (NO APP BUILD)

**Scope:** FTP data safety and verifiable source-only CI, not a completed Windows product. **Existing draft PR:** [#3](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/3) on `automation/idm-ftp-cancel-and-category-flush` → `codex/repair-rf00-rf01`; no main write or merge. Browser integration remains in its separate [draft PR #1](https://github.com/amroalhamedi2022-hue/personal-download-manager-browser-integration/pull/1).

**Production commits:** [`eb7ba7a8`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/eb7ba7a8dddf00b169716b2bcf30b27221313ffc) retains a staged file key and verifies the file before/after opening; [`bca6d0c2`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/bca6d0c236e42ffa6431d6333d3ca5e08c8ff768) rejects nonregular, truncated or replaced staged files before final publication; [`44cad80e`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/44cad80e8a9e7d4b76026b723f2fe743fa34b248) passes the opened file key and final downloaded byte count from the actual FTP transport. A replacement of `.ftp-part` after the stream was opened could otherwise publish unrelated data of the same length. Existing no-overwrite final-file policy is preserved.

**Tests and CI:** [`7e0a844e`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/7e0a844e34b8451323d96dcfd1b83b8ddf87b56a) adds isolated Kotlin source smoke; [`089a8604`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/089a8604ac14f2e19cd9df7a7f6f89cd68a7194b) adds 3 repository JUnit regression cases (not executed); [`4dd904f1`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/4dd904f1c583d46c741639c1e670ad0c299f3c4a) adds six Python source contracts; [`9638d1b5`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/9638d1b5fd18d3101d7b9cb49c6841c670a7b996) adds a dedicated **source-only** workflow (Python stdlib, no Gradle/Compose, app compile, EXE, NSIS, MSI or artifact upload). Existing Windows packaging workflows have push filters for `main`/`codex/repair-rf00-rf01`, not this PR head. No packaging workflow was requested or invoked.

| Gate | Status | Evidence / limitation |
| --- | --- | --- |
| Production and source/JUnit regressions committed on PR #3 | **PASS — SOURCE PRESENT** | Re-fetched exact GitHub blobs; 18/18 source/license/connection invariants passed. |
| Isolated Kotlin/JVM stage identity smoke | **PASS — 9/9 LOCAL PATCH-EQUIVALENT** | JDK 21 / kotlinc 1.9.0 compiled standalone `FtpStagedOutput.kt`, `FtpStagedFileCommit.kt` and new smoke; ordinary publish, size mismatch, symlink and same-size replacement checks passed. This is not full app compilation or exact-PR JUnit. |
| GitHub source-only CI | **PASS — 9/9 PYTHON** | [Run 40065060057](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40065060057) on SHA `9638d1b5fd18d3101d7b9cb49c6841c670a7b996`: 6/6 FTP stage static source contracts plus 3/3 local authenticated FTP fixture protocol tests; both jobs/steps succeeded. No application binary produced. |
| New repository `FtpStagedIdentityTest` JUnit | **NOT TESTED** | Full Gradle test/build remains prohibited before readiness. |
| Windows FTP Pause/REST, HTTP/HTTPS/HLS, 1/4/16, SHA-256, CPU/RAM, browser, UI, upgrade and HyperGet.exe | **NOT TESTED ON THIS HEAD** | Historical Windows active-16 failure 13/16 in round 2 and FTP Pause failure are not cleared by this source-only change. |
| Security and filesystem race completeness | **PARTIAL** | File-key checks only apply when the provider exposes a stable `fileKey`; checks cannot completely eliminate cross-process TOCTOU between validation and publish, nor defend against all malicious hard-link/directory-owner attacks. Independent Windows/NTFS and network-share tests remain mandatory. |
| License/dependency/cost | **PASS SOURCE REVIEW** | Existing Apache-2.0 project license retained; only JDK/Kotlin/Python stdlib already in project, no new dependency, paid API, installer, merge, release or Torrent. |

**Mandatory next work:** Continue source fixes, original Liquid Progress/UI and HyperGet identity/icon/migration requirements, full protocol/concurrency coverage and license/security review. Do not run any full Gradle compile/build or packaging until all source-readiness gates are met. Then permit the first final Windows build, run installed Windows tests, independent double recheck, and deliver an actual `HyperGet.exe` with SHA-256 before declaring completion or disabling the recurring task.

## 2026-10-24 — HG-HLS-AUTH-01 follow-through: final playlist URL and persisted media identity

**Functional requirement:** A previously completed HLS segment may be reused only when its media resource identity is unchanged. The existing reconciliation compared raw relative URI + duration + sequence, but `clip.ts` in `/a/` is not the same resource as `clip.ts` in `/b/` after a playlist redirect. A size-valid old staged file could otherwise be assembled into a different playlist.

**Production change (this PR head):** resolve each segment URI using OkHttp `HttpUrl.resolve` against the **final playlist response URL**, then persist the canonical absolute media URL as its identity. Keep existing sequence/duration matching and staged-file checks. Strip fragments (not sent to HTTP servers), retain signed query parameters, reject URL-embedded username/password. Existing relative-URI records are conservatively re-downloaded once on refresh; no database migration, deletion of user downloads or change to native messaging/IPC.

**Source regression:** seven Kotlin desktop tests for same-resource reuse, redirected directory, redirected host, absolute CDN segment, old relative records, query/fragment semantics and URL userinfo rejection. Five Python source contract tests guard production wiring and the new cases.

**Milestone build gate (planned, not yet claimed):** Run **only** `:downloader:core:desktopTest --tests '*HlsGuardedRedirect*Test*' --tests '*HlsSegmentIdentityTest*'` on free Windows GitHub Actions; expect three JUnit XML files, at least 19 tests, zero failures/skips. This purpose-specific compilation is necessary because Python static tests cannot validate Kotlin/OkHttp binary API compatibility or actual JUnit execution. It does **not** package HyperGet.exe or test the installed UI. Capture exact SHA and run log before marking PASS. Earlier loopback run 40115692417 succeeded on previous head `08d6ce14`, but that does not validate this patch.

**Remaining:** HLS end-to-end playlist→media download and byte/hash comparison on Windows; same URL with mutated server bytes remains intrinsically unprovable without trusted validators/content hashes; FTP Pause/REST, 1/4/16, HTTP/HTTPS, CPU/RAM, Liquid Progress, icon, browser and upgrade remain open. Status IN_PROGRESS. No main write/merge, Torrent, paid service or release.

**Reference/licensing:** reused existing OkHttp `HttpUrl.resolve` (OkHttp Apache-2.0) and existing project's Apache-2.0 Kotlin architecture, no new dependency or copied third-party source.

### Verified follow-up — 2026-10-24, HG-HLS-AUTH-01 media identity (exact-SHA evidence)

**Functional acceptance:** Resolved absolute media URL is persisted as the segment identity, so a changed final playlist directory/host invalidates completed bytes even when the relative filename, sequence, duration and staged file size match. A stable absolute CDN URL remains reusable. Legacy persisted relative identities deliberately re-download once rather than trust an unknown old base URL. This is a source/targeted-JUnit milestone, **not installed end-to-end HLS completion**.

**Production + regression SHA:** [`691ad625e15553728650d2026ede7b709020e572`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/691ad625e15553728650d2026ede7b709020e572). Source-only workflow wiring SHA: [`f585ab757b432bdc85449087ad6535d2f492b4fc`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/f585ab757b432bdc85449087ad6535d2f492b4fc).

- **PASS — source CI on exact latest source SHA `f585ab75`:** [40119024251](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40119024251). New five-case HLS media identity source contract passed along with the existing fast source/FTP fixture checks. This is not a runtime HLS download.
- **PASS — Windows Kotlin/JUnit on exact latest source SHA `f585ab75`:** [40119024272](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40119024272). The scoped Gradle desktop test task compiled and ran the guarded redirect and media identity tests; the workflow required **three** JUnit XML suites, **at least 19** executed tests, and **zero** failures/errors/skips. No EXE/installer was produced. Independent prior identical production+test SHA [40119014257](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/40119014257) also passed.
- **Evidence limitation:** Workflow's upload-artifact glob still selects only `HlsGuardedRedirect*Test.xml`; the gate validated all three XML files on-runner but the downloadable JUnit artifact omits `HlsSegmentIdentityTest.xml`. Fix this upload glob with the next functional milestone to avoid another redundant Windows compile solely for metadata. Do not claim artifact has the third suite.
- **NOT TESTED:** full playlist-to-media bytes download/assembly with SHA-256 on installed Windows; same-URL mutated server content, crash/restart, external FTP, HTTP/HTTPS, 1/4/16 strict active sockets, CPU/RAM, Liquid Progress, icon, single HyperGet.exe, browser extension E2E, old→new upgrade and independent full-product recheck.
- **Next milestone:** Build a loopback HLS end-to-end fixture (playlist redirect + two distinct .ts payloads + resume/relocation + output SHA-256) against the actual downloader job and then a Windows installed benchmark. Keep current draft PR, no main write/merge/release, no Torrent or paid services.

**Milestone verdict: PASS targeted source and Windows JUnit; overall HyperGet IN_PROGRESS.**
