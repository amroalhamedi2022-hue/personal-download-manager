# HyperGet F01-02 — host permit carries the resolved limiter (2026-11-06)

- Production: `1e9eb7d879e9c515244571ef43f8331dc5c37339` extends `HostConnectionPermit` to retain its existing effective host `SpeedLimiter`, and `299852a38a845c9ccf650904105c191cbb8c891c` reuses that limiter in `HttpPartDownloader`. Each HTTP range request no longer parses/resolves its host URL again to obtain the identical limiter; no new Pair allocation.
- Regression: `78a9d83729089f24ec6d1e92add33115cd22dffc` adds 3 Kotlin tests covering sixteen concurrent permits sharing a limiter, live host policy changes retaining the same limiter object, and an invalid URL returning no host permit.
- Exact source/test/CI-trigger SHA: `9508b7fc0cd30f4972d08ba5f4e49049a636396e`.
- Windows [42181140156](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/42181140156): **completed/success** on that SHA; Kotlin tests, desktop compilation, Windows package and installed safe-uninstall tests passed.
- The branch-only Windows workflow trigger is removed after this exact code/test PASS.
- **Not proven**: installed 1/4/16 active benchmark throughput/Working Set/Private Bytes within the 15% performance regression threshold. Manual GUI acceptance is **PENDING**.
- No themes, Liquid Glass, icons, Copilot-owned code, PR3 FTP/queue or PR4 changes.
