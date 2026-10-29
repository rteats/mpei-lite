package com.rteats.mpeineo.ui

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons

/**
 * Match the indicator's actual position to the Foundation pager's drag offset.
 * The indicator uses a fixed 24 dp width and does not apply a second animation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PrimaryIconServiceTabs(
    selected: AppTab,
    unreadMail: Boolean,
    pagerPosition: Float = primaryPageFor(selected).toFloat(),
    onSelect: (AppTab) -> Unit,
) {
    PrimaryTabRow(
        selectedTabIndex = primaryPageFor(selected),
        modifier = Modifier.testTag("primary-icon-tabs"),
        indicator = {
            TabRowDefaults.PrimaryIndicator(
                modifier = Modifier.tabIndicatorLayout { measurable, constraints, tabPositions ->
                    val safePosition = pagerPosition.coerceIn(0f, tabPositions.lastIndex.toFloat())
                    val before = safePosition.toInt()
                    val after = (before + 1).coerceAtMost(tabPositions.lastIndex)
                    val fraction = safePosition - before
                    val left = tabPositions[before].left.toPx()
                    val right = tabPositions[after].left.toPx()
                    val width = tabPositions[before].width.toPx()
                    val indicatorWidth = 24.dp.roundToPx()
                    val indicatorHeight = 3.dp.roundToPx()
                    val placeable = measurable.measure(
                        Constraints.fixed(indicatorWidth, indicatorHeight)
                    )
                    val center = left + (right - left) * fraction + width / 2f
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.placeRelative(
                            (center - indicatorWidth / 2f).toInt(),
                            constraints.maxHeight - indicatorHeight,
                        )
                    }
                },
                width = 24.dp,
            )
        },
    ) {
        PRIMARY_TABS.forEach { tab ->
            val (label, icon) = when (tab) {
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
