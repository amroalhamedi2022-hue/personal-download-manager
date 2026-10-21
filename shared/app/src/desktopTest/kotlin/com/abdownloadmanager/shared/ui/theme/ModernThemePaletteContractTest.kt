package com.abdownloadmanager.shared.ui.theme

import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModernThemePaletteContractTest {
    private val families = listOf(
        "indigo", "sand", "burgundy", "crimson",
        "emerald", "mint", "ice", "pearl"
    )
    private val legacyIds = setOf(
        "dark", "light", "obsidian", "deep_ocean",
        "twilight", "black", "light_gray"
    )

    @Test
    fun allEightModernFamiliesHaveDistinctLightAndDarkModes() {
        val themes = DefaultThemes.getAll()
        families.forEach { family ->
            val dark = themes.single { it.id == "${family}_dark" }
            val light = themes.single { it.id == "${family}_light" }
            assertFalse(dark.isLight, "${family}_dark must be dark")
            assertTrue(light.isLight, "${family}_light must be light")
            assertEquals(dark.primary, light.primary)
            assertEquals(dark.secondary, light.secondary)
        }
    }

    @Test
    fun sevenLegacyThemeIdsArePreservedWithoutAliasesOrOverwrites() {
        val themes = DefaultThemes.getAll()
        val ids = themes.map { it.id }
        assertEquals(23, themes.size, "7 legacy + 16 modern themes")
        assertEquals(ids.size, ids.toSet().size, "Theme IDs must be unique for persistence")
        legacyIds.forEach { legacy ->
            assertEquals(1, ids.count { it == legacy }, "Legacy setting '$legacy' must remain loadable")
        }
    }

    @Test
    fun eachModernModeHasReadableForegroundAndDifferentSurface() {
        val themes = DefaultThemes.getAll()
        families.forEach { family ->
            val dark = themes.single { it.id == "${family}_dark" }
            val light = themes.single { it.id == "${family}_light" }
            assertTrue(dark.background != light.background)
            assertTrue(dark.surface != light.surface)
            assertTrue(dark.onBackground != light.onBackground)
            assertTrue(dark.primary != dark.secondary, "Liquid colors must be distinguishable")
            assertTrue(light.primary != light.secondary, "Liquid colors must be distinguishable")
        }
    }

    @Test
    fun modernAccentButtonsMeetMinimumFourPointFiveToOneContrast() {
        DefaultThemes.getAll()
            .filter { theme -> families.any { theme.id.startsWith("${it}_") } }
            .forEach { theme ->
                val primaryContrast = 1.05f / (theme.primary.luminance() + 0.05f)
                val secondaryContrast = 1.05f / (theme.secondary.luminance() + 0.05f)
                assertTrue(primaryContrast >= 4.5f, "${theme.id} primary button has insufficient white-text contrast")
                assertTrue(secondaryContrast >= 4.5f, "${theme.id} secondary button has insufficient white-text contrast")
            }
    }

}
