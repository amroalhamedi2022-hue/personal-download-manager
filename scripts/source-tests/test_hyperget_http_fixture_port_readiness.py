"""Regression contract for race-free readiness of the local Windows HTTP fixture port."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
HELPER_PATH = "scripts/Wait-WindowsLoopbackHttpPort.ps1"
CONSUMERS = (
    "scripts/Run-WindowsInstalledResumeE2E.ps1",
    "scripts/Run-WindowsLiquidProgressVisualE2E.ps1",
    "scripts/Run-WindowsCrossVersionUpgradeRollbackE2E.ps1",
    "scripts/Run-WindowsActiveBenchmarkMatrix.ps1",
)
WORKFLOWS = {
    ".github/workflows/windows-installed-http-e2e.yml": False,
    ".github/workflows/windows-installed-active-benchmark.yml": True,
    ".github/workflows/windows-installed-ui-smoke.yml": True,
    ".github/workflows/windows-cross-version-upgrade-rollback.yml": True,
    ".github/workflows/windows-installed-memory-profile.yml": True,
}


class HttpFixturePortReadinessContract(unittest.TestCase):
    def test_shared_waiter_requires_complete_numeric_port_and_live_server(self):
        helper = (ROOT / HELPER_PATH).read_text(encoding="utf-8")
        for required in (
            "$Server.HasExited",
            "[IO.File]::ReadAllText($path).Trim()",
            "if ($portText -match '^\\d{1,5}$')",
            "[int]::TryParse($portText, [ref]$parsedPort)",
            "$parsedPort -ge 1 -and $parsedPort -le 65535",
            "for ($attempt = 0; $attempt -lt $Attempts; $attempt++)",
        ):
            with self.subTest(required=required):
                self.assertIn(required, helper)

    def test_every_script_consumer_uses_the_shared_waiter(self):
        for relative in CONSUMERS:
            with self.subTest(script=relative):
                source = (ROOT / relative).read_text(encoding="utf-8")
                self.assertIn(". (Join-Path $PSScriptRoot 'Wait-WindowsLoopbackHttpPort.ps1')", source)
                self.assertIn("Wait-HyperGetLoopbackHttpPort", source)
                self.assertNotIn("[int](Get-Content -LiteralPath $portPath -Raw)", source)
                self.assertNotIn("[int](Get-Content -LiteralPath $portFile -Raw)", source)

    def test_inline_installed_http_workflow_checks_loopback_url_after_readiness(self):
        source = (ROOT / ".github/workflows/windows-installed-http-e2e.yml").read_text(encoding="utf-8")
        self.assertIn(". (Join-Path $PWD 'scripts/Wait-WindowsLoopbackHttpPort.ps1')", source)
        self.assertIn("Wait-HyperGetLoopbackHttpPort", source)
        self.assertIn("[System.Uri]::new($url, [System.UriKind]::Absolute)", source)
        self.assertIn("$parsedLoopbackUrl.IsLoopback", source)
        self.assertIn("$parsedLoopbackUrl.Port -ne $port", source)

    def test_runtime_workflows_trigger_and_scope_the_helper_change(self):
        for relative, has_scope_gate in WORKFLOWS.items():
            with self.subTest(workflow=relative):
                source = (ROOT / relative).read_text(encoding="utf-8")
                trigger_config = source.split("\npermissions:", 1)[0]
                self.assertIn(HELPER_PATH, trigger_config)
                if has_scope_gate:
                    self.assertIn(HELPER_PATH, source.split('case "$file" in', 1)[1].split("esac", 1)[0])

    def test_source_only_workflow_runs_this_contract_without_building_the_app(self):
        source = (ROOT / ".github/workflows/hyperget-source-only.yml").read_text(encoding="utf-8")
        trigger_config = source.split("\npermissions:", 1)[0]
        self.assertIn(HELPER_PATH, trigger_config)
        self.assertIn("scripts/source-tests/test_hyperget_http_fixture_port_readiness.py", trigger_config)
        self.assertIn(
            "python3 -m unittest discover -s scripts/source-tests -p test_hyperget_http_fixture_port_readiness.py -v",
            source,
        )


if __name__ == "__main__":
    unittest.main()
