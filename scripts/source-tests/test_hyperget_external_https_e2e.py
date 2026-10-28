"""Source contract for the installed external HTTPS + Range acceptance gate."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW_PATH = ".github/workflows/windows-installed-external-https-e2e.yml"
SCRIPT_PATH = "scripts/Run-WindowsExternalHttpsE2E.ps1"
TEST_PATH = "scripts/source-tests/test_hyperget_external_https_e2e.py"
WORKFLOW = (ROOT / WORKFLOW_PATH).read_text(encoding="utf-8")
SCRIPT = (ROOT / SCRIPT_PATH).read_text(encoding="utf-8")
SOURCE_ONLY = (ROOT / ".github/workflows/hyperget-source-only.yml").read_text(encoding="utf-8")


class HyperGetExternalHttpsAcceptanceContract(unittest.TestCase):
    def test_external_endpoint_uses_normal_https_validation_and_is_not_mislabelled_signed(self):
        self.assertIn("https://httpbin.io/range/10485760", SCRIPT)
        self.assertIn("?hg_external_https_e2e=1", SCRIPT)
        self.assertNotIn("-SkipCertificateCheck", SCRIPT)
        self.assertIn("default TLS validation", SCRIPT)
        self.assertIn("SignedTokenExpiry=NOT_TESTED", SCRIPT)
        self.assertIn("not a provider-specific signed-token expiry test", SCRIPT)

    def test_external_range_is_real_206_and_matches_reference_prefix(self):
        self.assertIn("@{ Range = 'bytes=0-1023' }", SCRIPT)
        self.assertIn("$rangeStatus -ne 206", SCRIPT)
        self.assertIn("$rangeBytes -ne 1024", SCRIPT)
        self.assertIn("^bytes 0-1023/10485760$", SCRIPT)
        self.assertIn("$rangeHash -ne $rangePrefixHash", SCRIPT)

    def test_installed_hyperget_download_requires_exact_size_and_sha256(self):
        for required in (
            "createReleaseFolderForCi",
            "BaseName -eq 'HyperGet'",
            "BaseName -eq 'DownloadManager'",
            "BaseName -eq 'DownloadManagerCli'",
            "download add http --link $externalUrl",
            "ExpectedSHA256=$expectedHash",
            "ActualSHA256=$actualHash",
            "Get-FileHash",
            "10485760",
            "Start-Sleep -Seconds 2",
            "SignedTokenExpiry=NOT_TESTED",
        ):
            with self.subTest(required=required):
                self.assertIn(required, SCRIPT + WORKFLOW)

    def test_workflow_checks_out_exact_pr_head_and_uploads_failure_evidence(self):
        for required in (
            "pull_request:",
            "branches: [codex/repair-rf00-rf01]",
            "ref: ${{ github.event.pull_request.head.sha || github.sha }}",
            "HYPERGET_TESTED_SHA",
            "createReleaseFolderForCi",
            "Run-WindowsExternalHttpsE2E.ps1",
            "if: always()",
            "actions/upload-artifact@",
            "if-no-files-found: warn",
        ):
            with self.subTest(required=required):
                self.assertIn(required, WORKFLOW)

    def test_source_only_workflow_runs_contract_and_tracks_all_inputs(self):
        for required in (WORKFLOW_PATH, SCRIPT_PATH, TEST_PATH):
            with self.subTest(path=required):
                self.assertIn(required, SOURCE_ONLY)
        self.assertIn("test_hyperget_external_https_e2e.py", SOURCE_ONLY)

    def test_workflow_has_no_duplicate_top_level_keys(self):
        root_keys = {}
        for line_number, line in enumerate(WORKFLOW.splitlines(), start=1):
            if not line or line[0].isspace():
                continue
            match = re.match(r"^([A-Za-z_][A-Za-z0-9_-]*):(?:\s|$)", line)
            if match:
                root_keys.setdefault(match.group(1), []).append(line_number)
        duplicates = {key: lines for key, lines in root_keys.items() if len(lines) > 1}
        self.assertFalse(duplicates, f"Duplicate top-level workflow keys: {duplicates}")


if __name__ == "__main__":
    unittest.main()
