# HyperGet functional execution — 2026-11-04

## Scope and exact refs
- Base main: `eebc55752ee92364d9fca6f43362a91ef218665f`.
- Isolated branch: `automation/http-429-single-attempt-20261104`.
- Source/test commit: `b9ab6a1f06b8af6b862ec80f2a13fd9a26f363d2`.
- PR #3: independent FTP/queue work; PR #4: visual-only; PR #5: F3/Shift+F3 work. None modified.
- Existing plan: `docs/Download_Manager_Execution_Plan_v1.md`; historical 82-item trace located on `automation/idm-ftp-cancel-and-category-flush`. `docs/IDM_REQUIREMENTS_AUDIT_2026-10-30.md` was not found at queried refs.

## Actual functional regression addition
- P2-05 / F01-06: added an HTTP 429 + `Retry-After: 7` loopback-server test to `HttpSingleAttemptRetryTest.kt`.
- Asserts one observable HTTP request, original status 429, and preservation of the header; catches unintended hidden client retries before the job's bounded retry policy runs.
- No production code, Liquid Glass, icons, colors, UI, or workflow modifications.

## Verification and commands
- GitHub connector `fetch_file` on branch after write: PASS, new 429 assertions and original 503/200 tests present.
- Source structural checks: 6/6 PASS; this is **not** Kotlin/JUnit execution.
- Required on CI/Windows: `./gradlew :downloader:core:desktopTest --no-daemon` (module/task availability must be confirmed on checkout). **NOT RUN** in this environment; no Gradle checkout/runtime was available.
- GitHub Actions: exact new commit result **NOT YET VERIFIED**. Do not infer from earlier SHA.
- No merge or release. Next: exact-head desktop tests; if green, PR review and conflict checks before any merge.

## Remaining
- P2-05: host-specific retry budgets and full job integration.
- P2-03/P2-04: crash and pause/resume full-job Windows acceptance.
- PR #3 FTP/queue acceptance; PR #5 F3 keyboard GUI acceptance.
