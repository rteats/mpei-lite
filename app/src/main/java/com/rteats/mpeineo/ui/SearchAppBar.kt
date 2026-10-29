package com.rteats.mpeineo.ui

/** Context controls the shared Material 3 search field, not another search screen. */
internal data class SearchAppBarContext(
    val scope: SearchScope,
    val placeholder: String,
)

internal enum class AppBarMode { SEARCH, TITLE }

internal fun appBarMode(tab: AppTab?): AppBarMode = when (tab) {
    AppTab.SETTINGS -> AppBarMode.TITLE
    else -> AppBarMode.SEARCH
}

internal fun searchAppBarContext(tab: AppTab?): SearchAppBarContext = when (tab) {
    null -> SearchAppBarContext(SearchScope.SCHEDULE, "Поиск расписания")
    AppTab.HOME -> SearchAppBarContext(SearchScope.SCHEDULE, "Поиск MPEI Lite")
    AppTab.SCHEDULE -> SearchAppBarContext(
        SearchScope.SCHEDULE, "Группа, преподаватель, аудитория",
    )
    AppTab.BARS -> SearchAppBarContext(SearchScope.BARS, "Поиск дисциплины в БАРС")
    AppTab.MAIL -> SearchAppBarContext(SearchScope.MAIL, "Поиск по почте")
    AppTab.MAPS -> SearchAppBarContext(SearchScope.MAPS, "Корпус или аудитория")
    AppTab.SETTINGS -> error("Settings has a title bar")
}

internal enum class SearchAppBarAction { NONE, REFRESH_WEEK, COMPOSE_MAIL }

internal fun searchAppBarAction(tab: AppTab?, mailListReady: Boolean): SearchAppBarAction =
    when {
        tab == AppTab.SCHEDULE -> SearchAppBarAction.REFRESH_WEEK
        tab == AppTab.MAIL && mailListReady -> SearchAppBarAction.COMPOSE_MAIL
        else -> SearchAppBarAction.NONE
    }
