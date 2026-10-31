package com.abdownloadmanager.shared.ui.theme

import androidx.compose.ui.graphics.luminance
import com.abdownloadmanager.shared.storage.appsettings.DefaultAppSettings
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
        assertEquals(25, themes.size, "2 HyperGet + 7 legacy + 16 modern themes")
        assertEquals(ids.size, ids.toSet().size, "Theme IDs must be unique for persistence")
        legacyIds.forEach { legacy ->
            assertEquals(1, ids.count { it == legacy }, "Legacy setting '$legacy' must remain loadable")
        }
    }

    @Test
    fun hyperGetAuroraIsTheNewInstallDefaultWithoutRemovingLegacyThemes() {
        val themes = DefaultThemes.getAll()
        val newInstallDefaults = object : DefaultAppSettings() {
            override val useSparseFileAllocation = true
        }
        assertEquals("hyperget_aurora_dark", DefaultThemes.getDefaultDark().id)
        assertEquals("hyperget_aurora_light", DefaultThemes.getDefaultLight().id)
        assertEquals("hyperget_aurora_dark", newInstallDefaults.theme)
        assertEquals("hyperget_aurora_dark", newInstallDefaults.defaultDarkTheme)
        assertEquals("hyperget_aurora_light", newInstallDefaults.defaultLightTheme)
        assertEquals(1, themes.count { it.id == "hyperget_aurora_dark" })
        assertEquals(1, themes.count { it.id == "hyperget_aurora_light" })
        assertFalse(DefaultThemes.hyperGetDark.isLight)
        assertTrue(DefaultThemes.hyperGetLight.isLight)
        assertTrue(DefaultThemes.hyperGetDark.primary != DefaultThemes.hyperGetDark.secondary)
        assertTrue(DefaultThemes.hyperGetLight.primary != DefaultThemes.hyperGetLight.secondary)
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
    fun themeSelectorSwatchesReflectEachCandidateRatherThanTheActiveTheme() {
        DefaultThemes.getAll().forEach { theme ->
            val preview = theme.toThemeInfo()
            assertEquals(theme.id, preview.id)
            assertEquals(theme.surface, preview.color)
            assertEquals(theme.background, preview.backgroundColor)
            assertEquals(theme.primary, preview.accentColor)
        }
        val dark = DefaultThemes.hyperGetDark.toThemeInfo()
        val light = DefaultThemes.hyperGetLight.toThemeInfo()
        assertTrue(dark.backgroundColor != light.backgroundColor)
        assertTrue(dark.accentColor != dark.color)
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
