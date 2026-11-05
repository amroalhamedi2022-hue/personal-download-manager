# HyperGet F01-06/F01-09 — HTTP retry + per-host policy concurrency (2026-11-05)

## Production code and regression
- `downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/RetryPolicy.kt` — strict RFC 9110 Retry-After IMF-fixdate/GMT parsing; reject mismatched weekday and trailing junk; clamp overflowed decimal seconds/date intervals; cycle-safe throwable cause traversal. Preserve exponential backoff and existing 15-minute maximum.
- `downloader/core/src/commonMain/kotlin/ir/amirab/downloader/utils/hostlimits/HostLimitRegistry.kt` — ensure rule publication and per-host gate reconfiguration are atomic with first-time host registration, preventing stale caps after concurrent configuration changes.
- New `RetryPolicyRobustnessTest.kt`: 5 tests for seconds, valid/past dates, malformed dates, overflowed clock and cyclic exception causes.
- New `HostLimitRegistryConcurrencyTest.kt`: 4 tests for live cap reduction, policy removal and waiter wake, cancelled waiters, and wildcard/exact precedence.
- Non-overlapping with Copilot PR #8/F03/F07-03, PR #4 visuals, and PR #3 engine/FTP/queue file diff. No visual or Android work.

## Verified Windows CI
- Isolated [PR #22](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/22), base main `a707d3d987603c38b29dd3754dbc76110f363bd8`.
- Exact production + test SHA `8ef55dc1f30811f7ed24a890ba6d0ec0ea1e2ec1`.
- [Windows Actions 42083837712](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/42083837712): **COMPLETED / SUCCESS**. Downloader core desktop Kotlin tests, desktop compile/tests, Windows package, real NSIS silent installation and user-safe uninstall, artifact upload all successful.
- Temporary feature-branch CI trigger removed in workflow-only commit `5176b98c438d5a135744ecdfc5e82e67acb36bb3`; restored workflow blob `0fd348e612abc1760662998f618887ae6e05070a` matches main. All four production/test file Git blobs were explicitly confirmed unchanged after workflow cleanup.
- Local Linux full Gradle build not run (GitHub dependencies unreachable via DNS); Windows GitHub acceptance is the real test evidence. Physical GUI manual acceptance still PENDING.
- Before merging, re-read GET /branches/main and PR mergeability, ensure unrelated concurrent automation changes do not overlap; require clean PR and use expected-head SHA. A post-merge main Windows run must be read independently, not assumed PASS.

## Outstanding
- F01-02 Windows installed 16-active throughput/memory benchmark remains FAIL historically (Working Set +18.77%, Private Bytes +18.95% relative to 15% threshold) on PR #3; no thresholds changed. Next safe work is evidence-backed memory root cause or selective tested PR #3 integration.
