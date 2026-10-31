package com.abdownloadmanager.desktop.pages.singleDownloadPage

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.abdownloadmanager.desktop.pages.singleDownloadPage.SingleDownloadPageSections.*
import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.singledownloadpage.SingleDownloadPagePropertyItem
import com.abdownloadmanager.shared.ui.configurable.ConfigurableUiProps
import com.abdownloadmanager.shared.ui.configurable.RenderConfigurable
import com.abdownloadmanager.shared.ui.widget.*
import com.abdownloadmanager.shared.ui.widget.table.customtable.CellSize
import com.abdownloadmanager.shared.ui.widget.table.customtable.Table
import com.abdownloadmanager.shared.ui.widget.table.customtable.TableCell
import com.abdownloadmanager.shared.ui.widget.table.customtable.TableState
import com.abdownloadmanager.shared.util.LocalSizeUnit
import com.abdownloadmanager.shared.util.convertPositiveSizeToHumanReadable
import com.abdownloadmanager.shared.util.div
import com.abdownloadmanager.shared.util.downloaderror.DownloadErrorReason
import com.abdownloadmanager.shared.util.ui.*
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import com.abdownloadmanager.shared.util.ui.theme.myShapes
import com.abdownloadmanager.shared.util.ui.theme.mySpacings
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import com.abdownloadmanager.shared.util.ui.theme.LocalTextSizes
import com.abdownloadmanager.shared.util.ui.widget.MyIcon
import ir.amirab.downloader.downloaditem.DownloadJobStatus
import ir.amirab.downloader.monitor.*
import ir.amirab.downloader.part.PartDownloadStatus
import ir.amirab.downloader.utils.ExceptionUtils
import ir.amirab.util.compose.IconSource
import ir.amirab.util.compose.StringSource
import ir.amirab.util.compose.asStringSource
import ir.amirab.util.compose.resources.myStringResource
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay

enum class SingleDownloadPageSections(
    val title: StringSource,
    val icon: IconSource,
) {
    Info(
        Res.string.info.asStringSource(),
        MyIcons.info
    ),
    Settings(
        Res.string.speed.asStringSource(),
        MyIcons.fast,
    ),
    OnCompletion(
        Res.string.on_completion.asStringSource(),
        MyIcons.flag
    ),
}

private val tabs = entries.toList()

// All visible liquid tracks share one animation clock rather than running a
// separate infinite transition for each active connection/part.
// Read the State in the Canvas draw phase to avoid recomposing the entire page
// at the animation frame rate.
private val LocalLiquidAnimationPhase = compositionLocalOf<State<Float>> { mutableFloatStateOf(0f) }

// The reference is an electric-blue-to-cyan glass liquid, not a flat theme gradient.
// Non-downloading statuses still keep their semantic colors below.
private val referenceLiquidColors = listOf(
    Color(0xFF122AEF), Color(0xFF143CF5), Color(0xFF0766FC),
    Color(0xFF0795FB), Color(0xFF00C5F5), Color(0xFF00DFEB),
)

