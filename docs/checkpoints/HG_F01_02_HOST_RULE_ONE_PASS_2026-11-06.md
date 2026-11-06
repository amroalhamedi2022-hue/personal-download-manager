# HyperGet F01-02 — host-rule allocation reduction (2026-11-06)

Scope: Windows-only core functionality; no Liquid Glass, theme, icon, color, F03/F07-03, PR3, PR4, or Copilot changes.

- Base `main` when branched: `fdfc22cd832f277f8c2ecb9b4d1063584c9d9641`.
- Production commit: `18565e0224c0515bcabf3e5517dccbea304b2422` — remove matched-rule sequence/filter/sort allocations in `HostLimitRegistry.resolveLimits` while retaining wildcard-count ASC, pattern-length DESC, lexical ASC precedence.
- Regression commit: `c4a5c8961a939aec4f1fbfea0c3375194a773ab4` — three Kotlin priority tests for wildcard count, pattern length, and lexical tie.
- Exact code/test + one-time CI trigger SHA: `9e3c0f4e32871c8925dd28a3f2cc505841e932a4`.
- Windows GitHub Actions [42178593180](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/42178593180): **completed/success** on that exact SHA. Workflow runs `:downloader:core:desktopTest`, desktop compilation, package, NSIS install/uninstall acceptance.
- **F01-02 memory/throughput gate is NOT proven**. A successful Kotlin and Windows packaging workflow cannot establish 1/4/16 active installed memory and throughput within 15%. Keep that acceptance PENDING until the paired benchmark is completed with full SHA evidence.
- Manual installed GUI tests: **PENDING**.

Source and tests were not changed by this checkpoint. Restore the default Windows workflow trigger before merging.
