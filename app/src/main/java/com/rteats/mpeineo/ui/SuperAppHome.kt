package com.rteats.mpeineo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.TextButton
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons

/**
 * Root service launcher. Only cached state is read here; opening the home
 * screen must not fan out into several network requests.
 */
@Composable
internal fun SuperAppHome(
    bars: BarsUiState,
    onOpen: (AppTab) -> Unit,
    onSearch: () -> Unit,
    barsAutoRefreshEnabled: Boolean = true,
) {
    val clipboard = LocalClipboardManager.current
    val mainLines = remember(bars.rawMain) { bars.rawMain.lines().filter { it.isNotBlank() } }
    val summaryLines = remember(bars.rawSummary) { bars.rawSummary.lines().filter { it.isNotBlank() } }
    val entries = listOf(
        HomeService(AppTab.SCHEDULE, "Пары", AppIcons.Schedule),
        HomeService(AppTab.BARS, "БАРС", AppIcons.Bars),
        HomeService(AppTab.MAIL, "Почта", AppIcons.Mail),
        HomeService(AppTab.MAPS, "Корпуса", AppIcons.Location),
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
                modifier = Modifier.fillMaxWidth().height(148.dp)
                    .testTag("service-" + entry.tab.name.lowercase()),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(18.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            AppIcon(
                                entry.icon,
                                contentDescription = null,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 6.dp)) {
                Text(
                    "БАРС — полный набор данных",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Временный диагностический вывод. Данные загружаются из вашей сессии БАРС, а не из сохранённых HTML.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (bars.rawStatus.isNotBlank()) {
                    Text(
                        bars.rawStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Main · профиль, рейтинг, мероприятия",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium)
                if (bars.rawMain.isNotBlank()) {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(bars.rawMain))
                    }) { Text("Копировать") }
                }
            }
        }
        if (mainLines.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    when {
                        bars.loginRequired -> "Сессия БАРС истекла. Для входа и 2FA откройте БАРС."
                        !barsAutoRefreshEnabled -> "Автообновление БАРС выключено в настройках."
                        bars.isLoading -> "Получаем данные Main автоматически…"
                        else -> "Данные Main пока недоступны. Проверьте подключение к БАРС."
                    },
                    style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            items(
                count = mainLines.size,
                span = { GridItemSpan(maxLineSpan) },
                key = { "raw-main-" + it },
            ) { index ->
                Text(
                    mainLines[index],
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp, horizontal = 4.dp),
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Summary · матрица оценок, посещаемость",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium)
                if (bars.rawSummary.isNotBlank()) {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(bars.rawSummary))
                    }) { Text("Копировать") }
                }
            }
        }
        if (summaryLines.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    when {
                        bars.loginRequired -> "Требуется вход в БАРС для получения Summary."
                        !barsAutoRefreshEnabled -> "Автообновление БАРС выключено в настройках."
                        bars.isLoading -> "Получаем таблицы Summary автоматически…"
                        else -> "Summary пока недоступен. Повторная проверка — при следующем обновлении БАРС."
                    },
                    style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            items(
                count = summaryLines.size,
                span = { GridItemSpan(maxLineSpan) },
                key = { "raw-summary-" + it },
            ) { index ->
                Text(
                    summaryLines[index],
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp, horizontal = 4.dp),
                )
            }
        }

    }
}

private data class HomeService(
    val tab: AppTab,
    val title: String,
    val icon: Int,
)
