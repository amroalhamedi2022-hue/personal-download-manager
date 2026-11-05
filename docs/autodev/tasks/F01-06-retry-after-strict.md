# F01-06: Strict HTTP Retry-After date parsing with bounded delay

Modify ONLY:
1. downloader/core/src/commonMain/kotlin/ir/amirab/downloader/downloaditem/RetryPolicy.kt
2. downloader/core/src/desktopTest/kotlin/ir/amirab/downloader/downloaditem/RetryPolicyStrictTest.kt (create it)

First inspect RetryPolicy.kt and existing DownloadCoreBaselineTest retry tests.
Keep the existing capped exponential backoff and server-delay semantics, without
changing max bounds. Fix the HTTP-date parser so a date with trailing garbage
is not partly parsed as valid, only a valid complete IMF-fixdate is accepted,
negative and overflowed delta-seconds do not cause overflow or exponential
backoff bypass. Parse with strict Locale.US and GMT/UTC semantics, not local
time. Test a fixed nowEpochMillis for deterministic behavior. Existing valid
date and delta-seconds syntax must remain compatible.

MUST include a test named exactly:
  fun retryAfterDateRejectsTrailingJunk()

Cover future/past dates, whitespace, malformed strings and extreme numbers.
Do not modify any other path, thresholds, model configuration, UI, colors,
themes, or benchmark acceptance logic. Windows acceptance is required before
any merge. Code and tests both required, not report-only.
