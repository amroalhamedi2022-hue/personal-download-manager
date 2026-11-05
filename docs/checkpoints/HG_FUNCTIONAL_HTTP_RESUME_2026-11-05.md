# HyperGet Functional Delivery — HTTP resume identity safety (2026-11-05)

## Ownership / isolation
- Target: F01-07 / P2-02, safe link rotation and resume identity in Windows engine; no theme/UI, Liquid Glass, colours, assets, Copilot F03/F07-03 or PR #8 changes.
- Base `main` verified via `GET /repos/amroalhamedi2022-hue/personal-download-manager/branches/main`: `ef71df8404e8a31b650688e392d322f70ecf57e8`.
- PR #3 remains a divergent FTP/queue Draft; PR #4 visual Draft; Copilot PR #10 merged; PR #8 independent Draft; issue #11 remains owned by Copilot.
- Feature branch: `feature/http-resume-identity-guard-20261105`. Draft PR: https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/13

## Actual production work
- `downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/http/HttpResumeIdentityPolicy.kt`: preserve original resource selector comparisons; require valid strong ETag and known positive length for changed signed URL; fragment-only change is no-op.
- `.../HttpDownloadJob.kt`: reject changed/missing validators **only** for rotated links carrying a strict guard; keep legacy ETag-missing/length-compatible resume for unchanged links. Clear guard only after successful strong identity revalidation, or after explicit partial reset.
- `.../HttpDownloadItem.kt`: serialized `requireStrongETagOnResume` flag with backward-compatible `false` default; copy state on edits, so a crash/restart cannot silently evade the rotated-link check.
- `downloader/core/src/desktopTest/kotlin/ir/amirab/downloader/downloaditem/http/HttpResumeIdentityPolicyTest.kt`: regression tests for signed token rotation, S3 tokens, changed selector/path/host, ETag missing/weak/changed, unknown length, legacy missing ETag compatibility and persistent flag serialization.
- `.github/workflows/private-windows-validation.yml`: temporary branch-only trigger to run real Windows Gradle tests/package/NSIS. This addition must be removed before any main merge.

## Exact commits and verification
- `20a5643842047d8c74074618d686216faf73a980`: create standalone production policy. Git blob `e65a71c0f318f2e2aa407da743a32d7e193ca57c`, matches the locally compiled Kotlin source.
- `e885472c10377be2f786ebc00dc20dcbd952b1e8`: original HTTP job integration.
- `e409978e813c4961a504a4208a2c6c1ee7e9f598`: first regression tests.
- `751e7e870972caa62a5c5dd81dfb3882452c25a3`, `ad441c9afdbe9b678bb4d269e8ce193ff5eb8a78`, `944e5b6ed1f9919fe46cfb88918651670c4d4d10`: persisted guard, opt-in validation compatibility, more regression tests.
- PASS (real isolated execution): `kotlinc HttpResumeIdentityPolicy.kt HttpResumeIdentityPolicySmoke.kt -include-runtime -d policy-smoke.jar && java -jar policy-smoke.jar` → **PASS 18/18 production-policy cases**. `git hash-object HttpResumeIdentityPolicy.kt` → `e65a71c0f318f2e2aa407da743a32d7e193ca57c` (same as GitHub).
- Full exact-head Windows Gradle/installed acceptance: **PENDING** on https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41993270292 (SHA `944e5b6ed1f9919fe46cfb88918651670c4d4d10`). Superseded earlier runs were cancelled by concurrency when newer commits arrived. Do not imply green until GitHub shows completed/success.
- Local git clone unavailable: `git ls-remote https://github.com/amroalhamedi2022-hue/personal-download-manager.git HEAD` → `Could not resolve host: github.com`. Direct GitHub App connector has `push=true`, and all repository writes used its create_branch/create_file/update_file actions instead. Local full Gradle/installer NOT RUN.

## Remaining work / acceptance gates
1. Read exact-head Windows Action logs and JUnit/NSIS result. Fix source/tests on branch if FAIL. Never accept cancellation as PASS.
2. Remove temporary validation branch from workflow; verify only test-trigger differs from main in workflow and only approved functional Kotlin + this checkpoint remain in final PR.
3. Inspect fresh main HEAD/PR mergeability and guarantee no conflict with Copilot/PR #3/PR #4 before any merge. Do not merge while Windows CI pending or if regression fails. Physical GUI end-to-end of rotating signed URL is still PENDING.
4. This improvement closes one F01-07 safety slice, **not** F01-07 in full; host-specific Retry P2-05 and F01-02 benchmark remain OPEN.
5. F01-02 historical latest compared run 41705173226 on PR #3: throughput delta -4.54%, Working Set +18.77%, Private Bytes +18.95% at 16 tasks (15% memory gate FAIL). Do not weaken benchmarks.