@Composable
fun ProgressDownloadPage(
    singleDownloadComponent: DesktopSingleDownloadComponent,
    itemState: ProcessingDownloadItemState
) {
    var selectedTab by remember { mutableStateOf(Info) }
    val showSpeedGraph by singleDownloadComponent.showSpeedGraph.collectAsState()
    val showPartInfo by singleDownloadComponent.showPartInfo.collectAsState()
    val setShowPartInfo = singleDownloadComponent::setShowPartInfo
    val downloadHasError by singleDownloadComponent.lastError.collectAsState()
    val resizingState = LocalSingleDownloadPageSizing.current
    val horizontalPadding = 16.dp
    // Refined Windows type scale for the approved file-download dialog.
    // Stay within this window: users' global font/scale settings still apply.
    val downloadTextSizes = myTextSizes.copy(
        sm = 12.sp, base = 14.sp, lg = 16.sp, xl = 18.sp,
    )

    // Only sample actual monitor speeds while this window is composed.
    // Bounded at 90 seconds, including zero during pause/retry.
    val speedHistory = remember(itemState.id) { mutableStateListOf<Long>() }
    val currentObservedSpeed by rememberUpdatedState(
        if (itemState.status == DownloadJobStatus.Downloading) itemState.speed.coerceAtLeast(0L) else 0L
    )
    val samplingActive = itemState.status == DownloadJobStatus.Downloading
    LaunchedEffect(itemState.id, samplingActive) {
        if (!samplingActive) {
            // Preserve a final zero reading in the graph, without running a
            // forever-idle one-second timer after pause, retry, or completion.
            if (speedHistory.lastOrNull() != 0L) {
                speedHistory.add(0L)
                if (speedHistory.size > 90) speedHistory.removeAt(0)
            }
            return@LaunchedEffect
        }
        while (true) {
            speedHistory.add(currentObservedSpeed)
            if (speedHistory.size > 90) speedHistory.removeAt(0)
            delay(1_000)
        }
    }

    val transferStatus = itemState.status
    val hasMovingLiquid = transferStatus == DownloadJobStatus.Downloading ||
        transferStatus == DownloadJobStatus.Resuming ||
        transferStatus is DownloadJobStatus.PreparingFile ||
        itemState.parts.any { part ->
            part.status == PartDownloadStatus.ReceivingData ||
                part.status == PartDownloadStatus.Connecting
        }
    val liquidPhaseState: State<Float> = if (hasMovingLiquid) {
        val sharedTransition = rememberInfiniteTransition()
        sharedTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            )
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    LaunchedEffect(resizingState.resizingPartInfo) {
        if (resizingState.partInfoHeight <= 0.dp) {
            setShowPartInfo(false)
        }
    }
    CompositionLocalProvider(
        LocalLiquidAnimationPhase provides liquidPhaseState,
        LocalTextSizes provides downloadTextSizes,
        LocalTextStyle provides LocalTextStyle.current.copy(
            fontSize = downloadTextSizes.base,
            lineHeight = 18.sp,
        ),
    ) {
    Column(Modifier.fillMaxSize().background(Color(0xFFF4F7FB))) {
        // Tabs outside the bordered information panel, like the approved design.
        MyTabRow {
            for (tab in tabs) {
                MyTab(
                    selected = tab == selectedTab,
                    onClick = { selectedTab = tab },
                    icon = tab.icon,
                    title = tab.title,
                    // The approved IDM-style layout uses clean text-only tabs.
                    showIcon = false,
                    showSelectionUnderline = true,
                )
            }
        }
        val scrollState = rememberScrollState()
        // The information panel always starts at its own top when changing tabs.
        LaunchedEffect(selectedTab) { scrollState.scrollTo(0) }
        val panelShape = RoundedCornerShape(10.dp)
        // Info keeps room for the live chart. Speed and On Completion have only
        // two controls each; reclaim their unused space for the parts table.
        // The scrolling content still protects long labels and higher DPI.
        val panelHeight = when (selectedTab) {
            Info -> if (showSpeedGraph) 232.dp else 196.dp
            Settings, OnCompletion -> 144.dp
        }
        Column(
            Modifier
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(panelShape)
                .border(1.dp, Color(0xFFD7E0EB), panelShape)
                .background(Color.White)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(panelHeight)
                    .verticalScroll(scrollState)
            ) {
                when (selectedTab) {
                    Info -> RenderInfo(
                        modifier = Modifier,
                        horizontalPadding = horizontalPadding,
                        singleDownloadComponent = singleDownloadComponent,
                        speedHistory = speedHistory,
                        itemState = itemState,
                        showGraph = showSpeedGraph,
                        onToggleGraph = { singleDownloadComponent.setShowSpeedGraph(!showSpeedGraph) },
                    )
                    Settings -> RenderSettings(
                        modifier = Modifier.padding(end = 12.dp),
                        horizontalPadding = horizontalPadding,
                        singleDownloadComponent = singleDownloadComponent,
                    )
                    OnCompletion -> RenderOnCompletion(
                        modifier = Modifier.padding(end = 12.dp),
                        horizontalPadding = horizontalPadding,
                        singleDownloadComponent = singleDownloadComponent,
                    )
                }
                MultiplatformVerticalScrollbar(
                    adapter = rememberScrollbarAdapter(scrollState),
                    modifier = Modifier.matchParentSize().wrapContentWidth(Alignment.End),
                )
            }
        }
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(myColors.onBackground / 0.15f)
        )
        Column(
            Modifier.weight(1f).background(Color(0xFFF4F7FB))
        ) {
            Column(Modifier.padding(horizontal = horizontalPadding)) {
                Spacer(Modifier.height(9.dp))
                RenderProgressBar(itemState)
                Spacer(Modifier.height(8.dp))
                RenderTransferSummary(
                    itemState = itemState,
                    singleDownloadComponent = singleDownloadComponent,
                    showingPartInfo = showPartInfo,
                    onRequestShowPartInfo = setShowPartInfo,
                    downloadErrorReason = downloadHasError,
                    onRequestShowDownloadError = singleDownloadComponent::openDownloadErrorPage,
                )
                Spacer(Modifier.height(7.dp))
            }
            if (showPartInfo) {
                RenderPartInfo(
                    modifier = Modifier.weight(1f),
                    itemState = itemState,
                    horizontalPadding = horizontalPadding,
                )
            }
        }
    }
    }
}


/**
 * Match the approved transfer strip; percentage is in the native title only.
 */
