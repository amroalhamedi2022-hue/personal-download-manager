package com.abdownloadmanager.desktop.pages.singleDownloadPage

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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

@Composable
fun ProgressDownloadPage(
    singleDownloadComponent: DesktopSingleDownloadComponent,
    itemState: ProcessingDownloadItemState
) {
    var selectedTab by remember { mutableStateOf(Info) }
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
    LaunchedEffect(itemState.id) {
        while (true) {
            speedHistory.add(currentObservedSpeed)
            if (speedHistory.size > 90) speedHistory.removeAt(0)
            delay(1_000)
        }
    }

    LaunchedEffect(resizingState.resizingPartInfo) {
        if (resizingState.partInfoHeight <= 0.dp) {
            setShowPartInfo(false)
        }
    }
    Column(
        Modifier.fillMaxSize()
    ) {
        Column(
            Modifier
                .clip(myShapes.defaultRounded)
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
                    .height(150.dp)
                    .background(myColors.background)
                    .verticalScroll(scrollState)
            ) {
                when (selectedTab) {
                    Info -> RenderInfo(
                        tabContentModifier,
                        horizontalPadding,
                        singleDownloadComponent,
                    )

                    Settings -> RenderSettings(
                        modifier = tabContentModifier.padding(end = 12.dp),
                        horizontalPadding = horizontalPadding,
                        singleDownloadComponent = singleDownloadComponent,
                        speedHistory = speedHistory,
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
                .background(myColors.surface / 0.5f)
        ) {
            Column(
                Modifier
                    .padding(horizontal = horizontalPadding)
            ) {
                Spacer(Modifier.size(8.dp))
                RenderProgressBar(itemState)
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


@Composable
private fun RenderSettings(
    modifier: Modifier,
    horizontalPadding: Dp,
    singleDownloadComponent: DesktopSingleDownloadComponent,
    speedHistory: List<Long>,
) {
    Column(modifier) {
        RealSpeedHistoryChart(speedHistory, Modifier.padding(horizontal = horizontalPadding))
        Spacer(Modifier.height(8.dp))
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
private fun RealSpeedHistoryChart(history: List<Long>, modifier: Modifier = Modifier) {
    val samples = history.toList()
    val observedMaxSpeed = samples.maxOrNull() ?: 0L
    val maxSpeed = observedMaxSpeed.coerceAtLeast(1L)
    val currentSpeed = samples.lastOrNull() ?: 0L
    val speedUnit = LocalSizeUnit.current
    val graphColor = myColors.primary
    val gridColor = myColors.onBackground / 8
    val currentText = convertPositiveSizeToHumanReadable(currentSpeed, speedUnit).rememberString()
    val peakText = convertPositiveSizeToHumanReadable(observedMaxSpeed, speedUnit).rememberString()

    Column(modifier.fillMaxWidth()) {
        Text(
            "Speed: $currentText/s  •  Peak: $peakText/s  •  Last 90 s",
            fontSize = myTextSizes.base,
            color = myColors.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(myShapes.defaultRounded)
                .background(myColors.surface)
        ) {
            repeat(4) { index ->
                val y = size.height * (index + 1) / 5f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            if (samples.isEmpty()) return@Canvas
            val graph = Path()
            val h = size.height * 0.88f
            val bottom = size.height * 0.94f
            samples.forEachIndexed { index, bytesPerSecond ->
                val x = if (samples.size == 1) 0f else size.width * index / (samples.size - 1).toFloat()
                val normalized = (bytesPerSecond.toDouble() / maxSpeed.toDouble()).toFloat().coerceIn(0f, 1f)
                val y = bottom - normalized * h
                if (index == 0) graph.moveTo(x, y) else graph.lineTo(x, y)
            }
            drawPath(graph, color = graphColor, style = Stroke(width = 2.dp.toPx()))
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
        colors = liquidColors,
        // A retry timer owns no network transfer and must not animate like one.
        active = status == DownloadJobStatus.Downloading ||
            status == DownloadJobStatus.Resuming ||
            status is DownloadJobStatus.PreparingFile,
        modifier = Modifier.fillMaxWidth().height(18.dp),
        bubbleCount = 12,
        showCompletionIcon = true,
    )
}

/**
 * Theme-aware liquid progress used by both progress lines in the download
 * window. The wave and bubbles are clipped to the downloaded portion and the
 * palette changes immediately when the application theme changes.
 */
@Composable
private fun LiquidProgressTrack(
    progress: Float?,
    colors: List<Color>,
    active: Boolean,
    modifier: Modifier,
    bubbleCount: Int,
    showCompletionIcon: Boolean = false,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress?.coerceIn(0f, 1f) ?: 0f,
        animationSpec = tween(180, easing = LinearEasing),
    )
    // Do not keep a perpetual Compose animation running while paused, retrying,
    // completed or otherwise inactive. Hidden dialogs are not composed at all.
    val phase = if (active) {
        val liquidAnimation = rememberInfiniteTransition()
        val animatedPhase by liquidAnimation.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            )
        )
        animatedPhase
    } else {
        0f
    }
    val shape = myShapes.defaultRounded

    Box(
        modifier
            .clip(shape)
            .background(myColors.onBackground / 15)
            .border(1.dp, myColors.onBackground / 10, shape)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (progress == null && !active) return@Canvas

            val isIndeterminate = progress == null
            val nominalEnd = if (isIndeterminate) {
                size.width * (phase * 1.45f)
            } else {
                size.width * animatedProgress
            }
            val startX = if (isIndeterminate) {
                (nominalEnd - size.width * 0.38f).coerceAtLeast(0f)
            } else {
                0f
            }
            val endX = nominalEnd.coerceIn(0f, size.width)
            if (endX <= startX) return@Canvas

            val amplitude = if (!isIndeterminate && animatedProgress >= 0.995f) {
                0f
            } else {
                size.height * 0.16f
            }
            val liquidPath = Path().apply {
                moveTo(startX, 0f)
                lineTo(endX, 0f)
                val steps = 14
                for (step in 0..steps) {
                    val y = size.height * step / steps
                    val angle = (step.toFloat() / steps * PI * 2.0) + (phase * PI * 2.0)
                    val waveX = (endX + sin(angle).toFloat() * amplitude)
                        .coerceIn(startX, size.width)
                    lineTo(waveX, y)
                }
                lineTo(startX, size.height)
                close()
            }
            drawPath(
                path = liquidPath,
                brush = Brush.linearGradient(
                    colors = colors,
                    start = Offset(startX, 0f),
                    end = Offset(endX.coerceAtLeast(startX + 1f), size.height),
                )
            )

            val liquidWidth = (endX - startX).coerceAtLeast(1f)
            repeat(bubbleCount) { index ->
                val speed = 0.28f + (index % 4) * 0.07f
                val xRatio = ((index * 0.173f) + phase * speed) % 1f
                val yRatio = ((index * 0.317f) + phase * (0.35f + index % 3 * 0.08f)) % 1f
                val radius = size.height * (0.08f + (index % 3) * 0.035f)
                val center = Offset(
                    x = startX + liquidWidth * xRatio,
                    y = size.height * (0.18f + yRatio * 0.64f),
                )
                if (center.x + radius < endX + amplitude) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.28f),
                        radius = radius,
                        center = center,
                        style = Stroke(width = (radius * 0.38f).coerceAtLeast(0.7f)),
                    )
                }
            }
        }

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
                    .height(10.dp)
                    .clip(myShapes.defaultRounded)
                    .background(myColors.onBackground / 15)
            )
            Box(
                Modifier.weight(1f)
            ) {
                val (onlyActiveParts, setOnlyActiveParts) = rememberSaveable {
                    mutableStateOf(true)
                }
                val listToShow = remember(itemState, onlyActiveParts) {
                    itemState.parts
                        .let { parts ->
                            if (onlyActiveParts) {
                                parts.filter {
                                    when (it.status) {
                                        is PartDownloadStatus.Canceled -> true
                                        PartDownloadStatus.Completed -> false
                                        PartDownloadStatus.IDLE -> false
                                        PartDownloadStatus.ReceivingData -> true
                                        PartDownloadStatus.Connecting -> true
                                    }
                                }
                            } else {
                                parts
                            }
                        }
                        .withIndex()
                        .toList()
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
                                        if (isHovered) myColors.onSurface / 10
                                        else Color.Transparent
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
                            SimpleCellText(prettifyStatus(it.value.status).rememberString())
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
                if (useIsInDebugMode()) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 8.dp, end = 8.dp)
                            .onClick { setOnlyActiveParts(!onlyActiveParts) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Only Actives")
                        Spacer(Modifier.width(4.dp))
                        CheckBox(onlyActiveParts, { setOnlyActiveParts(it) })
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
private fun SimpleCellText(text: String) {
    Text(text, fontSize = myTextSizes.base, maxLines = 1)
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
) {
    Column(
        modifier
            .padding(horizontal = horizontalPadding)
            .padding(top = 8.dp)
    ) {
        for (propertyItem in singleDownloadComponent.extraDownloadProgressInfo.collectAsState().value) {
            Spacer(Modifier.height(2.dp))
            RenderPropertyItem(propertyItem)
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
    Row {
        PartInfoButton(showingPartInfo, onRequestShowPartInfo)
        downloadErrorReason?.let {
            Spacer(Modifier.width(mySpacings.smallSpace))
            DownloadErrorInfoButton(
                downloadErrorReason = it,
                onClick = onRequestShowDownloadError,
            )
        }
        Spacer(Modifier.weight(1f))
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
        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 12.dp),
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
                            .fillMaxHeight()
                            .weight(partSpace)
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
        PartDownloadStatus.ReceivingData -> myColors.primaryGradientColors
        PartDownloadStatus.Connecting -> listOf(myColors.info, myColors.secondary)
    }
    LiquidProgressTrack(
        progress = partProgress,
        colors = liquidColors,
        active = part.status == PartDownloadStatus.ReceivingData ||
            part.status == PartDownloadStatus.Connecting,
        modifier = modifier.padding(end = 1.dp),
        bubbleCount = 2,
    )
}
