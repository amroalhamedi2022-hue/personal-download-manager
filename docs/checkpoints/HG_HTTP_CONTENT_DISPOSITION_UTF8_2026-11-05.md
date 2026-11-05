# HyperGet F01-08 — International HTTP Content-Disposition filenames (2026-11-05)

- Isolated PR [#28](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/28). Base main source SHA `f6c2bc6eb1a25b4a4499a66fe78f1dcc953abf2c`.
- Production `downloader/core/src/commonMain/kotlin/ir/amirab/downloader/connection/response/headers/fileNameExtractor.kt`: recognize standards-compliant RFC 8187 `filename*=UTF-8'ar'...` language subtags, support whitespace around parameter equals, anchor parameter names to prevent `x-filename` spoofing, preserve ASCII fallback and quoted semicolons.
- Regression `downloader/core/src/desktopTest/kotlin/ir/amirab/downloader/connection/response/headers/ContentDispositionFileNameTest.kt`: 5 Kotlin desktop tests for Arabic filename, language tag, ASCII fallback, extension spoof and quoting. No themes, visual changes, PR #3, Copilot scope, or Android UI changes.
- Source/test SHA for Windows: `0b4e230c8e280d7e7c2791a01b086ba25eb400c9` (temporary branch-only workflow trigger).
- [Windows CI 42085946398](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/42085946398) **attempt 1 FAILED**: 204 desktop tests, 1 failure in pre-existing `RestartPersistenceTest.concurrentQueueCreationAndMovesDoNotDuplicateIdsOrMembership` at line 173, a known intermittent queue test unrelated to changed files. Do NOT hide this as PASS. Attempt 2 rerun on the exact SHA was requested via GitHub Actions; final conclusion must be checked before merge.
- Before merging: require completed/success on same source/test blobs; remove temporary workflow trigger and verify blob equals main; re-read latest main HEAD and PR mergeability; manual GUI acceptance remains pending.
- F01-02 16-active memory benchmark still historically FAIL; do not change the 15% threshold.

## Exact acceptance after rerun and workflow cleanup
- Run [42085946398](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/42085946398) **attempt 2 COMPLETED/SUCCESS**, exact source/test SHA `0b4e230c8e280d7e7c2791a01b086ba25eb400c9`: `:downloader:core:desktopTest` and other Kotlin/desktop tests, Windows package, real NSIS silent install/uninstall and proof artifacts all passed.
- Attempt 1's queue concurrency test failure is retained above and remains an intermittent test issue owned by separate PR #3. No test or performance thresholds were reduced to obtain PASS.
- Temporary branch-only CI trigger removed in workflow-only commit `3cfc96f3104e6b8cd150ca60801aa49594bb8ec5`; restored workflow blob `0fd348e612abc1760662998f618887ae6e05070a` matches main exactly. The two production/test file blobs were already verified identical to the tested SHA after docs-only checkpoint.
- Merge only after fresh GET /branches/main and PR mergeable/clean check. Manual GUI acceptance PENDING.
