package com.rteats.mpeineo.ui

import android.content.Context
import android.os.Build
import android.view.WindowInsets as AndroidWindowInsets
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.rememberContainedSearchBarState
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons
import com.rteats.mpeineo.MailNotificationTarget
import com.rteats.mpeineo.data.MailSummary
import com.rteats.mpeineo.model.ScheduleTarget
import com.rteats.mpeineo.MpeiNeoApplication
import com.rteats.mpeineo.data.InterfacePreferences
import com.rteats.mpeineo.data.InterfaceTheme
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Four horizontally scrolling service pages. The navigation state owns committed
 * routes; Pager owns gesture physics. BARS always remains composed for 2FA.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
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
    // The first Schedule frame must not compete with a hidden Chromium WebView.
    var backgroundStartupReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        // This delay gates background work only; it never holds the splash screen.
        delay(250)
        application.startPostLaunchMaintenance()
        backgroundStartupReady = true
    }
    val hostView = LocalView.current
    val hapticFeedback = LocalHapticFeedback.current
    val interfacePrefs = remember(application) { InterfacePreferences(application) }
    var navigationHaptics by remember(interfacePrefs) {
        mutableStateOf(interfacePrefs.navigationHaptics)
    }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val inputMethodManager = remember(hostView) {
        hostView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }
    fun logNavigation(event: String) = diagnostics.log("NAV", event)
    fun dismissIme(reason: String) {
        val focused = hostView.rootView.findFocus()
        logNavigation("IME cleanup reason=" + reason +
            " focused=" + (focused?.javaClass?.simpleName ?: "none") +
            " web=" + (focused is WebView))
        focused?.clearFocus()
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        inputMethodManager.hideSoftInputFromWindow(hostView.windowToken, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hostView.windowInsetsController?.hide(AndroidWindowInsets.Type.ime())
        }
    }

    val barsViewModel: BarsViewModel = composeViewModel(
        factory = BarsViewModel.factory(
            application.container.diagnostics,
            application.container.barsNetworkDiagnostics,
            application.container.barsSnapshots,
        ),
    )
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
    val barsState by barsViewModel.state.collectAsState()
    val mailState by mailViewModel.state.collectAsState()
    val scheduleState by viewModel.state.collectAsState()
    val barsPrefs = remember(application) {
        application.getSharedPreferences("bars_settings", Context.MODE_PRIVATE)
    }
    var barsAutoRefreshEnabled by remember(barsPrefs) {
        mutableStateOf(barsPrefs.getBoolean("auto_refresh", true))
    }

    // Route ownership is separate from the rendering/transition state.
    val navigation = rememberServiceNavigationState(initialMailTab)
    val currentService = navigation.current
    val beneathService = navigation.beneath
    val savedServiceState = rememberSaveableStateHolder()
    val foregroundTab = if (currentService == AppTab.SETTINGS) {
        beneathService ?: AppTab.SCHEDULE
    } else currentService ?: AppTab.SCHEDULE
    val pagerState = rememberPagerState(
        initialPage = primaryPageFor(foregroundTab),
        pageCount = { PRIMARY_TABS.size },
    )
    // Compose Foundation owns the page physics.
    var programmaticPaging by remember { mutableStateOf(false) }
    var pagerGesturePending by remember { mutableStateOf(false) }
    val pagerDragging by pagerState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(pagerDragging) {
        if (pagerDragging && !programmaticPaging) pagerGesturePending = true
    }
    var todayJumpRequest by rememberSaveable { mutableIntStateOf(0) }
    val searchUi = rememberSearchOverlayState()
    val searchBarState = rememberContainedSearchBarState()
    val searchScrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()
    val searchText = rememberTextFieldState()
    val searchScope = rememberCoroutineScope()
    LaunchedEffect(searchBarState.currentValue) {
        if (searchBarState.currentValue == SearchBarValue.Expanded) {
            if (!searchUi.isOpen) {
                searchUi.activate(searchAppBarContext(currentService).scope)
            }
        } else if (searchUi.isOpen) {
            searchUi.close()
            searchText.setTextAndPlaceCursorAtEnd("")
            viewModel.updateSearchQuery("")
        }
    }
    LaunchedEffect(searchText.text.toString()) {
        if (searchUi.isOpen) {
            val query = searchText.text.toString()
            searchUi.updateQuery(query)
            viewModel.updateSearchQuery(if (searchUi.scope.includesSchedule) query else "")
        }
    }
    // The tab indicator reads the pager offset each frame, not only the settled route.
    val navigationTargets = remember { AppNavigationTargets() }
    var mailWebVisible by rememberSaveable { mutableStateOf(false) }
    var barsDetailOpen by remember { mutableStateOf(false) }
    // Settings overlays the same mounted tab shell.
    var viewedScheduleWeek by remember { mutableIntStateOf(0) }

    var agendaScrolling by remember { mutableStateOf(false) }
    var weekInfoVisible by remember { mutableStateOf(false) }
    var barsBrowserJustClosed by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    // Route names are intentionally coarse; never record student or message data.
    val performanceScreen = if (searchUi.isOpen) "SEARCH"
        else currentService?.name ?: "SCHEDULE"
    LaunchedEffect(performanceScreen) {
        application.container.uiFrameProfiler.markScreen(performanceScreen)
    }

    LaunchedEffect(agendaScrolling) {
        if (agendaScrolling) weekInfoVisible = true
        else {
            delay(1800)
            weekInfoVisible = false
        }
    }
    LaunchedEffect(scheduleState.selected?.id, scheduleState.selected?.type) {
        viewedScheduleWeek = 0
        agendaScrolling = false
        weekInfoVisible = false
    }
    LaunchedEffect(barsBrowserJustClosed, barsState.browserVisible, imeVisible) {
        if (barsBrowserJustClosed && !barsState.browserVisible && imeVisible) {
            dismissIme("bars-post-exit")
        }
    }

    fun openService(target: AppTab, viaClassroom: Boolean = false) {
        if (target == currentService && target != AppTab.SETTINGS && !viaClassroom) return
        if (target != AppTab.SCHEDULE) navigationTargets.clearScheduleFocus()
        application.container.uiFrameProfiler.markInteraction("NAV_" +
            if (target == AppTab.SETTINGS || viaClassroom) "PUSH" else "TAB_SWITCH")
        if (navigationHaptics && !viaClassroom) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        if (currentService == AppTab.MAIL && mailWebVisible && target != AppTab.MAIL) {
            dismissIme("mail-leave-service")
            mailWebVisible = false
        }
        if (currentService == AppTab.BARS && barsState.browserVisible && target != AppTab.BARS) {
            dismissIme("bars-leave-service")
        }
        barsBrowserJustClosed = false
        pagerGesturePending = false
        navigation.open(target, viaClassroom)
        logNavigation("tab select " + target.name +
            " nested=" + (target == AppTab.SETTINGS || viaClassroom))
    }

    fun goBackOneService() {
        if (!navigation.canPop) return
        application.container.uiFrameProfiler.markInteraction("NAV_POP")
        if (navigationHaptics) hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (mailWebVisible || barsState.browserVisible) dismissIme("tab-pop")
        mailWebVisible = false
        barsBrowserJustClosed = false
        navigation.pop()
        barsDetailOpen = false
    }

    fun closeMailBrowser() {
        dismissIme("mail-browser-back")
        mailWebVisible = false
        mailViewModel.refresh()
    }

    fun closeGlobalSearch() {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        searchScope.launch {
            searchBarState.animateToCollapsed()
            searchUi.close()
            searchText.setTextAndPlaceCursorAtEnd("")
            viewModel.updateSearchQuery("")
        }
    }

    fun openGlobalSearch(scope: SearchScope = SearchScope.SCHEDULE) {
        if (!searchUi.activate(scope)) return
        searchText.setTextAndPlaceCursorAtEnd("")
        viewModel.updateSearchQuery("")
        searchScope.launch { searchBarState.animateToExpanded() }
    }

    fun dispatchNavigation(request: AppNavigationRequest) {
        when (request) {
            is AppNavigationRequest.Service -> {
                if (request.destination == AppTab.SCHEDULE) navigationTargets.clearScheduleFocus()
                openService(request.destination)
            }
            is AppNavigationRequest.SelectSchedule -> {
                closeGlobalSearch()
                navigationTargets.clearScheduleFocus()
                viewModel.selectTarget(request.target)
                if (currentService != AppTab.SCHEDULE) openService(AppTab.SCHEDULE)
            }
            is AppNavigationRequest.FocusScheduleLesson -> {
                closeGlobalSearch()
                navigationTargets.focusScheduleLesson(request.hit)
                // Never reselect the timetable: that would drop the cached week.
                if (currentService != AppTab.SCHEDULE) openService(AppTab.SCHEDULE)
            }
            is AppNavigationRequest.OpenMail -> {
                closeGlobalSearch()
                if (currentService != AppTab.MAIL) openService(AppTab.MAIL,
                    viaClassroom = currentService == AppTab.SCHEDULE)
                mailViewModel.switchMailbox(request.hit.folder)
                mailViewModel.open(request.hit.message)
            }
            is AppNavigationRequest.OpenMailNotification -> {
                openService(AppTab.MAIL)
                if (request.uid > 0 && request.validity > 0) {
                    mailViewModel.openFromNotification(request.uid, request.validity)
                } else mailViewModel.closeMessage()
            }
            is AppNavigationRequest.OpenBarsDiscipline -> {
                closeGlobalSearch()
                navigationTargets.focusBarsDiscipline(request.name)
                if (currentService != AppTab.BARS) openService(
                    AppTab.BARS, viaClassroom = currentService == AppTab.SCHEDULE,
                )
            }
            is AppNavigationRequest.FocusCampus -> {
                closeGlobalSearch()
                navigationTargets.focusCampus(request.building, request.classroom)
                if (request.fromSchedule || currentService != AppTab.MAPS) {
                    openService(AppTab.MAPS, viaClassroom = request.fromSchedule)
                }
            }
            is AppNavigationRequest.LocateClassroom -> {
                campusBuildingForRoom(request.room)?.let { building ->
                    dispatchNavigation(AppNavigationRequest.FocusCampus(
                        building = building, classroom = request.room, fromSchedule = true,
                    ))
                }
            }
        }
    }

    // Route and pager have one stable page order. Reordering live pager keys
    // can remount nested WebViews or display a different tab than the navbar.
    // Adjacent pages use the Material spring; distant tab taps snap to the
    // destination rather than moving authenticated pages through other slots.
    LaunchedEffect(foregroundTab) {
        val requested = primaryPageFor(foregroundTab)
        if (pagerState.currentPage == requested &&
            pagerState.currentPageOffsetFraction == 0f) return@LaunchedEffect
        programmaticPaging = true
        pagerGesturePending = false
        try {
            if (kotlin.math.abs(pagerState.currentPage - requested) > 1) {
                pagerState.scrollToPage(requested)
            } else {
                pagerState.animateScrollToPage(requested)
            }
        } finally {
            programmaticPaging = false
        }
    }

    val mailDetailForeground = currentService == AppTab.MAIL &&
        (mailState.selected != null || mailState.composing)
    val barsBrowserForeground = currentService == AppTab.BARS &&
        (barsState.browserVisible || barsState.authStage == BarsAuthStage.WEB_AUTH)
    val rootPagingEnabled = canSwipePrimaryPages(
        atRoot = navigation.routes.size == 1 && currentService != AppTab.SETTINGS,
        searchOpen = searchUi.isOpen || searchBarState.currentValue == SearchBarValue.Expanded,
        imeVisible = imeVisible,
        mailBrowser = mailWebVisible,
        barsBrowser = barsBrowserForeground,
        mailDetail = mailDetailForeground,
        barsDetail = currentService == AppTab.BARS && barsDetailOpen,
    )

    // A cancelled swipe must never leave the visual pager on another route.
    // Opening a WebView, a message detail, Search, Settings, or the keyboard
    // immediately gives the nested screen exclusive gesture ownership.
    LaunchedEffect(
        pagerState.settledPage, pagerState.isScrollInProgress,
        rootPagingEnabled, programmaticPaging, pagerGesturePending, foregroundTab,
    ) {
        if (programmaticPaging) return@LaunchedEffect

        if (!rootPagingEnabled) {
            pagerGesturePending = false
            val correction = pageToRestoreForRoute(
                route = foregroundTab,
                visiblePage = pagerState.currentPage,
                offsetFraction = pagerState.currentPageOffsetFraction,
                pendingUserSwipe = false,
                swipeEnabled = false,
                programmatic = false,
            )
            if (correction != null) {
                pagerState.requestScrollToPage(correction)
                logNavigation("pager restored to " + foregroundTab.name + " for nested UI")
            }
            return@LaunchedEffect
        }
        if (pagerState.isScrollInProgress) return@LaunchedEffect

        if (pagerGesturePending) {
            pagerGesturePending = false
            // Only a completed user gesture, not a programmatic or interrupted
            // movement, may commit a new service route.
            val settled = PRIMARY_TABS.getOrNull(pagerState.settledPage)
            if (settled != null && settled != currentService) openService(settled)
        } else {
            val correction = pageToRestoreForRoute(
                route = foregroundTab,
                visiblePage = pagerState.currentPage,
                offsetFraction = pagerState.currentPageOffsetFraction,
                pendingUserSwipe = false,
                swipeEnabled = rootPagingEnabled,
                programmatic = false,
            )
            if (correction != null) {
                pagerState.requestScrollToPage(correction)
                logNavigation("pager drift restored to " + foregroundTab.name)
            }
        }
    }

    LaunchedEffect(mailNotificationTarget?.requestId) {
        val target = mailNotificationTarget ?: return@LaunchedEffect
        dispatchNavigation(AppNavigationRequest.OpenMailNotification(target.uid, target.validity))
    }

    // Nested BackHandlers in MailScreen and BARS take precedence over the root.
    BackHandler(enabled = navigation.canPop && !mailWebVisible && !searchUi.isOpen) {
        goBackOneService()
    }
    BackHandler(enabled = currentService == AppTab.MAIL && mailWebVisible) {
        closeMailBrowser()
    }

    // Share the existing screen instances and saveable state between push,
    // background, and return. No second copy of the current page is ever drawn.
    val serviceContent: @Composable (AppTab, Boolean) -> Unit = { destination, active ->
        savedServiceState.SaveableStateProvider(destination.name) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Box(Modifier.fillMaxSize()) {
                    when (destination) {
                        AppTab.SCHEDULE -> {
                            ScheduleScreen(
                                state = scheduleState,
                                onEnsureAgendaWeek = viewModel::ensureAgendaWeek,
                                onRefreshAgendaWeek = viewModel::refreshAgendaWeek,
                                onRetryAgendaWeek = viewModel::retryAgendaWeek,
                                onViewedAgendaWeek = { viewedScheduleWeek = it },
                                onAgendaScrollingChanged = { agendaScrolling = it },
                                onLocateRoom = { room ->
                                    dispatchNavigation(AppNavigationRequest.LocateClassroom(room))
                                },
                                todayJumpRequest = todayJumpRequest,
                                onPreviewLessonClick = { highlight ->
                                    pagerGesturePending = false
                                    navigationTargets.focusUpcomingLesson(highlight, scheduleState.agendaWeeks)
                                },
                                focusLesson = navigationTargets.scheduleLessonFocus,
                                onFocusConsumed = navigationTargets::consumeScheduleFocus,
                                modifier = Modifier.fillMaxSize(),
                            )

                        }
                        // BARS lives in a permanent sibling composition below.
                        // Its WebView must not be destroyed by service routing.
                        AppTab.BARS -> Unit
                        AppTab.MAIL -> {
                            MailScreen(
                                viewModel = mailViewModel,
                                active = active && !mailWebVisible,
                                onOpenPortal = { mailWebVisible = true },
                                onSearch = { openGlobalSearch(SearchScope.MAIL) },
                                modifier = Modifier.fillMaxSize(),
                            )
                            SpatialScreenOverlay(visible = mailWebVisible && active) {
                                WebPortalScreen(
                                    url = "https://mail.mpei.ru/owa",
                                    testTag = "portal-mail",
                                    modifier = Modifier.fillMaxSize()
                                        .testTag("mail-webview"),
                                    onDebugEvent = { message ->
                                        diagnostics.log("MAIL_WEB", message)
                                    },
                                    onExit = ::closeMailBrowser,
                                )
                            }
                        }
                        AppTab.MAPS -> CampusMapScreen(
                            active = active,
                            focus = navigationTargets.mapFocus,
                            onFocusConsumed = navigationTargets::consumeCampusFocus,
                            modifier = Modifier.fillMaxSize(),
                        )
                        AppTab.SETTINGS -> SettingsScreen(
                            updateViewModel = updateViewModel,
                            mailViewModel = mailViewModel,
                            barsAutoRefreshEnabled = barsAutoRefreshEnabled,
                            onBarsAutoRefreshEnabledChange = { enabled ->
                                barsPrefs.edit().putBoolean("auto_refresh", enabled).apply()
                                barsAutoRefreshEnabled = enabled
                            },
                            active = active,
                            interfaceTheme = interfaceTheme,
                            onInterfaceThemeChange = onInterfaceThemeChange,
                            navigationHaptics = navigationHaptics,
                            onNavigationHapticsChange = { enabled ->
                                interfacePrefs.setNavigationHaptics(enabled)
                                navigationHaptics = enabled
                                diagnostics.log("UI_PREF", "navigation_haptics=" + enabled)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                }
            }
        }
    }


    // Material owns search expansion; the tab row lives below the search app bar.
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.imePadding().nestedScroll(searchScrollBehavior.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                val embeddedBrowserForeground =
                    (currentService == AppTab.MAIL && mailWebVisible) ||
                    (currentService == AppTab.BARS &&
                        (barsState.browserVisible || barsState.authStage == BarsAuthStage.WEB_AUTH))
                if (!embeddedBrowserForeground && currentService != AppTab.SETTINGS) {
                    Column {
                        MaterialSearchAppBar(
                            context = searchAppBarContext(foregroundTab),
                            scrollBehavior = searchScrollBehavior,
                            state = searchBarState,
                            textFieldState = searchText,
                            onCloseSearch = ::closeGlobalSearch,
                            onSettings = { openService(AppTab.SETTINGS) },
                            onRefresh = if (currentService == AppTab.SCHEDULE)
                                ({ viewModel.refreshAgendaWeek(viewedScheduleWeek) })
                                else null,
                            refreshing = viewedScheduleWeek in scheduleState.agendaLoadingOffsets,
                            onCompose = if (searchAppBarAction(
                                currentService,
                                mailListReady = mailState.configured &&
                                    !mailState.composing && mailState.selected == null &&
                                    !mailWebVisible,
                            ) == SearchAppBarAction.COMPOSE_MAIL)
                                mailViewModel::startCompose else null,
                            results = {
                                GlobalSearchScreen(
                                    query = searchUi.query,
                                    scope = searchUi.scope,
                                    onScheduleTypeChange = viewModel::setSearchType,
                                    onQueryChange = { searchText.setTextAndPlaceCursorAtEnd(it) },
                                    schedule = scheduleState,
                                    mail = mailState,
                                    bars = barsState,
                                    onSchedule = {
                                        dispatchNavigation(AppNavigationRequest.SelectSchedule(it))
                                    },
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onRenameFavorite = viewModel::renameFavorite,
                                    onLesson = {
                                        dispatchNavigation(AppNavigationRequest.FocusScheduleLesson(it))
                                    },
                                    onCampus = {
                                        dispatchNavigation(AppNavigationRequest.FocusCampus(it))
                                    },
                                    onMail = {
                                        dispatchNavigation(AppNavigationRequest.OpenMail(it))
                                    },
                                    onDiscipline = {
                                        dispatchNavigation(AppNavigationRequest.OpenBarsDiscipline(it))
                                    },
                                    onClose = ::closeGlobalSearch,
                                )
                            },
                        )
                        PrimaryIconServiceTabs(
                            selected = PRIMARY_TABS[pagerState.currentPage],
                            pagerPosition = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                            unreadMail = mailState.configured && !mailState.loginRequired &&
                                mailState.items.any { it.unread },
                            onSelect = { tab ->
                                if (tab == currentService && tab == AppTab.SCHEDULE) {
                                    todayJumpRequest++
                                } else if (tab != currentService) {
                                    openService(tab)
                                }
                            },
                        )
                    }
                }
            },
        ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // Four lightweight slots let Pager drag continuously. Only the
            // current/target neighborhood mounts costly non-BARS UI.
            // BARS remains mounted across every page.
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().testTag("primary-service-pager"),
                userScrollEnabled = rootPagingEnabled && !programmaticPaging,
                beyondViewportPageCount = PRIMARY_TABS.lastIndex,
                flingBehavior = PagerDefaults.flingBehavior(state = pagerState),
                key = { PRIMARY_TABS[it].name },
            ) { page ->
                val destination = PRIMARY_TABS[page]
                if (shouldComposePrimaryPage(
                        page, pagerState.currentPage, pagerState.targetPage,
                        isBarsPage = destination == AppTab.BARS,
                    )
                ) {
                    if (destination == AppTab.BARS) {
                        // Preserve this single authenticated WebView across
                        // swipes; it must never be disposed by lazy paging.
                        BarsScreen(
                            viewModel = barsViewModel,
                            onSearch = { openGlobalSearch(SearchScope.BARS) },
                            autoRefreshEnabled = barsAutoRefreshEnabled,
                            active = currentService == AppTab.BARS,
                            allowBackgroundSync = barsAutoRefreshEnabled && backgroundStartupReady,
                            onDetailOpenChange = {
                                if (currentService == AppTab.BARS) barsDetailOpen = it
                            },
                            openDisciplineName = navigationTargets.barsRequestedDiscipline,
                            openDisciplineRequest = navigationTargets.barsSearchRequest,
                            onOpenDisciplineConsumed = navigationTargets::consumeBarsDiscipline,
                            onBrowserExit = {
                                barsBrowserJustClosed = true
                                dismissIme("bars-browser-back")
                            },
                            onAuthBrowserExit = { dismissIme("bars-auth-browser-exit") },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        serviceContent(
                            destination,
                            currentService == destination && !searchUi.isOpen,
                        )
                    }
                } else {
                    Box(Modifier.fillMaxSize())
                }
            }
        }
    }
    // Keep the app shell mounted. Settings enters and exits as a spatial page.
    SpatialScreenOverlay(
        visible = currentService == AppTab.SETTINGS,
        modifier = Modifier.testTag("settings-fullscreen-layer"),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Настройки") },
                    navigationIcon = {
                        IconButton(
                            onClick = { goBackOneService() },
                            modifier = Modifier.testTag("settings-app-bar-back"),
                        ) {
                            AppIcon(AppIcons.Back, contentDescription = "Назад")
                        }
                    },
                    modifier = Modifier.testTag("settings-app-bar"),
                )
                Box(Modifier.weight(1f).navigationBarsPadding()) {
                    serviceContent(AppTab.SETTINGS, true)
                }
            }
        }
    }
    }
}
