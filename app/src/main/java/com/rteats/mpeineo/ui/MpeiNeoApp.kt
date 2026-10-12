package com.rteats.mpeineo.ui

import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.rteats.mpeineo.MailNotificationTarget
import com.rteats.mpeineo.MpeiNeoApplication
import com.rteats.mpeineo.R
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

internal enum class AppTab { SCHEDULE, BARS, MAIL, SETTINGS }

/**
 * An embedded WebView can request that its focused input be brought into view.
 * That request must not turn into a horizontal pager/tab change. Deliberate
 * navbar and edge-swipe navigation update the intended tab before scrolling.
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


/**
 * All four destinations occupy identical pager constraints. Unlike the former
 * AnimatedContent route, Schedule's floating search doesn't shrink its page
 * and cause an animation-size jump.
 *
 * Native pages, including Mail inbox and messages, support full-width horizontal
 * paging. Embedded WebViews own their gestures; narrow edge-swipe regions
 * avoid stealing Outlook/BARS website scrolling and text input.
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
    fun logNavigation(event: String) = diagnostics.log("NAV", event)
    val barsViewModel: BarsViewModel = composeViewModel(
        factory = BarsViewModel.factory(
            application.container.diagnostics,
            application.container.barsNetworkDiagnostics,
            application.container.barsSnapshots,
        ),
    )
    val mailViewModel: MailViewModel = composeViewModel(
        factory = MailViewModel.factory(application.container.mailRepository),
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
    val pager = rememberPagerState(
        initialPage = if (initialMailTab) AppTab.MAIL.ordinal else 0,
        pageCount = { AppTab.entries.size },
    )
    val scope = rememberCoroutineScope()
    // User-selected destination, independent of pager bring-into-view requests
    // originating inside a WebView when an input gains keyboard focus.
    var intendedPage by rememberSaveable {
        mutableIntStateOf(if (initialMailTab) AppTab.MAIL.ordinal else AppTab.SCHEDULE.ordinal)
    }
    var explicitNavigation by remember { mutableStateOf(false) }
    var navigationRequest by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val keyboardVisible = imeBottomPx > 0
    var todayJumpRequest by rememberSaveable { mutableIntStateOf(0) }
    // Transient overlay state must not survive a restored Activity separately
    // from the search field's own focus and keyboard state.
    var searchExpanded by remember { mutableStateOf(false) }
    var mailWebVisible by rememberSaveable { mutableStateOf(false) }
    var viewedScheduleWeek by remember { mutableIntStateOf(0) }
    var agendaScrolling by remember { mutableStateOf(false) }
    var weekInfoVisible by remember { mutableStateOf(false) }

    LaunchedEffect(agendaScrolling) {
        if (agendaScrolling) {
            weekInfoVisible = true
        } else {
            delay(1800)
            weekInfoVisible = false
        }
    }
    LaunchedEffect(scheduleState.selected?.id, scheduleState.selected?.type) {
        viewedScheduleWeek = 0
        agendaScrolling = false
        weekInfoVisible = false
    }

    val barsIsWeb = barsState.authStage == BarsAuthStage.WEB_AUTH ||
        barsState.browserVisible
    val browserLock = lockedBrowserTab(
        AppTab.entries[intendedPage], mailWebVisible, barsIsWeb,
    )

    LaunchedEffect(pager) {
        snapshotFlow {
            listOf(
                pager.currentPage, pager.targetPage, pager.settledPage,
                (pager.currentPageOffsetFraction * 4).roundToInt(),
                if (pager.isScrollInProgress) 1 else 0, intendedPage,
                if (mailWebVisible) 1 else 0, if (barsIsWeb) 1 else 0,
            )
        }.distinctUntilChanged().collect { x ->
            logNavigation(
                "pager current=${x[0]} target=${x[1]} settled=${x[2]} quarter=${x[3]} " +
                    "scrolling=${x[4]} intended=${x[5]} mailWeb=${x[6]} barsWeb=${x[7]}"
            )
        }
    }
    LaunchedEffect(keyboardVisible) {
        logNavigation(
            "ime visible=$keyboardVisible bottomPx=$imeBottomPx " +
                "intended=$intendedPage current=${pager.currentPage} target=${pager.targetPage}"
        )
    }
    LaunchedEffect(mailWebVisible, barsIsWeb, intendedPage, explicitNavigation) {
        logNavigation(
            "mode mailWeb=$mailWebVisible barsWeb=$barsIsWeb intended=$intendedPage " +
                "explicit=$explicitNavigation target=${pager.targetPage}"
        )
    }

    // Keep navigation tied to an explicitly selected WebView tab. The pager
    // target is still used during normal swipes and deliberate tab animations.
    val currentTab = if (browserLock != null && !explicitNavigation) {
        browserLock
    } else {
        AppTab.entries[pager.targetPage]
    }
    val showNavigation = !keyboardVisible &&
        !(currentTab == AppTab.SCHEDULE && searchExpanded)

    LaunchedEffect(pager, browserLock, explicitNavigation) {
        snapshotFlow {
            Triple(pager.targetPage, pager.settledPage, pager.isScrollInProgress)
        }.distinctUntilChanged().collect { (targetPage, settledPage, scrolling) ->
            if (shouldRestoreLockedBrowserPager(
                    browserLock, explicitNavigation, pager.currentPage, targetPage, settledPage,
                )
            ) {
                val lockedTab = browserLock ?: return@collect
                logNavigation(
                    "unexpected pager movement restore=${lockedTab.name} " +
                        "current=${pager.currentPage} target=$targetPage settled=$settledPage"
                )
                pager.scrollToPage(lockedTab.ordinal)
            } else if (!explicitNavigation && browserLock == null && !scrolling &&
                targetPage == settledPage && intendedPage != settledPage
            ) {
                logNavigation("native swipe settled=${AppTab.entries[settledPage]}")
                intendedPage = settledPage
            }
            if (settledPage != AppTab.SCHEDULE.ordinal) searchExpanded = false
        }
    }

    LaunchedEffect(mailNotificationTarget?.requestId) {
        val target = mailNotificationTarget ?: return@LaunchedEffect
        logNavigation("notification deep link -> MAIL")
        intendedPage = AppTab.MAIL.ordinal
        pager.scrollToPage(AppTab.MAIL.ordinal)
        if (target.uid > 0L && target.validity > 0L) {
            mailViewModel.openFromNotification(target.uid, target.validity)
        } else {
            mailViewModel.closeMessage()
        }
    }

    fun navigate(target: AppTab) {
        logNavigation(
            "navigate target=${target.name} from=${currentTab.name} current=${pager.currentPage} " +
                "settled=${pager.settledPage} mailWeb=$mailWebVisible ime=$keyboardVisible"
        )
        if (target == AppTab.SCHEDULE) todayJumpRequest++
        if (target != AppTab.MAIL && mailWebVisible) {
            logNavigation("mail portal dismissed for tab navigation")
            mailWebVisible = false
        }
        intendedPage = target.ordinal
        val request = ++navigationRequest
        explicitNavigation = true
        scope.launch {
            try {
                pager.animateScrollToPage(target.ordinal)
                logNavigation("navigate finished target=${target.name} current=${pager.currentPage}")
            } finally {
                if (navigationRequest == request) explicitNavigation = false
            }
        }
    }

    fun closeMailBrowser() {
        logNavigation(
            "close ОСЭП requested current=${pager.currentPage} target=${pager.targetPage} " +
                "settled=${pager.settledPage} ime=$keyboardVisible web=$mailWebVisible"
        )
        intendedPage = AppTab.MAIL.ordinal
        val request = ++navigationRequest
        explicitNavigation = true
        // Restore Mail before releasing the browser lock. Suppress the focus
        // guard while this explicit scroll runs, so it cannot cancel the close.
        scope.launch {
            try {
                pager.scrollToPage(AppTab.MAIL.ordinal)
                mailWebVisible = false
                logNavigation("close ОСЭП finished current=${pager.currentPage}")
            } finally {
                if (navigationRequest == request) explicitNavigation = false
            }
        }
    }
    BackHandler(enabled = mailWebVisible && intendedPage == AppTab.MAIL.ordinal) {
        logNavigation("android back closes ОСЭП")
        closeMailBrowser()
    }

    val portalAction = when (currentTab) {
        AppTab.BARS -> if (barsState.authStage == BarsAuthStage.AUTHENTICATED &&
            !barsState.browserVisible && !keyboardVisible
        ) AppTab.BARS else null
        AppTab.MAIL -> if (!mailWebVisible && !keyboardVisible) AppTab.MAIL else null
        else -> null
    }
    val nativeSwipeAllowed = !searchExpanded && browserLock == null &&
        when (pager.settledPage) {
        AppTab.MAIL.ordinal -> !mailWebVisible
        AppTab.BARS.ordinal -> !barsIsWeb
        else -> true
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = showNavigation,
                enter = expandVertically(
                    expandFrom = Alignment.Bottom,
                    animationSpec = tween(240),
                ) + fadeIn(animationSpec = tween(190)),
                exit = shrinkVertically(
                    shrinkTowards = Alignment.Bottom,
                    animationSpec = tween(165),
                ) + fadeOut(animationSpec = tween(120)),
            ) {
            ShortNavigationBar(
                arrangement = ShortNavigationBarArrangement.EqualWeight,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                ShortNavigationBarItem(
                    selected = currentTab == AppTab.SCHEDULE,
                    onClick = { navigate(AppTab.SCHEDULE) },
                    icon = { AppIcon(AppIcons.Schedule, contentDescription = "Расписание") },
                    label = null,
                )
                ShortNavigationBarItem(
                    selected = currentTab == AppTab.BARS,
                    onClick = {
                        if (currentTab == AppTab.BARS && barsState.browserVisible) {
                            logNavigation("BARS navbar back current=${pager.currentPage} target=${pager.targetPage}")
                            intendedPage = AppTab.BARS.ordinal
                            val request = ++navigationRequest
                            explicitNavigation = true
                            scope.launch {
                                try {
                                    pager.scrollToPage(AppTab.BARS.ordinal)
                                    barsViewModel.hideBrowser()
                                    logNavigation("BARS navbar back finished current=${pager.currentPage}")
                                } finally {
                                    if (navigationRequest == request) explicitNavigation = false
                                }
                            }
                        } else {
                            navigate(AppTab.BARS)
                        }
                    },
                    icon = {
                        if (currentTab == AppTab.BARS && barsState.browserVisible) {
                            AppIcon(
                                AppIcons.Back,
                                contentDescription = "Вернуться к оценкам",
                            )
                        } else {
                            AppIcon(
                                icon = AppIcons.Bars,
                                contentDescription = "БАРС",
                            )
                        }
                    },
                    label = null,
                )
                ShortNavigationBarItem(
                    selected = currentTab == AppTab.MAIL,
                    onClick = {
                        when {
                            currentTab == AppTab.MAIL && mailWebVisible -> {
                                logNavigation("MAIL navbar back")
                                closeMailBrowser()
                            }
                            currentTab == AppTab.MAIL && mailState.selected != null -> {
                                logNavigation("MAIL navbar close message")
                                mailViewModel.closeMessage()
                            }
                            else -> navigate(AppTab.MAIL)
                        }
                    },
                    icon = {
                        if (currentTab == AppTab.MAIL &&
                            (mailWebVisible || mailState.selected != null)
                        ) {
                            AppIcon(
                                AppIcons.Back,
                                contentDescription = "Вернуться к входящим",
                            )
                        } else {
                            AppIcon(
                                icon = AppIcons.Mail,
                                contentDescription = "Почта",
                            )
                        }
                    },
                    label = null,
                )
                ShortNavigationBarItem(
                    selected = currentTab == AppTab.SETTINGS,
                    onClick = { navigate(AppTab.SETTINGS) },
                    icon = { AppIcon(AppIcons.Settings, contentDescription = "Настройки") },
                    label = null,
                )
            }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = nativeSwipeAllowed,
            beyondViewportPageCount = 3,
            key = { AppTab.entries[it].name },
        ) { page ->
            val destination = AppTab.entries[page]
            Box(modifier = Modifier.fillMaxSize()) {
                when (destination) {
                    AppTab.SCHEDULE -> ScheduleScreen(
                        state = scheduleState,
                        onEnsureAgendaWeek = viewModel::ensureAgendaWeek,
                        onRefreshAgendaWeek = viewModel::refreshAgendaWeek,
                        onRetryAgendaWeek = viewModel::retryAgendaWeek,
                        onViewedAgendaWeek = { viewedScheduleWeek = it },
                        onAgendaScrollingChanged = { agendaScrolling = it },
                        todayJumpRequest = todayJumpRequest,
                        modifier = Modifier.fillMaxSize(),
                    )

                    AppTab.BARS -> BarsScreen(
                        viewModel = barsViewModel,
                        autoRefreshEnabled = barsAutoRefreshEnabled,
                        active = pager.settledPage == page,
                        modifier = Modifier.fillMaxSize(),
                    )

                    AppTab.MAIL -> MailScreen(
                        viewModel = mailViewModel,
                        active = pager.currentPage == page && !mailWebVisible,
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
                        active = pager.currentPage == page,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                if (destination == AppTab.SCHEDULE) {
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
                        active = pager.currentPage == AppTab.SCHEDULE.ordinal,
                        // Unlike an independent entrance animation, this tracks
                        // horizontal pager progress in both directions, including
                        // an interrupted or reversed edge swipe.
                        dockRevealProgress = {
                            scheduleDockRevealProgress(
                                pager.currentPage,
                                pager.currentPageOffsetFraction,
                            )
                        },
                        modifier = Modifier.fillMaxSize().align(Alignment.BottomCenter),
                    )
                } else if (destination == AppTab.BARS && barsIsWeb) {
                    // A WebView isn't a Compose nested-scroll child. Only
                    // deliberate swipes starting within a 24dp edge zone page
                    // horizontally; in-page web interactions remain native.
                    WebViewPageEdges(
                        onPrevious = {
                            if (page > 0) navigate(AppTab.entries[page - 1])
                        },
                        onNext = {
                            if (page < AppTab.entries.lastIndex) {
                                navigate(AppTab.entries[page + 1])
                            }
                        },
                    )
                }
            }
        }

        // ОСЭП runs OUTSIDE HorizontalPager. HTML input bring-into-view requests
        // can no longer cause the pager to scroll when the IME resizes the layout.
        if (mailWebVisible && intendedPage == AppTab.MAIL.ordinal) {
            WebPortalScreen(
                url = "https://mail.mpei.ru/owa",
                testTag = "portal-mail",
                modifier = Modifier.fillMaxSize().testTag("mail-webview"),
                onDebugEvent = { message -> diagnostics.log("MAIL_WEB", message) },
            )
            WebViewPageEdges(
                onPrevious = { navigate(AppTab.BARS) },
                onNext = { navigate(AppTab.SETTINGS) },
            )
        }

        // One FAB is composed above the pager, so it stays fixed while the
        // BARS and mail pages slide beneath it; only its label/action changes.
        AnimatedVisibility(
            visible = portalAction != null,
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(110)),
        ) {
            PortalFloatingButton(
                label = if (portalAction == AppTab.MAIL) "ОСЭП" else "БАРС",
                onClick = {
                    when (portalAction) {
                        AppTab.BARS -> {
                            logNavigation("FAB open БАРС browser")
                            barsViewModel.showBrowser()
                        }
                        AppTab.MAIL -> {
                            logNavigation("FAB open ОСЭП current=${pager.currentPage}")
                            intendedPage = AppTab.MAIL.ordinal
                            mailWebVisible = true
                        }
                        else -> Unit
                    }
                },
            )
        }
        }
    }
}

/** A single portal action shared between BARS and Mail across pager transitions. */
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
            .testTag("portal-floating-$label"),
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
                ) + fadeIn(animationSpec = tween(160))) togetherWith
                    (slideOutVertically(
                        animationSpec = tween(150),
                        targetOffsetY = { -it / 2 },
                    ) + fadeOut(animationSpec = tween(120)))
            },
            label = "portal-action-label",
        ) { currentLabel ->
            Text(currentLabel, style = ButtonDefaults.textStyleFor(height))
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.WebViewPageEdges(
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Box(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .width(24.dp)
            .fillMaxHeight()
            .pointerInput(onPrevious) {
                var drag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onHorizontalDrag = { _, delta -> drag += delta },
                    onDragEnd = {
                        if (drag > 64.dp.toPx()) onPrevious()
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f },
                )
            },
    )
    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .width(24.dp)
            .fillMaxHeight()
            .pointerInput(onNext) {
                var drag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onHorizontalDrag = { _, delta -> drag += delta },
                    onDragEnd = {
                        if (drag < -64.dp.toPx()) onNext()
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f },
                )
            },
    )
}

/** Fraction of the search dock exposed above the short navigation bar. */
internal fun scheduleDockRevealProgress(
    currentPage: Int,
    pageOffsetFraction: Float,
): Float = (1f - kotlin.math.abs(currentPage + pageOffsetFraction)).coerceIn(0f, 1f)
