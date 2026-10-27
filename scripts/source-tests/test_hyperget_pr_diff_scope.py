"""Guard installed Windows acceptance scope against latest-commit false skips."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
WORKFLOWS = {
    "active HTTP benchmark": ROOT / ".github/workflows/windows-installed-active-benchmark.yml",
    "JVM memory profile": ROOT / ".github/workflows/windows-installed-memory-profile.yml",
    "installed FTP": ROOT / ".github/workflows/windows-installed-ftp-e2e.yml",
    "cross-version rollback": ROOT / ".github/workflows/windows-cross-version-upgrade-rollback.yml",
    "Chrome and Firefox native host": ROOT / ".github/workflows/windows-installed-native-host-e2e.yml",
    "installed GUI smoke": ROOT / ".github/workflows/windows-installed-ui-smoke.yml",
}
WORKFLOW_TEST_PATHS = (
    ".github/workflows/windows-cross-version-upgrade-rollback.yml",
    ".github/workflows/windows-installed-native-host-e2e.yml",
    ".github/workflows/windows-installed-ui-smoke.yml",
)


class FullPrDiffScopeContract(unittest.TestCase):
    def test_expensive_acceptance_gates_use_full_pr_diff(self):
        expected_diff = 'changed="$(git diff --name-only "origin/$GITHUB_BASE_REF...HEAD")"'
        for name, path in WORKFLOWS.items():
            with self.subTest(workflow=name):
                text = path.read_text(encoding="utf-8")
                self.assertIn("fetch-depth: 0", text)
                self.assertIn(expected_diff, text)
                self.assertNotIn("git diff --name-only HEAD^ HEAD", text)
                self.assertIn("HYPERGET_EVENT_BEFORE: ${{ github.event.before || '' }}", text)

    def test_scope_contract_file_triggers_changed_acceptance_workflows_and_fast_gate(self):
        for relative_path in WORKFLOW_TEST_PATHS:
            with self.subTest(workflow=relative_path):
                text = (ROOT / relative_path).read_text(encoding="utf-8")
                self.assertIn('scripts/source-tests/test_hyperget_pr_diff_scope.py', text)
        source = (ROOT / ".github/workflows/hyperget-source-only.yml").read_text(encoding="utf-8")
        self.assertIn('scripts/source-tests/test_hyperget_pr_diff_scope.py', source)
        self.assertIn("python3 -m unittest scripts/source-tests/test_hyperget_pr_diff_scope.py -v", source)


if __name__ == "__main__":
    unittest.main()
