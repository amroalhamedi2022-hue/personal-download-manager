"""Regression contract for full-PR-diff gating of installed HyperGet acceptance jobs."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
TEST_PATH = "scripts/source-tests/test_hyperget_pr_diff_scope.py"
SCOPE_WORKFLOWS = (
    ".github/workflows/windows-installed-native-host-e2e.yml",
    ".github/workflows/windows-installed-ui-smoke.yml",
    ".github/workflows/windows-cross-version-upgrade-rollback.yml",
)

class PullRequestDiffScopeContract(unittest.TestCase):
    def test_all_identity_acceptance_gates_compare_full_pr_diff(self):
        for relative in SCOPE_WORKFLOWS:
            with self.subTest(workflow=relative):
                source = (ROOT / relative).read_text(encoding="utf-8")
                self.assertIn("fetch-depth: 0", source)
                self.assertIn(
                    'git fetch --no-tags origin "+refs/heads/${GITHUB_BASE_REF}:refs/remotes/origin/${GITHUB_BASE_REF}"',
                    source,
                )
                self.assertIn('git diff --name-only "origin/$GITHUB_BASE_REF...HEAD"', source)
                self.assertNotIn('git diff --name-only HEAD^ HEAD', source)
                trigger_config = source.split("\npermissions:", 1)[0]
                self.assertIn(TEST_PATH, trigger_config)

    def test_manual_dispatch_still_runs_the_acceptance_gate(self):
        for relative in SCOPE_WORKFLOWS:
            with self.subTest(workflow=relative):
                source = (ROOT / relative).read_text(encoding="utf-8")
                self.assertIn("workflow_dispatch:", source)
                self.assertIn('echo "run=true" >> "$GITHUB_OUTPUT"', source)

    def test_push_scope_keeps_before_sha_handling_where_supported(self):
        for relative in SCOPE_WORKFLOWS:
            source = (ROOT / relative).read_text(encoding="utf-8")
            if "\n  push:" in source.split("\npermissions:", 1)[0]:
                with self.subTest(workflow=relative):
                    self.assertIn('before="$HYPERGET_EVENT_BEFORE"', source)
                    self.assertIn('git diff --name-only "$before" "$GITHUB_SHA"', source)

if __name__ == "__main__":
    unittest.main()
