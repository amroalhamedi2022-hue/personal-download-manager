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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
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
    Color(0xFF2815F1), Color(0xFF123AF9), Color(0xFF006EFF),
    Color(0xFF06B6F2), Color(0xFF00E8EA),
)

@Composable
fun ProgressDownloadPage(
    singleDownloadComponent: DesktopSingleDownloadComponent,
    itemState: ProcessingDownloadItemState
) {
    var selectedTab by remember { mutableStateOf(Info) }
    var showSpeedGraph by rememberSaveable(itemState.id) { mutableStateOf(true) }
    val showPartInfo by singleDownloadComponent.showPartInfo.collectAsState()
    val setShowPartInfo = singleDownloadComponent::setShowPartInfo
    val downloadHasError by singleDownloadComponent.lastError.collectAsState()
    val resizingState = LocalSingleDownloadPageSizing.current
    val horizontalPadding = 16.dp

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
    CompositionLocalProvider(LocalLiquidAnimationPhase provides liquidPhaseState) {
    Column(
        Modifier.fillMaxSize().background(myColors.background)
    ) {
        Column(
            Modifier
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .clip(myShapes.defaultRounded)
                .border(1.dp, Color(0xFFD8E0EB), myShapes.defaultRounded)
                .background(Color.White)
                .padding(1.dp),
        ) {
            //tabs
            MyTabRow {
                for (tab in tabs) {
                    MyTab(
                        selected = tab == selectedTab,
                        onClick = {
                            selectedTab = tab
                        },
                        icon = tab.icon,
                        title = tab.title,
                    )
                }
            }
            val scrollState = rememberScrollState()
            //info / settings ...
            val tabContentModifier = Modifier

            Spacer(Modifier.fillMaxWidth().height(1.dp).background(myColors.surface))
            Box(
                Modifier
                    .height(232.dp)
                    .background(Color.White)
                    .verticalScroll(scrollState)
            ) {
                when (selectedTab) {
                    Info -> RenderInfo(
                        tabContentModifier,
                        horizontalPadding,
                        singleDownloadComponent,
                        speedHistory,
                        showSpeedGraph,
                        onToggleGraph = { showSpeedGraph = !showSpeedGraph },
                    )

                    Settings -> RenderSettings(
                        modifier = tabContentModifier.padding(end = 12.dp),
                        horizontalPadding = horizontalPadding,
                        singleDownloadComponent = singleDownloadComponent,
                    )

                    OnCompletion -> RenderOnCompletion(
                        modifier = tabContentModifier.padding(end = 12.dp),
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
            Modifier
                .weight(1f)
                .background(if (myColors.isLight) Color(0xFFF5F8FC) else myColors.surface / 0.5f)
        ) {
            Column(
                Modifier
                    .padding(horizontal = horizontalPadding)
            ) {
                Spacer(Modifier.size(8.dp))
                RenderProgressBar(itemState)
                Spacer(Modifier.size(8.dp))
                RenderTransferSummary(itemState)
                Spacer(Modifier.size(8.dp))
                RenderActions(
                    itemState = itemState,
                    singleDownloadComponent = singleDownloadComponent,
                    showingPartInfo = showPartInfo,
                    onRequestShowPartInfo = setShowPartInfo,
                    downloadErrorReason = downloadHasError,
                    onRequestShowDownloadError = singleDownloadComponent::openDownloadErrorPage
                )
                Spacer(Modifier.size(8.dp))
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
private fun RenderTransferSummary(itemState: ProcessingDownloadItemState) {
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${downloaded} of ${total}", fontSize = myTextSizes.base)
        Text("${remaining} remaining (${speed}/s)", fontSize = myTextSizes.base)
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
            Text("Transfer rate", fontSize = myTextSizes.base, color = myColors.onBackground)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$currentText/s", fontSize = myTextSizes.base, color = myColors.onBackground)
                Spacer(Modifier.width(8.dp))
                ActionButton(
                    text = "Hide graph",
                    onClick = onHideGraph,
                    contentPadding = PaddingValues(vertical = 3.dp, horizontal = 8.dp),
                )
            }
        }
        Text("Peak: $peakText/s  •  Last 90 seconds", fontSize = myTextSizes.base, color = myColors.onBackground / 0.7f)
        Spacer(Modifier.height(4.dp))
        Canvas(Modifier.fillMaxWidth().height(132.dp).clip(myShapes.defaultRounded).background(myColors.surface)) {
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
        bubbleCount = 12,
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
            .background(myColors.onBackground / 12)
            .border(1.dp, myColors.onBackground / 12, trackShape)
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
                        drawPath(surfaceWave, color = Color.White.copy(alpha = 0.12f))

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
                        drawPath(secondaryWave, color = Color(0xFFB3FFFF).copy(alpha = 0.08f))

                        // Glass bubbles have translucent cores, crisp illuminated
                        // rims and individual specular dots, not flat white circles.
                        if (fillWidth > size.height * 1.1f) {
                            repeat(bubbleCount) { index ->
                                val drift = (index * 0.173f + phase * (0.115f + index % 4 * 0.021f)) % 1f
                                val lift = (index * 0.313f + phase * (0.10f + index % 3 * 0.019f)) % 1f
                                val bubbleRadius = size.height * (0.045f + (index % 4) * 0.018f)
                                val center = Offset(
                                    x = startX + fillWidth * (0.06f + drift * 0.88f),
                                    y = size.height * (0.26f + lift * 0.55f),
                                )
                                drawCircle(Color.White.copy(alpha = 0.06f), bubbleRadius * 1.6f, center)
                                drawCircle(Color(0xFFB1F9FF).copy(alpha = 0.14f), bubbleRadius * 0.79f, center)
                                drawCircle(
                                    color = Color.White.copy(alpha = if (index % 3 == 0) 0.48f else 0.29f),
                                    radius = bubbleRadius,
                                    center = center,
                                    style = Stroke(width = (bubbleRadius * 0.12f).coerceAtLeast(0.6f)),
                                )
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.86f),
                                    radius = bubbleRadius * 0.10f,
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
    Column(modifier) {
        // The approved reference displays completed and waiting parts by default.
        var onlyActiveParts by rememberSaveable(itemState.id) { mutableStateOf(false) }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Connections and downloaded parts", fontSize = myTextSizes.base)
            val connected = itemState.parts.count { it.status == PartDownloadStatus.ReceivingData }
            Text("${itemState.parts.size} parts • $connected connected", fontSize = myTextSizes.base)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.onClick { onlyActiveParts = !onlyActiveParts },
            ) {
                Text("Only active", fontSize = myTextSizes.base)
                Spacer(Modifier.width(4.dp))
                CheckBox(onlyActiveParts, { onlyActiveParts = it })
            }
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
                itemState.parts,
                Modifier
                    .padding(horizontal = horizontalPadding)
                    .height(30.dp)
            )
            Box(
                Modifier.weight(1f)
            ) {
                val listToShow = remember(itemState.parts, onlyActiveParts) {
                    selectVisibleDownloadParts(itemState.parts, onlyActiveParts)
                }
                Table(
                    list = listToShow,
                    key = {
                        it.value.id
                    },
                    modifier = Modifier
                        .fillMaxSize(),
                    wrapHeader = {
                        WithContentAlpha(0.75f) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = horizontalPadding)
                                    .padding(vertical = 4.dp)
                            ) {
                                it()
                            }
                        }
                    },
                    tableState = remember {
                        TableState(
                            cells = PartInfoCells.all()
                        )
                    },
                    wrapItem = { _, _, content ->
                        WithContentAlpha(1f) {
                            val interactionSource = remember { MutableInteractionSource() }
                            val isHovered by interactionSource.collectIsHoveredAsState()
                            Box(
                                Modifier
                                    .padding(horizontal = horizontalPadding)
                                    .hoverable(interactionSource)
                                    .background(
                                        if (isHovered) Color(0xFFE7F1FF)
                                        else Color.White
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
                            SimpleCellText(prettifyStatus(it.value.status).rememberString(), stateColor)
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
private fun SimpleCellText(text: String, color: Color = LocalContentColor.current) {
    Text(text, fontSize = myTextSizes.base, maxLines = 1, color = color)
}

sealed class PartInfoCells : TableCell<IndexedValue<UiPart>> {
    data object Number : PartInfoCells() {
        override val id: String = "#"
        override val name: StringSource = "#".asStringSource()
        override val size: CellSize = CellSize.Fixed(32.dp)
    }

    data object Status : PartInfoCells() {
        override val id: String = "Status"
        override val name: StringSource = Res.string.status.asStringSource()
        override val size: CellSize = CellSize.Resizeable(100.dp..200.dp)
    }

    data object Downloaded : PartInfoCells() {
        override val id: String = "Downloaded"
        override val name: StringSource = Res.string.parts_info_downloaded_size.asStringSource()
        override val size: CellSize = CellSize.Resizeable(90.dp..200.dp)
    }

    data object Total : PartInfoCells() {
        override val id: String = "Total"
        override val name: StringSource = Res.string.parts_info_total_size.asStringSource()
        override val size: CellSize = CellSize.Resizeable(90.dp..200.dp)
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
private fun RenderPropertyItem(propertyItem: SingleDownloadPagePropertyItem) {
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
                fontSize = myTextSizes.base
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
                fontSize = myTextSizes.base,
                color = when (propertyItem.valueState) {
                    SingleDownloadPagePropertyItem.ValueType.Normal -> LocalContentColor.current
                    SingleDownloadPagePropertyItem.ValueType.Error -> myColors.error
                    SingleDownloadPagePropertyItem.ValueType.Success -> myColors.success
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
    showGraph: Boolean,
    onToggleGraph: () -> Unit,
) {
    // Approved Liquid window: file details on the left and a real chart on
    // the right, without hiding the transfer rate behind the Settings tab.
    Row(
        modifier.padding(horizontal = horizontalPadding).padding(top = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(if (showGraph) 0.59f else 1f)) {
            for (propertyItem in singleDownloadComponent.extraDownloadProgressInfo.collectAsState().value) {
                Spacer(Modifier.height(2.dp))
                RenderPropertyItem(propertyItem)
            }
        }
        Spacer(Modifier.width(12.dp))
        if (showGraph) {
            RealSpeedHistoryChart(speedHistory, Modifier.weight(0.41f), onHideGraph = onToggleGraph)
        } else {
            ActionButton(
                text = "Show graph",
                onClick = onToggleGraph,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun RenderActions(
    itemState: ProcessingDownloadItemState,
    singleDownloadComponent: DesktopSingleDownloadComponent,
    showingPartInfo: Boolean,
    onRequestShowPartInfo: (show: Boolean) -> Unit,
    downloadErrorReason: DownloadErrorReason?,
    onRequestShowDownloadError: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            PartInfoButton(showingPartInfo, onRequestShowPartInfo)
        downloadErrorReason?.let {
            Spacer(Modifier.width(mySpacings.smallSpace))
            DownloadErrorInfoButton(
                downloadErrorReason = it,
                onClick = onRequestShowDownloadError,
            )
        }
        }
        Row(Modifier.weight(2f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        ToggleButton(
            itemState = itemState,
            toggle = singleDownloadComponent::toggle,
            pause = singleDownloadComponent::pause,
        )
        Spacer(Modifier.width(8.dp))
        CancelButton(
            cancel = singleDownloadComponent::cancel,
            icon = if (singleDownloadComponent.deletePartialFileOnDownloadCancellation.collectAsState().value) {
                MyIcons.stop
            } else {
                null
            },
        )
        }
        Spacer(Modifier.weight(1f))
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
) {
    ActionButton(
        text = text,
        start = {
            icon?.let {
                Row {
                    MyIcon(it, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                }
            }
        },
        contentPadding = PaddingValues(vertical = 9.dp, horizontal = 24.dp),
        contentColor = color,
        onClick = onClick,
    )
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
    Row(
        modifier
            .fillMaxWidth()
    ) {
        if (parts.isNotEmpty()) {
            val sortedParts = remember(parts) {
                parts.sortedBy {
                    it.id
                }
            }
            for (p in sortedParts) {
                val partSpace = p.partSpace
                if (partSpace <= 0f) continue
                key(p.id) {
                    RenderPart(
                        p,
                        Modifier
                            .weight(partSpace)
                            .fillMaxHeight()
                            .padding(horizontal = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderPart(part: UiPart, modifier: Modifier) {
    val partProgress = part.percent?.let {
        it / 100f
    }

    val liquidColors = when (part.status) {
        is PartDownloadStatus.Canceled -> listOf(myColors.error, myColors.warning)
        PartDownloadStatus.Completed -> myColors.primaryGradientColors
        PartDownloadStatus.IDLE -> myColors.primaryGradientColors
        PartDownloadStatus.ReceivingData -> referenceLiquidColors
        PartDownloadStatus.Connecting -> referenceLiquidColors
    }
    LiquidProgressTrack(
        progress = partProgress,
        colors = liquidColors,
        active = part.status == PartDownloadStatus.ReceivingData ||
            part.status == PartDownloadStatus.Connecting,
        modifier = modifier,
        bubbleCount = 5,
        compact = true,
    )
}

