"""Source contract for the installed Windows JVM diagnostic milestone."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
PROFILE = (ROOT / "scripts/Collect-WindowsJvmMemoryProfile.ps1").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/windows-installed-memory-profile.yml").read_text(encoding="utf-8")
MATRIX_SCRIPT = (ROOT / "scripts/Run-WindowsActiveBenchmarkMatrix.ps1").read_text(encoding="utf-8")
UI_WORKFLOW = (ROOT / ".github/workflows/windows-installed-ui-smoke.yml").read_text(encoding="utf-8")


class WindowsJvmMemoryProfileContract(unittest.TestCase):
    def test_profile_captures_heap_classes_threads_and_process_metrics(self):
        for command in ("GC.heap_info", "GC.class_histogram", "Thread.print", "VM.version"):
            self.assertIn(command, PROFILE)
        for field in ("WorkingSetMiB", "PrivateBytesMiB", "Handles", "Threads", "SourceSha"):
            self.assertIn(field, PROFILE)

    def test_profile_runs_after_real_installed_transfer_matrix(self):
        self.assertIn("Run-WindowsActiveBenchmarkMatrix.ps1", WORKFLOW)
        self.assertIn("Collect-WindowsJvmMemoryProfile.ps1", WORKFLOW)
        baseline_call = WORKFLOW.index("-OutputDirectory 'build/ci-release/jvm-profile-before'")
        transfer_call = WORKFLOW.index("& ./scripts/Run-WindowsActiveBenchmarkMatrix.ps1")
        post_profile_call = WORKFLOW.index("-OutputDirectory 'build/ci-release/jvm-profile'")
        self.assertLess(baseline_call, transfer_call)
        self.assertLess(transfer_call, post_profile_call)
        settle_delay = WORKFLOW.index("Start-Sleep -Seconds 30")
        settled_profile_call = WORKFLOW.index("-OutputDirectory 'build/ci-release/jvm-profile-stabilized'")
        upload_step = WORKFLOW.index("- name: Upload benchmark and JVM diagnostic evidence")
        self.assertLess(post_profile_call, settle_delay)
        self.assertLess(settle_delay, settled_profile_call)
        self.assertLess(settled_profile_call, upload_step)
        self.assertIn("build/ci-release/jvm-profile-stabilized/**", WORKFLOW)
        self.assertIn("-XX:NativeMemoryTracking=summary", WORKFLOW)
        self.assertIn("-EnableNativeMemoryTracking", WORKFLOW)
        self.assertIn("build/ci-release/jvm-profile-before/**", WORKFLOW)
        self.assertIn("createReleaseFolderForCi", WORKFLOW)
        self.assertIn("github.event.pull_request.head.sha || github.sha", WORKFLOW)
        self.assertIn("[switch] $KeepGuiRunning", MATRIX_SCRIPT)
        self.assertIn("-KeepGuiRunning", WORKFLOW)
        self.assertIn("if ($KeepGuiRunning)", MATRIX_SCRIPT)
        self.assertIn("[switch] $EnableNativeMemoryTracking", MATRIX_SCRIPT)
        self.assertIn("-EnableNativeMemoryTracking", WORKFLOW)
        self.assertIn("NativeMemoryTracking=summary", MATRIX_SCRIPT)
        self.assertIn("VM.native_memory", PROFILE)
        self.assertIn('"JvmProcessId=$javaPid"', PROFILE)
        self.assertNotIn("$gui.Id", PROFILE)
        self.assertIn("com\\.abdownloadmanager\\.desktop\\.AppKt", PROFILE)
        self.assertIn("$javaPid", PROFILE)
        self.assertNotIn("& $jcmd $gui.Id", PROFILE)

    def test_expensive_profile_is_scoped_to_the_full_relevant_pr_diff(self):
        self.assertIn("change-scope:", WORKFLOW)
        self.assertIn('changed="$(git diff --name-only "origin/$GITHUB_BASE_REF...HEAD")"', WORKFLOW)
        self.assertIn("fetch-depth: 0", WORKFLOW)
        self.assertNotIn("git diff --name-only HEAD^ HEAD", WORKFLOW)
        self.assertIn("HYPERGET_EVENT_BEFORE: ${{ github.event.before || '' }}", WORKFLOW)
        self.assertIn('case "$file" in', WORKFLOW)
        self.assertIn(".github/workflows/windows-installed-memory-profile.yml", WORKFLOW)
        self.assertIn("run=false", WORKFLOW)
        self.assertIn("needs: change-scope", WORKFLOW)
        self.assertIn("workflow_dispatch", WORKFLOW)
        self.assertIn("needs: change-scope", WORKFLOW)
        self.assertIn("if: needs.change-scope.outputs.run == 'true'", WORKFLOW)
        self.assertIn("runs-on: windows-2022", WORKFLOW)
        self.assertIn('case "$file" in', WORKFLOW)
        self.assertIn(";;", WORKFLOW)
        self.assertIn("esac", WORKFLOW)

    def test_profile_artifact_is_uploaded_even_if_jcmd_fails(self):
        self.assertIn("if: always()", WORKFLOW)
        self.assertIn("jvm-profile", WORKFLOW)
        self.assertIn("if-no-files-found: warn", WORKFLOW)


    def test_installed_ui_idle_profile_uses_exact_checked_out_pr_head(self):
        # GITHUB_SHA is the synthetic merge ref on pull_request events. The
        # installed GUI's idle CSVs must identify the exact checked-out source.
        idle_step = UI_WORKFLOW.split(
            "- name: Launch installed desktop app and capture Windows GUI", 1
        )[1].split("- name: Verify real Liquid Progress while an installed download is active", 1)[0]
        self.assertIn("$sourceSha = (git rev-parse HEAD).Trim()", idle_step)
        self.assertIn("$expectedSourceSha = '${{ github.event.pull_request.head.sha || github.sha }}'", idle_step)
        self.assertIn("if ($sourceSha -ne $expectedSourceSha)", idle_step)
        self.assertIn("-SourceSha $sourceSha", idle_step)
        self.assertNotIn("-SourceSha $env:GITHUB_SHA", idle_step)
        self.assertIn("-RootProcessName HyperGet", idle_step)
        self.assertNotIn("-RootProcessName DownloadManager", idle_step)


if __name__ == "__main__":
    unittest.main()
