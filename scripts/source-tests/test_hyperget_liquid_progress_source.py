"""Source contracts for HyperGet's shared Liquid Progress and real Windows visual gate."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
TABLE = (ROOT / "desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/pages/home/sections/TableDownloadItem.kt").read_text(encoding="utf-8")
LIST = (ROOT / "desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/pages/home/sections/DownloadList.kt").read_text(encoding="utf-8")
SCRIPT = (ROOT / "scripts/Run-WindowsLiquidProgressVisualE2E.ps1").read_text(encoding="utf-8")
UI_WORKFLOW = (ROOT / ".github/workflows/windows-installed-ui-smoke.yml").read_text(encoding="utf-8")
SOURCE_WORKFLOW = (ROOT / ".github/workflows/hyperget-source-only.yml").read_text(encoding="utf-8")
PAGE = (ROOT / "desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/pages/singleDownloadPage/ProgressDownloadPage.kt").read_text(encoding="utf-8")
IPC_INTERFACE = (ROOT / "desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/utils/singleInstance/service/IDefaultAppIPCService.kt").read_text(encoding="utf-8")
IPC_IMPL = (ROOT / "desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/utils/singleInstance/service/DefaultAppIPCServiceImpl.kt").read_text(encoding="utf-8")
DOWNLOAD_COMMAND = (ROOT / "desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/cli/download/Download.kt").read_text(encoding="utf-8")
OPEN_COMMAND = (ROOT / "desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/cli/download/open/OpenDownload.kt").read_text(encoding="utf-8")
DOWNLOAD_WINDOW_STATE = (ROOT / "desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/pages/singleDownloadPage/SingleDownloadPageStateToPersist.kt").read_text(encoding="utf-8")
DOWNLOAD_WINDOW_COMPONENT = (ROOT / "desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/pages/singleDownloadPage/DesktopSingleDownloadPageComponent.kt").read_text(encoding="utf-8")


class LiquidProgressSourceContract(unittest.TestCase):
    def test_one_shared_clock_is_created_only_when_a_download_is_active(self):
        self.assertIn("hasActiveDownload = downloadList.any", LIST)
        self.assertIn("rememberInfiniteTransition()", LIST)
        self.assertIn("infiniteRepeatable(", LIST)
        self.assertIn("LocalLiquidProgressPhase provides liquidProgressPhase", LIST)
        self.assertNotIn("rememberInfiniteTransition", TABLE)

    def test_progress_phase_is_read_in_draw_not_by_each_row_composition(self):
        progress = TABLE.split("private fun ProgressStatus(", 1)[1]
        self.assertIn("Spacer(", progress)
        self.assertIn("drawWithCache", progress)
        self.assertNotIn("Canvas(", progress)
        self.assertIn(".clip(CircleShape)", progress)
        self.assertIn("liquidProgressPhase.value", progress)
        self.assertIn("animatedWidth.value", progress)
        self.assertIn("drawRoundRect(", progress)
        self.assertIn("drawCircle(", progress)
        self.assertIn("Brush.horizontalGradient(", progress)
        self.assertIn("clipRect(right = fillWidth)", progress)
        self.assertIn("isLiquidActive = status == DownloadProgressStatus.Downloading", TABLE)

    def test_frame_dependent_gradient_shaders_are_cached_at_stable_track_size(self):
        progress = TABLE.split("private fun ProgressStatus(", 1)[1]
        cache_builder = progress.split(".drawWithCache {", 1)[1].split("onDrawBehind {", 1)[0]
        draw_body = progress.split("onDrawBehind {", 1)[1]
        self.assertIn("val shimmerBrush = if (isLiquidActive", cache_builder)
        self.assertIn("Brush.horizontalGradient(", cache_builder)
        self.assertNotIn("Brush.horizontalGradient(", draw_body)
        self.assertNotIn("Canvas(", progress)
        self.assertIn("val animatedWidth = animateFloatAsState(", progress)
        animation_state = progress.split("val animatedWidth = animateFloatAsState(", 1)[1].split("Spacer(", 1)[0]
        self.assertNotIn(".value", animation_state)
        self.assertIn("animatedWidth.value", draw_body)
        self.assertIn("liquidProgressPhase.value", draw_body)
        self.assertIn("val fillScale = fillWidth / size.width", draw_body)
        self.assertIn("scale(", draw_body)
    def test_installed_windows_e2e_captures_only_a_proven_live_transfer(self):
        for marker in (
            "download add http",
            "download resume",
            "active_distinct_tasks",
            "statusAtCapture",
            "CopyFromScreen",
            "Get-FileHash",
            "liquid-progress-active.png",
            "liquid-progress-report.json",
            "FinalStatus = $finalStatus",
            "$finalStatus -cne 'Finished'",
            "Verified = $true",
        ):
            self.assertIn(marker, SCRIPT)
        self.assertIn("StatusAtCapture = $statusAtCapture", SCRIPT)
        self.assertIn("$actualSha -eq $expectedSha", SCRIPT)

    def test_windows_visual_capture_rejects_off_screen_clipping(self):
        self.assertIn("System.Windows.Forms.Screen", SCRIPT)
        self.assertIn("FromHandle($windowHandle).Bounds", SCRIPT)
        self.assertIn("$windowRect.Right -gt $displayBounds.Right", SCRIPT)
        self.assertIn("$windowRect.Bottom -gt $displayBounds.Bottom", SCRIPT)
        self.assertIn("outside display bounds", SCRIPT)
        self.assertIn("$maxInvisibleFrame = 12", SCRIPT)
        self.assertIn("$outsideRight -gt $maxInvisibleFrame", SCRIPT)
        self.assertIn("$captureWidth = $captureRight - $captureLeft", SCRIPT)
        self.assertIn("$captureHeight = $captureBottom - $captureTop", SCRIPT)
        self.assertIn("[System.Drawing.Bitmap]::new($captureWidth, $captureHeight)", SCRIPT)
        self.assertLess(
            SCRIPT.index("outside display bounds"),
            SCRIPT.index("$bitmap = [System.Drawing.Bitmap]::new"),
        )

    def test_visual_gate_selects_the_download_details_window_not_the_main_list(self):
        self.assertIn("FindVisibleWindow", SCRIPT)
        self.assertIn("FindVisibleWindowExcluding", SCRIPT)
        self.assertIn("EnumWindows", SCRIPT)
        self.assertIn("GetWindowThreadProcessId", SCRIPT)
        self.assertIn("IsWindowVisible", SCRIPT)
        self.assertIn("$fileName", SCRIPT)
        self.assertIn("$windowHandle = $detailWindowHandle", SCRIPT)
        self.assertIn("[uint32]$window.Id", SCRIPT)
        self.assertIn("$windowHandle", SCRIPT)
        self.assertIn("download open $taskId", SCRIPT)
        self.assertIn("Installed CLI could not open", SCRIPT)
        self.assertIn("refusing to accept a main-list screenshot", SCRIPT)
        self.assertLess(
            SCRIPT.index("$windowHandle = $detailWindowHandle"),
            SCRIPT.index("$windowRect = [HyperGetLiquidProgressNative+RECT]::new()"),
        )

    def test_graph_visibility_is_persisted_and_user_controlled(self):
        self.assertIn("val showSpeedGraph: Boolean = true", DOWNLOAD_WINDOW_STATE)
        self.assertIn("mapStateFlow { it.showSpeedGraph }", DOWNLOAD_WINDOW_COMPONENT)
        self.assertIn("fun setShowSpeedGraph(visible: Boolean)", DOWNLOAD_WINDOW_COMPONENT)
        self.assertIn("setShowSpeedGraph(!showSpeedGraph)", PAGE)

    def test_fresh_download_window_shows_connection_details_by_default(self):
        self.assertIn("val showPartInfo: Boolean = true", DOWNLOAD_WINDOW_STATE)

    def test_installed_cli_has_a_real_ipc_command_to_open_download_details(self):
        self.assertIn("suspend fun openDownload(ids: List<Long>)", IPC_INTERFACE)
        self.assertIn("override suspend fun openDownload(ids: List<Long>)", IPC_IMPL)
        self.assertIn("downloadSystem.getDownloadItemById(id) != null", IPC_IMPL)
        self.assertIn("appComponent.openDownloadDialog(id)", IPC_IMPL)
        self.assertIn("OpenDownload()", DOWNLOAD_COMMAND)
        self.assertIn('SuspendingCliktCommand("open")', OPEN_COMMAND)
        self.assertIn("it.openDownload(ids)", OPEN_COMMAND)

    def test_download_add_runs_asynchronously_so_live_status_can_be_observed(self):
        self.assertIn("$addArguments = 'download add http --link", SCRIPT)
        self.assertIn("$addProcess = Start-Process -FilePath $cliPath -ArgumentList $addArguments", SCRIPT)
        self.assertIn("$taskId = [long]0", SCRIPT)
        self.assertIn("$addProcess.WaitForExit(180000)", SCRIPT)
        self.assertIn("AddProcessExitCode = $addProcessExitCode", SCRIPT)
        self.assertNotIn("$addOutput = @(& $cliPath download add", SCRIPT)
        self.assertIn("if ($statusAtCapture -eq 'Paused' -and -not $addResumeSent)", SCRIPT)

    def test_cli_redirect_paths_are_not_overwritten_before_async_add(self):
        # The previous Windows run failed because paths were assigned, then
        # reset to None before Start-Process/Get-ReportedTaskIds used them.
        for variable in ("addStdout", "addStderr", "resumeStdout", "resumeStderr"):
            assignment = "$" + variable + " = Join-Path $env:RUNNER_TEMP"
            self.assertEqual(SCRIPT.count(assignment), 1)
            self.assertNotIn("$" + variable + " = $null", SCRIPT)
        guard = "RUNNER_TEMP did not produce valid CLI redirection paths."
        self.assertIn(guard, SCRIPT)
        self.assertLess(
            SCRIPT.index(guard),
            SCRIPT.index("$addProcess = Start-Process -FilePath $cliPath -ArgumentList $addArguments"),
        )
        self.assertIn("-RedirectStandardOutput $addStdout -RedirectStandardError $addStderr", SCRIPT)
        self.assertIn("Get-ReportedTaskIds -Path $addStdout", SCRIPT)

    def test_added_task_status_is_polled_before_waiting_for_fixture_activity(self):
        # An Added-only task is Paused until resume; status polling must not be
        # nested under the fixture-active condition or the resume can never fire.
        self.assertIn("$taskIdResolved = $false", SCRIPT)
        self.assertIn("if ($addProcess.HasExited -and -not $taskIdResolved)", SCRIPT)
        self.assertIn("if ($taskIdResolved -and (($elapsedMilliseconds - $lastStatusPollMilliseconds) -ge 500))", SCRIPT)
        self.assertIn("if ($statusAtCapture -eq 'Paused' -and -not $addResumeSent)", SCRIPT)
        self.assertIn('$resumeArguments = "download resume $taskId"', SCRIPT)
        self.assertIn("TaskIdResolved = $taskIdResolved", SCRIPT)
        self.assertIn("ResumeSent = $addResumeSent", SCRIPT)
        self.assertIn("if ($statusAtCapture -eq 'Downloading' -and [int]$captureStats.active_distinct_tasks -ge 1 -and [long]$captureStats.bytes_sent -ge $minimumVisualProgressBytes)", SCRIPT)

    def test_visual_fixture_is_paced_and_failure_diagnostics_are_preserved(self):
        self.assertIn("/stats?reset=1&gate_tasks=1", SCRIPT)
        self.assertIn("liquid-progress-diagnostics.json", SCRIPT)
        self.assertIn("LastFixtureStats = $captureStats", SCRIPT)
        self.assertIn("LastStatusLine = $lastStatusLine", SCRIPT)
        self.assertLess(
            SCRIPT.index("/stats?reset=1&gate_tasks=1"),
            SCRIPT.index("download resume"),
        )
        # Avoid launching a new CLI process on every fast stats poll. First
        # prove payload movement, then query the installed CLI status.
        self.assertNotIn("if ([int]$captureStats.active_distinct_tasks -ge 1 -and", SCRIPT)
        self.assertIn("if ($statusAtCapture -eq 'Downloading' -and [int]$captureStats.active_distinct_tasks -ge 1", SCRIPT)

    def test_status_cli_polling_is_rate_limited_and_capture_revalidates_live_bytes(self):
        self.assertIn("$lastStatusPollMilliseconds = -500", SCRIPT)
        self.assertIn("($elapsedMilliseconds - $lastStatusPollMilliseconds) -ge 500", SCRIPT)
        self.assertIn("BytesAdvancedBeforeScreenshot = $bytesAdvancedBeforeScreenshot", SCRIPT)
        self.assertIn("StatusPollCountAtCapture = $statusPollCount", SCRIPT)
        self.assertIn("if ([int]$preScreenshotStats.active_distinct_tasks -lt 1 -or", SCRIPT)
        self.assertIn("cancel-in-progress: false", UI_WORKFLOW)
        self.assertIn(
            "group: windows-installed-ui-smoke-${{ github.event.pull_request.head.sha || github.sha }}",
            UI_WORKFLOW,
        )

    def test_cli_status_parser_normalizes_terminal_prefixes_and_tab_columns(self):
        # Normalize ANSI/BOM/zero-width prefixes before parsing ID<TAB>Status.
        # Never persist raw table rows, names, paths, or arbitrary CLI output.
        self.assertIn("function Get-NormalizedCliLines", SCRIPT)
        self.assertIn("$line = $line.Replace([string]$character, '')", SCRIPT)
        self.assertIn("$line = $line.Trim()", SCRIPT)
        self.assertIn("[char]0x2060", SCRIPT)
        self.assertIn("codepoints=$codes", SCRIPT)
        self.assertIn("function Get-DownloadStatusFromOutput", SCRIPT)
        self.assertIn("$columns = [regex]::Split($line.Trim(), '\\s+')", SCRIPT)
        self.assertIn("Status row: $($Matches[1])", SCRIPT)
        self.assertIn("Plain-text CLI message: $line", SCRIPT)
        self.assertIn("$columns[0].Trim() -cne [string]$ExpectedId", SCRIPT)
        self.assertEqual(
            SCRIPT.count("Get-DownloadStatusFromOutput -Lines $statusOutput -ExpectedId $taskId"),
            2,
        )
        self.assertIn("Get-DownloadStatusFromOutput -Lines $finalStatusOutput -ExpectedId $taskId", SCRIPT)
        self.assertIn("Task lookup message: $line", SCRIPT)
        self.assertIn("Plain-text CLI message: $line", SCRIPT)
        self.assertIn("Foreground window changed before active Liquid Progress capture.", SCRIPT)
        self.assertIn("GetForegroundWindow", SCRIPT)
        self.assertIn("GetWindowRect", SCRIPT)
        self.assertIn("SetWindowPos", SCRIPT)
        self.assertNotIn("PrimaryScreen.Bounds", SCRIPT)
        self.assertNotIn("StartsWith($statusPrefix", SCRIPT)
        self.assertNotIn("Substring($statusPrefix.Length)", SCRIPT)

    def test_cli_status_probe_captures_sanitized_failure_diagnostics(self):
        self.assertEqual(SCRIPT.count("download show --status-lines $taskId"), 3)
        self.assertNotIn("download show $taskId --status-lines", SCRIPT)
        self.assertIn("$lastStatusCommandExitCode = $LASTEXITCODE", SCRIPT)
        self.assertIn("LastStatusCommandExitCode = $lastStatusCommandExitCode", SCRIPT)
        self.assertIn("LastStatusOutputPreview = $lastStatusOutputPreview", SCRIPT)
        self.assertIn("FinalStatusOutputPreview = $finalStatusOutputPreview", SCRIPT)
        self.assertIn("Get-SanitizedStatusPreview", SCRIPT)
        self.assertIn("No such download", SCRIPT)
        self.assertIn("Formatted table output detected", SCRIPT)
        self.assertIn("LastStatusOutputLineCount = @($statusOutput).Count", SCRIPT)
        self.assertIn("return $preview", SCRIPT)
        self.assertNotIn("return ,$preview", SCRIPT)

    def test_visual_workflow_runs_on_pr_ui_changes_and_uploads_evidence(self):
        self.assertIn("pull_request:", UI_WORKFLOW)
        self.assertIn("needs: change-scope", UI_WORKFLOW)
        self.assertIn("needs.change-scope.outputs.run", UI_WORKFLOW)
        self.assertIn("Run-WindowsLiquidProgressVisualE2E.ps1", UI_WORKFLOW)
        self.assertIn("Parse PowerShell acceptance harnesses before installer build", UI_WORKFLOW)
        self.assertIn("[System.Management.Automation.Language.Parser]::ParseFile", UI_WORKFLOW)
        self.assertLess(
            UI_WORKFLOW.index("Parse PowerShell acceptance harnesses before installer build"),
            UI_WORKFLOW.index("Build Windows installer from same source SHA"),
        )
        self.assertIn("windows-liquid-progress-visual", UI_WORKFLOW)
        self.assertIn("GetForegroundWindow", UI_WORKFLOW)
        self.assertIn("GetWindowRect", UI_WORKFLOW)
        self.assertIn("SetWindowPos", UI_WORKFLOW)
        self.assertIn("CopyFromScreen(", UI_WORKFLOW)
        self.assertNotIn("PrimaryScreen.Bounds", UI_WORKFLOW)
        embedded = UI_WORKFLOW.split("Add-Type -TypeDefinition @'\n", 1)[1].split("\n          '@", 1)[0]
        self.assertTrue(all(line.startswith("          ") for line in embedded.splitlines()))
        scope = UI_WORKFLOW.split('case "$file" in', 1)[1].split("esac", 1)[0]
        self.assertIn("scripts/source-tests/test_hyperget_liquid_progress_source.py", scope)
        self.assertIn("liquid-progress-active.png", SCRIPT)
        for visual_path in (
            "ProgressDownloadPage.kt",
            "ShowDownloadDialogs.kt",
            "SingleDownloadPageStateToPersist.kt",
            "DefaultThemes.kt",
            "DefaultAppSettings.kt",
            "ModernThemePaletteContractTest.kt",
        ):
            self.assertIn(visual_path, UI_WORKFLOW)

    def test_lifecycle_proof_records_exact_checked_out_pr_head(self):
        lifecycle_step = UI_WORKFLOW.split(
            "- name: Verify isolated NSIS same-version reinstall and silent uninstall", 1
        )[1].split("- name: Upload active Liquid Progress screenshot", 1)[0]
        self.assertIn("$sourceSha = (git rev-parse HEAD).Trim()", lifecycle_step)
        self.assertIn("$expectedSourceSha = '${{ github.event.pull_request.head.sha || github.sha }}'", lifecycle_step)
        self.assertIn("if ($sourceSha -ne $expectedSourceSha)", lifecycle_step)
        self.assertIn("-SourceSha $sourceSha -ProofFile $proof", lifecycle_step)
        self.assertNotIn("-SourceSha $env:GITHUB_SHA", lifecycle_step)

    def test_unknown_cli_output_diagnostics_are_bounded_and_nonverbatim(self):
        self.assertIn("Select-Object -First 32", SCRIPT)
        self.assertIn("codepoints=$codes", SCRIPT)
        self.assertNotIn("Unclassified CLI output (characters=$($line.Length)).", SCRIPT)

    def test_fast_source_gate_checks_the_contract_without_building(self):
        self.assertIn("scripts/source-tests/test_hyperget_liquid_progress_source.py", SOURCE_WORKFLOW)
        self.assertIn("test_hyperget_liquid_progress_source.py -v", SOURCE_WORKFLOW)
        self.assertIn("NO APP BUILD", SOURCE_WORKFLOW)


    def test_visual_harness_has_one_complete_body_and_no_truncated_branch(self):
        # Prevent a patch-composition error from appending a second script body
        # or leaving an unterminated status-preview condition.
        self.assertEqual(SCRIPT.count("function Get-SanitizedStatusPreview {"), 1)
        self.assertEqual(SCRIPT.count("$server = $null"), 1)
        self.assertEqual(SCRIPT.count("Start-Process -FilePath 'python'"), 1)
        self.assertEqual(SCRIPT.count("download show --status-lines $taskId"), 3)
        self.assertNotIn(chr(10) + ") {" + chr(10) + "            # Safe in the isolated runner:", SCRIPT)
        self.assertIn("Plain-text CLI message: $line", SCRIPT)
        self.assertTrue(SCRIPT.rstrip().endswith("}"))



    def test_screenshot_requires_enough_fill_to_show_liquid_bubbles(self):
        # A live screenshot at 6% proved transfer but the fill was too short to
        # render the two-bubble treatment. Require at least 25% before capture.
        self.assertIn("$minimumVisualProgressBytes = [long][Math]::Ceiling($expectedBytes * 0.25)", SCRIPT)
        self.assertIn("[long]$preScreenshotStats.bytes_sent -lt $minimumVisualProgressBytes", SCRIPT)
        self.assertIn("MinimumVisualProgressBytes = $minimumVisualProgressBytes", SCRIPT)
        self.assertIn("ProgressFractionAtCapture = [Math]::Round", SCRIPT)
        self.assertIn("BytesSentAtCapture = [long]$preScreenshotStats.bytes_sent", SCRIPT)
        self.assertIn("visible Liquid Progress fill reached", SCRIPT)


    def test_liquid_fill_has_visible_gloss_and_bubbles_without_new_animation_loops(self):
        progress = TABLE.split("private fun ProgressStatus(", 1)[1]
        draw_body = progress.split("onDrawBehind {", 1)[1]
        self.assertIn(".height(7.dp)", progress)
        self.assertIn("val shimmerWidth = (size.width * 0.28f)", progress)
        self.assertIn("Color.White.copy(alpha = 0.42f)", progress)
        self.assertIn("val glossHeight = 1.dp.toPx()", draw_body)
        self.assertIn("Color.White.copy(alpha = 0.16f)", draw_body)
        self.assertIn("radius = 2.dp.toPx()", draw_body)
        self.assertIn("radius = 1.3.dp.toPx()", draw_body)
        self.assertIn("drawCircle(", draw_body)
        self.assertIn("drawWithCache", progress)
        self.assertNotIn("rememberInfiniteTransition", progress)

    def test_reference_visual_parity_light_window_parts_and_graph_toggle(self):
        window = (ROOT / "desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/pages/singleDownloadPage/ShowDownloadDialogs.kt").read_text(encoding="utf-8")
        self.assertIn("LocalMyColors provides DefaultThemes.hyperGetLight", window)
        self.assertIn("val defaultWidth = 1120f", window)
        self.assertIn("val defaultHeight = 465f", window)
        self.assertIn("val safeWidth = (workArea.width / density - 32f)", window)
        self.assertIn("val safeHeight = (workArea.height / density - 48f)", window)
        self.assertIn("val w = defaultWidth.coerceAtMost(safeWidth)", window)
        self.assertIn("val h = requestedHeight.coerceAtMost(safeHeight)", window)
        self.assertIn("resizable = true", window)
        self.assertNotIn("onRequestToggleMaximize = null,", window.split("private fun ProgressWindow(", 1)[1].split("private fun UpdateTaskBar(", 1)[0])
        self.assertIn('append(" — HyperGet")', window)
        self.assertIn("val showSpeedGraph by singleDownloadComponent.showSpeedGraph.collectAsState()", PAGE)
        self.assertIn('"Hide graph"', PAGE)
        self.assertIn('text = "Show graph"', PAGE)
        self.assertIn("RoundedCornerShape(4.dp)", PAGE)
        self.assertIn("compact = true", PAGE)
        self.assertIn(".padding(horizontal = 2.dp)", PAGE)
        self.assertIn("Tabs outside the bordered information panel", PAGE)
        self.assertIn("URL", PAGE)
        self.assertIn("itemState.downloadLink.asStringSource()", PAGE)
        self.assertIn("java.text.NumberFormat.getIntegerInstance()", PAGE)
        self.assertIn("Reference: byte summary on the left, buttons centered", PAGE)
        self.assertNotIn("private fun RenderActions(", PAGE)
        self.assertIn("useEqualWeights = sortedParts.any", PAGE)
        self.assertIn("if (part.status == PartDownloadStatus.Completed)", PAGE)
        self.assertIn("PartDownloadStatus.Completed -> referenceLiquidColors", PAGE)
        self.assertIn("if (index % 2 == 0) Color.White", PAGE)
        self.assertIn("Modifier.width(58.dp).fillMaxHeight()", PAGE)
        self.assertIn("Color(0xFFF7FBFF), Color(0xFFEAF5FF)", PAGE)
        self.assertIn("primary = isResumeSupported", PAGE)

    def test_single_download_page_liquid_track_height_and_specular_gloss(self):
        self.assertIn(".height(42.dp)", PAGE)
        self.assertIn("val glassTop = Brush.verticalGradient(", PAGE)
        self.assertIn("Color.White.copy(alpha = 0.48f)", PAGE)
        self.assertIn("Color.White.copy(alpha = 0.86f)", PAGE)
        self.assertIn("// Glass bubbles have translucent cores", PAGE)

    def test_parts_table_fits_visible_width_without_losing_resizable_columns(self):
        section = PAGE.split("private fun RenderPartInfo(", 1)[1].split("private fun prettifyStatus(", 1)[0]
        self.assertIn("BoxWithConstraints(", section)
        self.assertIn("val usableWidth = (maxWidth - horizontalPadding * 2 - 24.dp)", section)
        self.assertIn("val resizeWidth = usableWidth - 58.dp", section)
        self.assertIn("val totalWidth = (resizeWidth - statusWidth - downloadedWidth)", section)
        self.assertIn("remember(usableWidth)", section)
        self.assertIn("initialCustomSizes = mapOf(", section)
        self.assertIn("PartInfoCells.Status to statusWidth", section)
        self.assertIn("PartInfoCells.Downloaded to downloadedWidth", section)
        self.assertIn("PartInfoCells.Total to totalWidth", section)
        self.assertIn("tableState = fittedTableState", section)
        self.assertIn("CellSize.Resizeable", PAGE)
        self.assertIn("selectVisibleDownloadParts(itemState.parts, onlyActiveParts)", section)

    def test_refined_liquid_density_and_graph_controls(self):
        track = PAGE.split("private fun LiquidProgressTrack(", 1)[1].split("private fun RenderPartInfo(", 1)[0]
        self.assertIn("if (active && fillWidth > size.height * 1.1f)", track)
        self.assertIn("val visibleBubbleCount = bubbleCount.coerceAtMost(", track)
        self.assertIn("repeat(visibleBubbleCount)", track)
        self.assertIn("index * 0.618034f", track)
        self.assertIn("!it.partSpace.isFinite()", PAGE)
        self.assertIn('text = "Hide graph"', PAGE)
        self.assertIn("maxSpeed.toDouble() * tick / 4.0", PAGE)

    def test_reference_liquid_draws_real_crests_and_keeps_shared_animation(self):
        track = PAGE.split("private fun LiquidProgressTrack(", 1)[1].split("private fun RenderPartInfo(", 1)[0]
        self.assertIn(".drawWithCache {", track)
        self.assertIn("clipPath(fillClip)", track)
        self.assertIn("surfaceWave.lineTo(x, y)", track)
        self.assertIn("secondaryWave.lineTo(x, y)", track)
        self.assertIn("referenceLiquidColors", PAGE)
        self.assertIn(".height(30.dp)", PAGE)
        self.assertIn("val fillWidth = size.width * fraction", track)
        self.assertIn("val phase = if (active) sharedPhase.value else 0f", track)
        self.assertNotIn("rememberInfiniteTransition", track)

    def test_contrast_values_meet_visual_minimums_without_new_animation_loops(self):
        # Numeric bounds complement the rendering E2E: string-presence checks
        # alone would pass even if the liquid treatment were dialed back.
        progress = TABLE.split("private fun ProgressStatus(", 1)[1]
        cache_builder = progress.split(".drawWithCache {", 1)[1].split("onDrawBehind {", 1)[0]
        draw_body = progress.split("onDrawBehind {", 1)[1]
        gloss_block = draw_body.split("val glossInset", 1)[1].split("withTransform({", 1)[0]
        bubble_block = draw_body.split("if (fillWidth > 14.dp.toPx())", 1)[1].split(
            "// Restore the rounded ends", 1
        )[0]

        def read_alphas(block):
            marker = "Color.White.copy(alpha = "
            return [float(part.split("f)", 1)[0]) for part in block.split(marker)[1:]]

        shimmer_alphas = read_alphas(cache_builder)
        gloss_alphas = read_alphas(gloss_block)
        bubble_alphas = read_alphas(bubble_block)
        bubble_radii = [
            float(part.split(".dp.toPx()", 1)[0])
            for part in bubble_block.split("radius = ")[1:]
        ]

        self.assertEqual(len(shimmer_alphas), 1)
        self.assertGreaterEqual(shimmer_alphas[0], 0.40)
        self.assertEqual(len(gloss_alphas), 1)
        self.assertGreaterEqual(gloss_alphas[0], 0.15)
        self.assertEqual(len(bubble_alphas), 2)
        self.assertGreaterEqual(bubble_alphas[0], 0.40)
        self.assertGreaterEqual(bubble_alphas[1], 0.28)
        self.assertEqual(len(bubble_radii), 2)
        self.assertGreaterEqual(bubble_radii[0], 2.0)
        self.assertGreaterEqual(bubble_radii[1], 1.3)
        self.assertIn(".height(7.dp)", progress)
        self.assertIn("drawWithCache", progress)
        self.assertNotIn("rememberInfiniteTransition", progress)


if __name__ == "__main__":
    unittest.main()

