# HyperGet OpenCode + Gemini unattended pilot

Status: **proposed and opt-in**, not deployed until the setup PR is merged.
Scope: **Windows functionality only**. No Codex, ChatGPT Work, paid OpenAI
API, automatic merging, theme work, engine migrations, or release publishing.

## What is already wired

- `.github/workflows/hyperget-autodev-pilot.yml` offers a **dry-run**
  (configuration preflight, no AI) and an **implement** trial.
- The AI implementation task is intentionally small and fixed in
  `docs/autodev/PILOT_TASK.md`: one HTTP 429 / HTTP-date Kotlin regression.
- Only that test file may change. The runner refuses unknown, extra or
  unrelated modifications; the AI step does not receive a GitHub token.
- A successful implementation run commits source on a new, isolated branch,
  attempts to create a **draft PR**, then dispatches `private-windows-validation.yml`
  on the exact branch. GitHub may deny PR creation to `GITHUB_TOKEN` despite
  accepting the code push. In that case the branch and CI still exist, and a
  human or the ChatGPT GitHub connector must create the PR. Never claim it
  exists without a link. **Dispatch does not prove Windows acceptance.**
- The 6-hour schedule is inert unless repository variable
  `HYPERGET_AUTODEV_ENABLED` is exactly `true`. Keep it disabled for
  the pilot. Repeated runs are blocked while a pilot PR is open, and the
  already-implemented named test is a no-op (no AI credits).
- The model is `google/gemini-3-flash-preview`, using
  `DEFAULT_GEMINI_API_KEY` from GitHub Actions Secrets. It can be free only
  within the Gemini API free-tier quota for the user's project and region.
  Do not enable a billed API project for this experiment.

## One-time activation (owner)

1. Review and merge the separate **setup** PR only after its preflight
   passes. This step alone changes no application code.
2. Obtain your own key at https://aistudio.google.com/api-keys from a
   free-tier eligible project. Never paste it into chat or any committed file.
3. Repository Settings > Secrets and variables > Actions > New repository
   secret: name `DEFAULT_GEMINI_API_KEY`, value the key. No PAT is necessary.
   The workflow maps this secret to OpenCode's `GOOGLE_GENERATIVE_AI_API_KEY` environment variable.
4. Actions > **HyperGet OpenCode unattended pilot** > Run workflow:
   select branch `main`, mode `dry-run`. Confirm the preflight is green.
5. Run the workflow again with mode `implement`. Confirm **one draft PR**
   and the specifically dispatched **Private Windows validation** workflow
   on the generated `autodev/pilot-...` branch.
6. Review the Kotlin diff, exact branch SHA, Windows tests, installer safety,
   and reports; only then manually approve/merge the implementation PR.
   If a run fails or the PR cannot be created because of repository settings,
   inspect its logs and permissions instead of reporting success.

GitHub Actions must be allowed to create pull requests at the repository
or organization policy level for step 5. Do not remove the safeguards to
force it through; ask the owner to change that explicit repository setting
if GitHub denies it.

## Extension after the pilot

Replace the fixed regression with a curated, ID-based backlog and a
lease/claim mechanism. Select tasks from authoritative functional roadmaps
and verify latest main/PRs first. Run Windows acceptance on exact source SHA,
recover from errors, bound token and CI consumption, and preserve strict
separation from Copilot PRs and Liquid Glass. Only then consider enabling
`HYPERGET_AUTODEV_ENABLED=true` or creating equivalent Android/Colab runners.

**Do not represent a green preflight or green AI workflow as a finished
HyperGet feature. A new PR and exact Windows acceptance evidence are required.**
