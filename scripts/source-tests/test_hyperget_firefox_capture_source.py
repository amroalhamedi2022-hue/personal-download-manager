"""Source guardrails for the real Firefox -> installed HyperGet browser download gate.

These checks do not replace the Windows Firefox/WebDriver/HTTP/SHA integration run.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = (ROOT / "scripts/Run-WindowsFirefoxNativeE2E.mjs").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/windows-installed-native-host-e2e.yml").read_text(encoding="utf-8")


class FirefoxInstalledCaptureSourceTest(unittest.TestCase):
    def test_uses_real_webdriver_session_and_temporary_built_addon(self):
        self.assertIn("/moz/addon/install", SCRIPT)
        self.assertIn("temporary: true", SCRIPT)
        self.assertIn("browser-integration@personaldownloadmanager.local", SCRIPT)
        self.assertIn("moz-extension://", SCRIPT)
        self.assertIn("src/optionsui/option-ui.html", SCRIPT)

    def test_native_messages_use_production_host_and_correlate_replies(self):
        self.assertIn("app.personaldownloadmanager", SCRIPT)
        self.assertIn("browser.runtime.sendNativeMessage", SCRIPT)
        self.assertIn("ping?.reply?.id", SCRIPT)
        self.assertIn("FIREFOX_ADD_REPLY_ID_MISMATCH", SCRIPT)
        self.assertIn("HyperGet could not complete the browser request.", SCRIPT)

    def test_real_download_requires_actual_get_and_sha256(self):
        self.assertIn("fixture.getRequests() < 1", SCRIPT)
        self.assertIn("createHash('sha256').update(readFileSync(path)).digest('hex')", SCRIPT)
        self.assertIn("FIREFOX_BROWSER_DOWNLOAD_SHA256_MISMATCH_OR_TIMEOUT", SCRIPT)
        self.assertIn("browserDownloadSha256Verified: downloadVerified", SCRIPT)
        self.assertIn("browserFixtureBytes: fixture.bytes.length", SCRIPT)

    def test_warm_gui_ipc_and_error_payloads_are_not_logged(self):
        self.assertIn("download', 'show', '--status-lines', '9223372036854775807'", SCRIPT)
        self.assertIn("Installed HyperGet GUI/IPC readiness check failed", SCRIPT)
        self.assertNotIn("console.log(add", SCRIPT)
        self.assertNotIn("console.log(safeError", SCRIPT)
        self.assertNotIn("console.log(warmup.stdout", SCRIPT)

    def test_windows_gate_uses_official_firefox_and_geckodriver_inputs(self):
        self.assertIn("product-details.mozilla.org", WORKFLOW)
        self.assertIn("archive.mozilla.org", WORKFLOW)
        self.assertIn("api.github.com/repos/mozilla/geckodriver/releases/latest", WORKFLOW)
        self.assertIn("npm run pack:firefox", WORKFLOW)
        self.assertIn("Run-WindowsFirefoxNativeE2E.mjs", WORKFLOW)
        self.assertIn("firefox-native-extension-report.json", WORKFLOW)
        self.assertIn("Run-WindowsFirefoxNativeE2E.mjs", WORKFLOW)
        self.assertIn("test_hyperget_firefox_capture_source.py", WORKFLOW)
        self.assertIn("node --check scripts/Run-WindowsFirefoxNativeE2E.mjs", WORKFLOW)


if __name__ == "__main__":
    unittest.main()
