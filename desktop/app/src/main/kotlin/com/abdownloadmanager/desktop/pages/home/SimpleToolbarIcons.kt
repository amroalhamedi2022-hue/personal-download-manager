package com.abdownloadmanager.desktop.pages.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import ir.amirab.util.compose.IconSource

/**
 * Genuine simplified filled Home glyphs, not merely the same outline glyphs
 * with a colored backing tile. Unknown/custom plugin glyphs use original art.
 * Cached once; no per-item animation or vector allocation on recomposition.
 */
internal fun simpleToolbarGlyph(icon: IconSource): ImageVector? {
    val key = icon.uri?.substringAfterLast(':') ?: return null
    return simpleHomeIcons[key]
}

private val simpleHomeIcons: Map<String, ImageVector> by lazy {
    listOf("resume", "pause", "queueStart", "queueStop", "queue",
        "stopAll", "stop", "remove", "settings").associateWith(::buildSimpleHomeGlyph)
}

private fun buildSimpleHomeGlyph(name: String): ImageVector =
    ImageVector.Builder(
        name = "SimpleToolbar_$name",
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        when (name) {
            "resume" -> path(fill = SolidColor(Color.White)) {
                moveTo(6f, 3.5f); lineTo(20f, 12f); lineTo(6f, 20.5f); close()
            }
            "pause" -> path(fill = SolidColor(Color.White)) {
                moveTo(5f, 4f); horizontalLineTo(10f); verticalLineTo(20f)
                horizontalLineTo(5f); close()
                moveTo(14f, 4f); horizontalLineTo(19f); verticalLineTo(20f)
                horizontalLineTo(14f); close()
            }
            "queueStart" -> path(fill = SolidColor(Color.White)) {
                moveTo(3f, 4f); horizontalLineTo(19f); verticalLineTo(7f)
                horizontalLineTo(3f); close()
                moveTo(3f, 10f); horizontalLineTo(12f); verticalLineTo(13f)
                horizontalLineTo(3f); close()
                moveTo(3f, 16f); horizontalLineTo(12f); verticalLineTo(19f)
                horizontalLineTo(3f); close()
                moveTo(15f, 10f); lineTo(22f, 15f); lineTo(15f, 20f); close()
            }
            "queueStop" -> path(fill = SolidColor(Color.White)) {
                moveTo(3f, 4f); horizontalLineTo(21f); verticalLineTo(7f)
                horizontalLineTo(3f); close()
                moveTo(3f, 10f); horizontalLineTo(12f); verticalLineTo(13f)
                horizontalLineTo(3f); close()
                moveTo(3f, 16f); horizontalLineTo(12f); verticalLineTo(19f)
                horizontalLineTo(3f); close()
                moveTo(15f, 11f); horizontalLineTo(21f); verticalLineTo(19f)
                horizontalLineTo(15f); close()
            }
            "queue" -> path(fill = SolidColor(Color.White)) {
                moveTo(4f, 4f); horizontalLineTo(20f); verticalLineTo(8f)
                horizontalLineTo(4f); close()
                moveTo(4f, 10f); horizontalLineTo(20f); verticalLineTo(14f)
                horizontalLineTo(4f); close()
                moveTo(4f, 16f); horizontalLineTo(20f); verticalLineTo(20f)
                horizontalLineTo(4f); close()
            }
            "stopAll" -> path(fill = SolidColor(Color.White)) {
                moveTo(3f, 6f); horizontalLineTo(13f); verticalLineTo(18f)
                horizontalLineTo(3f); close()
                moveTo(16f, 6f); horizontalLineTo(21f); verticalLineTo(18f)
                horizontalLineTo(16f); close()
            }
            "stop" -> path(fill = SolidColor(Color.White)) {
                moveTo(5f, 5f); horizontalLineTo(19f); verticalLineTo(19f)
                horizontalLineTo(5f); close()
            }
            "remove" -> path(fill = SolidColor(Color.White)) {
                moveTo(4f, 6f); horizontalLineTo(20f); verticalLineTo(9f)
                horizontalLineTo(4f); close()
                moveTo(8f, 3f); horizontalLineTo(16f); verticalLineTo(5f)
                horizontalLineTo(8f); close()
                moveTo(6f, 10f); horizontalLineTo(18f); lineTo(17f, 21f)
                horizontalLineTo(7f); close()
            }
            "settings" -> path(
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.EvenOdd,
            ) {
                moveTo(9f, 2f); horizontalLineTo(15f); lineTo(16f, 5f)
                horizontalLineTo(19f); lineTo(22f, 9f); lineTo(19.5f, 12f)
                lineTo(22f, 15f); lineTo(19f, 19f); horizontalLineTo(16f)
                lineTo(15f, 22f); horizontalLineTo(9f); lineTo(8f, 19f)
                horizontalLineTo(5f); lineTo(2f, 15f); lineTo(4.5f, 12f)
                lineTo(2f, 9f); lineTo(5f, 5f); horizontalLineTo(8f); close()
                moveTo(12f, 8f)
                curveTo(9.8f, 8f, 8f, 9.8f, 8f, 12f)
                curveTo(8f, 14.2f, 9.8f, 16f, 12f, 16f)
                curveTo(14.2f, 16f, 16f, 14.2f, 16f, 12f)
                curveTo(16f, 9.8f, 14.2f, 8f, 12f, 8f); close()
            }
        }
    }.build()