@Composable
private fun RenderTransferSummary(
    itemState: ProcessingDownloadItemState,
    singleDownloadComponent: DesktopSingleDownloadComponent,
    showingPartInfo: Boolean,
    onRequestShowPartInfo: (Boolean) -> Unit,
    downloadErrorReason: DownloadErrorReason?,
    onRequestShowDownloadError: () -> Unit,
) {
    val units = LocalSizeUnit.current
    val downloaded = convertPositiveSizeToHumanReadable(itemState.progress.coerceAtLeast(0L), units).rememberString()
    val total = itemState.contentLength.takeIf { it > 0L }?.let {
        convertPositiveSizeToHumanReadable(it, units).rememberString()
    } ?: "Unknown"
    val currentSpeed = if (itemState.status == DownloadJobStatus.Downloading) itemState.speed.coerceAtLeast(0L) else 0L
    val speed = convertPositiveSizeToHumanReadable(currentSpeed, units).rememberString()
    val remaining = itemState.remainingTime?.takeIf { it >= 0L && itemState.status == DownloadJobStatus.Downloading }?.let { seconds ->
        val hours = seconds / 3600
        val mins = (seconds % 3600 / 60).toString().padStart(2, '0')
        val secs = (seconds % 60).toString().padStart(2, '0')
        if (hours > 0) "${hours}:${mins}:${secs}" else "${mins}:${secs}"
    } ?: "—"
    // Reference: byte summary on the left, buttons centered, ETA and speed on the right.
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${downloaded} of ${total}",
                fontSize = myTextSizes.base,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(5.dp))
            PartInfoButton(showingPartInfo, onRequestShowPartInfo)
            downloadErrorReason?.let {
                Spacer(Modifier.width(4.dp))
                DownloadErrorInfoButton(it, onRequestShowDownloadError)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToggleButton(
                itemState = itemState,
                toggle = singleDownloadComponent::toggle,
                pause = singleDownloadComponent::pause,
            )
            Spacer(Modifier.width(10.dp))
            CancelButton(
                cancel = singleDownloadComponent::cancel,
                icon = if (singleDownloadComponent.deletePartialFileOnDownloadCancellation.collectAsState().value) {
                    MyIcons.stop
                } else null,
            )
        }
        Text(
            "${remaining} remaining  (${speed}/s)",
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            fontSize = myTextSizes.base,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RenderSettings(
    modifier: Modifier,
    horizontalPadding: Dp,
    singleDownloadComponent: DesktopSingleDownloadComponent,
) {
    Column(modifier) {
        for (configurable in singleDownloadComponent.settings) {
            RenderConfigurable(
                configurable,
                ConfigurableUiProps(
                    modifier = Modifier
                        // I'm using Configurable object which their renderer by default uses 8.dp, we want 16.dp, so I only add 8.dp here 16-8 == 8
                        // I may improve this later
                        .padding(horizontal = (horizontalPadding - 8.dp).coerceAtLeast(0.dp))
                )
            )
        }
    }
}

/**
 * Real B/s samples from ProcessingDownloadItemState.speed.
 * No simulated waveform or unbounded per-download samples.
 */
@Composable
private fun RealSpeedHistoryChart(
    history: List<Long>, modifier: Modifier = Modifier,
    onHideGraph: () -> Unit,
) {
    // Actual monitor byte deltas; sampled once per second with a hard 90-s cap.
    val samples = history.toList()
    val observedMaxSpeed = samples.maxOrNull() ?: 0L
    val maxSpeed = observedMaxSpeed.coerceAtLeast(1L)
    val currentSpeed = samples.lastOrNull() ?: 0L
    val speedUnit = LocalSizeUnit.current
    val lineColor = Color(0xFF11B9E8)
    val fillColor = Color(0xFF1D9DEE).copy(alpha = 0.30f)
    val gridColor = myColors.onBackground / 10
    val currentText = convertPositiveSizeToHumanReadable(currentSpeed, speedUnit).rememberString()
    val peakText = convertPositiveSizeToHumanReadable(observedMaxSpeed, speedUnit).rememberString()
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "Transfer rate",
                fontSize = myTextSizes.base,
                fontWeight = FontWeight.SemiBold,
                color = myColors.onBackground,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$currentText/s",
                    fontSize = myTextSizes.base,
                    fontWeight = FontWeight.Medium,
                    color = myColors.onBackground,
                )
                Spacer(Modifier.width(8.dp))
                ActionButton(
                    text = "Hide graph",
                    onClick = onHideGraph,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().height(154.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.width(58.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                for (tick in 4 downTo 0) {
                    Text(
                        convertPositiveSizeToHumanReadable((maxSpeed.toDouble() * tick / 4.0).toLong(), speedUnit).rememberString(),
                        fontSize = myTextSizes.sm,
                        color = myColors.onBackground / 0.70f,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            Canvas(
                Modifier.weight(1f).fillMaxHeight()
                    .clip(myShapes.defaultRounded)
                    .background(Brush.verticalGradient(
                        listOf(Color(0xFFF7FBFF), Color(0xFFEAF5FF))
                    ))
            ) {
            repeat(4) { index ->
                val y = size.height * (index + 1) / 5f
                val x = size.width * (index + 1) / 5f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            }
            if (samples.isEmpty()) return@Canvas
            val top = size.height * 0.08f
            val bottom = size.height * 0.94f
            val points = samples.mapIndexed { index, bytes ->
                val x = if (samples.size == 1) 0f else size.width * index / (samples.size - 1).toFloat()
                val fraction = (bytes.toDouble() / maxSpeed.toDouble()).toFloat().coerceIn(0f, 1f)
                Offset(x, bottom - fraction * (bottom - top))
            }
            if (points.size == 1) {
                drawCircle(lineColor, 2.dp.toPx(), points.first())
                return@Canvas
            }
            val curve = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (index in 1 until points.size) {
                    val left = points[index - 1]
                    val right = points[index]
                    quadraticBezierTo(left.x, left.y, (left.x + right.x) / 2f, (left.y + right.y) / 2f)
                }
                lineTo(points.last().x, points.last().y)
            }
            val area = Path().apply {
                addPath(curve)
                lineTo(points.last().x, bottom)
                lineTo(points.first().x, bottom)
                close()
            }
            drawPath(area, brush = Brush.verticalGradient(listOf(fillColor, Color.Transparent), startY = top, endY = bottom))
            drawPath(curve, color = lineColor, style = Stroke(width = 2.dp.toPx()))
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            "Peak: $peakText/s  •  Last 90 seconds",
            fontSize = myTextSizes.sm,
            color = myColors.onBackground / 0.76f,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RenderOnCompletion(
    modifier: Modifier,
    horizontalPadding: Dp,
    singleDownloadComponent: DesktopSingleDownloadComponent,
) {
    Column(modifier) {
        for (configurable in singleDownloadComponent.onCompletion) {
            RenderConfigurable(
                configurable, ConfigurableUiProps(
                    modifier = Modifier
                        // I'm using Configurable object which their renderer by default uses 8.dp, we want 16.dp, so I only add 8.dp here 16-8 == 8
                        // I may improve this later
                        .padding(horizontal = (horizontalPadding - 8.dp).coerceAtLeast(0.dp))
                )
            )
        }
    }
}


@Composable
private fun RenderProgressBar(itemState: IDownloadItemState) {
    val progress = when (itemState) {
        is CompletedDownloadItemState -> 100
        is ProcessingDownloadItemState -> when (val status = itemState.status) {
            is DownloadJobStatus.PreparingFile -> status.percent
            else -> itemState.percent
        }
    }?.let {
        it / 100f
    }

    val status = itemState.statusOrFinished()
    val liquidColors = when (status) {
        is DownloadJobStatus.Finished -> myColors.primaryGradientColors
        is DownloadJobStatus.Canceled -> if (ExceptionUtils.isNormalCancellation(status.e)) {
            listOf(myColors.warning, myColors.secondary)
        } else {
            listOf(myColors.error, myColors.warning)
        }

        DownloadJobStatus.IDLE -> listOf(myColors.warning, myColors.secondary)
        is DownloadJobStatus.Retrying -> listOf(myColors.error, myColors.warning)
        DownloadJobStatus.Finished -> myColors.primaryGradientColors
        is DownloadJobStatus.PreparingFile -> listOf(myColors.info, myColors.secondary)
        DownloadJobStatus.Resuming,
        DownloadJobStatus.Downloading,
            -> myColors.primaryGradientColors
    }

    LiquidProgressTrack(
        progress = progress,
        colors = if (status == DownloadJobStatus.Downloading ||
            status == DownloadJobStatus.Resuming || status is DownloadJobStatus.PreparingFile
        ) referenceLiquidColors else liquidColors,
        // A retry timer owns no network transfer and must not animate like one.
        active = status == DownloadJobStatus.Downloading ||
            status == DownloadJobStatus.Resuming ||
            status is DownloadJobStatus.PreparingFile,
        modifier = Modifier.fillMaxWidth().height(42.dp),
        bubbleCount = 18,
        showCompletionIcon = true,
    )
}

/**
 * Theme-aware liquid progress used by both progress lines in the download
 * window. The wave and bubbles are clipped to the downloaded portion and the
 * palette changes immediately when the application theme changes.
 * Visual specification target: file-download-window-approved-2026-10-17.webp.
 */
@Composable
private fun LiquidProgressTrack(
    progress: Float?,
    colors: List<Color>,
    active: Boolean,
    modifier: Modifier,
    bubbleCount: Int,
    compact: Boolean = false,
    showCompletionIcon: Boolean = false,
) {
    // Downloaded bytes, not the animation phase, determine the filled width.
    val animatedProgress = animateFloatAsState(
        targetValue = progress?.coerceIn(0f, 1f) ?: 0f,
        animationSpec = tween(180, easing = LinearEasing),
    )
    val sharedPhase = LocalLiquidAnimationPhase.current
    val trackShape = if (compact) RoundedCornerShape(4.dp) else CircleShape

    Box(
        modifier
            .clip(trackShape)
            .background(Brush.horizontalGradient(listOf(Color(0xFFEAF0F7), Color(0xFFDDE5F0))))
            .border(if (compact) 0.75.dp else 1.dp, Color(0xFFBFCAD9), trackShape)
    ) {
        Spacer(
            Modifier.fillMaxSize().drawWithCache {
                // All brushes and mutable paths are created on cache invalidation,
                // not on each phase tick. All tracks share the outer animation clock.
                val liquidBrush = Brush.horizontalGradient(
                    colors = colors,
                    startX = 0f,
                    endX = size.width.coerceAtLeast(1f),
                )
                val glassTop = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.48f),
                        Color.White.copy(alpha = 0.11f),
                        Color.Transparent,
                    ),
                    startY = 0f,
                    endY = size.height * 0.34f,
                )
                val glassDepth = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0xFF06265C).copy(alpha = 0.24f)),
                    startY = size.height * 0.4f,
                    endY = size.height,
                )
                val fillClip = Path()
                val surfaceWave = Path()
                val secondaryWave = Path()

                onDrawBehind {
                    if (size.width <= 0f || size.height <= 0f || (progress == null && !active)) {
                        return@onDrawBehind
                    }
                    val phase = if (active) sharedPhase.value else 0f
                    val fraction = if (progress == null) {
                        // An unknown-size transfer uses a traveling capsule.
                        // Known-size transfers never use this animation for progress.
                        0.32f
                    } else animatedProgress.value.coerceIn(0f, 1f)
                    val fillWidth = size.width * fraction
                    if (fillWidth <= 0f) return@onDrawBehind

                    val radius = (if (compact) 4.dp.toPx() else size.height / 2f)
                        .coerceAtMost(fillWidth / 2f)
                    val startX = if (progress == null) {
                        (size.width - fillWidth) * phase.coerceIn(0f, 1f)
                    } else 0f
                    val finishX = startX + fillWidth
                    fillClip.reset()
                    fillClip.addRoundRect(
                        RoundRect(startX, 0f, finishX, size.height, CornerRadius(radius))
                    )
                    clipPath(fillClip) {
                        // Scale a stable cached horizontal shader so cyan appears
                        // at the actual fluid front even at low completion percentages.
                        withTransform({
                            translate(left = startX, top = 0f)
                            scale(scaleX = fraction, scaleY = 1f, pivot = Offset.Zero)
                        }) {
                            drawRect(brush = liquidBrush, size = Size(size.width, size.height))
                        }
                        drawRect(brush = glassDepth)
                        drawRect(brush = glassTop)

                        // Two traveling crests inside the fluid, matching the
                        // brighter rolling surface visible in the approved image.
                        // The geometry is clipped to the real downloaded fraction.
                        surfaceWave.reset()
                        surfaceWave.moveTo(startX, 0f)
                        surfaceWave.lineTo(finishX, 0f)
                        for (step in 24 downTo 0) {
                            val x = startX + fillWidth * step / 24f
                            val angle = (step / 24f * PI * 3.5 + phase * PI * 2.0)
                            val y = size.height * (0.19f + sin(angle).toFloat() * 0.09f)
                            surfaceWave.lineTo(x, y)
                        }
                        surfaceWave.close()
                        drawPath(surfaceWave, color = Color.White.copy(alpha = 0.20f))

                        secondaryWave.reset()
                        secondaryWave.moveTo(startX, 0f)
                        secondaryWave.lineTo(finishX, 0f)
                        for (step in 24 downTo 0) {
                            val x = startX + fillWidth * step / 24f
                            val angle = (step / 24f * PI * 4.0 - phase * PI * 2.0)
                            val y = size.height * (0.27f + sin(angle).toFloat() * 0.08f)
                            secondaryWave.lineTo(x, y)
                        }
                        secondaryWave.close()
                        drawPath(secondaryWave, color = Color(0xFFB3FFFF).copy(alpha = 0.15f))

                        // Keep completed/paused parts still and uncluttered. Bubble
                        // density follows the actual filled width, so a narrow part
                        // never receives the same number as the full-size track.
                        if (active && fillWidth > size.height * 1.1f) {
                            val visibleBubbleCount = bubbleCount.coerceAtMost(
                                (fillWidth / (size.height * if (compact) 0.50f else 0.60f))
                                    .toInt().coerceAtLeast(0)
                            )
                            repeat(visibleBubbleCount) { index ->
                                // Golden-ratio spacing avoids obvious rows or clumps.
                                // The small sinusoidal drift is continuous at phase wrap.
                                val drift = (
                                    index * 0.618034f + 0.13f +
                                        sin(phase.toDouble() * PI * 2.0 + index).toFloat() * 0.018f
                                ) % 1f
                                val lift = (index * 0.371f + phase * (0.58f + index % 3 * 0.13f)) % 1f
                                val bubbleRadius = size.height * (0.058f + (index % 4) * 0.026f)
                                val center = Offset(
                                    x = startX + fillWidth * (0.08f + drift * 0.84f),
                                    y = size.height * (0.88f - lift * 0.74f),
                                )
                                drawCircle(Color.White.copy(alpha = 0.11f), bubbleRadius * 1.6f, center)
                                drawCircle(Color(0xFFB1F9FF).copy(alpha = 0.12f), bubbleRadius * 0.95f, center)
                                drawCircle(
                                    color = Color.White.copy(alpha = if (index % 3 == 0) 0.66f else 0.43f),
                                    radius = bubbleRadius,
                                    center = center,
                                    style = Stroke(width = (bubbleRadius * 0.13f).coerceAtLeast(0.6f)),
                                )
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.86f),
                                    radius = bubbleRadius * 0.11f,
                                    center = Offset(center.x - bubbleRadius * 0.31f, center.y - bubbleRadius * 0.33f),
                                )
                            }
                        }
                    }
                    // A thin glass edge helps the fluid stand out on light backgrounds.
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.48f),
                        topLeft = Offset(startX, 0f),
                        size = Size(fillWidth, size.height),
                        cornerRadius = CornerRadius(radius),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            }
        )

        if (showCompletionIcon && progress != null && progress >= 1f) {
            MyIcon(
                MyIcons.check,
                null,
                Modifier
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(myColors.onBackground)
                    .padding(1.dp)
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd),
                tint = myColors.background,
            )
        }
    }
}

