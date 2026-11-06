# HyperGet range cursor guard — F01-02 / F01-08 (2026-11-06)

- Existing isolated PR #42 protects `splitToRange` against incrementing the last cursor when the final range can end at `Long.MAX_VALUE-1`. The production change does not alter configured part counts or minimum sizes.
- Kotlin regression checks sixteen contiguous valid parts for a `Long.MAX_VALUE`-byte file.
- Exact tested branch SHA: `dd371bfbb6f3327ad41e27ef6c75f581708270f9`.
- Windows [42178980765](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/42178980765): **completed/success**, including desktop Kotlin tests, desktop compile, Windows installer and safe-uninstall workflow.
- CI-only trigger removed after success, without changing the production/test Kotlin sources.
- F01-02 installed 1/4/16 throughput and Working Set/Private Bytes within 15%: **PENDING**, not proven by CI compile/tests. Manual GUI: **PENDING**.
- No themes, Liquid Glass, Copilot-owned code or bulk PR3 changes included.
