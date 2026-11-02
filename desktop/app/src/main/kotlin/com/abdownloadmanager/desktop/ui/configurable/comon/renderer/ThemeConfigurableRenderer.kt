package com.abdownloadmanager.desktop.ui.configurable.comon.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abdownloadmanager.desktop.ui.configurable.ConfigTemplate
import com.abdownloadmanager.shared.ui.configurable.ConfigurableRenderer
import com.abdownloadmanager.shared.ui.configurable.RenderSpinner
import com.abdownloadmanager.desktop.ui.configurable.TitleAndDescription
import com.abdownloadmanager.shared.ui.configurable.ConfigurableUiProps
import com.abdownloadmanager.shared.ui.configurable.isConfigEnabled
import com.abdownloadmanager.shared.ui.configurable.item.ThemeConfigurable
import com.abdownloadmanager.shared.ui.theme.ThemeInfo
import com.abdownloadmanager.shared.ui.widget.Text
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import ir.amirab.util.ifThen

/**
 * Miniature real Home: sidebar, content panel and two-tone liquid line.
 * Swatches use each CANDIDATE theme's own colors (not the active UI theme);
 * no effects or per-row animation clocks in the dropdown.
 */
@Composable
private fun ThemeWindowSwatch(theme: ThemeInfo, selected: Boolean) {
    val tile = RoundedCornerShape(8.dp)
    Box(
        Modifier.size(width = 72.dp, height = 40.dp)
            .clip(tile)
            .background(theme.backgroundColor)
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) myColors.primary else myColors.onSurface.copy(alpha = 0.25f),
                tile,
            )
    ) {
        // Left navigation column.
        Box(
            Modifier.align(Alignment.CenterStart).padding(start = 4.dp)
                .size(width = 13.dp, height = 31.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(theme.color)
        ) {
            Spacer(
                Modifier.align(Alignment.TopCenter).padding(top = 5.dp)
                    .size(width = 7.dp, height = 3.dp)
                    .clip(CircleShape)
                    .background(theme.accentColor)
            )
        }
        // Table panel, miniature column header and the approved liquid colors.
        Box(
            Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)
                .size(width = 47.dp, height = 31.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(theme.color)
        ) {
            Spacer(
                Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 5.dp)
                    .size(width = 35.dp, height = 3.dp)
                    .clip(CircleShape).background(theme.accentColor)
            )
            Spacer(
                Modifier.align(Alignment.Center).padding(horizontal = 4.dp)
                    .fillMaxWidth().height(5.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(theme.accentColor, theme.secondaryAccentColor)
                        )
                    )
            )
            Spacer(
                Modifier.align(Alignment.BottomStart).padding(start = 4.dp, bottom = 5.dp)
                    .size(width = 25.dp, height = 2.dp)
                    .clip(CircleShape)
                    .background(theme.accentColor.copy(alpha = 0.45f))
            )
        }
        if (selected) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(1.dp)
                    .size(11.dp).clip(CircleShape).background(myColors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓", color = Color.White, fontSize = 8.sp)
            }
        }
    }
}

object ThemeConfigurableRenderer : ConfigurableRenderer<ThemeConfigurable> {
    @Composable
    override fun RenderConfigurable(configurable: ThemeConfigurable, configurableUiProps: ConfigurableUiProps) {
        RenderThemeConfig(configurable, configurableUiProps)
    }

    @Composable
    private fun RenderThemeConfig(cfg: ThemeConfigurable, configurableUiProps: ConfigurableUiProps) {
        val value by cfg.stateFlow.collectAsState()
        val enabled = isConfigEnabled()
        ConfigTemplate(
            modifier = configurableUiProps.modifier.padding(configurableUiProps.itemPaddingValues),
            title = { TitleAndDescription(cfg) },
            value = {
                RenderSpinner(
                    possibleValues = cfg.possibleValues,
                    value = value,
                    onSelect = { cfg.set(it) },
                    modifier = Modifier.widthIn(min = 195.dp),
                    enabled = enabled,
                    valueToString = cfg.valueToString,
                    render = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.ifThen(!enabled) { alpha(0.5f) },
                        ) {
                            // Each option is a real candidate mini-window.
                            ThemeWindowSwatch(theme = it, selected = value.id == it.id)
                            Spacer(Modifier.width(12.dp))
                            Text(cfg.describe(it).rememberString(), fontSize = myTextSizes.lg)
                        }
                    },
                )
            },
        )
    }
}
