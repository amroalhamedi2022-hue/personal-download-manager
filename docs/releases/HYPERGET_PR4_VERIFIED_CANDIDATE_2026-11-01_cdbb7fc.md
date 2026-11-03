# HyperGet — PR #4 verified Windows candidate (2026-11-01)

## Exact source identity
- Repository: `amroalhamedi2022-hue/personal-download-manager`
- Working branch: `experiment/liquid-reference-match-20261030`
- Pull request: #4, **Open / Draft** — not merged.
- **Verified CODE SHA:** `cdbb7fc89320bd9cfd07a3d32af89ee7f37ba931`
- GitHub Actions Windows installed GUI workflow: **[41344482096](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41344482096)**, conclusion **success**.

## Verified Windows EXE
- Name: `HyperGet_1.0.0-sha.gcdbb7fc_windows_x64.exe`
- [Download exact verified installer artifact ZIP](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41344482096/artifacts/13098658354)
- Installer SHA-256: `24feaa283cc32612996a2a888cf5ff67018043252b72cbfa7478e8c3a8b7431b`
- Uncompressed EXE size: 79,654,055 bytes.
- This is a **development validation candidate**, not a merged production release.

## Acceptance actually executed
- Kotlin desktop tests, including 10k mixed URL extractor, 10k home selection and visible-lookup regression, mixed Arabic/non-ASCII Windows Internet Shortcut URLs and 2048-character token/query preservation.
- Installed Windows app launched successfully; native main GUI smoke passed.
- Ctrl+F focused Search; native Windows keyboard Ctrl+V, Ctrl+A and Ctrl+C round-tripped a known loopback host through an STA seeded clipboard.
- A real local HTTP 16 MiB download ended in Finished status and matched expected SHA-256; screenshot and status report artifacts uploaded.
- Silent NSIS install, same-version reinstall and uninstall verified; idle CPU/RAM process metrics recorded.
- CI artifacts: [GUI screenshot](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41344482096/artifacts/13099126811), [real transfer visual evidence](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41344482096/artifacts/13098852547), [installer lifecycle](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41344482096/artifacts/13098932401), [idle performance sample](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41344482096/artifacts/13099181543).
- Compared with the older verified `56fd102`, this candidate incorporates dual-flavor external Windows drag priority, real payload re-read at onDrop, stable URL rendering under RTL, full Unicode `URL=` shortcut parsing, and linear-time home selection and category/queue membership.

## Not yet verified — do NOT mark complete
- Physical multi-file Explorer → installed HyperGet mouse gesture (the source and parser contracts pass, but native gesture outcome is unproven).
- Native Windows DPI scaling at 125%, 150% and 200% with visual/interaction proof.
- Full Arabic/RTL long-link pixel-by-pixel GUI acceptance and missing IDM P01–P27 human comparison references.
- Sustained 1,000/10,000 **live** downloads and resource stress; the 10k parser/list unit tests do **not** satisfy this.
- Remaining IDM-equivalence matrix, project roadmap, user acceptance, PR #4 merge and main integration.

## Continuation guard
Always fetch fresh HEAD / PR #4 / Actions before edits. Work from actual code and recent CI. Do not repeat accepted commits or copy from third-party HyperGet without explicit user approval. Do not touch main or PR #3; leave PR #4 Draft.
