package com.abdownloadmanager.shared.settings

import com.abdownloadmanager.shared.ui.configurable.ConfigurableGroup
import com.abdownloadmanager.shared.ui.configurable.item.BooleanConfigurable
import ir.amirab.util.compose.asStringSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SettingsSearchTest {
    private fun option(label: String, detail: String, state: MutableStateFlow<Boolean> = MutableStateFlow(false)) =
        BooleanConfigurable(
            title = label.asStringSource(),
            description = detail.asStringSource(),
            backedBy = state,
            describe = { if (it) "Enabled".asStringSource() else "Disabled".asStringSource() },
        )

    @Test
    fun matchesTitleAndDescriptionIgnoringCaseWithoutChangingStoredValues() {
        val storage = MutableStateFlow(false)
        val browser = option("Browser integration", "Connect to supported browsers", storage)
        val download = option("Download folder", "Destination for local files")
        val original = listOf(ConfigurableGroup(nestedConfigurable = listOf(browser, download)))
        val byTitle = filterSettingsGroups(original, "BROWSER")
        assertEquals(listOf(browser), byTitle.single().nestedConfigurable)
        val byDescription = filterSettingsGroups(original, "destination")
        assertEquals(listOf(download), byDescription.single().nestedConfigurable)
        assertEquals(false, storage.value)
    }

    @Test
    fun matchingChildKeepsItsRealMasterSwitchAndLiveBackingFlow() {
        val enabled = MutableStateFlow(false)
        val main = option("Enable sound notifications", "Main sound switch", enabled)
        val success = option("Success sound", "Pick completion audio")
        val error = option("Error sound", "Pick error audio")
        val original = ConfigurableGroup(mainConfigurable = main, nestedConfigurable = listOf(success, error))
        val match = filterSettingsGroups(listOf(original), "ERROR").single()
        assertSame(main, match.mainConfigurable)
        assertEquals(listOf(error), match.nestedConfigurable)
        match.mainConfigurable!!.set(true)
        assertTrue(enabled.value)
    }

    @Test
    fun matchingMasterOrGroupHeaderKeepsDependentOptions() {
        val master = option("Global transfer limit", "Master setting")
        val dependent = option("Limit bandwidth", "Per transfer rate")
        val group = ConfigurableGroup(mainConfigurable = master, nestedConfigurable = listOf(dependent))
        assertSame(group, filterSettingsGroups(listOf(group), "GLOBAL").single())
        assertEquals(listOf(dependent), filterSettingsGroups(listOf(group), "rate").single().nestedConfigurable)
    }

    @Test
    fun clearingSearchPreservesUnfilteredGroupsAndNoMatchesReturnsEmpty() {
        val group = ConfigurableGroup(nestedConfigurable = listOf(option("Proxy server", "Configure HTTP proxy")))
        val source = listOf(group)
        assertSame(source, filterSettingsGroups(source, " "))
        assertTrue(filterSettingsGroups(source, "nonexistent setting XYZ").isEmpty())
    }
}
