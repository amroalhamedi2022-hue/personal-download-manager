# E060 — Exact-head active Windows performance recheck

- Candidate source SHA: `0b4bd363997353cd530a9824625d5baf87f74dca`
- Target: existing `windows-installed-active-benchmark.yml` workflow.
- Question: does the installed Windows GUI/CLI pass 63 real HTTP downloads across 1/4/16 concurrency (three rounds each), exact SHA-256 checks, live socket-overlap gates, and nine CPU/RAM samples?
- Expected: all existing gates pass without loosening thresholds; the report and samples carry the actual checkout SHA.
- Source-only tests are insufficient because the milestone depends on Gradle/Compose/NSIS build, installed Windows processes, live sockets, actual bytes, and OS resource measurements.
- This is an internal acceptance build, not a release. Do not use a single run to claim a performance improvement; a controlled baseline/candidate comparison is still required.
- The prior run `40432508000` skipped the benchmark job on a docs-only checkpoint; a skipped job is not evidence.
- No downloader production source is changed by this checkpoint.
