package com.rteats.mpeineo.ui

import android.content.Context
import android.os.Build
import android.view.WindowInsets as AndroidWindowInsets
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A single composited foreground page over a permanently mounted background.
 * Drag progress and automatic push/pop share one reveal value.
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
    val hostView = LocalView.current
    val hapticFeedback = LocalHapticFeedback.current
    val homePrefs = remember(application) { HomeLayoutPreferences(application) }
    var homeTiles by remember(homePrefs) { mutableStateOf(homePrefs.read()) }
    var homeEditing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(homePrefs) {
        diagnostics.log("HOME_LAYOUT", "restored=" + HomeTileLayout.encode(homeTiles))
    }
    val interfacePrefs = remember(application) { InterfacePreferences(application) }
    var navigationHaptics by remember(interfacePrefs) {
        mutableStateOf(interfacePrefs.navigationHaptics)
    }
    fun changeHomeLayout(next: List<HomeTile>) {
        val normalized = HomeTileLayout.normalize(next)
        val before = homeTiles
        if (before == normalized) return
        homePrefs.write(normalized)
        homeTiles = normalized
        val oldIndex = before.map { it.tab }
        val newIndex = normalized.map { it.tab }
        if (oldIndex != newIndex) {
            diagnostics.log("HOME_LAYOUT", "reorder=" + newIndex.joinToString(",") { it.name })
        } else {
            normalized.firstOrNull { tile ->
                before.firstOrNull { it.tab == tile.tab }?.size != tile.size
            }?.let { tile ->
                diagnostics.log("HOME_LAYOUT", "resize=" + tile.tab.name + " size=" + tile.size.name)
            }
        }
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
    var reveal by rememberSaveable {
        mutableFloatStateOf(if (initialMailTab) 1f else 0f)
    }
    var isTransitioning by remember { mutableStateOf(false) }
    var transitionJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    var widthPx by remember { mutableFloatStateOf(1f) }
    var todayJumpRequest by rememberSaveable { mutableIntStateOf(0) }
    val searchUi = rememberSearchOverlayState()
    // The bar switches context when navigation begins, not when its slide ends.
    var appBarService by remember { mutableStateOf(currentService) }
    var searchHostBounds by remember { mutableStateOf(Rect.Zero) }
    val searchReveal by animateFloatAsState(
        targetValue = if (searchUi.isOpen) 1f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        finishedListener = searchUi::onRevealSettled,
        label = "search-anchored-container-reveal",
    )
    val navigationTargets = remember { AppNavigationTargets() }
    var mailWebVisible by rememberSaveable { mutableStateOf(false) }
    var barsDetailOpen by remember { mutableStateOf(false) }
    var viewedScheduleWeek by remember { mutableIntStateOf(0) }

    var agendaScrolling by remember { mutableStateOf(false) }
    var weekInfoVisible by remember { mutableStateOf(false) }
    var barsBrowserJustClosed by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    // Route names are intentionally coarse; never record student or message data.
    val performanceScreen = if (searchUi.isOpen) "SEARCH"
        else currentService?.name ?: "HOME"
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

    fun animateReveal(target: Float, onComplete: () -> Unit = {}) {
        transitionJob?.cancel()
        isTransitioning = true
        transitionJob = scope.launch {
            animate(
                initialValue = reveal.coerceIn(0f, 1f),
                targetValue = target,
                animationSpec = tween(
                    durationMillis = if (target == 0f) 260 else 310,
                    easing = FastOutSlowInEasing,
                ),
            ) { value, _ ->
                reveal = value
            }
            reveal = target
            onComplete()
            isTransitioning = false
        }
    }

    fun openService(target: AppTab, viaClassroom: Boolean = false) {
        application.container.uiFrameProfiler.markInteraction("NAV_PUSH")
        transitionJob?.cancel()
        if (navigationHaptics && !viaClassroom) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        if (currentService == AppTab.MAIL && mailWebVisible) {
            dismissIme("mail-leave-service")
            mailWebVisible = false
        }
        if (currentService == AppTab.BARS && barsState.browserVisible) {
            dismissIme("bars-leave-service")
        }
        barsBrowserJustClosed = false
        barsDetailOpen = false
        appBarService = target
        navigation.open(target, viaClassroom)
        reveal = 0f
        logNavigation("superapp push " + target.name +
            " origin=" + (currentService?.name ?: "HOME") +
            " classroom=" + viaClassroom)
        animateReveal(1f)
    }

    fun goBackOneService() {
        if (isTransitioning || !navigation.canPop) return
        application.container.uiFrameProfiler.markInteraction("NAV_POP")
        if (navigationHaptics) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        logNavigation("superapp pop " + (currentService?.name ?: "HOME") +
            " depth=" + navigation.routes.size)
        if (mailWebVisible || barsState.browserVisible) dismissIme("superapp-pop")
        mailWebVisible = false
        barsBrowserJustClosed = false
        appBarService = navigation.peekBack()
        animateReveal(0f) {
            navigation.pop()
            // The underlying service is already fully rendered, at rest.
            reveal = if (!navigation.canPop) 0f else 1f
            barsDetailOpen = false
        }
    }

    fun closeMailBrowser() {
        dismissIme("mail-browser-back")
        mailWebVisible = false
        mailViewModel.refresh()
    }

    fun updateGlobalQuery(value: String) {
        searchUi.updateQuery(value)
        // Only schedule-scoped search performs remote target lookup.
        viewModel.updateSearchQuery(if (searchUi.scope.includesSchedule) value else "")
    }

    fun closeGlobalSearch() {
        searchUi.close()
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        // Preserve visible results until the reverse reveal has fully closed.
        viewModel.updateSearchQuery("")
    }

    fun openGlobalSearch(scope: SearchScope = SearchScope.SCHEDULE) {
        if (!searchUi.activate(scope)) return
        viewModel.updateSearchQuery("")
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
            is AppNavigationRequest.FocusHomeLesson -> {
                navigationTargets.focusHomeLesson(request.highlight, scheduleState.agendaWeeks)
                openService(AppTab.SCHEDULE)
            }
            is AppNavigationRequest.OpenMail -> {
                closeGlobalSearch()
                if (currentService != AppTab.MAIL) openService(AppTab.MAIL)
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
                if (currentService != AppTab.BARS) openService(AppTab.BARS)
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

    LaunchedEffect(mailNotificationTarget?.requestId) {
        val target = mailNotificationTarget ?: return@LaunchedEffect
        dispatchNavigation(AppNavigationRequest.OpenMailNotification(target.uid, target.validity))
    }

    // Nested BackHandlers in MailScreen and BARS take precedence over the root.
    BackHandler(enabled = currentService != null && !mailWebVisible) {
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
                                focusLesson = navigationTargets.scheduleLessonFocus,
                                modifier = Modifier.fillMaxSize(),
                            )

                        }
                        // BARS lives in a permanent sibling composition below.
                        // Its WebView must not be destroyed by Home/service routing.
                        AppTab.BARS -> Unit
                        AppTab.MAIL -> {
                            MailScreen(
                                viewModel = mailViewModel,
                                active = active && !mailWebVisible,
                                onOpenPortal = { mailWebVisible = true },
                                onSearch = { openGlobalSearch(SearchScope.MAIL) },
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (mailWebVisible && active) {
                                WebPortalScreen(
                                    url = "https://mail.mpei.ru/owa",
                                    testTag = "portal-mail",
                                    modifier = Modifier.fillMaxSize()
                                        .testTag("mail-webview"),
                                    onDebugEvent = { message ->
                                        diagnostics.log("MAIL_WEB", message)
                                    },
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
                            onResetHomeLayout = {
                                homePrefs.reset()
                                homeTiles = HomeTileLayout.defaults
                                diagnostics.log("HOME_LAYOUT", "reset=defaults")
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                }
            }
        }
    }


    // Shared swipe-back handler. BARS uses the same gesture even when its
    // single WebView was initialized from the Home screen.
    fun Modifier.serviceBackGesture(
        eligible: Boolean,
        interactive: Boolean,
        mapEdgeZone: Boolean = false,
    ): Modifier =
        pointerInput(currentService, eligible, interactive, mapEdgeZone) {
            if (!eligible) return@pointerInput
            var acceptedGesture = false
            var gestureDistance = 0f
            val systemBackEdge = 24.dp.toPx()
            detectHorizontalDragGestures(
                onDragStart = { start ->
                    acceptedGesture = acceptsServiceBackStart(
                        start.x, size.width.toFloat(), systemBackEdge, mapEdgeZone,
                    )
                    gestureDistance = 0f
                },
                onHorizontalDrag = { change, amount ->
                    if (acceptedGesture) {
                        gestureDistance = (gestureDistance + amount).coerceAtLeast(0f)
                        if (interactive) {
                            reveal = (1f - gestureDistance /
                                widthPx).coerceIn(0f, 1f)
                        }
                        if (amount > 0f) change.consume()
                    }
                },
                onDragCancel = {
                    if (acceptedGesture && interactive) animateReveal(1f)
                    acceptedGesture = false
                },
                onDragEnd = {
                    if (acceptedGesture && shouldCommitServiceBackSwipe(
                            gestureDistance, widthPx,
                        )
                    ) {
                        if (interactive) goBackOneService()
                        else backDispatcher?.onBackPressed()
                    } else if (interactive) animateReveal(1f)
                    acceptedGesture = false
                },
            )
        }

    // Keep the underlying app bar mounted while Search expands. Removing it
    // during activation would remeasure the entire scaffold and visibly jump.
    Box(
        modifier = Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
            val newBounds = coordinates.boundsInRoot()
            if (newBounds != searchHostBounds) searchHostBounds = newBounds
        },
    ) {
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // The native search app bar reserves space rather than floating
            // over controls, scrollable content, maps, or WebViews.
            if (appBarMode(appBarService) == AppBarMode.TITLE) {
                TopAppBar(
                    title = { Text("Настройки") },
                    navigationIcon = {
                        IconButton(
                            onClick = { if (!isTransitioning) goBackOneService() },
                            modifier = Modifier.testTag("settings-app-bar-back"),
                        ) {
                            AppIcon(AppIcons.Back, contentDescription = "Назад")
                        }
                    },
                    modifier = Modifier.testTag("settings-app-bar"),
                )
            } else {
            ContextSearchAppBar(
                    context = searchAppBarContext(appBarService),
                    onSearch = {
                        if (!isTransitioning) {
                            openGlobalSearch(searchAppBarContext(currentService).scope)
                        }
                    },
                    onScheduleRefresh = if (!searchUi.isOpen && appBarService == AppTab.SCHEDULE)
                        ({ viewModel.refreshAgendaWeek(viewedScheduleWeek) }) else null,
                    scheduleRefreshing =
                        viewedScheduleWeek in scheduleState.agendaLoadingOffsets,
                    onMailCompose = if (!searchUi.isOpen && searchAppBarAction(
                            appBarService,
                            mailListReady = mailState.configured && !mailState.composing &&
                                mailState.selected == null && !mailWebVisible,
                        ) == SearchAppBarAction.COMPOSE_MAIL
                    ) mailViewModel::startCompose else null,
                    homeEditing = homeEditing,
                    onSearchBoundsChanged = { bounds ->
                        if (bounds != searchUi.entryBounds) searchUi.entryBounds = bounds
                    },
                    onHomeEditToggle = if (!searchUi.isOpen && appBarService == null) ({
                        if (!isTransitioning) {
                            homeEditing = !homeEditing
                            diagnostics.log("HOME_LAYOUT", "edit_mode=" + homeEditing)
                        }
                    }) else null,
                    onHomeSettings = if (!searchUi.isOpen && appBarService == null) ({
                        if (!isTransitioning) {
                            homeEditing = false
                            openService(AppTab.SETTINGS)
                        }
                    }) else null,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding).clipToBounds()
                .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) },
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer { translationX = -widthPx * reveal / 7f },
            ) {
                if (beneathService == null) {
                    SuperAppHome(
                        schedule = scheduleState,
                        bars = barsState,
                        mail = mailState,
                        tiles = homeTiles,
                        onChangeLayout = ::changeHomeLayout,
                        editing = homeEditing,
                        onOpen = { target ->
                            if (currentService == null && !isTransitioning) {
                                dispatchNavigation(AppNavigationRequest.Service(target))
                            }
                        },
                        onOpenNextLesson = { highlight ->
                            if (currentService == null && !isTransitioning) {
                                dispatchNavigation(AppNavigationRequest.FocusHomeLesson(highlight))
                            }
                        },
                        onOpenBarsDiscipline = { discipline ->
                            if (currentService == null && !isTransitioning) {
                                dispatchNavigation(AppNavigationRequest.OpenBarsDiscipline(discipline))
                            }
                        },
                    )
                } else serviceContent(beneathService, false)
            }

            if (currentService != null && currentService != AppTab.BARS) {
                // This branch excludes BARS; browser-aware BARS gestures
                // are handled by their own persistent sibling composition.
                val canHandleGesture = !isTransitioning &&
                    !imeVisible && !mailWebVisible
                val canInteractivelyPop = canHandleGesture &&
                    !barsDetailOpen &&
                    !(currentService == AppTab.MAIL && mailState.selected != null)
                Box(
                    modifier = Modifier.fillMaxSize()
                        .graphicsLayer { translationX = widthPx * (1f - reveal) }
                        .serviceBackGesture(
                            canHandleGesture && currentService != AppTab.MAPS,
                            canInteractivelyPop,
                        ),
                ) {
                    serviceContent(currentService, true)
                    if (currentService == AppTab.MAPS && canHandleGesture) {
                        // Reserve a slim left strip for the interactive pop.
                        // Everywhere else is exclusively for map panning/pinching.
                        // The alphabet keyboard now occupies the top of the
                        // map. Start the left-edge back target below it so the
                        // letter keys remain clickable (including the first
                        // columns), while back-swipe is still available below.
                        Box(
                            modifier = Modifier.align(Alignment.CenterStart)
                                .width(96.dp).fillMaxHeight()
                                .padding(top = 248.dp, bottom = 200.dp)
                                .testTag("campus-map-swipe-back-zone")
                                .serviceBackGesture(
                                    eligible = true,
                                    interactive = canInteractivelyPop,
                                    mapEdgeZone = true,
                                ),
                        )
                    }
                }
            }
            // The BARS composition is mounted exactly once, including on Home.
            // Place it offscreen while inactive rather than recreating WebViews.
            // Compose layout still grants the full viewport to BARS DOM extraction.
            val barsForeground = currentService == AppTab.BARS
            val barsSwipe = barsSwipeTarget(
                foreground = barsForeground,
                isTransitioning = isTransitioning,
                imeVisible = imeVisible,
                browserVisible = barsState.browserVisible,
                authenticated = barsState.authStage == BarsAuthStage.AUTHENTICATED,
                detailOpen = barsDetailOpen,
            )
            Box(
                modifier = Modifier.fillMaxSize()
                    .offset {
                        IntOffset(
                            x = (widthPx *
                                (if (barsForeground) 1f - reveal else 1f)).roundToInt(),
                            y = 0,
                        )
                    }
                    .serviceBackGesture(
                        eligible = barsSwipe != BarsSwipeTarget.DISABLED,
                        interactive = barsSwipe == BarsSwipeTarget.HOME,
                    ),
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BarsScreen(
                        viewModel = barsViewModel,
                        onSearch = { openGlobalSearch(SearchScope.BARS) },
                        autoRefreshEnabled = barsAutoRefreshEnabled,
                        active = barsForeground,
                        allowBackgroundSync = barsAutoRefreshEnabled,
                        onDetailOpenChange = { if (barsForeground) barsDetailOpen = it },
                        openDisciplineName = navigationTargets.barsRequestedDiscipline,
                        openDisciplineRequest = navigationTargets.barsSearchRequest,
                        onOpenDisciplineConsumed = navigationTargets::consumeBarsDiscipline,
                        onBrowserExit = {
                            barsBrowserJustClosed = true
                            dismissIme("bars-browser-back")
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

        }
    }
    // Positioned above Scaffold and its top bar, not inside Scaffold's content
    // padding. Clip starts at the actual tapped field and grows to the screen.
    if (searchUi.overlayMounted) {
        val measured = searchUi.entryBounds
        val relativeBounds = if (measured.width > 0f && measured.height > 0f) {
            Rect(
                measured.left - searchHostBounds.left,
                measured.top - searchHostBounds.top,
                measured.right - searchHostBounds.left,
                measured.bottom - searchHostBounds.top,
            )
        } else Rect.Zero
        Box(
            modifier = Modifier.fillMaxSize().imePadding()
                .graphicsLayer {
                    clip = true
                    shape = SearchRevealShape(relativeBounds, searchReveal)
                }
                .testTag("search-anchored-overlay"),
        ) {
            GlobalSearchScreen(
                query = searchUi.query,
                scope = searchUi.scope,
                onScheduleTypeChange = viewModel::setSearchType,
                onQueryChange = ::updateGlobalQuery,
                schedule = scheduleState,
                mail = mailState,
                bars = barsState,
                onSchedule = { dispatchNavigation(AppNavigationRequest.SelectSchedule(it)) },
                onToggleFavorite = viewModel::toggleFavorite,
                onRenameFavorite = viewModel::renameFavorite,
                onLesson = { dispatchNavigation(AppNavigationRequest.FocusScheduleLesson(it)) },
                onCampus = { dispatchNavigation(AppNavigationRequest.FocusCampus(it)) },
                onMail = { dispatchNavigation(AppNavigationRequest.OpenMail(it)) },
                onDiscipline = { dispatchNavigation(AppNavigationRequest.OpenBarsDiscipline(it)) },
                onClose = ::closeGlobalSearch,
                revealProgress = searchReveal,
                sourceFieldBounds = searchUi.anchoredBounds(),
                focusWhenReady = searchUi.isOpen && searchReveal >= 0.98f,
            )
        }
    }
    }
}
