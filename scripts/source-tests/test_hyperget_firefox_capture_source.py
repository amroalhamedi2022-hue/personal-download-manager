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
        start_command = "['gui', 'start-if-not-started']"
        status_command = "['download', 'show', '--status-lines', '9223372036854775807']"
        self.assertIn(start_command, SCRIPT)
        self.assertIn(status_command, SCRIPT)
        self.assertLess(SCRIPT.index(start_command), SCRIPT.index(status_command))
        self.assertIn("function spawnFailureCode(result, requireEmptyStdout = false)", SCRIPT)
        self.assertIn("function writeGuiWarmupFailure(stage, failureCode, startResult, statusResult)", SCRIPT)
        self.assertIn("writeGuiWarmupFailure('GUI_START', guiStartFailure, guiStart, null)", SCRIPT)
        self.assertIn("writeGuiWarmupFailure('GUI_IPC_READINESS', warmupFailure, guiStart, warmup)", SCRIPT)
        self.assertIn("guiStartFailureCode: spawnFailureCode(startResult)", SCRIPT)
        self.assertIn("guiIpcFailureCode: spawnFailureCode(statusResult, true)", SCRIPT)
        self.assertIn("Installed HyperGet GUI/IPC readiness check failed:", SCRIPT)
        self.assertNotIn("console.log(add", SCRIPT)
        self.assertNotIn("console.log(safeError", SCRIPT)
        self.assertNotIn("console.log(warmup.stdout", SCRIPT)
        self.assertNotIn("stdout: warmup.stdout", SCRIPT)
        self.assertNotIn("stderr: warmup.stderr", SCRIPT)

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

    def test_webdriver_failure_reports_safe_route_status_and_error_class(self):
        self.assertIn("function safeWebDriverRoute(route)", SCRIPT)
        self.assertIn("normalizedWebDriverError(parsed?.value?.error)", SCRIPT)
        self.assertIn("recordWebDriverFailure(method, route, statusCode, errorCode)", SCRIPT)
        self.assertIn("WEBDRIVER_COMMAND_FAILED:", SCRIPT)
        self.assertIn("routeClass: safeWebDriverRoute(route)", SCRIPT)
        self.assertNotIn("parsed?.value?.message", SCRIPT)
        self.assertNotIn("rejectRequest(new Error(parsed", SCRIPT)

    def test_failure_artifact_is_written_with_stage_without_raw_exception(self):
        self.assertIn("function writeEvidenceReport(verified, failureCode = null)", SCRIPT)
        self.assertIn("writeEvidenceReport(false, failureCode)", SCRIPT)
        self.assertIn("stage: currentStage", SCRIPT)
        self.assertIn("webdriverFailure: lastWebDriverFailure", SCRIPT)
        self.assertIn("FIREFOX_NATIVE_E2E_FAILED:", SCRIPT)
        self.assertIn("event: 'FIREFOX_NATIVE_E2E_FAILED'", SCRIPT)

    def test_extension_page_uses_supported_privileged_open_not_webdriver_navigation(self):
        self.assertIn("'--allow-system-access'", SCRIPT)
        self.assertIn("`/session/${sessionId}/moz/context`", SCRIPT)
        self.assertIn("{ context: 'chrome' }", SCRIPT)
        self.assertIn("{ context: 'content' }", SCRIPT)
        self.assertIn("chromeContext?.value !== 'chrome'", SCRIPT)
        self.assertIn("contentContext?.value !== 'content'", SCRIPT)
        self.assertIn("FIREFOX_CHROME_CONTEXT_NOT_ACTIVE", SCRIPT)
        self.assertIn("FIREFOX_CONTENT_CONTEXT_NOT_RESTORED", SCRIPT)
        self.assertIn("gBrowser.addTab(arguments[0]", SCRIPT)
        self.assertIn("`/session/${sessionId}/window/handles`", SCRIPT)
        self.assertNotIn("currentStage = 'NAVIGATE_EXTENSION_OPTIONS'", SCRIPT)
        self.assertNotIn("await webdriverRequest(geckoPort, `/session/${sessionId}/url`, 'POST', { url: optionsUrl })", SCRIPT)

    def test_browser_add_must_cold_wake_the_stopped_gui(self):
        self.assertIn("['gui', 'exit']", SCRIPT)
        self.assertIn("function waitForGuiProcessState(expectRunning, timeoutMs = 30000)", SCRIPT)
        self.assertIn("waitForGuiProcessState(false, 30000)", SCRIPT)
        self.assertIn("waitForGuiProcessState(true, 30000)", SCRIPT)
        self.assertIn("function stopGuiBeforeBrowserAdd()", SCRIPT)
        self.assertIn("function confirmGuiWakeAfterBrowserAdd()", SCRIPT)
        self.assertIn("guiColdWakeVerified: guiStoppedBeforeBrowserAdd && guiWakeObservedAfterBrowserAdd && downloadVerified", SCRIPT)
        self.assertIn("Get-Process -Name", SCRIPT)
        self.assertLess(SCRIPT.index("stopGuiBeforeBrowserAdd();"), SCRIPT.index("currentStage = 'NATIVE_ADD'"))
        self.assertLess(SCRIPT.index("confirmGuiWakeAfterBrowserAdd();"), SCRIPT.index("currentStage = 'VERIFY_DOWNLOAD_SHA256'"))

    def test_browser_acceptance_stages_are_explicit(self):
        for stage in (
            "CREATE_FIREFOX_SESSION", "INSTALL_FIREFOX_ADDON",
            "OPEN_EXTENSION_OPTIONS_PRIVILEGED", "WAIT_EXTENSION_CONTEXT",
            "NATIVE_PING", "NATIVE_SAFE_ERROR", "STOP_GUI_BEFORE_BROWSER_ADD",
            "GUI_STOPPED_BEFORE_BROWSER_ADD", "NATIVE_ADD",
            "WAIT_GUI_WAKE_AFTER_ADD", "GUI_WAKE_CONFIRMED", "VERIFY_DOWNLOAD_SHA256",
        ):
            self.assertIn("currentStage = '" + stage + "'", SCRIPT)


if __name__ == "__main__":
    unittest.main()
