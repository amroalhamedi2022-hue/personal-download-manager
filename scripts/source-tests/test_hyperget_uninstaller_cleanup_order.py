from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
NSIS_PATH = ROOT / "desktop/app/resources/installer/nsis-script-template.nsi"


class HyperGetUninstallerCleanupOrderTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = NSIS_PATH.read_text(encoding="utf-8")
        match = re.search(
            r'(?ms)^Section\s+"Uninstall"\s*$([\s\S]*?)^SectionEnd\s*$',
            cls.source,
        )
        if match is None:
            raise AssertionError('The NSIS template must contain the dedicated "Uninstall" section.')
        cls.uninstall_section = match.group(1)

    def test_registry_and_shortcut_cleanup_precedes_self_removal(self):
        commands = [
            '!insertmacro RemoveStartMenu',
            '!insertmacro RemoveDesktopShortCut',
            '!insertmacro RemoveKnownBrandShortcuts',
            'DeleteRegKey SHCTX "${REG_UNINSTALL_KEY}"',
            'DeleteRegKey SHCTX "${REG_APP_KEY}"',
            'DeleteRegValue SHCTX "${REG_RUN_KEY}" "${APP_NAME}"',
            '!insertmacro clearFiles',
        ]
        positions = [self.uninstall_section.index(command) for command in commands]
        self.assertEqual(
            positions,
            sorted(positions),
            'Uninstaller must remove shortcuts/registry registration before deleting its own files.',
        )
        self.assertEqual(
            self.uninstall_section.count('!insertmacro clearFiles'),
            1,
            'The uninstall section must perform file cleanup exactly once, at the end.',
        )

    def test_user_data_deletion_remains_opt_in_and_outside_uninstall_section(self):
        self.assertIn('Section /o "un.Remove User Data"', self.source)
        self.assertNotIn('!insertmacro RemoveUserData', self.uninstall_section)


if __name__ == "__main__":
    unittest.main()
