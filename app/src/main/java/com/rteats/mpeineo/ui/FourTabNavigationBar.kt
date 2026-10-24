package com.rteats.mpeineo.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons

/**
 * Four short navigation destinations: EqualWeight / icon-on-top on compact
 * phones, Centered / icon-at-start on medium landscape widths. Both variants
 * are native Material 3 Expressive and inherit the theme motion scheme.
 */
@Composable
internal fun FourTabNavigationBar(
    selected: AppTab,
    unreadMail: Boolean,
    onSelect: (AppTab) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.testTag("four-tab-navigation")) {
        val mediumWidth = maxWidth >= 600.dp
        ShortNavigationBar(
            arrangement = if (mediumWidth) ShortNavigationBarArrangement.Centered
                else ShortNavigationBarArrangement.EqualWeight,
        ) {
            listOf(
                Triple(AppTab.SCHEDULE, "Пары", AppIcons.Schedule),
                Triple(AppTab.BARS, "БАРС", AppIcons.Bars),
                Triple(AppTab.MAIL, "Почта", AppIcons.Mail),
                Triple(AppTab.MAPS, "Корпуса", AppIcons.Location),
            ).forEach { (tab, title, icon) ->
                ShortNavigationBarItem(
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
                    iconPosition = if (mediumWidth) NavigationItemIconPosition.Start
                        else NavigationItemIconPosition.Top,
                    modifier = Modifier.testTag("tab-" + tab.name.lowercase()),
                )
            }
        }
    }
}
