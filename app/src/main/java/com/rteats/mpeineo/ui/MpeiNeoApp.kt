package com.rteats.mpeineo.ui

import android.content.Context
import android.os.Build
import android.view.WindowInsets as AndroidWindowInsets
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons
import com.rteats.mpeineo.MailNotificationTarget
import com.rteats.mpeineo.MpeiNeoApplication
import kotlinx.coroutines.delay

internal enum class AppTab { SCHEDULE, BARS, MAIL, MAPS, SETTINGS }

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
            AnimatedContent(
                targetState = service,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    fadeIn(tween(180)) togetherWith fadeOut(tween(110))
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
                                        onHome = ::goHome,
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
                        }
                    }
                }
            }

            val portalAction = when (service) {
                AppTab.BARS -> AppTab.BARS.takeIf {
                    barsState.authStage == BarsAuthStage.AUTHENTICATED &&
                        !barsState.browserVisible && !imeVisible
                }
                AppTab.MAIL -> AppTab.MAIL.takeIf { !mailWebVisible && !imeVisible }
                else -> null
            }
            AnimatedVisibility(
                visible = service != null && service != AppTab.SCHEDULE &&
                    !imeVisible && !(service == AppTab.MAIL && mailWebVisible),
                modifier = Modifier.align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 16.dp),
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(110)),
            ) {
                FilledTonalIconButton(
                    onClick = ::goHome,
                    modifier = Modifier.size(56.dp).testTag("service-home"),
                ) {
                    AppIcon(AppIcons.Home, contentDescription = "На главную")
                }
            }
            AnimatedVisibility(
                visible = portalAction != null,
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
