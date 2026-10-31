"""Source contracts for real Chrome -> installed HyperGet download acceptance.

These are deliberately not substitutes for Windows Chrome/GUI/IPC execution.
"""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT = (ROOT / "scripts/Run-WindowsChromeNativeE2E.mjs").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/windows-installed-native-host-e2e.yml").read_text(encoding="utf-8")
STARTUP_SOURCE = (ROOT / "desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/utils/singleInstance/EnsureAppIsAwake.kt").read_text(encoding="utf-8")


class ChromeInstalledCaptureSourceTest(unittest.TestCase):
    def test_explicit_cli_gui_start_and_read_only_ipc_probe_precede_browser_add(self):
        self.assertIn("const guiStart = spawnSync(cliExe,", SCRIPT)
        self.assertIn("['gui', 'start-if-not-started']", SCRIPT)
        self.assertIn("const warmup = spawnSync(cliExe,", SCRIPT)
        self.assertIn("'download', 'show', '--status-lines', '9223372036854775807'", SCRIPT)
        self.assertIn("spawnFailureCode(warmup, true)", SCRIPT)
        self.assertLess(SCRIPT.index("const guiStart = spawnSync"), SCRIPT.index("const warmup = spawnSync"))
        self.assertLess(SCRIPT.index("const warmup = spawnSync"), SCRIPT.index("const fixtureName ="))

    def test_cold_start_budget_leaves_time_to_launch_and_poll_readiness(self):
        self.assertIn("timeout: Long = 50_000", STARTUP_SOURCE)
        self.assertIn("howMuchWait: Long = 30_000", STARTUP_SOURCE)
        self.assertIn("awaitToBeReady(timeout = 2.seconds)", STARTUP_SOURCE)
        self.assertIn("{ encoding: 'utf8', timeout: 45000, windowsHide: true }", SCRIPT)

    def test_gui_warmup_failure_artifact_is_sanitized(self):
        self.assertIn("writeGuiWarmupFailure('GUI_START'", SCRIPT)
        self.assertIn("writeGuiWarmupFailure('GUI_IPC_READINESS'", SCRIPT)
        self.assertIn("guiIpcStdoutPresent: Boolean(statusResult?.stdout?.trim())", SCRIPT)
        self.assertIn("guiIpcStderrPresent: Boolean(statusResult?.stderr?.trim())", SCRIPT)
        self.assertNotIn("stdout: statusResult.stdout", SCRIPT)
        self.assertNotIn("stderr: statusResult.stderr", SCRIPT)
        self.assertNotIn("console.log(warmup.stdout", SCRIPT)

    def test_installed_gui_and_cli_are_required(self):
        self.assertIn("process.env.HYPERGET_GUI_EXE", SCRIPT)
        self.assertIn("join(dirname(guiExe), 'DownloadManagerCli.exe')", SCRIPT)
        self.assertIn("Installed HyperGet GUI/IPC readiness check failed", SCRIPT)

    def test_browser_add_must_cold_wake_the_stopped_gui(self):
        self.assertIn("['gui', 'exit']", SCRIPT)
        self.assertIn("function waitForGuiProcessState(expectRunning, timeoutMs = 30000)", SCRIPT)
        self.assertIn("waitForGuiProcessState(false, 30000)", SCRIPT)
        self.assertIn("waitForGuiProcessState(true, 30000)", SCRIPT)
        self.assertIn("function stopGuiBeforeBrowserAdd()", SCRIPT)
        self.assertIn("function confirmGuiWakeAfterBrowserAdd()", SCRIPT)
        self.assertIn("guiColdWakeVerified: guiStoppedBeforeBrowserAdd && guiWakeObservedAfterBrowserAdd && captureVerified", SCRIPT)
        self.assertIn("Get-Process -Name", SCRIPT)
        self.assertLess(SCRIPT.index("stopGuiBeforeBrowserAdd();"), SCRIPT.index("let evaluated;"))
        self.assertLess(SCRIPT.index("confirmGuiWakeAfterBrowserAdd();"), SCRIPT.index("captureVerified = true"))

    def test_native_failure_is_classified_without_exposing_payload(self):
        self.assertIn("candidate === 'INVALID_REQUEST' || candidate === 'REQUEST_FAILED'", SCRIPT)
        self.assertIn("safeType = 'UNCLASSIFIED'", SCRIPT)
        self.assertIn("'Installed HyperGet rejected browser-origin add request: ' + safeType", SCRIPT)
        self.assertNotIn("console.log(addReply", SCRIPT)
        self.assertNotIn("throw Error(addReply.content.payload", SCRIPT)

    def test_real_byte_and_sha_gate_is_not_relaxed(self):
        self.assertIn("chrome.runtime.sendNativeMessage(", SCRIPT)
        self.assertIn("fixtureGetCount < 1", SCRIPT)
        self.assertIn("createHash('sha256').update(readFileSync(path)).digest('hex') === expectedSha", SCRIPT)
        self.assertIn("}, 'SHA-verified browser-origin download', 120000)", SCRIPT)

    def test_windows_installed_gate_and_pinned_browser_source(self):
        self.assertIn("createReleaseFolderForCi", WORKFLOW)
        self.assertIn("Run-WindowsChromeNativeE2E.mjs", WORKFLOW)
        self.assertIn("0fb5a304cbde07a9758d2b42417e7a8a85cba0f2", WORKFLOW)
        self.assertIn("HYPERGET_GUI_EXE=", WORKFLOW)


if __name__ == "__main__":
    unittest.main()
