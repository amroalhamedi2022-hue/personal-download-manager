package com.abdownloadmanager.shared.settings

import com.abdownloadmanager.shared.ui.configurable.Configurable
import com.abdownloadmanager.shared.ui.configurable.ConfigurableGroup
import ir.amirab.util.compose.StringSource

/**
 * Search the existing live setting controls, not cloned setting-value objects.
 * Preserve a parent/master control whenever one of its dependent options is
 * matched; otherwise a filtered child could lose its enabling switch.
 *
 * A caller may supply translated labels, while the default uses the current
 * LanguageManager via StringSource.getString().
 */
fun filterSettingsGroups(
    groups: List<ConfigurableGroup>,
    query: String,
    resolveLabel: (StringSource) -> String = { it.getString() },
): List<ConfigurableGroup> {
    val term = query.trim()
    if (term.isEmpty()) return groups
    fun matches(setting: Configurable<*>): Boolean =
        resolveLabel(setting.title).contains(term, ignoreCase = true) ||
            resolveLabel(setting.description).contains(term, ignoreCase = true)

    return groups.mapNotNull { group ->
        val groupMatches = group.groupTitle.value?.let {
            resolveLabel(it).contains(term, ignoreCase = true)
        } == true
        if (groupMatches || group.mainConfigurable?.let(::matches) == true) {
            group
        } else {
            val children = group.nestedConfigurable.filter(::matches)
            if (children.isEmpty()) null
            else group.copy(nestedConfigurable = children)
        }
    }
}
