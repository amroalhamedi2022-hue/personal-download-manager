# HyperGet F01-02 — per-HTTP-connection limiter wrapper allocation (2026-11-06)

- Production `50762b7af4b8d26f2fb335d5642801331bac080e`: retain all job-speed limiters, then outer host-speed limiter and permit-close semantics, but stop allocating `buildList` for every HTTP range connection.
- Initial Kotlin regressions `254ebae0f78461b05c35977699aecb92b30b2ab3`: sixteen concurrent independent sources with shared host limiter; underlying transport closes once; no limiter returns original source.
- Windows `42178119164`, SHA `7d5f1275309620b5d9bcfa3c1d044b6ecdf7c1ab`: **failure**, new JUnit test method returned `List<Unit>` and JUnit4 rejected the test class as an invalid non-void test. Not a production test failure.
- Test repair `0954a77637602c2cd07d666f30f5cc2bbd37bbdc`: return Unit from the 16-worker regression method.
- Windows `42178757281` [run](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/42178757281): **completed/success** on exact production + repaired Kotlin tests SHA `0954a77637602c2cd07d666f30f5cc2bbd37bbdc`. Workflow checks `:downloader:core:desktopTest`, monitor/shared desktop tests, desktop compile, Windows package and safe installer/uninstaller.
- **NOT PASSED:** F01-02 installed 1/4/16 active throughput, Working Set, Private Bytes within 15% versus baseline; requires paired installed benchmark artifacts. GUI manual testing **PENDING**.
- No Copilot, PR3, PR4, themes, colors or icons changed. CI branch trigger is temporary and must be restored.
