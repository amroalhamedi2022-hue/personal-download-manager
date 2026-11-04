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

## Follow-up verification — 2026-11-04 16:44 Asia/Gaza
- Re-read live GitHub PRs: #3 (FTP/queue, draft), #4 (visual, draft), #5 (F3/Shift+F3, draft), #6 (this HTTP test, draft). Avoid overlapping production changes.
- main HEAD observed: `eebc55752ee92364d9fca6f43362a91ef218665f`; this branch HEAD before this documentation update: `421e370a61fab86397479ef02b363fda6de9bf3a`.
- GitHub compare `main...automation/http-429-single-attempt-20261104`: ahead 2, behind 0, only this Kotlin test and this checkpoint changed. PR #3 branch compare: diverged, ahead 5, behind 50; do not merge without reconciliation.
- Connector `fetch_commit_workflow_runs` on exact PR #6 HEAD `421e370a...`: **0 returned** (connector only searches PR-triggered runs, first page); `get_commit_combined_status`: **0 returned**. Same checks on PR #5 `4c12745363eca5fcf379a2df8dc33a0fa1bbea5a`: 0/0. **No exact-head CI success proven.**
- Attempted `git ls-remote https://github.com/amroalhamedi2022-hue/personal-download-manager.git HEAD` in the execution container: **FAILED** (`Could not resolve host: github.com`). Thus Gradle/JUnit and Windows installer acceptance could not be executed locally; no executable-test PASS claimed.
- Read `docs/IDM_Requirements_Trace_82_2026-10-21.md` from `automation/idm-ftp-cancel-and-category-flush`: P2-05 still requires per-host limits. Historical trace says 81 active requirements after removal of P7-11; do not overwrite this with an obsolete 82-complete claim.
- `docs/IDM_REQUIREMENTS_AUDIT_2026-10-30.md` remains unavailable at the default branch; visual handoff read from PR #4 branch only as context, not authority to edit UI.
- Decision: **no additional downloader implementation** without executable tests, and **no merge** while PR #6 exact-head checks are absent. Next: restore a working Gradle checkout/CI path; run `./gradlew :downloader:core:desktopTest --no-daemon` on the exact commit, then evaluate host-specific retry budget implementation separately from FTP and F3 work.

## Verification refresh — 2026-11-04 19:41 Asia/Gaza
- Live `main` HEAD from GitHub commit search: `eebc55752ee92364d9fca6f43362a91ef218665f`. PR #3 HEAD `ff0fd84be441557eb2e00dc0016259e65b369ac3`; PR #4 HEAD `a5867fdc940fd197b290ee32815ce91e2712dd79` (visual-only, untouched); PR #5 HEAD `4c12745363eca5fcf379a2df8dc33a0fa1bbea5a`; PR #6 HEAD before this documentation change `0d45437fc97cec23cf39724d2ac46254c00cb01a`.
- Read `docs/IDM_REQUIREMENTS_AUDIT_2026-10-30.md` on PR #3 branch `automation/idm-ftp-cancel-and-category-flush` (not present on `main`). It marks P2-05 partial: host-specific retry budgets remain missing. Read historical 82-item trace on same branch; its historical ticks are not acceptance evidence. Read PR #4 handoff on its own isolated branch without editing it.
- GitHub `fetch_commit_workflow_runs(ff0fd84...)`: 16 returned, **15 success, 1 failure**. Failing exact-head workflow: Windows installed active HTTP 1-4-16 benchmark [41705173226](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41705173226). Passing FTP [41705173146](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41705173146), queue [41705172977](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41705172977), and other gates do not override benchmark failure. Failure's job logs were not available in this pass; **root cause unknown**.
- `fetch_commit_workflow_runs(0d45437...)` returned 0 PR-triggered runs and `get_commit_combined_status(0d45437...)` returned 0 statuses. These are limited connector queries, not proof that all possible CI sources were checked. **No exact-HEAD JUnit/Windows acceptance for PR #6.**
- Local command `git ls-remote https://github.com/amroalhamedi2022-hue/personal-download-manager.git HEAD`: FAILED `Could not resolve host: github.com`. No local checkout, Gradle/JUnit, or Windows runner access. No production code modified or merged in this refresh. Do not label the existing HTTP 429 source regression tested.
- Next safe work: obtain exact-head runnable Windows/Gradle gate for PR #6; investigate the active-16 benchmark failure on PR #3 with its job log/artifact before altering concurrency logic. Implement per-host retry budgets only with isolated regression coverage and executable acceptance; keep Liquid Glass, icons and palettes out of scope.
