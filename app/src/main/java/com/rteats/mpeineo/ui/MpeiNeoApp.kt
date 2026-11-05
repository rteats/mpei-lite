package com.rteats.mpeineo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons
import com.rteats.mpeineo.MailNotificationTarget
import com.rteats.mpeineo.MpeiNeoApplication
import com.rteats.mpeineo.data.InterfacePreferences
import com.rteats.mpeineo.data.InterfaceTheme
import com.rteats.mpeineo.data.BarsNetworkDiagnostics
import com.rteats.mpeineo.data.BarsSnapshotStore

/** Single bottom navigation shell with native BARS and no pager. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    val mailLoginViewModel: MailLoginViewModel = composeViewModel(
        factory = MailLoginViewModel.factory(application, diagnostics),
    )
    val updateViewModel: UpdateViewModel = composeViewModel(
        factory = UpdateViewModel.factory(application.container),
    )
    val scheduleState by viewModel.state.collectAsState()
    val mailState by mailViewModel.state.collectAsState()
    val interfacePrefs = remember(application) { InterfacePreferences(application) }
    var navigationHaptics by remember { mutableStateOf(interfacePrefs.navigationHaptics) }
    var settingsReturnTab by rememberSaveable { mutableStateOf(AppTab.SCHEDULE) }
    var currentTab by rememberSaveable {
        mutableStateOf(if (initialMailTab) AppTab.MAIL else AppTab.SCHEDULE)
    }
    var mailWebVisible by rememberSaveable { mutableStateOf(false) }
    val primaryTabs = listOf(AppTab.SCHEDULE, AppTab.MAIL, AppTab.BARS, AppTab.MAPS)
    val widthDp = LocalConfiguration.current.screenWidthDp
    val expandedWidth = widthDp >= 840
    val compactWidth = widthDp < 600
    var reselectRequest by remember { mutableIntStateOf(0) }
    var barsDetailOpen by remember { mutableStateOf(false) }
    var mailFullScreenOpen by remember { mutableStateOf(false) }
    val detailOpen = currentTab == AppTab.MAIL &&
        (mailState.selected != null || mailState.composing || mailFullScreenOpen) ||
        currentTab == AppTab.BARS && barsDetailOpen
    // Primary navigation is a persistent app shell, not a detail-page control.
    // Shell navigation remains available even when an embedded portal is open.
    val navigationVisible = true

    fun openTab(tab: AppTab) {
        if (tab == currentTab) {
            reselectRequest++
            diagnostics.log("NAV", "reselect=" + tab.name)
            return
        }
        if (mailWebVisible) mailWebVisible = false
        if (tab == AppTab.SETTINGS && currentTab != AppTab.SETTINGS) {
            settingsReturnTab = currentTab
        }
        diagnostics.log("NAV", "tab=" + tab.name + " from=" + currentTab.name)
        currentTab = tab
    }

    fun closeMailBrowser() {
        mailWebVisible = false
        diagnostics.log("MAIL_WEB", "portal closed")
        mailViewModel.refresh()
    }

    // Only retry an expired OWA session once with previously saved credentials.
    // A successful mailbox request resets the guard for the next expiry.
    LaunchedEffect(mailState.loginRequired, mailState.loading, mailState.configured, mailState.error) {
        when {
            mailState.loginRequired && !mailState.loading ->
                mailLoginViewModel.restoreSavedLogin(mailViewModel::refresh)
            mailState.configured && !mailState.loading &&
                !mailState.loginRequired && mailState.error == null ->
                mailLoginViewModel.onMailboxReady()
        }
    }
    // Keep session state fresh without requiring the user to open Mail.
    // Checks stop while the app is not visible; WorkManager handles longer periods.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val listener = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                mailViewModel.checkSessionProactively("app_foreground")
            }
        }
        lifecycleOwner.lifecycle.addObserver(listener)
        onDispose { lifecycleOwner.lifecycle.removeObserver(listener) }
    }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(15 * 60_000L)
                mailViewModel.checkSessionProactively("foreground_periodic")
            }
        }
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
        !detailOpen) {
        openTab(if (currentTab == AppTab.SETTINGS) settingsReturnTab else AppTab.SCHEDULE)
    }

    val railVisible = navigationVisible && expandedWidth
    Row(Modifier.fillMaxSize()) {
        if (railVisible) {
            NavigationRail(modifier = Modifier.testTag("main-navigation-rail")) {
                primaryTabs.forEach { tab ->
                    val selected = currentTab == tab
                    NavigationRailItem(
                        selected = selected,
                        onClick = { openTab(tab) },
                        icon = {
                            MainNavigationIcon(
                                tab = tab, selected = selected,
                                unreadMail = mailState.configured && mailState.items.any { it.unread },
                            )
                        },
                        label = { Text(mainTabLabel(tab), maxLines = 1) },
                        modifier = Modifier.testTag("nav-" + tab.name.lowercase()),
                    )
                }
            }
        }
        Scaffold(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(
                    if (railVisible) {
                        Modifier.consumeWindowInsets(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Start),
                        )
                    } else Modifier,
                ),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = if (railVisible) {
                ScaffoldDefaults.contentWindowInsets.only(
                    WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom,
                )
            } else ScaffoldDefaults.contentWindowInsets,
            topBar = {
                if (!mailWebVisible && !(currentTab == AppTab.MAIL &&
                        (mailState.composing || mailFullScreenOpen))) {
                    if (currentTab == AppTab.MAIL) {
                        MailSearchAppBar(
                            mail = mailState,
                            onOpen = { hit ->
                                mailViewModel.switchMailbox(hit.folder)
                                mailViewModel.open(hit.message)
                            },
                            onOpenPortal = { mailWebVisible = true },
                            onLogout = mailViewModel::logout,
                            onSettings = { openTab(AppTab.SETTINGS) },
                        )
                    } else if (currentTab != AppTab.SCHEDULE) {
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
                            navigationIcon = {
                                if (currentTab == AppTab.SETTINGS) {
                                    IconButton(onClick = { openTab(settingsReturnTab) },
                                        modifier = Modifier.testTag("settings-back")) {
                                        AppIcon(AppIcons.Back, contentDescription = "Назад")
                                    }
                                }
                            },
                            actions = {
                                if (currentTab != AppTab.SETTINGS) {
                                    IconButton(onClick = { openTab(AppTab.SETTINGS) },
                                        modifier = Modifier.testTag("open-settings")) {
                                        AppIcon(AppIcons.Settings, contentDescription = "Настройки")
                                    }
                                }
                            },
                            modifier = Modifier.testTag("main-app-bar"),
                        )
                    }
                }
            },
            bottomBar = {
                if (navigationVisible && !expandedWidth) {
                    ShortNavigationBar(
                        modifier = Modifier.testTag("main-navigation-bar"),
                        arrangement = if (compactWidth) ShortNavigationBarArrangement.EqualWeight
                            else ShortNavigationBarArrangement.Centered,
                    ) {
                        primaryTabs.forEach { tab ->
                            val selected = currentTab == tab
                            ShortNavigationBarItem(
                                selected = selected,
                                onClick = { openTab(tab) },
                                icon = {
                                    MainNavigationIcon(
                                        tab = tab, selected = selected,
                                        unreadMail = mailState.configured && mailState.items.any { it.unread },
                                    )
                                },
                                label = { Text(mainTabLabel(tab), maxLines = 1) },
                                iconPosition = if (compactWidth) NavigationItemIconPosition.Top
                                    else NavigationItemIconPosition.Start,
                                modifier = Modifier.testTag("nav-" + tab.name.lowercase()),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(
                Modifier.fillMaxSize().padding(padding)
                    .consumeWindowInsets(padding).imePadding(),
            ) {
                when (currentTab) {
                AppTab.SCHEDULE -> ScheduleExplorerScreen(
                    state = scheduleState,
                    onQueryChange = viewModel::updateSearchQuery,
                    onSearchTypeChange = viewModel::setSearchType,
                    onSelectTarget = viewModel::selectTarget,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onEnsureWeek = viewModel::ensureAgendaWeek,
                    onRefreshWeek = viewModel::refreshAgendaWeek,
                    onRetryWeek = viewModel::retryAgendaWeek,
                    onSettings = { openTab(AppTab.SETTINGS) },
                    reselectRequest = reselectRequest,
                    modifier = Modifier.fillMaxSize(),
                )
                AppTab.MAIL -> {
                    MailScreen(
                        viewModel = mailViewModel,
                        loginViewModel = mailLoginViewModel,
                        active = !mailWebVisible,
                        onOpenPortal = { mailWebVisible = true },
                        reselectRequest = reselectRequest,
                        onFullScreenChange = { mailFullScreenOpen = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                    SpatialScreenOverlay(visible = mailWebVisible) {
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
                    reselectRequest = reselectRequest,
                    onNavigationHapticsChange = {
                        interfacePrefs.setNavigationHaptics(it)
                        navigationHaptics = it
                        diagnostics.log("UI_PREF", "navigation_haptics=" + it)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                AppTab.BARS -> {
                    val barsViewModel: BarsViewModel = composeViewModel(
                        factory = BarsViewModel.factory(
                            diagnostics,
                            BarsNetworkDiagnostics(application, diagnostics),
                            BarsSnapshotStore(application),
                        ),
                    )
                    BarsScreen(
                        viewModel = barsViewModel,
                        reselectRequest = reselectRequest,
                        onDetailOpenChange = { barsDetailOpen = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                }
            }
        }
    }
}

private fun mainTabLabel(tab: AppTab): String = when (tab) {
    AppTab.SCHEDULE -> "Пары"
    AppTab.MAIL -> "Почта"
    AppTab.BARS -> "БАРС"
    AppTab.MAPS -> "Карта"
    AppTab.SETTINGS -> "Настройки"
}

@Composable
private fun MainNavigationIcon(tab: AppTab, selected: Boolean, unreadMail: Boolean) {
    val icon = when (tab) {
        AppTab.SCHEDULE -> if (selected) AppIcons.ScheduleFilled else AppIcons.Schedule
        AppTab.MAIL -> if (selected) AppIcons.MailFilled else AppIcons.Mail
        AppTab.BARS -> if (selected) AppIcons.BarsFilled else AppIcons.Bars
        AppTab.MAPS -> if (selected) AppIcons.LocationFilled else AppIcons.Location
        AppTab.SETTINGS -> if (selected) AppIcons.SettingsFilled else AppIcons.Settings
    }
    if (tab == AppTab.MAIL && unreadMail) {
        BadgedBox(badge = { Badge() }) {
            AppIcon(icon, contentDescription = null)
        }
    } else {
        AppIcon(icon, contentDescription = null)
    }
}
