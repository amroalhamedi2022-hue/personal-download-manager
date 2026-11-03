from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
AUDIT = ROOT / "scripts/audit_windows_icon_resources.py"
UI_WORKFLOW = ROOT / ".github/workflows/windows-installed-ui-smoke.yml"
SOURCE_WORKFLOW = ROOT / ".github/workflows/hyperget-source-only.yml"


class HyperGetIconResourceAuditTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.audit = AUDIT.read_text(encoding="utf-8")
        cls.ui = UI_WORKFLOW.read_text(encoding="utf-8")
        cls.source_workflow = SOURCE_WORKFLOW.read_text(encoding="utf-8")

    def test_audit_reads_actual_pe_group_icon_resources_with_win32(self):
        for marker in (
            "RT_GROUP_ICON = 14",
            "LoadLibraryExW",
            "EnumResourceNamesW",
            "FindResourceW",
            "SizeofResource",
            "LockResource",
            "HYPERGET_NATIVE_ICON_RESOURCE_AUDIT=PASS",
        ):
            self.assertIn(marker, self.audit)

    def test_audit_requires_all_six_native_sizes_and_32_bit_entries(self):
        self.assertIn("EXPECTED_SIZES = (16, 32, 48, 64, 128, 256)", self.audit)
        self.assertIn("32 not in entries.get(size, set())", self.audit)
        self.assertIn("sha256_file(path)", self.audit)

    def test_installed_ui_workflow_audits_the_real_installed_gui_and_setup_exe(self):
        self.assertGreaterEqual(self.ui.count('"scripts/audit_windows_icon_resources.py"'), 2)
        self.assertIn("IDM_SMOKE_INSTALLER=$($installers[0].FullName)", self.ui)
        self.assertIn("--application-exe $env:IDM_SMOKE_EXECUTABLE", self.ui)
        self.assertIn("--installer-exe $env:IDM_SMOKE_INSTALLER", self.ui)
        self.assertLess(
            self.ui.index("name: Install single NSIS EXE"),
            self.ui.index("name: Audit embedded HyperGet icon resources"),
        )
        self.assertLess(
            self.ui.index("name: Audit embedded HyperGet icon resources"),
            self.ui.index("name: Launch installed desktop app"),
        )

    def test_source_only_workflow_runs_this_static_contract_without_building(self):
        self.assertIn('"scripts/audit_windows_icon_resources.py"', self.source_workflow)
        self.assertIn('"scripts/source-tests/test_hyperget_icon_resource_audit.py"', self.source_workflow)
        self.assertIn(
            "python3 -m unittest discover -s scripts/source-tests -p test_hyperget_icon_resource_audit.py -v",
            self.source_workflow,
        )


if __name__ == "__main__":
    unittest.main(verbosity=2)