@Composable
private fun RenderPartInfo(
    modifier: Modifier,
    itemState: ProcessingDownloadItemState,
    horizontalPadding: Dp,
) {
    Column(
        modifier
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFD7E0EB), RoundedCornerShape(8.dp))
            .background(Color.White)
    ) {
        // The approved reference displays completed and waiting parts by default.
        var onlyActiveParts by rememberSaveable(itemState.id) { mutableStateOf(false) }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Connections and downloaded parts",
                fontSize = myTextSizes.base,
                fontWeight = FontWeight.SemiBold,
            )
            val connected = itemState.parts.count { it.status == PartDownloadStatus.ReceivingData }
            Text(
                "${itemState.parts.size} parts • $connected connected",
                fontSize = myTextSizes.sm,
                color = myColors.onBackground / 0.78f,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.onClick { onlyActiveParts = !onlyActiveParts },
            ) {
                Text("Only active", fontSize = myTextSizes.sm)
                Spacer(Modifier.width(4.dp))
                CheckBox(onlyActiveParts, { onlyActiveParts = it })
            }
        }
        // Keep the segmented bars and table in sync when Only active is selected.
        val visibleParts = remember(itemState.parts, onlyActiveParts) {
            selectVisibleDownloadParts(itemState.parts, onlyActiveParts)
        }
        val singleDownloadPageSizing = LocalSingleDownloadPageSizing.current
        val mutableInteractionSource = remember { MutableInteractionSource() }
        val isDraggingHandle by mutableInteractionSource.collectIsDraggedAsState()
        LaunchedEffect(isDraggingHandle) {
            singleDownloadPageSizing.resizingPartInfo = isDraggingHandle
        }
        Column(
            Modifier.weight(1f)
        ) {
            RenderParts(
                visibleParts.map { it.value },
                Modifier
                    .padding(horizontal = horizontalPadding)
                    .height(20.dp)
            )
            BoxWithConstraints(
                Modifier.weight(1f)
            ) {
                // Table columns must fit the visible download-details width.
                // Their old 58+310+350+300dp defaults overflowed a 1024px
                // display, hiding the Total column and horizontal scrollbar.
                // Keep the existing resizable Table and status rendering.
                val usableWidth = (maxWidth - horizontalPadding * 2 - 24.dp)
                    .coerceAtLeast(608.dp)
                val resizeWidth = usableWidth - 58.dp
                val statusWidth = (resizeWidth * 0.35f).coerceIn(190.dp, 440.dp)
                val downloadedWidth = (resizeWidth * 0.35f).coerceIn(190.dp, 450.dp)
                val totalWidth = (resizeWidth - statusWidth - downloadedWidth)
                    .coerceIn(170.dp, 410.dp)
                val fittedTableState = remember(usableWidth) {
                    TableState<IndexedValue<UiPart>, PartInfoCells>(
                        cells = PartInfoCells.all(),
                        initialCustomSizes = mapOf(
                            PartInfoCells.Status to statusWidth,
                            PartInfoCells.Downloaded to downloadedWidth,
                            PartInfoCells.Total to totalWidth,
                        ),
                    )
                }
                Table(
                    list = visibleParts,
                    key = {
                        it.value.id
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(5.dp))
                        .border(1.dp, Color(0xFFD7E0EB), RoundedCornerShape(5.dp)),
                    wrapHeader = {
                        WithContentAlpha(0.85f) {
                            CompositionLocalProvider(
                                LocalTextStyle provides LocalTextStyle.current.copy(
                                    fontSize = myTextSizes.sm,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 16.sp,
                                ),
                            ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = horizontalPadding)
                                    .background(Color(0xFFF0F5FB))
                                    .heightIn(min = 30.dp)
                                    .padding(vertical = 4.dp)
                            ) {
                                it()
                            }
                            }
                        }
                    },
                    tableState = fittedTableState,
                    wrapItem = { index, _, content ->
                        WithContentAlpha(1f) {
                            val interactionSource = remember { MutableInteractionSource() }
                            val isHovered by interactionSource.collectIsHoveredAsState()
                            Box(
                                Modifier
                                    .padding(horizontal = horizontalPadding)
                                    .hoverable(interactionSource)
                                    .heightIn(min = 32.dp)
                                    .background(
                                        if (isHovered) Color(0xFFE0EFFF)
                                        else if (index % 2 == 0) Color.White else Color(0xFFF6F9FD)
                                    )
                            ) {
                                content()
                            }
                        }
                    }
                ) { cell, it ->
                    when (cell) {
                        PartInfoCells.Number -> {
                            SimpleCellText("${it.index + 1}")
                        }

                        PartInfoCells.Status -> {
                            val stateColor = when (it.value.status) {
                                PartDownloadStatus.ReceivingData, PartDownloadStatus.Connecting -> Color(0xFF0067DD)
                                PartDownloadStatus.Completed -> myColors.success
                                is PartDownloadStatus.Canceled -> myColors.error
                                PartDownloadStatus.IDLE -> myColors.onBackground / 0.68f
                            }
                            SimpleCellText(
                                prettifyStatus(it.value.status).rememberString(),
                                stateColor,
                                fontWeight = FontWeight.Medium,
                            )
                        }

                        PartInfoCells.Downloaded -> {
                            SimpleCellText(
                                convertPositiveSizeToHumanReadable(
                                    it.value.howMuchProceed,
                                    LocalSizeUnit.current
                                ).rememberString()
                            )
                        }

                        PartInfoCells.Total -> {
                            SimpleCellText(
                                it.value.length?.let { length ->
                                    convertPositiveSizeToHumanReadable(length, LocalSizeUnit.current).rememberString()
                                } ?: myStringResource(Res.string.unknown),
                            )
                        }
                    }
                }
            }
        }
        Handle(
            Modifier.fillMaxWidth().height(8.dp),
            orientation = Orientation.Vertical,
            interactionSource = mutableInteractionSource
        ) {
            singleDownloadPageSizing.partInfoHeight += it
        }
    }
}

