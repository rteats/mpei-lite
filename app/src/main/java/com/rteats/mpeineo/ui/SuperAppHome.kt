package com.rteats.mpeineo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons
import com.rteats.mpeineo.data.MailSummary

/** Only navigation and local preferences. Never loads network data on Home. */
@Composable
internal fun SuperAppHome(
    schedule: MainUiState,
    bars: BarsUiState,
    mail: MailUiState,
    tiles: List<HomeTile>,
    onChangeLayout: (List<HomeTile>) -> Unit,
    onOpen: (AppTab) -> Unit,
    onOpenNextLesson: (HomeLessonHighlight) -> Unit = { onOpen(AppTab.SCHEDULE) },
    onOpenBarsDiscipline: (String) -> Unit,
    onOpenMailMessage: (MailSummary) -> Unit,
    editing: Boolean = false,
) {
    val entries = HomeTileLayout.normalize(tiles)

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().testTag("superapp-home"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!editing) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                HomeNextLessonCard(
                    state = schedule,
                    onClick = { highlight ->
                        if (highlight != null) onOpenNextLesson(highlight)
                        else onOpen(AppTab.SCHEDULE)
                    },
                    barsDisciplines = bars.disciplines,
                    onOpenBarsDiscipline = onOpenBarsDiscipline,
                )
            }

        }
        items(
            items = entries,
            key = { it.tab.name },
            span = { tile ->
                GridItemSpan(if (editing || tile.size == HomeTileSize.EXPANDED) maxLineSpan else 1)
            },
        ) { tile ->
            val label = when (tile.tab) {
                AppTab.SCHEDULE -> "Пары"
                AppTab.BARS -> "БАРС"
                AppTab.MAIL -> "Почта"
                AppTab.MAPS -> "Корпуса"
                AppTab.SETTINGS -> "Настройки"
            }
            val icon = when (tile.tab) {
                AppTab.SCHEDULE -> AppIcons.Schedule
                AppTab.BARS -> AppIcons.Bars
                AppTab.MAIL -> AppIcons.Mail
                AppTab.MAPS -> AppIcons.Location
                AppTab.SETTINGS -> AppIcons.Settings
            }

            // Home shows only short status hints, not full service previews.
            val status = when (tile.tab) {
                AppTab.BARS -> when {
                    bars.loginRequired -> "Требуется вход"
                    bars.gradeChanges.values.any { it.isNotEmpty() } -> "Есть новые оценки"
                    else -> null
                }
                AppTab.MAIL -> when {
                    mail.loginRequired -> "Требуется вход"
                    mail.items.any { it.unread } -> "Есть непрочитанные"
                    else -> null
                }
                else -> null
            }

            if (editing) {
                Surface(
                    modifier = Modifier.fillMaxWidth()
                        .animateItem()
                        .testTag("home-edit-" + tile.tab.name.lowercase()),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            HomeTileIcon(icon, 40)
                            Text(
                                label,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            IconButton(
                                modifier = Modifier.testTag("home-move-up-" + tile.tab.name.lowercase()),
                                enabled = entries.indexOf(tile) > 0,
                                onClick = {
                                    onChangeLayout(HomeTileLayout.move(entries, tile.tab, -1))
                                },
                            ) {
                                AppIcon(AppIcons.ExpandLess, "Выше")
                            }
                            IconButton(
                                modifier = Modifier.testTag("home-move-down-" + tile.tab.name.lowercase()),
                                enabled = entries.indexOf(tile) < entries.lastIndex,
                                onClick = {
                                    onChangeLayout(HomeTileLayout.move(entries, tile.tab, 1))
                                },
                            ) {
                                AppIcon(AppIcons.ExpandMore, "Ниже")
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            listOf(
                                HomeTileSize.COMPACT to "Компактная",
                                HomeTileSize.REGULAR to "Обычная",
                                HomeTileSize.EXPANDED to "Широкая",
                            ).forEach { (size, title) ->
                                FilterChip(
                                    modifier = Modifier.testTag(
                                        "home-size-" + tile.tab.name.lowercase() + "-" + size.name.lowercase(),
                                    ),
                                    selected = tile.size == size,
                                    onClick = {
                                        onChangeLayout(HomeTileLayout.resize(entries, tile.tab, size))
                                    },
                                    label = { Text(title) },
                                )
                            }
                        }
                    }
                }
            } else {
                Surface(
                    onClick = { onOpen(tile.tab) },
                    modifier = Modifier.fillMaxWidth()
                        .animateItem()
                        .height(when (tile.size) {
                            HomeTileSize.COMPACT -> 84.dp
                            HomeTileSize.REGULAR -> 148.dp
                            HomeTileSize.EXPANDED -> 168.dp
                        })
                        .testTag("service-" + tile.tab.name.lowercase()),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    if (tile.size == HomeTileSize.COMPACT) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            HomeTileIcon(icon, 40)
                            Text(
                                label,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (status != null) {
                                Box(modifier = Modifier.size(8.dp)
                                    .testTag("home-status-" + tile.tab.name.lowercase())) {
                                    Surface(
                                        modifier = Modifier.fillMaxSize(),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                    ) {}
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(18.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            HomeTileIcon(icon, if (tile.size == HomeTileSize.EXPANDED) 56 else 48)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    label,
                                    style = if (tile.size == HomeTileSize.EXPANDED)
                                        MaterialTheme.typography.headlineSmall
                                    else MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                                if (status != null) {
                                    Text(
                                        status,
                                        modifier = Modifier.testTag("home-status-" + tile.tab.name.lowercase()),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTileIcon(icon: Int, sizeDp: Int) {
    Surface(
        modifier = Modifier.size(sizeDp.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            AppIcon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(if (sizeDp < 48) 22.dp else 26.dp),
            )
        }
    }
}
