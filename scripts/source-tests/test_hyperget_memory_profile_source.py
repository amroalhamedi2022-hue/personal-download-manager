"""Source contract for the installed Windows JVM diagnostic milestone."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
PROFILE = (ROOT / "scripts/Collect-WindowsJvmMemoryProfile.ps1").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/windows-installed-memory-profile.yml").read_text(encoding="utf-8")


class WindowsJvmMemoryProfileContract(unittest.TestCase):
    def test_profile_captures_heap_classes_threads_and_process_metrics(self):
        for command in ("GC.heap_info", "GC.class_histogram", "Thread.print", "VM.version"):
            self.assertIn(command, PROFILE)
        for field in ("WorkingSetMiB", "PrivateBytesMiB", "Handles", "Threads", "SourceSha"):
            self.assertIn(field, PROFILE)

    def test_profile_runs_after_real_installed_transfer_matrix(self):
        self.assertIn("Run-WindowsActiveBenchmarkMatrix.ps1", WORKFLOW)
        self.assertIn("Collect-WindowsJvmMemoryProfile.ps1", WORKFLOW)
        transfer_call = WORKFLOW.index("& ./scripts/Run-WindowsActiveBenchmarkMatrix.ps1")
        profile_call = WORKFLOW.index("& ./scripts/Collect-WindowsJvmMemoryProfile.ps1")
        self.assertLess(transfer_call, profile_call)
        self.assertIn("createReleaseFolderForCi", WORKFLOW)
        self.assertIn("github.event.pull_request.head.sha || github.sha", WORKFLOW)

    def test_profile_artifact_is_uploaded_even_if_jcmd_fails(self):
        self.assertIn("if: always()", WORKFLOW)
        self.assertIn("jvm-profile", WORKFLOW)
        self.assertIn("if-no-files-found: warn", WORKFLOW)


if __name__ == "__main__":
    unittest.main()
