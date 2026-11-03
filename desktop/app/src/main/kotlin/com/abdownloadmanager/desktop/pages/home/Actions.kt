package com.abdownloadmanager.desktop.pages.home

import com.abdownloadmanager.shared.util.ui.widget.MyIcon
import com.abdownloadmanager.shared.util.ui.widget.Icon
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import ir.amirab.util.ifThen
import com.abdownloadmanager.shared.ui.widget.menu.custom.MyDropDown
import com.abdownloadmanager.shared.ui.widget.menu.custom.SubMenu
import com.abdownloadmanager.shared.util.ui.WithContentAlpha
import com.abdownloadmanager.shared.util.ui.WithContentColor
import ir.amirab.util.compose.action.MenuItem
import com.abdownloadmanager.shared.util.div
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import com.abdownloadmanager.shared.ui.widget.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.abdownloadmanager.shared.ui.widget.Tooltip
import com.abdownloadmanager.shared.util.ui.LocalMultiplatformScrollbarStyle
import com.abdownloadmanager.shared.util.ui.MultiplatformHorizontalScrollbar
import com.abdownloadmanager.shared.util.ui.needScroll
import ir.amirab.util.compose.IconSource
import ir.amirab.util.compose.StringSource

@Composable
fun Actions(
    list: List<MenuItem>,
    showLabels: Boolean,
    iconSizePreset: String = "medium",
    iconPack: String = "outline",
) {
    val scrollState = rememberScrollState()
    Column {
        Row(
            Modifier
                .height(IntrinsicSize.Max)
                .horizontalScroll(scrollState)
        ) {
            for (a in list) {
                when (a) {
                    MenuItem.Separator -> {
                        Spacer(
                            Modifier
                                .padding(horizontal = 4.dp)
                                .fillMaxHeight()
                                .padding(vertical = 4.dp)
                                .width(1.dp)
                                .background(myColors.onBackground / 5)
                        )
                    }

                    is MenuItem.SingleItem -> {
                        ActionButton(Modifier, a, showLabels, iconSizePreset, iconPack)
                    }

                    is MenuItem.SubMenu -> {
                        GroupActionButton(Modifier, a, showLabels, iconSizePreset, iconPack)
                    }
                }
            }
        }
        val adapter = rememberScrollbarAdapter(scrollState)
        if (adapter.needScroll()) {
            MultiplatformHorizontalScrollbar(
                adapter = adapter,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun ActionButton(
    modifier: Modifier = Modifier,
    action: MenuItem.SingleItem,
    showLabels: Boolean,
    iconSizePreset: String,
    iconPack: String,
) {
    val enabled by action.isEnabled.collectAsState()
    Column(modifier) {
        ActionIconWithLabel(
            title = action.title.collectAsState().value,
            icon = action.icon.collectAsState().value,
            showLabels = showLabels,
            iconSizePreset = iconSizePreset,
            iconPack = iconPack,
            onClick = {
                action()
            },
            enabled = enabled
        )
    }
}

@Composable
private fun GroupActionButton(
    modifier: Modifier = Modifier,
    action: MenuItem.SubMenu,
    showLabels: Boolean,
    iconSizePreset: String,
    iconPack: String,
) {
    val enabled by action.isEnabled.collectAsState()
    var showSubMenu by remember { mutableStateOf(false) }
    Column(modifier) {
        ActionIconWithLabel(
            title = action.title.collectAsState().value,
            icon = action.icon.collectAsState().value,
            showLabels = showLabels,
            iconSizePreset = iconSizePreset,
            iconPack = iconPack,
            onClick = {
                showSubMenu = !showSubMenu
            },
            enabled = enabled
        )
        val close = {
            showSubMenu = false
        }
        if (enabled && showSubMenu) {
            MyDropDown(onDismissRequest = close) {
                val items by action.items.collectAsState()
                SubMenu(subMenu = items, onRequestClose = close)
            }
        }
    }
}

@Composable
private fun ActionIconWithLabel(
    title: StringSource,
    icon: IconSource?,
    showLabels: Boolean,
    iconSizePreset: String,
    iconPack: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val iconDp = when (iconSizePreset) {
        "small" -> 16.dp
        "large" -> 28.dp
        else -> 20.dp
    }
    val touchPadding = when (iconSizePreset) {
        "small" -> 6.dp
        "large" -> 10.dp
        else -> 8.dp
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val tileShape = RoundedCornerShape(8.dp)
    OptionalTooltip(
        title.takeIf { !showLabels },
    ) {
        Column(
            modifier = Modifier
                .clip(tileShape)
                .background(
                    if (enabled && isHovered) myColors.primary.copy(alpha = 0.09f)
                    else Color.Transparent
                )
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onClick,
                )
                .ifThen(!enabled) {
                    alpha(0.5f)
                }
                .padding(
                    if (showLabels) PaddingValues(horizontal = touchPadding, vertical = 8.dp)
                    else PaddingValues(touchPadding + 4.dp)
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WithContentColor(myColors.onBackground) {
                WithContentAlpha(1f) {
                    icon?.let {
                        val simpleGlyph = if (iconPack == "simple") simpleToolbarGlyph(it) else null
                        if (simpleGlyph != null) {
                            Icon(
                                imageVector = simpleGlyph,
                                contentDescription = null,
                                modifier = Modifier.size(iconDp),
                            )
                        } else {
                            MyIcon(
                                icon = it,
                                contentDescription = null,
                                modifier = Modifier.size(iconDp),
                            )
                        }
                    }
                    if (showLabels) {
                        Spacer(Modifier.size(4.dp))
                        Text(title.rememberString(), maxLines = 1, fontSize = myTextSizes.base)
                    }
                }
            }
        }
    }

}

@Composable
private fun OptionalTooltip(
    tooltip: StringSource?,
    content: @Composable () -> Unit
) {
    if (tooltip != null) {
        Tooltip(tooltip) {
            content()
        }
    } else {
        content()
    }
}
