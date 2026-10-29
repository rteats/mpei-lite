package com.rteats.mpeineo.ui

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons

/** Native Material primary icon tabs. The pager and route share the same order. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PrimaryIconServiceTabs(
    selected: AppTab,
    unreadMail: Boolean,
    onSelect: (AppTab) -> Unit,
) {
    PrimaryTabRow(
        selectedTabIndex = primaryPageFor(selected),
        modifier = Modifier.testTag("primary-icon-tabs"),
    ) {
        PRIMARY_TABS.forEach { tab ->
            val (label, icon) = when (tab) {
                AppTab.HOME -> "Главная" to AppIcons.Home
                AppTab.SCHEDULE -> "Пары" to AppIcons.Schedule
                AppTab.BARS -> "БАРС" to AppIcons.Bars
                AppTab.MAIL -> "Почта" to AppIcons.Mail
                AppTab.MAPS -> "Карта" to AppIcons.Location
                AppTab.SETTINGS -> error("Settings is not a pager destination")
            }
            Tab(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = {
                    if (tab == AppTab.MAIL && unreadMail) {
                        BadgedBox(badge = { Badge() }) {
                            AppIcon(icon, contentDescription = label)
                        }
                    } else AppIcon(icon, contentDescription = label)
                },
                modifier = Modifier.testTag("tab-" + tab.name.lowercase()),
            )
        }
    }
}
