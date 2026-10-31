"""Ensure an asynchronous NSIS uninstall settles before owned residue audit."""
from pathlib import Path
import unittest

RUNNER = Path(__file__).resolve().parents[2] / "scripts/Run-WindowsControlledBenchmarkComparison.ps1"

class UninstallSettleSourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = RUNNER.read_text(encoding="utf-8")

    def test_wait_is_bounded_and_checks_all_three_residue_classes(self):
        block = self.source.split("function Wait-ForUninstallerToSettle {", 1)[1].split("function Assert-NoProductResidue {", 1)[0]
        self.assertIn("[ValidateRange(1, 60)][int] $TimeoutSeconds = 20", block)
        self.assertIn("$deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)", block)
        self.assertIn("$uninstallKeys | Where-Object { Test-Path -LiteralPath $_ }", block)
        self.assertIn("$shortcuts | Where-Object { Test-Path -LiteralPath $_ }", block)
        self.assertIn("Test-Path -LiteralPath $installDir", block)
        self.assertIn("Start-Sleep -Milliseconds 250", block)
        self.assertIn("} while ([DateTime]::UtcNow -lt $deadline)", block)
        self.assertNotIn("Remove-Item", block)

    def test_wait_follows_uninstaller_exit_and_precedes_owned_audit(self):
        block = self.source.split("function Invoke-VariantRound {", 1)[1]
        wait = block.index("Wait-ForUninstallerToSettle -TimeoutSeconds 20")
        self.assertLess(block.index("$remove.WaitForExit(180000)"), wait)
        self.assertLess(block.index("if ($remove.ExitCode -ne 0)"), wait)
        self.assertLess(wait, block.index("Assert-NoProductResidue -Variant $Variant -Round $Round"))
        for check in ("Clear-OwnedRegistryResidue -Variant $Variant -Round $Round", "Clear-OwnedShortcutResidue -Variant $Variant -Round $Round", "Clear-OwnedInstallDirectoryResidue -Variant $Variant -Round $Round"):
            self.assertIn(check, self.source)

    def test_timeout_does_not_waive_cleanup_failures(self):
        block = self.source.split("function Wait-ForUninstallerToSettle {", 1)[1].split("function Assert-NoProductResidue {", 1)[0]
        self.assertIn("auditing remaining owned residue without suppressing defects", block)
        self.assertNotIn("Assert-NoProductResidue -Variant", block)

if __name__ == "__main__":
    unittest.main()
