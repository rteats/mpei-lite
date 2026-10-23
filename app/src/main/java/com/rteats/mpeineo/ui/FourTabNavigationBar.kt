package com.rteats.mpeineo.ui

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons

/** Four stable and accessible primary destinations; Settings is not a tab. */
@Composable
internal fun FourTabNavigationBar(
    selected: AppTab,
    unreadMail: Boolean,
    onSelect: (AppTab) -> Unit,
) {
    NavigationBar(modifier = Modifier.testTag("four-tab-navigation")) {
        listOf(
            Triple(AppTab.SCHEDULE, "Пары", AppIcons.Schedule),
            Triple(AppTab.BARS, "БАРС", AppIcons.Bars),
            Triple(AppTab.MAIL, "Почта", AppIcons.Mail),
            Triple(AppTab.MAPS, "Корпуса", AppIcons.Location),
        ).forEach { (tab, title, icon) ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = {
                    if (tab == AppTab.MAIL && unreadMail) {
                        BadgedBox(badge = { Badge() }) {
                            AppIcon(icon, contentDescription = null)
                        }
                    } else AppIcon(icon, contentDescription = null)
                },
                label = { Text(title) },
                modifier = Modifier.testTag("tab-" + tab.name.lowercase()),
            )
        }
    }
}
