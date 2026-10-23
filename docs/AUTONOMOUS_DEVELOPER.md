## Canonical brand decision: HyperGet (2026-10-23)

The user approved **HyperGet** as the only current and future user-visible name for this project. **READ [HYPERGET_REBRAND_PLAN.md](HYPERGET_REBRAND_PLAN.md) BEFORE ALL NEW WORK, PR TITLES, ACCEPTANCE WRITING, UI CHANGES AND FINAL DELIVERABLES.** Use HyperGet consistently in all future feature tickets and outputs. “IDM” refers only to the original comparison target or historical source naming. Main repo/branch/PR IDs stay unchanged; browser extension changes must happen in its separate repo without breaking IPC/data. Brand HG-01–HG-05 is future work and cannot be marked done until real Windows upgrade and exact-SHA tests. This note modifies documentation only, not source code.

# Autonomous Developer Contract — Personal Download Manager
Project repo: amroalhamedi2022-hue/personal-download-manager
Authoritative working base: `codex/repair-rf00-rf01`

## Approved outcome
Repair and complete the existing Windows IDM-like app on codex/repair-rf00-rf01. Read docs/WORK_HANDOFF_IDM_2026-10-23.md, docs/Download_Manager_Execution_Plan_v1.md and latest checkpoints before edits. Resolve current compiler/test failures first. Verify 1/4/16 real concurrency, SHA256, resource use, FTP, HTTP/HTTPS/HLS, browser extension integration and faithful IDM Liquid Progress/settings against approved assets when available. Single verified EXE; no Torrent. Browser integration is a different repo: do not modify its contents from this workflow; identify cross-repo changes as blockers.

## Mandatory development loop
1. On each authorized run, inspect actual files, authoritative handoff, current branch, last PR and CI results. Never restart the project or invent progress. Do not treat READMEs as more recent than verified commits.
2. Identify the highest-priority **unmet requirement** from established user instructions and the repository's own plan. First inspect reputable maintained open-source solutions and their compatibility, license, security and actual implementation rather than rewrite blindly.
3. Make a bounded, useful production-code + test change in **this repository only**. Avoid cosmetic-only revisions, fake implementations and meaningless tests. Do not edit GitHub Actions or credentials/autonomous runner permissions, do not publish releases or change repo visibility.
4. Execute available relevant unit/integration/static/compile tests within this runner. If platform SDK/Windows/real-device verification is impossible, record it explicitly as NOT TESTED. Never describe old CI results as testing the current SHA.
5. Maintain `docs/AUTONOMOUS_ACCEPTANCE.md` recording each user's requirement as PASS / FAIL / NOT TESTED / BLOCKED with exact test command, SHA, CI URL where available, remaining tasks, and the next bounded development action. Preserve prior historical evidence. Always record whether this run actually changed production source.
6. Once you suspect ALL requirements are met, change `AUTOMATION_COMPLETE: CANDIDATE` in that status file. Do **not** write VERIFIED yourself. A separate second-pass agent invocation will recheck from scratch.
7. Never certify finished if target-device/app startup, real UI, user workflow, reliability, data safety, relevant security and resource acceptance have not been actually demonstrated. Do not make up missing test results.
8. Do not modify another project's repo, auto-merge, force-push, make public releases, send private assets to public repos, or ask the user to perform routine coding. In real approval/security/access/device blockers, document the exact minimum required action.

## Double-check gate
For CANDIDATE, independent recheck must separately inspect: exact user requirements vs actual code/tests, real build output, test log links, negative/error handling, end-to-end functional workflows, visual parity when reference available, credentials/privacy, regressions, and the actual platform/device. Only if every mandatory requirement has direct proof may a second pass write `AUTOMATION_COMPLETE: VERIFIED`; otherwise it must write `AUTOMATION_COMPLETE: BLOCKED` and specific failures. When VERIFIED, refrain from further feature changes and report the evidence; final public releases require user approval.

## Free ChatGPT development task (replaces billed API workflow)
The `.github/workflows/autonomous-development.yml` file was removed on 2026-10-23 because the user requires **no additional charges**. **Do not** create an OpenAI API key, enable paid Codex Actions, or start chargeable services. The project is assigned to a scheduled ChatGPT development task which, when GitHub tools are available to its runs, should inspect the repo and make bounded source+test fixes through isolated GitHub branches/PRs. Use existing free-tier GitHub Actions prudently; a successful build is not proof of device acceptance. If unattended runs cannot execute source changes due to session/tool limits, report the precise blocker; do not invent commits or background execution. Keep acceptance evidence in `docs/AUTONOMOUS_ACCEPTANCE.md` when meaningful.
