package com.rteats.mpeineo.ui

import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import android.graphics.Bitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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

/** Message detail retains the shell search bar; login and composer occupy it. */
internal fun mailHidesShellSearchBar(
    loginPageOpen: Boolean,
    messageOpen: Boolean,
    composing: Boolean,
): Boolean = when {
    loginPageOpen || composing -> true
    messageOpen -> false
    else -> false
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MailScreen(
    viewModel: MailViewModel,
    loginViewModel: MailLoginViewModel,
    active: Boolean,
    onOpenPortal: () -> Unit = {},
    reselectRequest: Int = 0,
    onFullScreenChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val loginState by loginViewModel.status.collectAsState()
    var showLoginPage by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(showLoginPage, state.selected, state.composing) {
        onFullScreenChange(mailHidesShellSearchBar(
            loginPageOpen = showLoginPage,
            messageOpen = state.selected != null,
            composing = state.composing,
        ))
    }
    DisposableEffect(Unit) { onDispose { onFullScreenChange(false) } }
    BackHandler(enabled = active && showLoginPage) { showLoginPage = false }
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
    val otherScrollStates = remember { mutableStateMapOf<OwaMailFolder, LazyListState>() }
    val scrollState = when (state.mailbox) {
        OwaMailFolder.INBOX -> inboxScrollState
        OwaMailFolder.SENT -> sentScrollState
        else -> otherScrollStates.getOrPut(state.mailbox) { LazyListState() }
    }
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
    val otherPage = state.otherPages[state.mailbox] ?: OtherMailPage()
    val folderItems = when (state.mailbox) {
        OwaMailFolder.INBOX -> state.items
        OwaMailFolder.SENT -> state.sentItems
        else -> otherPage.items
    }
    val folderLoading = when (state.mailbox) {
        OwaMailFolder.INBOX -> state.loading
        OwaMailFolder.SENT -> state.sentLoading
        else -> otherPage.loading
    }
    val folderLoadingMore = when (state.mailbox) {
        OwaMailFolder.INBOX -> state.loadingMore
        OwaMailFolder.SENT -> state.sentLoadingMore
        else -> otherPage.loadingMore
    }
    val folderHasMore = when (state.mailbox) {
        OwaMailFolder.INBOX -> state.hasMore
        OwaMailFolder.SENT -> state.sentHasMore
        else -> otherPage.hasMore
    }
    val folderMoreError = when (state.mailbox) {
        OwaMailFolder.INBOX -> state.moreError
        OwaMailFolder.SENT -> state.sentMoreError
        else -> otherPage.moreError
    }
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
    Scaffold(
        modifier = modifier.testTag("mail-inbox"),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (active && state.configured && !state.loginRequired &&
                state.selected == null && !state.composing &&
                !showLoginPage) {
                MediumFloatingActionButton(
                    onClick = viewModel::startCompose,
                    modifier = Modifier.testTag("mail-compose-fab"),
                ) {
                    AppIcon(AppIcons.Edit, contentDescription = "Написать письмо",
                        modifier = Modifier.size(FloatingActionButtonDefaults.MediumIconSize))
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
                    modifier = Modifier.fillMaxSize()
                        .then(if (state.loading) Modifier.padding(24.dp) else Modifier),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(
                        12.dp, if (state.loading) Alignment.CenterVertically else Alignment.Top,
                    ),
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
                // Keep the full parent mailbox mounted behind message detail.
                // The overlay hides folder tabs until predictive Back previews it.
                if (!state.composing) {
                    MailFolderTabs(
                        selectedFolder = state.mailbox,
                        onSelectFolder = viewModel::switchMailbox,
                        availableFolders = state.availableFolders,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // Keep the mailbox mounted underneath the detail preview,
                // preserving scroll position during a cancelled back gesture.
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
                                if (visibleMessages.isEmpty()) {
                                    item(key = "empty-inbox") {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (!folderLoading) Text(
                                                if (state.mailbox == OwaMailFolder.SENT)
                                                    "Отправленных писем нет."
                                                else "В папке «${state.mailbox.title}» писем нет. Потяните вниз для обновления.",
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
                                        isSent = state.mailbox == OwaMailFolder.SENT ||
                                            state.mailbox == OwaMailFolder.DRAFTS,
                                        photoLoader = viewModel::senderPhoto,
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
                }
            }
        }
        MailDetailOverlay(
            visible = state.configured && state.selected != null,
            enabled = active && !showLoginPage && !state.composing,
            onBack = viewModel::closeMessage,
            modifier = Modifier.testTag("mail-message-slide-layer"),
        ) {
            val detailState = if (state.selected != null) state else lastDetailState
            if (detailState != null) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MailMessage(
                        state = detailState,
                        isSent = detailState.mailbox == OwaMailFolder.SENT ||
                            detailState.mailbox == OwaMailFolder.DRAFTS,
                        photoLoader = viewModel::senderPhoto,
                        onDownload = viewModel::download,
                        onOpenLocal = { attachment, uri ->
                            if (!MailAttachmentOpener.open(context, uri, attachment.mimeType)) {
                                viewModel.localOpenFailed()
                            }
                        },
                        onBack = viewModel::closeMessage,
                        modifier = Modifier.fillMaxSize(),
                    )
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

}


/**
 * Primary tabs select the current mail folder directly below the search app bar.
 * The ViewModel remains the source of truth and keeps each folder's scroll state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MailFolderTabs(
    selectedFolder: OwaMailFolder,
    onSelectFolder: (OwaMailFolder) -> Unit,
    availableFolders: List<OwaMailFolder> = OwaMailFolder.defaults,
    modifier: Modifier = Modifier,
) {
    val folders = availableFolders.distinctBy { it.name }
        .ifEmpty { OwaMailFolder.defaults }
    val selectedIndex = folders.indexOf(selectedFolder).coerceAtLeast(0)
    val tabs: @Composable () -> Unit = {
        folders.forEach { folder ->
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text(folder.title) } },
                state = rememberTooltipState(),
            ) {
                Tab(
                    selected = selectedFolder == folder,
                    onClick = {
                        if (selectedFolder != folder) onSelectFolder(folder)
                    },
                    icon = { AppIcon(mailFolderIcon(folder), contentDescription = null) },
                    modifier = Modifier
                        .semantics { contentDescription = folder.title }
                        .testTag("mail-folder-${folder.name.lowercase()}"),
                )
            }
        }
    }
    BoxWithConstraints(modifier = modifier.testTag("mail-folder-tabs")) {
        if (64.dp * folders.size <= maxWidth) {
            PrimaryTabRow(selectedTabIndex = selectedIndex, tabs = tabs)
        } else {
            PrimaryScrollableTabRow(selectedTabIndex = selectedIndex,
                edgePadding = 0.dp, tabs = tabs)
        }
    }
}

private fun mailFolderIcon(folder: OwaMailFolder): Int = when (folder) {
    OwaMailFolder.INBOX -> AppIcons.Inbox
    OwaMailFolder.SENT -> AppIcons.Sent
    OwaMailFolder.DRAFTS -> AppIcons.Drafts
    OwaMailFolder.DELETED -> AppIcons.DeletedMail
    OwaMailFolder.JUNK -> AppIcons.JunkMail
    OwaMailFolder.NOTES -> AppIcons.Notes
    else -> if (folder.title.equals("Archive", true) ||
        folder.title.equals("Архив", true)) AppIcons.Archive else AppIcons.Folder
}


/**
 * Compact inbox surfaces have separate shapes and muted tonal emphasis.
 * Unread state remains explicit in typography, without badges or extra labels.
 */
@Composable
private fun MailInboxRow(
    summary: MailSummary,
    isSent: Boolean,
    photoLoader: suspend (String) -> Bitmap?,
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
            leadingContent = { MailSenderAvatar(correspondent, summary.unread,
                if (isSent) null else summary.senderEmail, photoLoader) },
            modifier = Modifier.fillMaxWidth(),
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        )
    }
}

@Composable
private fun MailSenderAvatar(
    sender: String,
    unread: Boolean,
    email: String? = null,
    photoLoader: suspend (String) -> Bitmap? = { null },
) {
    val photo by produceState<Bitmap?>(initialValue = null, key1 = email) {
        value = email?.let { photoLoader(it) }
    }
    Surface(
        modifier = Modifier.size(46.dp).clip(CircleShape),
        shape = CircleShape,
        color = if (unread) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (unread) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (photo != null) Image(
                bitmap = photo!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            ) else Text(
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
    photoLoader: suspend (String) -> Bitmap? = { null },
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
                MailSenderAvatar(correspondent, unread = false,
                    email = if (isSent) null else detail.senderEmail,
                    photoLoader = photoLoader)
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
