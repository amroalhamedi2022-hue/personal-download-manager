package com.abdownloadmanager.desktop.ui.configurable.comon.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.abdownloadmanager.desktop.ui.configurable.ConfigTemplate
import com.abdownloadmanager.shared.ui.configurable.ConfigurableRenderer
import com.abdownloadmanager.shared.ui.configurable.RenderSpinner
import com.abdownloadmanager.desktop.ui.configurable.TitleAndDescription
import com.abdownloadmanager.shared.ui.configurable.ConfigurableUiProps
import com.abdownloadmanager.shared.ui.configurable.isConfigEnabled
import com.abdownloadmanager.shared.ui.configurable.item.ThemeConfigurable
import com.abdownloadmanager.shared.ui.widget.Text
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import ir.amirab.util.ifThen

object ThemeConfigurableRenderer : ConfigurableRenderer<ThemeConfigurable> {
    @Composable
    override fun RenderConfigurable(configurable: ThemeConfigurable, configurableUiProps: ConfigurableUiProps) {
        RenderThemeConfig(configurable, configurableUiProps)
    }

    @Composable
    private fun RenderThemeConfig(cfg: ThemeConfigurable, configurableUiProps: ConfigurableUiProps) {
        val value by cfg.stateFlow.collectAsState()
        val setValue = cfg::set
        val enabled = isConfigEnabled()
        ConfigTemplate(
            modifier = configurableUiProps.modifier.padding(configurableUiProps.itemPaddingValues),
            title = {
                TitleAndDescription(cfg)
            },
            value = {
                RenderSpinner(
                    possibleValues = cfg.possibleValues, value = value, onSelect = {
                        setValue(it)
                    },
                    modifier = Modifier.widthIn(min = 160.dp),
                    enabled = enabled,
                    valueToString = cfg.valueToString,
                    render = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.ifThen(!enabled) {
                                alpha(0.5f)
                            }
                        ) {
                            // True candidate preview: background, panel and accent.
                            val swatchShape = RoundedCornerShape(5.dp)
                            Box(
                                Modifier
                                    .size(width = 42.dp, height = 24.dp)
                                    .clip(swatchShape)
                                    .background(it.backgroundColor)
                                    .border(1.dp, myColors.onBackground.copy(alpha = 0.25f), swatchShape)
                            ) {
                                Spacer(
                                    Modifier
                                        .align(Alignment.CenterStart)
                                        .padding(start = 4.dp)
                                        .size(width = 21.dp, height = 15.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(it.color)
                                )
                                Spacer(
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 4.dp, bottom = 4.dp)
                                        .size(width = 14.dp, height = 6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(it.accentColor)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(cfg.describe(it).rememberString(), fontSize = myTextSizes.lg)
                        }
                    })
            }
        )
    }

}
