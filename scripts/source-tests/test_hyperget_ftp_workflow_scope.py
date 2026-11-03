"""Source contract for full-PR-diff scoping of installed-Windows FTP acceptance."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
FTP_WORKFLOW = (ROOT / ".github/workflows/windows-installed-ftp-e2e.yml").read_text(encoding="utf-8")
SOURCE_WORKFLOW = (ROOT / ".github/workflows/hyperget-source-only.yml").read_text(encoding="utf-8")


class InstalledFtpWorkflowScopeContract(unittest.TestCase):
    def test_installed_job_is_gated_by_full_pr_diff_not_only_latest_commit(self):
        self.assertIn("  change-scope:", FTP_WORKFLOW)
        self.assertIn("needs: [fixture-contract, change-scope]", FTP_WORKFLOW)
        self.assertIn(
            "if: needs.change-scope.outputs.run == 'true' || github.event_name == 'workflow_dispatch'",
            FTP_WORKFLOW,
        )
        self.assertIn('changed="$(git diff --name-only "origin/$GITHUB_BASE_REF...HEAD")"', FTP_WORKFLOW)
        self.assertIn("fetch-depth: 0", FTP_WORKFLOW)
        self.assertNotIn("git diff --name-only HEAD^ HEAD", FTP_WORKFLOW)
        self.assertIn("HYPERGET_EVENT_BEFORE: ${{ github.event.before || '' }}", FTP_WORKFLOW)
        self.assertIn('changed="$(git diff --name-only "$before" "$GITHUB_SHA")"', FTP_WORKFLOW)
        self.assertIn('if [[ "$GITHUB_EVENT_NAME" == "workflow_dispatch" ]]; then', FTP_WORKFLOW)
        case_block = FTP_WORKFLOW.split('case "$file" in', 1)[1].split("esac", 1)[0]
        self.assertIn("scripts/Run-WindowsInstalledFtpE2E.ps1", case_block)
        self.assertIn("downloader/core/src/**/ftp/**", case_block)
        self.assertIn(".github/workflows/windows-installed-ftp-e2e.yml", case_block)
        self.assertIn("desktop/app/resources/installer/nsis-script-template.nsi", case_block)
        self.assertIn("desktop/app/build.gradle.kts", case_block)
        self.assertIn("scripts/source-tests/test_hyperget_executable_identity_workflows.py", case_block)
        self.assertNotIn("docs/", case_block)

    def test_fast_source_gate_runs_this_workflow_contract(self):
        self.assertIn('scripts/source-tests/test_hyperget_ftp_workflow_scope.py', SOURCE_WORKFLOW)
        self.assertIn('.github/workflows/windows-installed-ftp-e2e.yml', SOURCE_WORKFLOW)
        self.assertIn(
            "python3 -m unittest discover -s scripts/source-tests -p test_hyperget_ftp_workflow_scope.py -v",
            SOURCE_WORKFLOW,
        )


if __name__ == "__main__":
    unittest.main()
