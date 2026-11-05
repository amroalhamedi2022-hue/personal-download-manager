# HyperGet: first unattended OpenCode pilot

You are running inside a GitHub Actions checkout of the latest main branch of the
Windows-only HyperGet repository. This is a **single, tightly scoped regression
test task**, NOT permission to work on unrelated features.

Make a real and minimal Kotlin source edit in ONLY:
downloader/core/src/desktopTest/kotlin/ir/amirab/downloader/connection/HttpSingleAttemptRetryTest.kt

Add a distinct Kotlin test named:
rateLimited429WithHttpDateRetryAfterIsOneObservableAttempt

Test the actual existing OkHttpHttpDownloaderClient.head() against a local
127.0.0.1 HttpServer responding with status HTTP 429 and an IMF-fixdate
Retry-After header (e.g., Wed, 21 Oct 2015 07:28:00 GMT). Assert:
* Status code is still 429.
* Response header retry-after is preserved exactly as sent.
* The server counted exactly one network request; client must not do any
  hidden retry. The download job owns retry/backoff decisions.
* Always stop the server in a finally block.
* Reuse the existing createClient() test helper.
* Keep existing test cases and behaviour intact.

Before editing, read the entire target file. If this exact named test already
exists, do NOT change anything. Avoid duplicate implementation or broad
refactors.

Forbidden: production source changes, workflow changes, dependency changes,
theme/Liquid Glass/icon changes, renaming projects or packages, touching
performance thresholds, skipping tests, revealing credentials, changing
anything outside the single test file, and claiming CI passed without evidence.

The runner will reject all modified/untracked files except the designated test
file. This Ubuntu runner does not prove Windows Gradle acceptance: the real
Windows GitHub workflow will be dispatched after the code is proposed as a
draft pull request. Your job is to write the regression test, not a report.