private fun prettifyStatus(status: PartDownloadStatus): StringSource {
    return when (status) {
        is PartDownloadStatus.Canceled -> Res.string.disconnected
        PartDownloadStatus.IDLE -> Res.string.idle
        PartDownloadStatus.Completed -> Res.string.finished
        PartDownloadStatus.ReceivingData -> Res.string.downloading
        PartDownloadStatus.Connecting -> Res.string.connecting
    }.asStringSource()
}

@Composable
private fun SimpleCellText(
    text: String,
    color: Color = LocalContentColor.current,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    Text(
        text,
        fontSize = myTextSizes.base,
        fontWeight = fontWeight,
        lineHeight = 18.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = color,
    )
}

sealed class PartInfoCells : TableCell<IndexedValue<UiPart>> {
    data object Number : PartInfoCells() {
        override val id: String = "#"
        override val name: StringSource = "#".asStringSource()
        override val size: CellSize = CellSize.Fixed(58.dp)
    }

    data object Status : PartInfoCells() {
        override val id: String = "Status"
        override val name: StringSource = Res.string.status.asStringSource()
        override val size: CellSize = CellSize.Resizeable(190.dp..440.dp, defaultWidth = 310.dp)
    }

    data object Downloaded : PartInfoCells() {
        override val id: String = "Downloaded"
        override val name: StringSource = Res.string.parts_info_downloaded_size.asStringSource()
        override val size: CellSize = CellSize.Resizeable(190.dp..450.dp, defaultWidth = 350.dp)
    }

