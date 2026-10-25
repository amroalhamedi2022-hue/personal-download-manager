"""Source contracts for real Chrome -> installed HyperGet download acceptance.

These are deliberately not substitutes for Windows Chrome/GUI/IPC execution.
"""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT = (ROOT / "scripts/Run-WindowsChromeNativeE2E.mjs").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/windows-installed-native-host-e2e.yml").read_text(encoding="utf-8")


class ChromeInstalledCaptureSourceTest(unittest.TestCase):
    def test_warm_gui_ipc_before_browser_add(self):
        self.assertIn("const warmup = spawnSync(cliExe,", SCRIPT)
        self.assertIn("'download', 'show', '--status-lines', '9223372036854775807'", SCRIPT)
        self.assertIn("warmup.status !== 0", SCRIPT)
        self.assertIn("warmup.stdout?.trim()", SCRIPT)
        self.assertLess(SCRIPT.index("const warmup = spawnSync"), SCRIPT.index("const fixtureName ="))

    def test_installed_gui_and_cli_are_required(self):
        self.assertIn("process.env.HYPERGET_GUI_EXE", SCRIPT)
        self.assertIn("join(dirname(guiExe), 'DownloadManagerCli.exe')", SCRIPT)
        self.assertIn("Installed HyperGet GUI/IPC readiness check failed", SCRIPT)

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
