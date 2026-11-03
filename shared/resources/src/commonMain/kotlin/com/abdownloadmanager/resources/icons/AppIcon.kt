package com.abdownloadmanager.resources.icons

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * HyperGet brand mark at all in-app/icon-tray sizes. The rounded glass tile,
 * cyan/blue/violet arrow, halo and download shelf match the approved
 * desktop/app/icons/hyperget-icon.svg instead of the obsolete purple logo.
 * Native Windows .ico packaging is intentionally left unchanged.
 */
val ABDMIcons.AppIcon: ImageVector
    get() {
        if (_AppIcon == null) {
            _AppIcon = ImageVector.Builder(
                name = "HyperGetAppIcon",
                defaultWidth = 48.dp,
                defaultHeight = 48.dp,
                viewportWidth = 48f,
                viewportHeight = 48f,
            ).apply {
                // Glass tile with soft rounded edges and its subtle light rim.
                path(
                    fill = Brush.linearGradient(
                        colors = listOf(Color(0xFF071435), Color(0xFF0D3B8B),
                            Color(0xFF3C1F86), Color(0xFF08183D)),
                        start = Offset(2f, 2f),
                        end = Offset(46f, 46f),
                    ),
                    stroke = SolidColor(Color(0xFF8AC9EE)),
                    strokeAlpha = 0.7f,
                    strokeLineWidth = 0.45f,
                ) {
                    moveTo(11f, 1.5f)
                    horizontalLineTo(37f)
                    curveTo(43f, 1.5f, 46.5f, 5f, 46.5f, 11f)
                    verticalLineTo(37f)
                    curveTo(46.5f, 43f, 43f, 46.5f, 37f, 46.5f)
                    horizontalLineTo(11f)
                    curveTo(5f, 46.5f, 1.5f, 43f, 1.5f, 37f)
                    verticalLineTo(11f)
                    curveTo(1.5f, 5f, 5f, 1.5f, 11f, 1.5f)
                    close()
                }
                // Open gradient halo: lighter than the tile, behind the arrow.
                path(
                    fill = SolidColor(Color.Transparent),
                    stroke = Brush.linearGradient(
                        colors = listOf(Color(0xFF23D8FF), Color(0xFF2F7BFF),
                            Color(0xFF9649FF), Color(0xFFFF71BC)),
                        start = Offset(8f, 9f),
                        end = Offset(40f, 38f),
                    ),
                    strokeAlpha = 0.78f,
                    strokeLineWidth = 1.6f,
                ) {
                    moveTo(39f, 22.5f)
                    curveTo(39f, 30.8f, 32.3f, 37.5f, 24f, 37.5f)
                    curveTo(15.7f, 37.5f, 9f, 30.8f, 9f, 22.5f)
                    curveTo(9f, 14.2f, 15.7f, 7.5f, 24f, 7.5f)
                    curveTo(32.3f, 7.5f, 39f, 14.2f, 39f, 22.5f)
                    close()
                }
                // Approved brand arrow, never an alphabet/font glyph.
                path(
                    fill = Brush.linearGradient(
                        colors = listOf(Color(0xFF72EAFF), Color(0xFF1DAAFF),
                            Color(0xFF2D70FF), Color(0xFF8048FF),
                            Color(0xFFE74FCF), Color(0xFFFF935B)),
                        start = Offset(24f, 8f),
                        end = Offset(24f, 37f),
                    ),
                    stroke = SolidColor(Color(0xFFC6F1FF)),
                    strokeAlpha = 0.8f,
                    strokeLineWidth = 0.5f,
                ) {
                    moveTo(19.7f, 9f)
                    horizontalLineTo(28.3f)
                    verticalLineTo(23.6f)
                    horizontalLineTo(34.3f)
                    lineTo(24f, 34.7f)
                    lineTo(13.7f, 23.6f)
                    horizontalLineTo(19.7f)
                    close()
                }
                path(fill = SolidColor(Color(0xFFEFFBFF)), fillAlpha = 0.25f) {
                    moveTo(20.6f, 10.7f)
                    horizontalLineTo(22.3f)
                    verticalLineTo(24.3f)
                    horizontalLineTo(24.5f)
                    lineTo(23f, 27.2f)
                    lineTo(20.6f, 24.5f)
                    close()
                }
                // Small, rounded receiving shelf from the approved SVG.
                path(
                    fill = Brush.linearGradient(
                        colors = listOf(Color(0xFF36EBFF), Color(0xFF2A8BFF),
                            Color(0xFF9A46FF), Color(0xFFFF899A)),
                        start = Offset(10f, 39f),
                        end = Offset(38f, 42f),
                    ),
                    stroke = SolidColor(Color(0xFFD8F8FF)),
                    strokeAlpha = 0.65f,
                    strokeLineWidth = 0.35f,
                ) {
                    moveTo(12f, 37f)
                    horizontalLineTo(36f)
                    curveTo(37.5f, 37f, 38.5f, 38f, 38.5f, 39.5f)
                    curveTo(38.5f, 41f, 37.5f, 42f, 36f, 42f)
                    horizontalLineTo(12f)
                    curveTo(10.5f, 42f, 9.5f, 41f, 9.5f, 39.5f)
                    curveTo(9.5f, 38f, 10.5f, 37f, 12f, 37f)
                    close()
                }
                path(fill = SolidColor(Color.White), fillAlpha = 0.32f) {
                    moveTo(13f, 38.1f)
                    horizontalLineTo(35f)
                    verticalLineTo(38.5f)
                    horizontalLineTo(13f)
                    close()
                }
            }.build()
        }
        return _AppIcon!!
    }

@Suppress("ObjectPropertyName")
private var _AppIcon: ImageVector? = null
