# HyperGet — Windows functionality acceptance checkpoint (2026-11-06)

Scope: Windows download core and correctness only. Does not approve Liquid Glass, UI parity, Copilot-owned features, PR #3 FTP/queue bulk merge, or an Android release. All Windows run links are exact-code evidence from GitHub Actions, **not** manually installed GUI acceptance.

| PR | Functional change | Windows exact tested SHA | Windows run | Exact-code result | Main squash SHA |
| --- | --- | --- | --- | --- | --- |
| #38 | Pre-size split-to-range list for the 16-connection case; bound initial allocation; 3 regressions | `146d3082fe131bd224a63c0a7db7a61f1751a5e3` | [42170040994](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/42170040994) | completed/success | `607e125788013fe6704a2dee567e423858b7a73a` |
| #39 | Deep-copy mutable HTTP ranged parts and HLS media segments in in-memory persistence snapshots; 4 tests including 16 parallel readers | `a3de6c00562ae6a2a1b50fb1cf12ec417dff076e` | [42174587922](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/42174587922) | completed/success | `c4bca7e39dabcaf5ab6adfdc710c70828f8adbbd` |
| #29 | Reject control characters and malformed OWS in HTTP Retry-After | `6cffd6668aab60964c529e3702cb59bdb1a35d92` | [42174739554](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/42174739554) | completed/success | `2ef0964345c75a4412e8cfb659677d2ff787ebe4` |
| #30 | Safe idempotent removal of absent in-memory download entries | `8c288f8036fb0e5db4878ebaa71a5e4b07b72f99` | [42174735764](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/42174735764) | completed/success | `b8615d1d7d878853bfcd9dc1f00793bcd1fb120c` |
| #31 | Consistent active job registry snapshot; count 16 concurrent active jobs without allocating filtered list | `1297de3ef375bdbc4290d99f078922ee4b70b777` | [42174742526](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/42174742526) | completed/success | `e50ee3390008738b0e73efb4aba3d383ebc1d466` |

Validation performed by the shared Private Windows validation workflow (same tested Kotlin before branch trigger removal):
```sh
./gradlew --no-daemon --stacktrace --dependency-verification strict \
  :downloader:core:desktopTest \
  :downloader:monitor:desktopTest \
  :shared:app:desktopTest \
  :desktop:app:compileKotlin \
  :desktop:app:test
./gradlew --no-daemon --stacktrace --dependency-verification strict createReleaseFolderForCi
```
Workflow additionally runs Windows NSIS installed safe-uninstall check and uploads package/proof artifacts. Branch-only CI workflow modifications were reverted to the baseline blob `0fd348e612abc1760662998f618887ae6e05070a` *after* each exact-code Windows success; no intervening source/test Kotlin changes.

Open acceptance: **F01-02 is NOT closed**. Historical PR #3 16-active performance regression (Working Set +18.77%, Private Bytes +18.95%, threshold +15%) remains unproven fixed; require real 1/4/16 × 3 comparison and throughput/CPU/IO evidence. These unit regressions and Windows packaging runs are not a substitute. All manual installed GUI checks remain PENDING. New PR #40 ranged-part large-number progress regression requires separate exact-source Windows acceptance before merge; do not mark passed from this checkpoint.

Next: verify integrated main SHA on Windows; complete PR #40 Windows tests; isolate real PR #3 16-active benchmark bottleneck without bulk-merging the divergent FTP/queue tree. Never modify PR #4 visuals or Copilot-owned F03/F07/PR #8/Issue #11.