    data object Total : PartInfoCells() {
        override val id: String = "Total"
        override val name: StringSource = Res.string.parts_info_total_size.asStringSource()
        override val size: CellSize = CellSize.Resizeable(170.dp..410.dp, defaultWidth = 300.dp)
    }

    companion object {
        fun all(): List<PartInfoCells> {
            return listOf(
                Number,
                Status,
                Downloaded,
                Total,
            )
        }
    }
}


@Composable
private fun RenderPropertyItem(propertyItem: SingleDownloadPagePropertyItem, highlightStatus: Boolean = false) {
    val title = propertyItem.name
    val value = propertyItem.value
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        WithContentAlpha(0.75f) {
            Text(
                text = "${title.rememberString()}:",
                modifier = Modifier.weight(0.3f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 18.sp,
            )
        }
        WithContentAlpha(1f) {
            Text(
                text = value.rememberString(),
                modifier = Modifier
                    .basicMarquee(
                        iterations = Int.MAX_VALUE
                    )
                    .weight(0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = myTextSizes.base,
                fontWeight = FontWeight.Medium,
                lineHeight = 18.sp,
                color = when {
                    highlightStatus -> Color(0xFF0065DB)
                    propertyItem.valueState == SingleDownloadPagePropertyItem.ValueType.Normal -> LocalContentColor.current
                    propertyItem.valueState == SingleDownloadPagePropertyItem.ValueType.Error -> myColors.error
                    else -> myColors.success
                }
            )
        }
    }
}

@Composable
private fun RenderInfo(
    modifier: Modifier,
    horizontalPadding: Dp,
    singleDownloadComponent: DesktopSingleDownloadComponent,
    speedHistory: List<Long>,
    itemState: ProcessingDownloadItemState,
    showGraph: Boolean,
    onToggleGraph: () -> Unit,
) {
    // Approved Liquid window: file details on the left and a real chart on
    // the right, without hiding the transfer rate behind the Settings tab.
    Row(
        modifier.padding(horizontal = horizontalPadding).padding(top = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(if (showGraph) 0.60f else 1f)) {
            val properties = singleDownloadComponent.extraDownloadProgressInfo.collectAsState().value
            properties.forEachIndexed { index, property ->
                Spacer(Modifier.height(3.dp))
                val displayProperty = when (index) {
                    2 -> if (itemState.contentLength > 0L) property.copy(
                        value = "${property.value.rememberString()}  (${java.text.NumberFormat.getIntegerInstance().format(itemState.contentLength)} bytes)".asStringSource()
                    ) else property
                    3 -> property.copy(
                        value = "${property.value.rememberString()}  (${java.text.NumberFormat.getIntegerInstance().format(itemState.progress)} bytes)".asStringSource()
                    )
                    else -> property
                }
                RenderPropertyItem(displayProperty, highlightStatus = index == 1 &&
                    itemState.status == DownloadJobStatus.Downloading)
                if (index == 0) {
                    Spacer(Modifier.height(3.dp))
                    RenderPropertyItem(
                        SingleDownloadPagePropertyItem(
                            "URL".asStringSource(), itemState.downloadLink.asStringSource()
                        )
                    )
                }
            }
        }
        if (showGraph) {
            Spacer(Modifier.width(12.dp))
            RealSpeedHistoryChart(speedHistory, Modifier.weight(0.40f), onHideGraph = onToggleGraph)
        } else {
            Spacer(Modifier.width(8.dp))
            ActionButton(
                text = "Show graph",
                onClick = onToggleGraph,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}


@Composable
private fun PartInfoButton(
    showing: Boolean,
    onClick: (Boolean) -> Unit,
) {
    val partsInfoTitle = Res.string.parts_info.asStringSource()
    Tooltip(partsInfoTitle) {
        IconActionButton(
            onClick = {
                onClick(!showing)
            },
            contentDescription = partsInfoTitle,
            icon = if (showing) {
                MyIcons.up
            } else {
                MyIcons.down
            }
        )
    }
}

@Composable
private fun DownloadErrorInfoButton(
    downloadErrorReason: DownloadErrorReason,
    onClick: () -> Unit,
) {
    val description = downloadErrorReason.title.asStringSource()
    Tooltip(description) {
        IconActionButton(
            onClick = {
                onClick()
            },
            contentDescription = description,
            icon = MyIcons.question,
            contentColor = myColors.error,
        )
    }
}

@Composable
private fun SingleDownloadPageButton(
    onClick: () -> Unit,
    text: String,
    color: Color = LocalContentColor.current,
    icon: IconSource? = null,
    primary: Boolean = false,
) {
    CompositionLocalProvider(
        LocalTextStyle provides LocalTextStyle.current.copy(fontWeight = FontWeight.SemiBold),
    ) {
    ActionButton(
        text = text,
        modifier = Modifier.widthIn(min = if (primary) 156.dp else 136.dp),
        start = {
            icon?.let {
                Row {
                    MyIcon(it, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                }
            }
        },
        contentPadding = PaddingValues(vertical = 9.dp, horizontal = 24.dp),
        backgroundColor = if (primary) Brush.horizontalGradient(
            listOf(Color(0xFF0B73FA), Color(0xFF0059E8))
        ) else SolidColor(Color.White),
        borderColor = SolidColor(if (primary) Color(0xFF0A63DF) else Color(0xFFAAB6C7)),
        contentColor = if (primary) Color.White else color,
        onClick = onClick,
    )
    }
}

@Composable
private fun CancelButton(
    cancel: () -> Unit,
    icon: IconSource?,
) {
    SingleDownloadPageButton(
        {
            cancel()
        },
        icon = icon,
        text = myStringResource(Res.string.cancel)
    )
}

@Composable
private fun ToggleButton(
    itemState: ProcessingDownloadItemState,
    toggle: () -> Unit,
    pause: () -> Unit,
) {
    var showPromptOnNonePresumablePause by remember(itemState.status is DownloadJobStatus.IsActive) {
        mutableStateOf(false)
    }

    val isResumeSupported = itemState.supportResume == true
    val (icon, text) = when {
        itemState.canBeResumed() -> {
            MyIcons.resume to Res.string.resume
        }

        itemState.canBePaused() -> {
            MyIcons.pause to Res.string.pause
        }

        else -> return
    }

    Box {
        SingleDownloadPageButton(
            {
                if (isResumeSupported) {
                    toggle()
                } else {
                    if (itemState.status is DownloadJobStatus.IsActive) {
                        showPromptOnNonePresumablePause = true
                    } else {
                        toggle()
                    }
                }
            },
            icon = icon,
            text = myStringResource(text),
            primary = isResumeSupported,
            color = if (isResumeSupported) {
                LocalContentColor.current
            } else {
                if (itemState.status is DownloadJobStatus.IsActive) {
                    myColors.error
                } else {
                    LocalContentColor.current
                }
            },
        )
        if (showPromptOnNonePresumablePause) {
            val shape = myShapes.defaultRounded
            val closePopup = {
                showPromptOnNonePresumablePause = false
            }
            Popup(
                popupPositionProvider = rememberMyComponentRectPositionProvider(
                    offset = DpOffset.Zero,
                    anchor = Alignment.TopEnd,
                    alignment = Alignment.TopStart,
                ),
                onDismissRequest = closePopup
            ) {
                Column(
                    Modifier
                        .clip(shape)
                        .border(2.dp, myColors.onBackground / 10, shape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    myColors.surface,
                                    myColors.background,
                                )
                            )
                        )
                        .padding(16.dp)
                        .widthIn(max = 140.dp)
                ) {
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(color = myColors.warning)) {
                            append("${myStringResource(Res.string.warning)}:\n")
                        }
                        append(myStringResource(Res.string.unsupported_resume_warning))
                    })
                    Spacer(Modifier.height(8.dp))
                    ActionButton(
                        myStringResource(Res.string.stop_anyway),
                        onClick = {
                            closePopup()
                            pause()
                        },
                        contentColor = myColors.error
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderParts(parts: List<UiPart>, modifier: Modifier) {
    // Keep every part in its own narrow rectangular track, vertically centered
    // in a small touch-safe strip instead of stretching it to the full 30dp.
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (parts.isNotEmpty()) {
            val sortedParts = remember(parts) { parts.sortedBy { it.id } }
            // Unknown-size parts still need visible connection tracks.
            val useEqualWeights = sortedParts.any { !it.partSpace.isFinite() || it.partSpace <= 0f }
            for (p in sortedParts) {
                val partSpace = if (useEqualWeights) 1f else p.partSpace
                key(p.id) {
                    RenderPart(
                        p,
                        Modifier
                            .weight(partSpace)
                            .height(14.dp)
                            // Wider separation makes each connection a clearly
                            // independent, slim rectangle rather than one long line.
                            .padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderPart(part: UiPart, modifier: Modifier) {
    val partProgress = if (part.status == PartDownloadStatus.Completed) {
        1f
    } else part.percent?.let { it / 100f }

    val liquidColors = when (part.status) {
        is PartDownloadStatus.Canceled -> listOf(myColors.error, myColors.warning)
        PartDownloadStatus.Completed -> referenceLiquidColors
        // Waiting or paused segments remain still and visually distinct
        // from the bright blue-cyan actively receiving connections.
        PartDownloadStatus.IDLE -> listOf(Color(0xFF8197B5), Color(0xFFB8C8DC))
        PartDownloadStatus.ReceivingData -> referenceLiquidColors
        PartDownloadStatus.Connecting -> referenceLiquidColors
    }
    LiquidProgressTrack(
        progress = partProgress,
        colors = liquidColors,
        active = part.status == PartDownloadStatus.ReceivingData ||
            part.status == PartDownloadStatus.Connecting,
        modifier = modifier,
        // Three subtle bubbles are enough for a 14dp-high connection line.
        bubbleCount = 3,
        compact = true,
    )
}

