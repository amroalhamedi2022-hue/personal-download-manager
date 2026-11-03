package com.abdownloadmanager.desktop.pages.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abdownloadmanager.desktop.pages.home.simpleToolbarGlyph
import com.abdownloadmanager.shared.ui.widget.Text
import com.abdownloadmanager.shared.util.ui.WithContentColor
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import com.abdownloadmanager.shared.util.ui.widget.Icon
import com.abdownloadmanager.shared.util.ui.widget.MyIcon
import ir.amirab.util.compose.IconSource

/**
 * Appearance studio: an exact, immediately reactive sample of the *real*
 * Home toolbar glyphs, not screenshots or a separate decorative icon set.
 * Selecting a chip writes to the existing persisted preferences used by Home.
 */
@Composable
internal fun ToolbarAppearancePreview(
    iconSizePreset: String,
    iconPack: String,
    onIconSizeSelected: (String) -> Unit,
    onIconPackSelected: (String) -> Unit,
) {
    val frame = RoundedCornerShape(12.dp)
    val iconDp = when (iconSizePreset) {
        "small" -> 16.dp
        "large" -> 28.dp
        else -> 20.dp
    }
    val samples = listOf(
        MyIcons.resume,
        MyIcons.pause,
        MyIcons.queueStart,
        MyIcons.queueStop,
        MyIcons.remove,
        MyIcons.settings,
    )
    Column(
        Modifier.fillMaxWidth()
            .clip(frame)
            .background(myColors.surface)
            .border(1.dp, myColors.onSurface.copy(alpha = 0.14f), frame)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Toolbar appearance · Live preview",
            fontSize = myTextSizes.lg,
            fontWeight = FontWeight.SemiBold,
            color = myColors.onSurface,
        )
        Text(
            "Choose an icon style and size. Changes apply to Home instantly and are saved.",
            fontSize = myTextSizes.base,
            color = myColors.onSurface.copy(alpha = 0.72f),
        )
        // Distinct vectors from the two real icon packs, with the same tint
        // and preset size as the real Home toolbar. No animation timer.
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            samples.forEach { icon ->
                val shape = RoundedCornerShape(9.dp)
                Box(
                    Modifier.size(iconDp + 24.dp)
                        .clip(shape)
                        .background(myColors.background)
                        .border(1.dp, myColors.onSurface.copy(alpha = 0.12f), shape),
                    contentAlignment = Alignment.Center,
                ) {
                    WithContentColor(myColors.onBackground) {
                        PreviewToolbarIcon(icon, iconPack, Modifier.size(iconDp))
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("outline" to "Modern Outline", "simple" to "Simple Filled").forEach { (choice, title) ->
                AppearanceChoice(
                    title = title,
                    selected = iconPack == choice,
                    onClick = { onIconPackSelected(choice) },
                )
            }
        }
        Spacer(Modifier.height(1.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("small" to "Small", "medium" to "Medium", "large" to "Large").forEach { (choice, title) ->
                AppearanceChoice(
                    title = title,
                    selected = iconSizePreset == choice,
                    onClick = { onIconSizeSelected(choice) },
                )
            }
        }
    }
}

@Composable
private fun PreviewToolbarIcon(icon: IconSource, iconPack: String, modifier: Modifier) {
    val simpleGlyph = if (iconPack == "simple") simpleToolbarGlyph(icon) else null
    if (simpleGlyph == null) {
        MyIcon(icon, contentDescription = null, modifier = modifier)
    } else {
        Icon(imageVector = simpleGlyph, contentDescription = null, modifier = modifier)
    }
}

@Composable
private fun AppearanceChoice(title: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) myColors.primary.copy(alpha = 0.14f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) myColors.primary else myColors.onSurface.copy(alpha = 0.16f),
                shape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            color = if (selected) myColors.primary else myColors.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = myTextSizes.base,
        )
    }
}
