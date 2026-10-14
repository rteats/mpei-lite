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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
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
import com.rteats.mpeineo.MpeiNeoApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class AppTab { SCHEDULE, BARS, MAIL, MAPS, SETTINGS }

private enum class ServiceNavDirection { PUSH, POP }

/** Swipe-back completion measured as a fraction of the current screen width. */
internal fun shouldCommitServiceBackSwipe(distancePx: Float, widthPx: Float): Boolean =
    widthPx > 0f && distancePx >= widthPx * 0.20f


/**
 * Compatibility helper for the old pager policy tests. The super-app shell no
 * longer pages horizontally and doesn't need to lock its route for a WebView.
 */
internal fun lockedBrowserTab(
    intended: AppTab,
    mailWebVisible: Boolean,
    barsWebVisible: Boolean,
): AppTab? = when (intended) {
    AppTab.MAIL -> AppTab.MAIL.takeIf { mailWebVisible }
    AppTab.BARS -> AppTab.BARS.takeIf { barsWebVisible }
    else -> null
}

/** Legacy calculation retained for transition tests; the new dock is stationary. */
internal fun scheduleDockRevealProgress(
    currentPage: Int,
    pageOffsetFraction: Float,
): Float = (1f - kotlin.math.abs(currentPage + pageOffsetFraction)).coerceIn(0f, 1f)

