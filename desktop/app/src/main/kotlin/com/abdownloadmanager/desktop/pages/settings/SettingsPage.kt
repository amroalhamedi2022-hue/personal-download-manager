package com.abdownloadmanager.desktop.pages.settings

import com.abdownloadmanager.shared.util.ui.WithContentAlpha
import com.abdownloadmanager.desktop.window.custom.WindowIcon
import ir.amirab.util.compose.IconSource
import com.abdownloadmanager.shared.util.ui.widget.MyIcon
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.ui.widget.Handle
import com.abdownloadmanager.shared.ui.widget.Text
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abdownloadmanager.shared.ui.configurable.RenderConfigurableGroup
import com.abdownloadmanager.shared.settings.filterSettingsGroups
import com.abdownloadmanager.shared.ui.widget.MyTextField
import com.abdownloadmanager.shared.ui.widget.ActionButton
import androidx.compose.runtime.saveable.rememberSaveable
import com.abdownloadmanager.shared.util.div
import com.abdownloadmanager.shared.util.ui.MultiplatformVerticalScrollbar
import com.abdownloadmanager.shared.util.ui.needScroll
import com.abdownloadmanager.shared.util.ui.theme.myShapes
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import ir.amirab.util.ifThen

@Composable
private fun SideBar(
    settingsComponent: DesktopSettingsComponent,
    modifier: Modifier = Modifier,
) {
    val shape = myShapes.defaultRounded
    Column(
        modifier
            .fillMaxHeight()
            .border(1.dp, myColors.surface / 0.5f, shape)
            .clip(shape)
    ) {
//        var searchText by remember { mutableStateOf("") }
//        SearchBox(
//            searchText,
//            onTextChange = { searchText = it },
//            modifier = Modifier.height(38.dp),
//        )
        val collectAsState by settingsComponent.currentPage.collectAsState()
        for (i in settingsComponent.pages) {
            SideBarItem(
                icon = i.icon,
                name = i.name.rememberString(),
                isSelected = collectAsState == i,
                onClick = {
                    settingsComponent.setCurrentPage(i)
                }
            )
        }
    }
}

@Composable
private fun SideBarItem(icon: IconSource, name: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(IntrinsicSize.Max)
            .ifThen(isSelected) {
                background(myColors.onBackground / 0.05f)
            }
            .selectable(
                selected = isSelected,
                onClick = onClick
            )
    ) {
        Row(
            Modifier
                .padding(vertical = 8.dp)
                .padding(start = 16.dp)
                .padding(end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WithContentAlpha(if (isSelected) 1f else 0.75f) {
                MyIcon(
                    icon,
                    null,
                    Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    name,
                    Modifier.weight(1f),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = myTextSizes.lg,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            }
        }
        AnimatedVisibility(
            isSelected,
            modifier = Modifier
                .align(Alignment.CenterStart),
            enter = scaleIn(),
            exit = scaleOut(),
        ) {
            Spacer(
                Modifier
                    .height(16.dp)
                    .width(3.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 0.dp,
                            bottomStart = 0.dp,
                            bottomEnd = 12.dp,
                            topEnd = 12.dp,
                        )
                    )
                    .background(myColors.primary)
            )
        }
        if (isSelected) {
            listOf(
                Alignment.TopCenter,
                Alignment.BottomCenter,
            ).forEach {
                Spacer(
                    Modifier
                        .align(it)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    myColors.onBackground / 0.1f,
                                    myColors.onBackground / 0.1f,
                                    Color.Transparent,
                                )
                            )
                        )
                )
            }
        }
    }
}

@Composable
fun SettingsPage(
    settingsComponent: DesktopSettingsComponent,
    onDismissRequest: () -> Unit,
) {
    WindowIcon(MyIcons.appIcon)
    var search by rememberSaveable { mutableStateOf("") }
    val searching = search.isNotBlank()
    val currentPage by settingsComponent.currentPage.collectAsState()
    val currentConfigurables by settingsComponent.configurables.collectAsState()

    // A search spans Appearance, Download Engine and Browser Integration.
    // Reuse real backedBy controls; the search itself never mutates a setting.
    val displayedSections = if (searching) {
        settingsComponent.allSettingsSections().mapNotNull { (section, groups) ->
            val matchingGroups = if (section.name.getString().contains(search.trim(), ignoreCase = true)) {
                groups
            } else {
                filterSettingsGroups(groups, search)
            }
            if (matchingGroups.isEmpty()) null else section to matchingGroups
        }
    } else {
        listOf(currentPage to currentConfigurables)
    }

    Column {
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(myColors.surface))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MyTextField(
                search,
                { search = it },
                "Search all settings by name or description",
                Modifier.weight(1f),
            )
            if (searching) {
                Spacer(Modifier.width(8.dp))
                ActionButton("Clear", onClick = { search = "" })
            }
        }
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(myColors.onBackground / 0.12f))
        Row {
            var sideBarWidth by remember { mutableStateOf(250.dp) }
            SideBar(
                settingsComponent,
                Modifier.fillMaxHeight().width(sideBarWidth).padding(8.dp),
            )
            Handle(Modifier.width(5.dp).fillMaxHeight(), orientation = Orientation.Horizontal) {
                sideBarWidth = (sideBarWidth + it).coerceIn(150.dp..300.dp)
            }
            val scrollState = rememberScrollState()
            val scrollbarAdapter = rememberScrollbarAdapter(scrollState)
            Row {
                Column(
                    Modifier.weight(1f).verticalScroll(scrollState)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (displayedSections.isEmpty()) {
                        Text(
                            "No settings match your search.",
                            Modifier.padding(16.dp),
                            color = myColors.onBackground,
                        )
                    }
                    for ((section, groups) in displayedSections) {
                        if (searching) {
                            Text(
                                section.name.rememberString(),
                                Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                                fontSize = myTextSizes.lg,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        for (cfgGroup in groups) {
                            RenderConfigurableGroup(
                                cfgGroup,
                                Modifier,
                                itemPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
                            )
                        }
                    }
                }
                if (scrollbarAdapter.needScroll()) {
                    MultiplatformVerticalScrollbar(
                        adapter = scrollbarAdapter,
                        modifier = Modifier.padding(vertical = 8.dp).padding(end = 2.dp),
                    )
                }
            }
        }
    }
}



