package com.rteats.mpeineo.ui

import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Card
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import androidx.compose.ui.platform.LocalConfiguration
import com.rteats.mpeineo.R
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.rteats.mpeineo.data.MailSummary
import com.rteats.mpeineo.data.OwaMailFolder
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.DateFormat
import java.util.Date

/** Short-lived success is a Snackbar; cached/offline notices remain visible banners. */
internal fun mailNoticeInSnackbar(note: String?): Boolean =
    note == "Письмо отправлено" || note?.startsWith("Сохранено:") == true

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MailScreen(
    viewModel: MailViewModel,
    loginViewModel: MailLoginViewModel,
    active: Boolean,
    onOpenPortal: () -> Unit = {},
    searchRequest: Int = 0,
    reselectRequest: Int = 0,
    onFullScreenChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val loginState by loginViewModel.status.collectAsState()
    var showLoginPage by rememberSaveable { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(searchRequest) {
        if (searchRequest > 0) showSearch = true
    }
    LaunchedEffect(showLoginPage, showSearch, state.selected, state.composing) {
        onFullScreenChange(showLoginPage || showSearch ||
            state.selected != null || state.composing)
    }
    DisposableEffect(Unit) { onDispose { onFullScreenChange(false) } }
    BackHandler(enabled = active && showLoginPage) { showLoginPage = false }
    BackHandler(enabled = active && showSearch) { showSearch = false }
    LaunchedEffect(state.loginRequired) {
        if (!state.loginRequired) showLoginPage = false
    }
    LaunchedEffect(active) {
        if (active) viewModel.onMailScreenVisible()
    }
    // Retain the outgoing snapshot while its spatial exit spring settles.
    // The ViewModel clears selected/composing on Back, not after the animation.
    var lastDetailState by remember { mutableStateOf<MailUiState?>(null) }
    var lastComposerState by remember { mutableStateOf<MailUiState?>(null) }
    LaunchedEffect(state) {
        if (state.selected != null) lastDetailState = state
        if (state.composing) lastComposerState = state
    }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.notice) {
        val note = state.notice
        if (mailNoticeInSnackbar(note)) {
            snackbarHostState.showSnackbar(note!!, duration = SnackbarDuration.Short)
        }
    }
    // The ViewModel survives navigation. Pause nonessential Sent enrichment
    // immediately when the screen is hidden, then resume cached work on return.
    DisposableEffect(active, state.mailbox, state.selected, state.composing) {
        viewModel.setSentVisible(active && state.mailbox == OwaMailFolder.SENT &&
            state.selected == null && !state.composing)
        onDispose { viewModel.setSentVisible(false) }
    }
    val context = LocalContext.current
    val inboxScrollState = rememberLazyListState()
    val sentScrollState = rememberLazyListState()
    val scrollState = if (state.mailbox == OwaMailFolder.SENT) sentScrollState else inboxScrollState
    var lastReselect by remember { androidx.compose.runtime.mutableIntStateOf(reselectRequest) }
    LaunchedEffect(reselectRequest) {
        if (reselectRequest != lastReselect) scrollState.animateScrollToItem(0)
        lastReselect = reselectRequest
    }
    // Compose list keys contain only stable, opaque IDs; never log or persist
    // those identifiers. Prioritize rows that are actually on screen.
    LaunchedEffect(active, state.mailbox, state.selected, state.composing, sentScrollState) {
        if (active && state.mailbox == OwaMailFolder.SENT &&
            state.selected == null && !state.composing) {
            snapshotFlow {
                sentScrollState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
                    val key = info.key.toString()
                    if (key.startsWith("SENT-")) key.substringAfterLast('-').toLongOrNull()
                    else null
                }.toSet()
            }.distinctUntilChanged().collect(viewModel::updateVisibleSentUids)
        }
    }
    val folderItems = if (state.mailbox == OwaMailFolder.SENT) state.sentItems else state.items
    val folderLoading = if (state.mailbox == OwaMailFolder.SENT) state.sentLoading else state.loading
    val folderLoadingMore = if (state.mailbox == OwaMailFolder.SENT)
        state.sentLoadingMore else state.loadingMore
    val folderHasMore = if (state.mailbox == OwaMailFolder.SENT)
        state.sentHasMore else state.hasMore
    val folderMoreError = if (state.mailbox == OwaMailFolder.SENT)
        state.sentMoreError else state.moreError
    val visibleMessages = folderItems
    // Full-screen search handles cached Inbox and Sent results in one place.
    LaunchedEffect(scrollState, folderItems.size, folderHasMore,
        folderLoadingMore, folderLoading, folderMoreError,
        state.selected, active, state.mailbox) {
        if (active && state.selected == null && !state.composing &&
            folderHasMore && !folderLoadingMore &&
            !folderLoading && !folderMoreError) {
            snapshotFlow {
                scrollState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            }.distinctUntilChanged().collect { last ->
                if (last >= folderItems.size - 4) viewModel.loadMore()
            }
        }
    }
    BackHandler(enabled = active && !showLoginPage && !state.composing && state.selected != null) {
        viewModel.closeMessage()
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Выйти из ОСЭП?") },
            text = {
                Text("Сохранённые письма, сессия и данные для автоматического входа будут удалены.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        showLoginPage = false
                        viewModel.logout()
                    },
                    modifier = Modifier.testTag("mail-logout-confirm"),
                ) { Text("Выйти") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("Отмена") }
            },
        )
    }

    Scaffold(
        modifier = modifier.testTag("mail-inbox"),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (active && state.configured && !state.loginRequired &&
                state.selected == null && !state.composing &&
                !showLoginPage && !showSearch) {
                FloatingActionButton(
                    onClick = viewModel::startCompose,
                    modifier = Modifier.testTag("mail-compose-fab"),
                ) {
                    AppIcon(AppIcons.Edit, contentDescription = "Написать письмо")
                }
            }
        },
        // The app scaffold already handles safe-area insets; no second blank header.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { insets ->
        Box(
            modifier = Modifier.fillMaxSize().padding(insets),
        ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (state.configured) {
                if (state.loginRequired) {
                    ServiceStatus(
                        title = if (loginState.busy) "Восстанавливаем сеанс ОСЭП…"
                            else "Сессия ОСЭП истекла",
                        detail = if (loginState.busy)
                            "Проверяем сохранённые данные входа."
                        else loginState.error ?: "Письма доступны из кэша. Для обновления войдите снова.",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            .testTag("mail-session-expired-banner"),
                        tone = ServiceStatusTone.RECOVERY,
                        actionLabel = if (loginState.busy) null else "Войти",
                        onAction = if (loginState.busy) null else { { showLoginPage = true } },
                    )
                }
                if (state.sessionCheckProblem && !state.loginRequired && state.error == null) {
                    ServiceStatus(
                        title = "Не удалось проверить подключение к ОСЭП",
                        detail = "Сессия может быть действительна. Проверьте VPN или соединение.",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        tone = ServiceStatusTone.RECOVERY,
                        actionLabel = "Повторить",
                        onAction = { viewModel.checkSessionProactively("manual_retry") },
                    )
                }
                if (state.error != null && !state.loginRequired) {
                    val hasCachedContent = folderItems.isNotEmpty() || state.message != null
                    val canRetryMessage = state.selected != null && state.message == null
                    ServiceStatus(
                        title = when {
                            state.loginRequired -> "Сессия ОСЭП истекла"
                            state.selected != null -> "Не удалось выполнить действие"
                            else -> "Не удалось обновить почту"
                        },
                        detail = if (hasCachedContent && state.selected == null) {
                            "Показаны сохранённые письма."
                        } else state.error,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        tone = if (hasCachedContent) ServiceStatusTone.RECOVERY
                            else ServiceStatusTone.ERROR,
                        actionLabel = when {
                            state.loginRequired -> "Войти"
                            state.selected == null || canRetryMessage -> "Повторить"
                            else -> null // Re-tap an attachment to retry its download.
                        },
                        onAction = when {
                            state.loginRequired -> onOpenPortal
                            state.selected == null -> viewModel::refresh
                            canRetryMessage -> {
                                { state.selected?.let(viewModel::open); Unit }
                            }
                            else -> null
                        },
                    )
                } else {
                    state.notice?.takeUnless(::mailNoticeInSnackbar)?.let { note ->
                        ServiceStatus(
                            title = note,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            if (!state.configured) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                ) {
                    if (state.loading) {
                        ContainedLoadingIndicator()
                        Text(
                            "Проверяем подключение к ОСЭП…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        MailLoginPanel(
                            loginViewModel = loginViewModel,
                            onAuthenticated = viewModel::refresh,
                            onOpenBrowser = onOpenPortal,
                        )
                    }
                }
            } else {
                // Keep the Inbox mounted underneath a real sliding detail page,
                // preserving scroll position during a cancelled rightward swipe.
                Box(
                    modifier = Modifier.weight(1f),
                ) {
                        val refreshState = rememberPullToRefreshState()
                        PullToRefreshBox(
                            isRefreshing = folderLoading,
                            onRefresh = viewModel::refresh,
                            state = refreshState,
                            modifier = Modifier.fillMaxSize(),
                            indicator = {
                                PullToRefreshDefaults.LoadingIndicator(
                                    state = refreshState,
                                    isRefreshing = folderLoading,
                                    modifier = Modifier.align(Alignment.TopCenter),
                                )
                            },
                        ) {
                            LazyColumn(
                                state = scrollState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 12.dp, end = 12.dp,
                                    top = 12.dp, bottom = 28.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                item(key = "mail-action-group") {
                                    MailFolderActionGroup(
                                        selectedFolder = state.mailbox,
                                        onSelectFolder = viewModel::switchMailbox,
                                        onOpenPortal = onOpenPortal,
                                        onRequestLogout = { confirmLogout = true },
                                        modifier = Modifier.fillMaxWidth()
                                            .padding(bottom = 6.dp),
                                    )
                                }
                                if (visibleMessages.isEmpty()) {
                                    item(key = "empty-inbox") {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (folderLoading) ContainedLoadingIndicator()
                                            else Text(
                                                if (state.mailbox == OwaMailFolder.SENT)
                                                    "Отправленных писем нет."
                                                else "Писем нет. Потяните вниз для обновления.",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                                items(
                                    items = visibleMessages,
                                    key = { "${state.mailbox.name}-${it.uidValidity}-${it.uid}" },
                                ) { item ->
                                    MailInboxRow(
                                        summary = item,
                                        isSent = state.mailbox == OwaMailFolder.SENT,
                                        onClick = {
                                            (context.applicationContext as? com.rteats.mpeineo.MpeiNeoApplication)
                                                ?.container?.uiFrameProfiler?.markInteraction("MAIL_OPEN")
                                            viewModel.open(item)
                                        },
                                        modifier = Modifier,
                                    )
                                }
                                if (folderHasMore || folderLoadingMore || folderMoreError) {
                                    item(key = "inbox-older") {
                                        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center) {
                                            if (folderLoadingMore) {
                                                ContainedLoadingIndicator()
                                            } else {
                                                TextButton(
                                                    onClick = viewModel::loadMore,
                                                    enabled = !folderLoading,
                                                ) {
                                                    Text(if (folderMoreError) "Повторить загрузку старых писем"
                                                        else "Загрузить предыдущие письма")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    SpatialScreenOverlay(
                        visible = state.selected != null,
                        modifier = Modifier.testTag("mail-message-slide-layer"),
                    ) {
                        val detailState = if (state.selected != null) state else lastDetailState
                        if (detailState != null) {
                            SwipeBackDetail(
                                onBack = viewModel::closeMessage,
                                modifier = Modifier.testTag("mail-detail-swipe-back"),
                            ) {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.background,
                                ) {
                                    MailMessage(
                                        state = detailState,
                                        isSent = detailState.mailbox == OwaMailFolder.SENT,
                                        onDownload = viewModel::download,
                                        onOpenLocal = { attachment, uri ->
                                            if (!MailAttachmentOpener.open(context, uri, attachment.mimeType)) {
                                                viewModel.localOpenFailed()
                                            }
                                        },
                                        onBack = { viewModel.closeMessage() },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        SpatialScreenOverlay(
            visible = showLoginPage && state.configured,
            modifier = Modifier.testTag("mail-login-slide-layer"),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { showLoginPage = false }) {
                            AppIcon(AppIcons.Back, contentDescription = "Назад к письмам")
                        }
                        Text("Вход в ОСЭП", style = MaterialTheme.typography.titleLarge)
                    }
                    MailLoginPanel(
                        loginViewModel = loginViewModel,
                        onAuthenticated = {
                            showLoginPage = false
                            viewModel.refresh()
                        },
                        onOpenBrowser = {
                            showLoginPage = false
                            onOpenPortal()
                        },
                        modifier = Modifier.testTag("mail-login-deep-page"),
                    )
                }
            }
        }
        SpatialScreenOverlay(
            visible = state.composing,
            modifier = Modifier.testTag("mail-compose-slide-layer"),
        ) {
            val composerState = if (state.composing) state else lastComposerState
            if (composerState != null) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MailComposeScreen(
                        state = composerState,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize(),
                        onExit = { viewModel.closeCompose() },
                    )
                }
            }
        }
        }
    }
    if (showSearch) {
        MailSearchDialog(
            mail = state,
            onOpen = { hit ->
                showSearch = false
                viewModel.switchMailbox(hit.folder)
                viewModel.open(hit.message)
            },
            onClose = { showSearch = false },
        )
    }
}


/**
 * Medium connected group at the start of the message list.
 * Folder controls share all flexible width; actions have fixed 48 dp hit targets.
 * Uses M3 connected shapes and checked state rather than a second top app bar.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MailFolderActionGroup(
    selectedFolder: OwaMailFolder,
    onSelectFolder: (OwaMailFolder) -> Unit,
    onOpenPortal: () -> Unit,
    onRequestLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val height = ButtonDefaults.MediumContainerHeight
    val neutralColors = ToggleButtonDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    val actionColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    Row(
        modifier = modifier.testTag("mail-folder-button-group"),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            Triple(OwaMailFolder.INBOX, "Входящие", AppIcons.Mail),
            Triple(OwaMailFolder.SENT, "Отправленные", AppIcons.Forward),
        ).forEachIndexed { index, (folder, title, icon) ->
            ToggleButton(
                checked = selectedFolder == folder,
                onCheckedChange = {
                    if (selectedFolder != folder) onSelectFolder(folder)
                },
                modifier = Modifier.weight(if (folder == OwaMailFolder.SENT) 1.15f else 0.85f)
                    .height(height)
                    .semantics { role = Role.RadioButton }
                    .testTag(if (folder == OwaMailFolder.INBOX)
                        "mail-folder-inbox" else "mail-folder-sent"),
                shapes = if (index == 0)
                    ButtonGroupDefaults.connectedLeadingButtonShapes()
                else ButtonGroupDefaults.connectedMiddleButtonShapes(),
                colors = neutralColors,
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                AppIcon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(3.dp))
                Text(
                    title, maxLines = 1, softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Button(
            onClick = onOpenPortal,
            modifier = Modifier.width(48.dp).height(height)
                .testTag("portal-header-ОСЭП"),
            shape = ButtonGroupDefaults.connectedMiddleButtonShapes().shape,
            colors = actionColors,
            contentPadding = PaddingValues(0.dp),
        ) {
            AppIcon(AppIcons.OpenPortal, contentDescription = "Открыть ОСЭП",
                modifier = Modifier.size(22.dp))
        }
        Button(
            onClick = onRequestLogout,
            modifier = Modifier.width(48.dp).height(height)
                .testTag("mail-logout-open"),
            shape = ButtonGroupDefaults.connectedTrailingButtonShapes().shape,
            colors = actionColors,
            contentPadding = PaddingValues(0.dp),
        ) {
            AppIcon(AppIcons.Logout, contentDescription = "Выйти из ОСЭП",
                modifier = Modifier.size(22.dp))
        }
    }
}


/**
 * Compact inbox surfaces have separate shapes and muted tonal emphasis.
 * Unread state remains explicit in typography, without badges or extra labels.
 */
@Composable
private fun MailInboxRow(
    summary: MailSummary,
    isSent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val correspondent = if (isSent) {
        summary.recipients.orEmpty().joinToString(", ").ifBlank { "Получатель не определён" }
    } else summary.sender.ifBlank { "Неизвестный отправитель" }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (summary.unread) {
            lerp(
                MaterialTheme.colorScheme.surfaceContainerLow,
                MaterialTheme.colorScheme.primaryContainer,
                0.20f,
            )
        } else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        // Native Material two-line list row owns density, alignment and
        // leading/trailing layout. The clickable outer surface owns semantics.
        ListItem(
            headlineContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        if (isSent) "Кому: $correspondent" else correspondent,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (summary.unread) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        DateFormat.getDateInstance(DateFormat.SHORT).format(Date(summary.sentAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            supportingContent = {
                Text(
                    summary.subject.ifBlank { "Без темы" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (summary.unread) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (summary.unread) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingContent = { MailSenderAvatar(correspondent, summary.unread) },
            modifier = Modifier.fillMaxWidth(),
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        )
    }
}

@Composable
private fun MailSenderAvatar(sender: String, unread: Boolean) {
    Surface(
        modifier = Modifier.size(46.dp),
        shape = CircleShape,
        color = if (unread) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (unread) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                sender.trim().take(1).uppercase().ifBlank { "@" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MailMessage(
    state: MailUiState,
    isSent: Boolean,
    onDownload: (com.rteats.mpeineo.data.MailAttachment) -> Unit,
    onOpenLocal: (com.rteats.mpeineo.data.MailAttachment, Uri) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = state.message
    // Applies to previews, search results, notifications and ordinary mail
    // rows alike; no separate destination-specific animation code is needed.
    val toRecipients = detail?.recipients?.takeIf { it.isNotEmpty() }
        ?: state.selected?.recipients.orEmpty()
    val correspondent = if (isSent) {
        toRecipients.joinToString(", ").ifBlank { "Получатель не определён" }
    } else detail?.sender.orEmpty()
    when {
        state.loadingMessage -> Box(modifier, contentAlignment = Alignment.Center) {
            ContainedLoadingIndicator()
        }
        detail == null -> Box(modifier, contentAlignment = Alignment.Center) {
            if (state.error == null) {
                Text("Письмо не загружено")
            }
        }
        else -> Column(
            modifier = modifier.verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconButton(onClick = onBack) {
                AppIcon(AppIcons.Back, contentDescription = "К списку писем")
            }
            Text(
                detail.subject.ifBlank { "<Без темы>" },
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("mail-message-focus"),
                style = MaterialTheme.typography.titleLarge,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MailSenderAvatar(correspondent, unread = false)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        if (isSent) "Кому: $correspondent" else detail.sender,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        DateFormat.getDateTimeInstance().format(Date(detail.sentAt)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            )
            if (detail.attachments.isNotEmpty()) {
                Text(
                    "Вложения",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                detail.attachments.forEach { attachment ->
                    val localUri = attachment.owaAttachmentId?.let { id ->
                        state.localAttachmentUris[id]
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                start = 12.dp, end = 4.dp, top = 7.dp, bottom = 7.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(
                                AppIcons.Attachment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                attachment.name,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            if (state.savingAttachment == attachment.partPath) {
                                ContainedLoadingIndicator(modifier = Modifier.size(36.dp))
                            } else {
                                IconButton(
                                    onClick = { onDownload(attachment) },
                                    enabled = state.savingAttachment == null,
                                ) {
                                    AppIcon(
                                        AppIcons.Download,
                                        contentDescription = "Скачать " + attachment.name,
                                    )
                                }
                            }
                            if (localUri != null) {
                                IconButton(onClick = { onOpenLocal(attachment, localUri) }) {
                                    AppIcon(
                                        AppIcons.OpenPortal,
                                        contentDescription = "Открыть " + attachment.name,
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    "Скачанные вложения доступны в Загрузки/MPEI Neo.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                )
            }
            if (detail.threadMessages.size > 1) {
                Text("Сообщений в переписке: ${detail.threadMessages.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                detail.threadMessages.forEachIndexed { index, message ->
                    if (index > 0) HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                    Text(message.sender.ifBlank { "Неизвестный отправитель" },
                        style = MaterialTheme.typography.titleSmall)
                    Text(DateFormat.getDateTimeInstance().format(Date(message.sentAt)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SelectionContainer {
                        Text(annotatedMailLinks(message.body,
                            linkColor = MaterialTheme.colorScheme.primary),
                            style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else {
                SelectionContainer {
                    Text(annotatedMailLinks(detail.body,
                        linkColor = MaterialTheme.colorScheme.primary),
                        style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.size(100.dp))
        }
    }
}

/** Compose's URL links use Android's browser handler; no embedded WebView. */
private fun annotatedMailLinks(plain: String, linkColor: Color) = buildAnnotatedString {
    append(plain)
    val expression = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    expression.findAll(plain).forEach { match ->
        val clean = match.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']')
        if (clean.isNotEmpty()) {
            addLink(LinkAnnotation.Url(clean), match.range.first, match.range.first + clean.length)
            addStyle(
                SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                match.range.first,
                match.range.first + clean.length,
            )
        }
    }
}
