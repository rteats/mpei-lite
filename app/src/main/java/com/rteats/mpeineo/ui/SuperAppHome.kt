package com.rteats.mpeineo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons

/**
 * Root service launcher. Only cached state is read here; opening the home
 * screen must not fan out into several network requests.
 */
@Composable
internal fun SuperAppHome(
    schedule: MainUiState,
    bars: BarsUiState,
    mail: MailUiState,
    onOpen: (AppTab) -> Unit,
    onSearch: () -> Unit,
) {
    val unread = mail.items.count { it.unread }
    val entries = listOf(
        HomeService(
            AppTab.SCHEDULE, "Расписание",
            schedule.selected?.name?.takeIf { it.isNotBlank() } ?: "Пары и поиск",
            AppIcons.Schedule,
        ),
        HomeService(
            AppTab.BARS, "БАРС",
            if (bars.disciplines.isNotEmpty())
                bars.disciplines.size.toString() + " дисциплин"
            else "Оценки и посещаемость",
            AppIcons.Bars,
        ),
        HomeService(
            AppTab.MAIL, "Почта",
            when {
                !mail.configured -> "Подключить ОСЭП"
                unread > 0 -> "Есть непрочитанные письма"
                else -> "Входящие ОСЭП"
            },
            AppIcons.Mail,
        ),
        HomeService(
            AppTab.MAPS, "Кампус", "Корпуса и аудитории", AppIcons.Location,
        ),
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().testTag("superapp-home"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    modifier = Modifier.weight(1f).height(58.dp)
                        .clickable(onClick = onSearch).testTag("superapp-global-search"),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppIcon(AppIcons.Search, contentDescription = null)
                        Text(
                            "Поиск по МЭИ",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(
                    onClick = { onOpen(AppTab.SETTINGS) },
                    modifier = Modifier.testTag("superapp-settings"),
                ) {
                    AppIcon(AppIcons.Settings, contentDescription = "Настройки")
                }
            }
        }
        items(entries, key = { it.tab.name }) { entry ->
            Surface(
                onClick = { onOpen(entry.tab) },
                modifier = Modifier.fillMaxWidth().height(172.dp)
                    .testTag("service-" + entry.tab.name.lowercase()),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(18.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(
                            entry.icon,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            entry.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Text(
                            entry.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private data class HomeService(
    val tab: AppTab,
    val title: String,
    val subtitle: String,
    val icon: Int,
)
