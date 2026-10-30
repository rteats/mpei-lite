package com.rteats.mpeineo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons
import com.rteats.mpeineo.MailNotificationTarget
import com.rteats.mpeineo.MpeiNeoApplication
import com.rteats.mpeineo.data.InterfacePreferences
import com.rteats.mpeineo.data.InterfaceTheme

/** Single bottom navigation shell: no pager, search overlay, or active BARS. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MpeiNeoApp(
    viewModel: MainViewModel,
    initialMailTab: Boolean = false,
    mailNotificationTarget: MailNotificationTarget? = null,
    interfaceTheme: InterfaceTheme = InterfaceTheme.SYSTEM,
    onInterfaceThemeChange: (InterfaceTheme) -> Unit = {},
) {
    val application = LocalContext.current.applicationContext as MpeiNeoApplication
    val diagnostics = application.container.diagnostics
    val mailViewModel: MailViewModel = composeViewModel(
        factory = MailViewModel.factory(
            application.container.mailRepository,
            application.container.owaAuthProbe,
            application.container.owaMailRepository,
        ),
    )
    val updateViewModel: UpdateViewModel = composeViewModel(
        factory = UpdateViewModel.factory(application.container),
    )
    val scheduleState by viewModel.state.collectAsState()
    val mailState by mailViewModel.state.collectAsState()
    val interfacePrefs = remember(application) { InterfacePreferences(application) }
    var navigationHaptics by remember { mutableStateOf(interfacePrefs.navigationHaptics) }
    var currentTab by rememberSaveable {
        mutableStateOf(if (initialMailTab) AppTab.MAIL else AppTab.SCHEDULE)
    }
    var mailWebVisible by rememberSaveable { mutableStateOf(false) }
    val primaryTabs = listOf(AppTab.SCHEDULE, AppTab.MAIL, AppTab.MAPS, AppTab.SETTINGS)

    fun openTab(tab: AppTab) {
        if (tab == currentTab) return
        if (mailWebVisible) mailWebVisible = false
        diagnostics.log("NAV", "tab=" + tab.name + " from=" + currentTab.name)
        currentTab = tab
    }

    fun closeMailBrowser() {
        mailWebVisible = false
        diagnostics.log("MAIL_WEB", "portal closed")
        mailViewModel.refresh()
    }

    LaunchedEffect(Unit) { application.startPostLaunchMaintenance() }
    LaunchedEffect(mailNotificationTarget?.requestId) {
        val target = mailNotificationTarget ?: return@LaunchedEffect
        openTab(AppTab.MAIL)
        if (target.uid > 0 && target.validity > 0) {
            mailViewModel.openFromNotification(target.uid, target.validity)
        } else mailViewModel.closeMessage()
    }
    LaunchedEffect(currentTab) { application.container.uiFrameProfiler.markScreen(currentTab.name) }

    BackHandler(enabled = currentTab == AppTab.MAIL && mailWebVisible) {
        closeMailBrowser()
    }
    BackHandler(enabled = currentTab != AppTab.SCHEDULE && !mailWebVisible &&
        !(currentTab == AppTab.MAIL && (mailState.selected != null || mailState.composing))) {
        openTab(AppTab.SCHEDULE)
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(when (currentTab) {
                        AppTab.SCHEDULE -> "Расписание"
                        AppTab.MAIL -> "Почта"
                        AppTab.MAPS -> "Карта"
                        AppTab.SETTINGS -> "Настройки"
                        AppTab.BARS -> "БАРС"
                    })
                },
                modifier = Modifier.testTag("main-app-bar"),
            )
        },
        bottomBar = {
            NavigationBar(modifier = Modifier.testTag("main-navigation-bar")) {
                primaryTabs.forEach { tab ->
                    val label = when (tab) {
                        AppTab.SCHEDULE -> "Расписание"
                        AppTab.MAIL -> "Почта"
                        AppTab.MAPS -> "Карта"
                        AppTab.SETTINGS -> "Настройки"
                        AppTab.BARS -> "БАРС"
                    }
                    val icon = when (tab) {
                        AppTab.SCHEDULE -> AppIcons.Schedule
                        AppTab.MAIL -> AppIcons.Mail
                        AppTab.MAPS -> AppIcons.Location
                        AppTab.SETTINGS -> AppIcons.Settings
                        AppTab.BARS -> AppIcons.Bars
                    }
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { openTab(tab) },
                        icon = {
                            if (tab == AppTab.MAIL && mailState.configured &&
                                mailState.items.any { it.unread }) {
                                BadgedBox(badge = { Badge() }) {
                                    AppIcon(icon, contentDescription = label)
                                }
                            } else AppIcon(icon, contentDescription = label)
                        },
                        label = { Text(label) },
                        modifier = Modifier.testTag("nav-" + tab.name.lowercase()),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (currentTab) {
                AppTab.SCHEDULE -> ScheduleRawScreen(
                    state = scheduleState,
                    onQueryChange = viewModel::updateSearchQuery,
                    onSelectTarget = viewModel::selectTarget,
                    onEnsureWeek = viewModel::ensureAgendaWeek,
                    onRefreshWeek = viewModel::refreshAgendaWeek,
                    onRetryWeek = viewModel::retryAgendaWeek,
                    modifier = Modifier.fillMaxSize(),
                )
                AppTab.MAIL -> {
                    MailScreen(
                        viewModel = mailViewModel,
                        active = !mailWebVisible,
                        onOpenPortal = { mailWebVisible = true },
                        onSearch = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (mailWebVisible) {
                        WebPortalScreen(
                            url = "https://mail.mpei.ru/owa",
                            testTag = "portal-mail",
                            modifier = Modifier.fillMaxSize().testTag("mail-webview"),
                            onDebugEvent = { diagnostics.log("MAIL_WEB", it) },
                            onExit = ::closeMailBrowser,
                        )
                    }
                }
                AppTab.MAPS -> CampusMapScreen(
                    active = true,
                    modifier = Modifier.fillMaxSize(),
                )
                AppTab.SETTINGS -> SettingsScreen(
                    updateViewModel = updateViewModel,
                    mailViewModel = mailViewModel,
                    interfaceTheme = interfaceTheme,
                    onInterfaceThemeChange = onInterfaceThemeChange,
                    navigationHaptics = navigationHaptics,
                    onNavigationHapticsChange = {
                        interfacePrefs.setNavigationHaptics(it)
                        navigationHaptics = it
                        diagnostics.log("UI_PREF", "navigation_haptics=" + it)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                AppTab.BARS -> Unit
            }
        }
    }
}
