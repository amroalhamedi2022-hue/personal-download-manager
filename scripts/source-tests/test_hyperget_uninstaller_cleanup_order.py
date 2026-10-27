from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
NSIS_PATH = ROOT / "desktop/app/resources/installer/nsis-script-template.nsi"


class HyperGetUninstallerCleanupOrderTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = NSIS_PATH.read_text(encoding="utf-8")
        start = cls.source.find('Section "Uninstall"')
        if start < 0:
            raise AssertionError('The NSIS template must contain the dedicated "Uninstall" section.')
        end = cls.source.find("SectionEnd", start)
        if end < 0:
            raise AssertionError('The NSIS uninstall section must terminate with SectionEnd.')
        cls.uninstall_section = cls.source[start:end]

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

    def test_start_menu_cleanup_never_recursively_deletes_unrelated_files(self):
        start = self.source.index('!macro RemoveStartMenu')
        end = self.source.index('!macroend', start)
        macro = self.source[start:end]
        self.assertIn('Delete "$SMPROGRAMS\\${APP_DISPLAY_NAME}\\${APP_DISPLAY_NAME}.lnk"', macro)
        self.assertIn('RMDir "$SMPROGRAMS\\${APP_DISPLAY_NAME}"', macro)
        self.assertNotIn('RMDir /r', macro)

    def test_installer_and_uninstaller_pin_current_user_context(self):
        init_start = self.source.find("Function .onInit")
        self.assertGreaterEqual(init_start, 0, 'Installer .onInit must be present.')
        init_end = self.source.find("FunctionEnd", init_start)
        self.assertGreater(init_end, init_start, 'Installer .onInit must terminate.')
        init_section = self.source[init_start:init_end]
        self.assertIn('SetShellVarContext current', init_section)
        self.assertIn('SetShellVarContext current', self.uninstall_section)
        self.assertLess(
            self.uninstall_section.index('SetShellVarContext current'),
            self.uninstall_section.index('!insertmacro RemoveStartMenu'),
        )
        self.assertLess(
            self.uninstall_section.index('SetShellVarContext current'),
            self.uninstall_section.index('DeleteRegKey SHCTX "${REG_UNINSTALL_KEY}"'),
        )

    def test_user_data_deletion_remains_opt_in_and_outside_uninstall_section(self):
        self.assertIn('Section /o "un.Remove User Data"', self.source)
        self.assertNotIn('!insertmacro RemoveUserData', self.uninstall_section)


if __name__ == "__main__":
    unittest.main()
