# HyperGet F01-07 — malformed ETag resume hardening (2026-11-05)

- Main baseline read from GitHub GET /branches/main: `70f52b6f20b4c9bbbb147f861e2082d29927e204`.
- Main post-PR13 Windows validation [41995042974](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41995042974): **completed/success** on `046fcd6e24d46ddff2a4ae17bf75bcb3b1c05abf`.
- Isolated PR [#17](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/17) changes only HTTP resume validator policy, its desktop Kotlin regression, this checkpoint and temporary branch CI trigger. No Copilot F03/F07-03, PR3 FTP/queue, PR4 visual changes.
- Production commit [334a269](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/334a269c845021be98ed0474ce943e256bc382f0): reject malformed strong ETag values containing embedded quote, control characters or DEL; prevents ambiguous server validators authorising signed-link partial reuse.
- Tests commits [5218819](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/5218819d3bfc14db4e43c48b75f18048ae78a90b) and [c0c8d5a](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/c0c8d5ad311c6ec0e7cbee9b591d7946c497c3d4): six malformed ETag cases, policy refusal and strict remote revalidation rejection, plus valid control case.
- Exact Windows workflow candidate SHA `10e1c60498a4b2d2b6411dfa91ca28721d538e86`, [run 41997550655](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41997550655). **Check completed conclusion before acceptance**; no PASS claimed while in progress.
- Before merge: require completed/success Windows Kotlin tests, desktop compilation, installer and NSIS safe install/uninstall; remove temporary branch trigger, verify production/test SHA unchanged, re-read main HEAD and mergeability. Manual installed GUI acceptance pending.
- F01-02 active16 Working Set +18.77% / Private Bytes +18.95% historical benchmark FAIL remains unresolved; no threshold relaxation. PR3 queue intermittent test failure remains independent.

## 2026-11-05 follow-up (automation)
- Windows validation [41997550655](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41997550655) was previously observed **completed/success** on production/test SHA `10e1c60498a4b2d2b6411dfa91ca28721d538e86`; do not equate workflow-only changes with a new tested source SHA.
- Removed temporary feature-branch CI trigger in workflow-only commit [`6407681`](https://github.com/amroalhamedi2022-hue/personal-download-manager/commit/6407681fcee4e0439987237c490c45829d360eaf). Kotlin source and tests unchanged.
- GitHub compare `main...feature/http-etag-validator-hardening-20261105` reports main base SHA `36da525d557cb67ac64c1900a2a65f0ee417dd31`, **diverged: ahead 6, behind 1**; PR metadata reports **mergeable=false**. Do not merge until reconciled, exact mergeability and Windows acceptance confirmed. Manual installed GUI acceptance remains pending.
- No new F01-02 active16 fix in this follow-up; historical memory regression remains FAIL.