/**
 * Home is the root destination. Each service owns its gestures and internal
 * navigation. There is no offscreen horizontal pager for WebView focus/IME to
 * accidentally scroll. State holders preserve saveable search and scroll state.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MpeiNeoApp(
    viewModel: MainViewModel,
    initialMailTab: Boolean = false,
    mailNotificationTarget: MailNotificationTarget? = null,
) {
    val application = LocalContext.current.applicationContext as MpeiNeoApplication
    val diagnostics = application.container.diagnostics
    val hostView = LocalView.current
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

    var service by rememberSaveable {
        mutableStateOf<AppTab?>(if (initialMailTab) AppTab.MAIL else null)
    }
    var navDirection by remember { mutableStateOf(ServiceNavDirection.PUSH) }
    var skipPopTransition by remember { mutableStateOf(false) }
    var swipePx by remember { mutableFloatStateOf(0f) }
    var gestureSettling by remember { mutableStateOf(false) }
    val navigationScope = rememberCoroutineScope()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    var returnFromMapToSchedule by rememberSaveable { mutableStateOf(false) }
    val savedServiceState = rememberSaveableStateHolder()
    var todayJumpRequest by rememberSaveable { mutableIntStateOf(0) }
    var searchExpanded by remember { mutableStateOf(false) }
    var mailWebVisible by rememberSaveable { mutableStateOf(false) }
    var viewedScheduleWeek by remember { mutableIntStateOf(0) }
    var mapFocus by remember { mutableStateOf<CampusMapFocus?>(null) }
    var nextMapFocusId by remember { mutableIntStateOf(0) }
    var agendaScrolling by remember { mutableStateOf(false) }
    var weekInfoVisible by remember { mutableStateOf(false) }
    var barsBrowserJustClosed by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

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
    // On some ROMs an embedded browser requests IME focus again just after
    // detachment. No pager exists now, but the keyboard still needs cleanup.
    LaunchedEffect(barsBrowserJustClosed, barsState.browserVisible, imeVisible) {
        if (barsBrowserJustClosed && !barsState.browserVisible && imeVisible) {
            dismissIme("bars-post-exit")
        }
    }

    fun openService(target: AppTab, viaClassroom: Boolean = false) {
        if (service == AppTab.MAIL && mailWebVisible) {
            dismissIme("mail-leave-service")
            mailWebVisible = false
        }
        if (service == AppTab.BARS && barsState.browserVisible) {
            dismissIme("bars-leave-service")
        }
        searchExpanded = false
        barsBrowserJustClosed = false
        navDirection = ServiceNavDirection.PUSH
        skipPopTransition = false
        swipePx = 0f
        returnFromMapToSchedule = viaClassroom
        logNavigation("superapp navigate from=" + (service?.name ?: "HOME") +
            " to=" + target.name + " classroom=" + viaClassroom)
        service = target
    }
    fun goHome() {
        if (mailWebVisible || (service == AppTab.BARS && barsState.browserVisible)) {
            dismissIme("go-home-browser")
        }
        mailWebVisible = false
        searchExpanded = false
        barsBrowserJustClosed = false
        navDirection = ServiceNavDirection.POP
        returnFromMapToSchedule = false
        logNavigation("superapp HOME from=" + (service?.name ?: "HOME"))
        service = null
    }
    fun closeMailBrowser() {
        dismissIme("mail-browser-back")
        mailWebVisible = false
        mailViewModel.refresh()
    }

    // The deep link opens the mail service (not a tab), preserving its
    // notification-to-message routing regardless of the current destination.
    LaunchedEffect(mailNotificationTarget?.requestId) {
        val target = mailNotificationTarget ?: return@LaunchedEffect
        openService(AppTab.MAIL)
        if (target.uid > 0 && target.validity > 0) {
            mailViewModel.openFromNotification(target.uid, target.validity)
        } else mailViewModel.closeMessage()
    }

    // Nested BackHandlers in MailScreen/BarsScreen take priority over this root
    // handler: message -> inbox -> Home, discipline -> grades -> Home.
    BackHandler(enabled = service != null && !mailWebVisible) {
        if (service == AppTab.MAPS && returnFromMapToSchedule) {
            openService(AppTab.SCHEDULE)
            navDirection = ServiceNavDirection.POP
        } else goHome()
    }
    BackHandler(enabled = service == AppTab.MAIL && mailWebVisible) {
        closeMailBrowser()
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            // A right-swipe temporarily reveals the launcher behind the page.
            val previewBack = service != null && !searchExpanded && !imeVisible &&
                !(service == AppTab.MAIL && (mailWebVisible || mailState.selected != null)) &&
                service != AppTab.BARS &&
                !(service == AppTab.MAPS && returnFromMapToSchedule)
            if (service != null && swipePx > 0f && previewBack) {
                SuperAppHome(
                    schedule = scheduleState,
                    bars = barsState,
                    mail = mailState,
                    onOpen = { /* covered by the interactive page */ },
                )
            }
            AnimatedContent(
                targetState = service,
                modifier = Modifier.fillMaxSize().clipToBounds(),
                transitionSpec = {
                    when {
                        skipPopTransition && navDirection == ServiceNavDirection.POP ->
                            EnterTransition.None togetherWith ExitTransition.None
                        navDirection == ServiceNavDirection.POP ->
                            (slideInHorizontally(
                                initialOffsetX = { -it / 6 },
                                animationSpec = tween(300, easing = FastOutSlowInEasing),
                            ) + fadeIn(tween(260))) togetherWith
                                (slideOutHorizontally(
                                    targetOffsetX = { it },
                                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                                ) + fadeOut(tween(180)))
                        else ->
                            (slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = tween(340, easing = FastOutSlowInEasing),
                            ) + fadeIn(tween(240))) togetherWith
                                (slideOutHorizontally(
                                    targetOffsetX = { -it / 6 },
                                    animationSpec = tween(340, easing = FastOutSlowInEasing),
                                ) + fadeOut(tween(200)))
                    }
                },
                label = "superapp-service",
            ) { destination ->
                if (destination == null) {
                    SuperAppHome(
                        schedule = scheduleState,
                        bars = barsState,
                        mail = mailState,
                        onOpen = { selected -> openService(selected) },
                    )
                } else {
                    savedServiceState.SaveableStateProvider(destination.name) {
                        // Watch horizontal drags across the whole native page.
                        // Android's edge zones continue to use native Back; child
                        // horizontal scroll controls keep their own gestures.
                        val canSwipe = destination == service && !imeVisible &&
                            !searchExpanded && !gestureSettling
                        var acceptedGesture = false
                        var gestureDistance = 0f
                        Box(
                            Modifier.fillMaxSize()
                                .graphicsLayer {
                                    translationX = if (destination == service) swipePx else 0f
                                }
                                .pointerInput(destination, canSwipe, previewBack) {
                                    if (!canSwipe) return@pointerInput
                                    val systemEdge = 24.dp.toPx()
                                    detectHorizontalDragGestures(
                                        onDragStart = { start ->
                                            acceptedGesture = start.x >= systemEdge &&
                                                start.x <= size.width - systemEdge
                                            gestureDistance = 0f
                                        },
                                        onHorizontalDrag = { change, amount ->
                                            if (acceptedGesture) {
                                                gestureDistance = (gestureDistance + amount)
                                                    .coerceAtLeast(0f)
                                                if (previewBack) {
                                                    swipePx = gestureDistance
                                                        .coerceAtMost(size.width.toFloat())
                                                }
                                                if (amount > 0f) change.consume()
                                            }
                                        },
                                        onDragCancel = {
                                            if (previewBack && acceptedGesture) {
                                                navigationScope.launch {
                                                    animate(
                                                        swipePx, 0f,
                                                        animationSpec = tween(180),
                                                    ) { value, _ -> swipePx = value }
                                                }
                                            }
                                            acceptedGesture = false
                                        },
                                        onDragEnd = {
                                            if (acceptedGesture &&
                                                shouldCommitServiceBackSwipe(
                                                    gestureDistance,
                                                    size.width.toFloat(),
                                                )
                                            ) {
                                                if (previewBack) {
                                                    gestureSettling = true
                                                    navigationScope.launch {
                                                        animate(
                                                            swipePx, size.width.toFloat(),
                                                            animationSpec = tween(
                                                                180, easing = FastOutSlowInEasing,
                                                            ),
                                                        ) { value, _ -> swipePx = value }
                                                        skipPopTransition = true
                                                        backDispatcher?.onBackPressed()
                                                        delay(50)
                                                        swipePx = 0f
                                                        skipPopTransition = false
                                                        gestureSettling = false
                                                    }
                                                } else {
                                                    backDispatcher?.onBackPressed()
                                                }
                                            } else if (previewBack) {
                                                navigationScope.launch {
                                                    animate(
                                                        swipePx, 0f,
                                                        animationSpec = tween(180),
                                                    ) { value, _ -> swipePx = value }
                                                }
                                            }
                                            acceptedGesture = false
                                        },
                                    )
                                },
                        ) {
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
                                            campusBuildingForRoom(room)?.let { building ->
                                                nextMapFocusId++
                                                mapFocus = CampusMapFocus(
                                                    nextMapFocusId, building, room.take(100),
                                                )
                                                openService(AppTab.MAPS, viaClassroom = true)
                                            }
                                        },
                                        todayJumpRequest = todayJumpRequest,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    ScheduleDockedSearch(
                                        state = scheduleState,
                                        onQueryChanged = viewModel::updateSearchQuery,
                                        onTypeChanged = viewModel::setSearchType,
                                        onSelect = viewModel::selectTarget,
                                        onToggleFavorite = viewModel::toggleFavorite,
                                        onRenameFavorite = viewModel::renameFavorite,
                                        visibleWeekOffset = viewedScheduleWeek,
                                        onRefreshWeek = viewModel::refreshAgendaWeek,
                                        showWeekInfo = weekInfoVisible,
                                        expanded = searchExpanded,
                                        onExpandedChange = { searchExpanded = it },
                                        active = service == AppTab.SCHEDULE,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                AppTab.BARS -> BarsScreen(
                                    viewModel = barsViewModel,
                                    autoRefreshEnabled = barsAutoRefreshEnabled,
                                    active = service == AppTab.BARS,
                                    onBrowserExit = {
                                        barsBrowserJustClosed = true
                                        dismissIme("bars-browser-back")
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                                AppTab.MAIL -> {
                                    MailScreen(
                                        viewModel = mailViewModel,
                                        active = service == AppTab.MAIL && !mailWebVisible,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    if (mailWebVisible && service == AppTab.MAIL) {
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
                                    active = service == AppTab.MAPS,
                                    focus = mapFocus,
                                    onFocusConsumed = { mapFocus = null },
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
                                    active = service == AppTab.SETTINGS,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            // The portal FAB is part of the page. It moves with the
                            // foreground rather than staying behind on a back swipe.
                            val portalAction = when (destination) {
                                AppTab.BARS -> AppTab.BARS.takeIf {
                                    barsState.authStage == BarsAuthStage.AUTHENTICATED &&
                                        !barsState.browserVisible && !imeVisible
                                }
                                AppTab.MAIL -> AppTab.MAIL.takeIf {
                                    !mailWebVisible && !imeVisible
                                }
                                else -> null
                            }
                            AnimatedVisibility(
                                visible = portalAction != null && destination == service,
                                modifier = Modifier.align(Alignment.BottomEnd)
                                    .padding(end = 16.dp, bottom = 16.dp),
                                enter = fadeIn(tween(150)),
                                exit = fadeOut(tween(110)),
                            ) {
                                PortalFloatingButton(
                                    label = if (portalAction == AppTab.MAIL) "ОСЭП" else "БАРС",
                                    onClick = {
                                        when (portalAction) {
                                            AppTab.BARS -> {
                                                barsBrowserJustClosed = false
                                                barsViewModel.showBrowser()
                                            }
                                            AppTab.MAIL -> mailWebVisible = true
                                            else -> Unit
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Browser action shared by the BARS and mail services. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PortalFloatingButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val height = ButtonDefaults.MediumContainerHeight
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = height).widthIn(min = 132.dp)
            .testTag("portal-floating-" + label),
        contentPadding = ButtonDefaults.contentPaddingFor(height),
        shapes = ButtonDefaults.shapes(),
    ) {
        AppIcon(
            icon = AppIcons.OpenPortal,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
        )
        Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
        AnimatedContent(
            targetState = label,
            modifier = Modifier.widthIn(min = 48.dp),
            transitionSpec = {
                (slideInVertically(
                    animationSpec = tween(190),
                    initialOffsetY = { it / 2 },
                ) + fadeIn(tween(160))) togetherWith
                    (slideOutVertically(
                        animationSpec = tween(150),
                        targetOffsetY = { -it / 2 },
                    ) + fadeOut(tween(120)))
            },
            label = "portal-action-label",
        ) { currentLabel ->
            Text(currentLabel, style = ButtonDefaults.textStyleFor(height))
        }
    }
}
