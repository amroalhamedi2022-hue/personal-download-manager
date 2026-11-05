# HyperGet — unattended OpenCode / Gemini functionality runner

Status: **bounded functionality queue, reviewed changes only**.
Scope: **Windows downloader correctness**, not themes, Liquid Glass, Copilot-owned
PRs, aria2 engine switching, app releases, or financial data.

## Evidence that the pilot actually worked

- First autonomous Kotlin test commit: `6e2b94d753fe84d2b63fa4729ddd2d50f8d3b542`.
- Full exact-SHA Windows Kotlin compile, tests, installer and safe NSIS acceptance:
  https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41998366784
  (**SUCCESS**, 2026-11-05).
- Real code PR #18 merged; pilot setup and policy fixes in PR #14/#15/#19.
- GitHub's default Actions token can **push branches** but cannot always create
  PRs. The runner preserves the new branch and exact code SHA even if that
  API call is denied; someone must create the PR manually or via GitHub connector.

## New real workflow

`.github/workflows/hyperget-autodev-real.yml`:

1. Every 6 hours, or by manual **implement** dispatch, inspect a finite curated
   backlog in `docs/autodev/task_runner.py`. A normal PR triggers only the
   **free preflight**, not model execution.
2. Skip completed acceptance-test markers. If a pending task has an existing
   `autodev/real-<task-id>` branch, **stop** to avoid duplication and token
   waste. Do not move to the next task until its predecessor is integrated.
3. Verify `DEFAULT_GEMINI_API_KEY` exists; reserve an isolated durable
   branch **before** using Gemini. Failed attempts leave that branch as a
   visible lease; subsequent schedules cannot silently re-bill the model.
4. Run pinned OpenCode with `google/gemini-3-flash-preview`. No Copilot,
   Codex, Work or paid OpenAI API. The free Gemini quota is external, limited,
   and must be verified on the user's AI Studio project.
5. Demand exactly the approved **production Kotlin file and regression-test
   file**. Reject any extra source changes; tolerate EOL-only differences in
   two legacy Windows .bat scripts without committing them. The selected
   test's exact marker must exist.
6. Push one real code commit, attempt a **draft PR**, and explicitly dispatch
   `private-windows-validation.yml` against the produced branch. **A CI
   dispatch is not proof of passing**, and no automatic merge occurs.

### Approved backlog (2026-11-05)

- `f01-08-content-range`: validate HTTP Content-Range bounds, overflow and
  malformed responses (production code + desktop regression suite).
- `f01-06-retry-after-strict`: strict Retry-After HTTP-date validation and
  integer overflow safety (production code + desktop regression suite).

Each has a fixed exact-path prompt under `docs/autodev/tasks/`. More work
requires explicitly adding another reviewed task; it must never invent a
high-risk feature to use up idle AI capacity. If all tasks are done, the
scheduled run exits successfully **without an API call**.

## Operation and safety

- GitHub Actions → **HyperGet autonomous functionality (bounded Gemini)** →
  Run workflow on `main`, mode `dry-run` for no-API preflight, or mode
  `implement` to attempt one real task.
- The six-hour schedule is active once merged into `main`; disable the
  workflow from GitHub Actions if you wish to stop scheduled execution.
- Secret: GitHub repository Actions Secret `DEFAULT_GEMINI_API_KEY`. Never
  reveal or commit it, and do not enable billing unless independently intended.
- Follow each branch's Windows run. Review patch, tests, exact SHA, Windows
  installer and safe-uninstall proof before manually merging a draft PR.
- Failed Kotlin/CI, GitHub denial, unavailable model, missing secret or quota:
  report exact blocker without claiming an implemented/accepted feature.
- The source and acceptance files on current `main` override older handoffs;
  never change Liquid Glass / PR #4 or Copilot PR #8, nor bulk-merge PR #3.
- To rerun a blocked task after fixing a real error, inspect and deliberately
  delete/reconcile its reserved branch first; do not force-push or silently
  rerun model calls.

The former `hyperget-autodev-pilot.yml` is preserved as a manual historical
smoke-test only; it is no longer the recurring development workflow.
