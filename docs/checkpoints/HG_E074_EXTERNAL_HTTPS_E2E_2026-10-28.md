# HG-E074 — Installed external HTTPS + Range acceptance (2026-10-28)

## Scope and decision

This is the next acceptance gate after HG-E073 scheduled queue start/stop passed. It is a purpose-bound Windows installer test, not a release build and not a production downloader change.

**Acceptance question:** can the exact PR-head HyperGet installation use the machine/JVM's normal HTTPS trust, download a deterministic 10 MiB public range-test resource, honor an external 206 Range response for a query-bearing URL, and produce byte-identical output with a matching SHA-256?

**Expected PASS**
- The source contract and PowerShell parser pass before packaging.
- A single test installer is built from the exact PR head and installed in an isolated temporary directory.
- The independent reference fetch returns exactly 10,485,760 bytes using normal TLS validation.
- A Range request to the query-bearing HTTPS URL returns HTTP 206, `Content-Range: bytes 0-1023/10485760`, exactly 1,024 bytes, and a SHA-256 matching the first 1,024 bytes of the reference file.
- Installed HyperGet downloads the same 10 MiB URL and the final file length and SHA-256 match the independent reference, remaining stable after completion.
- The sanitized artifact records source SHA, test-installer SHA, reference/output hashes, Range status and failure stage; it does not log full URLs or tokens.

**Why source tests are insufficient:** they cannot establish public DNS/TLS trust, the external server's actual Range behavior, JVM HTTPS negotiation, installed GUI/CLI IPC, Windows file publication, or byte equality after a real network transfer. A targeted Windows build is justified specifically to answer this question.

## Security and interpretation boundary

The public range endpoint is not a signed-URL issuer. The query value is synthetic and only checks query-bearing HTTPS/Range handling. This gate **does not** prove provider-specific signed-token expiration or refresh. The existing local verified-TLS JUnit test covers controlled old-token rejection and new-token resume; external provider-specific signed-link expiry remains **NOT TESTED**.

## Pre-run status

**FIRST ATTEMPT BLOCKED BEFORE BUILD:** the source contract incorrectly matched the explanatory comment containing `-SkipCertificateCheck`; both the source-only and external-HTTPS jobs stopped before Gradle or Windows runtime. The contract was corrected to inspect executable PowerShell lines only, then exposed a second prose-wording mismatch (the test expected a phrase not present in the harness). The test now asserts the actual HTTPS reference-fetch command rather than descriptive wording; byte-prefix allocation was made explicit. Both attempts stopped before Gradle packaging and before any external endpoint or installed-app test. These are source-contract issues, not product results. The gate remains OPEN pending a new exact-head run. No result is inferred from older HTTP, HLS, FTP, browser, scheduler, or benchmark runs. No production source is changed, no merge or `main` write, no release, no paid API/runner, no browser-repository change, and no Torrent feature.
