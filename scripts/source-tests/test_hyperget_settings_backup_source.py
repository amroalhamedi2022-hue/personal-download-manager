"""Fail-closed source acceptance for HyperGet portable desktop settings."""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
STORAGE = ROOT / "desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/storage"
UI = ROOT / "desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/pages/settings"

class SettingsBackupSourceTest(unittest.TestCase):
    def test_explicit_allowlist_and_security_protection(self):
        s = (STORAGE / "DesktopSettingsBackup.kt").read_text(encoding="utf-8")
        allowed = s.split("private val portableKeys = setOf(", 1)[1].split("\n    )", 1)[0]
        for secret in ("apiAuthKey", "apiAuthEnabled", "apiPort", "apiEnabled",
                       "ignoreSSLCertificates", "autoStartOnBoot"):
            self.assertNotIn('"' + secret + '"', allowed)
        self.assertIn("settings.keys.all { it in portableKeys }", s)
        self.assertIn("MAX_BACKUP_BYTES", s)
        self.assertIn("restored(current: AppSettingsModel, backup: String)", s)

    def test_transactional_import_and_visible_commands(self):
        storage = (STORAGE / "AppSettingsStorage.kt").read_text(encoding="utf-8")
        self.assertIn("settingsStore.updateData { current ->", storage)
        self.assertIn("DesktopSettingsBackup.restored(current, backup)", storage)
        page = (UI / "SettingsPage.kt").read_text(encoding="utf-8")
        self.assertIn('ActionButton("Export settings"', page)
        self.assertIn('ActionButton("Import settings"', page)
        self.assertIn("StandardOpenOption.CREATE_NEW", page)
        self.assertIn("JOptionPane.YES_NO_OPTION", page)

    def test_browser_api_key_masked_by_default(self):
        configurable = (ROOT / "shared/app/src/commonMain/kotlin/com/abdownloadmanager/shared/ui/configurable/item/StringConfigurable.kt").read_text(encoding="utf-8")
        common = (ROOT / "shared/app/src/commonMain/kotlin/com/abdownloadmanager/shared/settings/CommonSettings.kt").read_text(encoding="utf-8")
        renderer = (ROOT / "desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/ui/configurable/comon/renderer/StringConfigurableRenderer.kt").read_text(encoding="utf-8")
        self.assertIn("val isSecret: Boolean = false", configurable)
        body = common.split("fun apiAuthKey(", 1)[1].split("fun proxyConfig(", 1)[0]
        self.assertIn("isSecret = true", body)
        self.assertIn("PasswordVisualTransformation()", renderer)
        self.assertIn("if (cfg.isSecret && !revealSecret)", renderer)
        self.assertIn('if (revealSecret) "Hide" else "Show"', renderer)

    def test_kotlin_behavioral_tests_exist(self):
        t = (ROOT / "desktop/app/src/test/kotlin/com/abdownloadmanager/desktop/storage/DesktopSettingsBackupTest.kt").read_text(encoding="utf-8")
        for name in ("exportDoesNotIncludeSecurityOrBrowserSecrets",
                     "roundTripRestoresSafeSettingsButNeverChangesProtectedFields",
                     "rejectsInjectedSecretOrUnsafePreference",
                     "rejectsCorruptVersionAndOversizedPayload",
                     "rejectsDangerousConnectionLimits"):
            self.assertIn("fun " + name + "(", t)

if __name__ == "__main__":
    unittest.main()
