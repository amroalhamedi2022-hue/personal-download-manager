# HyperGet F01-02 — system proxy client reuse (2026-11-06)

- Production `5416359f44f2e257b4dbb48943e86707a95acb4c`: when an OkHttpClient already has the current effective system `ProxySelector` by identity, reuse it rather than rebuilding a new client for each parallel HTTP range request. If that selector changes, create a new client with the new selector, inheriting dispatcher and connection pool.
- Regression `5364d3af0d0a0ce07a9220fe4b6f6347a70292ea`: sixteen unchanged-selector lookups reuse the same client, while a genuinely new selector rebuilds and retains shared connection infrastructure.
- Exact source/test + one-time CI trigger SHA: `0259567c11eadc27ae47380e0d25a6c3da56e0ee`.
- Windows [42179844149](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/42179844149): **completed/success** on that exact SHA. Includes Kotlin desktop tests, Windows compile, package and safe uninstall.
- NOT claimed: 1/4/16 installed throughput or memory within 15%; those require paired benchmark evidence. Physical GUI acceptance remains **PENDING**.
- Safe scope: no theme, visual, Copilot, PR3 FTP/queue or PR4 changes. The Windows branch-only trigger must be removed after successful validation.
