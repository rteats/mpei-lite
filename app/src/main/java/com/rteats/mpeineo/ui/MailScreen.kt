package com.rteats.mpeineo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
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
import com.rteats.mpeineo.data.MailServerCertificate
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MailScreen(
    viewModel: MailViewModel,
    webVisible: Boolean,
    onWebVisibleChange: (Boolean) -> Unit,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    var showCertificateDetails by remember { mutableStateOf(false) }
    val inboxScrollState = rememberLazyListState()
    val motion = MaterialTheme.motionScheme

    BackHandler(enabled = active && (webVisible || state.selected != null)) {
        if (webVisible) onWebVisibleChange(false) else viewModel.closeMessage()
    }

    if (webVisible) {
        // The Mail nav item becomes the back button (same as BARS).
        // The embedded OWA page owns the entire available content viewport.
        WebPortalScreen(
            url = "https://mail.mpei.ru/owa",
            testTag = "portal-mail",
            modifier = modifier.fillMaxSize().testTag("mail-webview"),
        )
        return
    }

    state.certificate?.takeIf { showCertificateDetails }?.let { certificate ->
        ModalBottomSheet(
            onDismissRequest = { showCertificateDetails = false },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.78f)
                    .verticalScroll(rememberScrollState()),
            ) {
                MailCertificateCard(
                    certificate = certificate,
                    busy = state.loading,
                    onApprove = {
                        showCertificateDetails = false
                        viewModel.approveCertificate()
                    },
                    onDismiss = {
                        showCertificateDetails = false
                        viewModel.dismissCertificateWarning()
                    },
                )
                Spacer(Modifier.size(28.dp))
            }
        }
    }

    Scaffold(
        modifier = modifier.testTag("mail-inbox"),
        containerColor = MaterialTheme.colorScheme.background,
        // The app scaffold already handles safe-area insets; no second blank header.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { insets ->
        Column(modifier = Modifier.fillMaxSize().padding(insets)) {
            if (state.error != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        state.error.orEmpty(),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            if (state.inspectingCertificate) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    Text("Получаем сведения о TLS-сертификате…")
                }
            }
            if (state.certificate != null) {
                TextButton(
                    onClick = { showCertificateDetails = true },
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    Text("Проверить сертификат и подключиться")
                }
            }
            state.notice?.let { note ->
                Text(
                    note,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            if (!state.configured) {
                MailLogin(
                    usernameHint = state.username,
                    busy = state.loading,
                    onLogin = viewModel::login,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // Preserve the inbox LazyListState while smoothly navigating
                // into and out of a message; all mail operations remain native.
                AnimatedContent(
                    targetState = state.selected != null,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        val opening = targetState
                        (slideInHorizontally(
                            initialOffsetX = { if (opening) it / 5 else -it / 5 },
                            animationSpec = motion.defaultSpatialSpec(),
                        ) + fadeIn(animationSpec = motion.defaultEffectsSpec())) togetherWith
                            (slideOutHorizontally(
                                targetOffsetX = { if (opening) -it / 5 else it / 5 },
                                animationSpec = motion.defaultSpatialSpec(),
                            ) + fadeOut(animationSpec = motion.defaultEffectsSpec()))
                    },
                    label = "mail-inbox-message",
                ) { reading ->
                    if (reading) {
                        MailMessage(
                            state = state,
                            onDownload = viewModel::download,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        val refreshState = rememberPullToRefreshState()
                        PullToRefreshBox(
                            isRefreshing = state.loading,
                            onRefresh = viewModel::refresh,
                            state = refreshState,
                            modifier = Modifier.fillMaxSize(),
                            indicator = {
                                PullToRefreshDefaults.LoadingIndicator(
                                    state = refreshState,
                                    isRefreshing = state.loading,
                                    modifier = Modifier.align(Alignment.TopCenter),
                                )
                            },
                        ) {
                            LazyColumn(
                                state = inboxScrollState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 12.dp, end = 12.dp,
                                    top = 12.dp, bottom = 100.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                if (state.items.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (state.loading) ContainedLoadingIndicator()
                                            else Text(
                                                "Писем нет. Потяните вниз для обновления.",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                                items(
                                    items = state.items,
                                    key = { "${it.uidValidity}-${it.uid}" },
                                ) { item ->
                                    MailInboxRow(
                                        summary = item,
                                        onClick = { viewModel.open(item) },
                                        // The screen transition owns row movement; avoid
                                        // competing lazy-item placement animations on reentry.
                                        modifier = Modifier,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


/**
 * Explicit certificate exception for mail.mpei.ru only. We show the actual
 * leaf SHA-256, certificate identity and validity before requesting consent.
 */
@Composable
private fun MailCertificateCard(
    certificate: MailServerCertificate,
    busy: Boolean,
    onApprove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var checked by remember(certificate.sha256) { mutableStateOf(false) }
    val allowed = certificate.currentlyValid && certificate.matchesMailHostname
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                "Сертификат почтового сервера",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "MPEI использует центр сертификации, которому Android не доверяет " +
                    "по умолчанию. Сравните отпечаток SHA-256 ниже с тем, который " +
                    "показывает FairEmail для mail.mpei.ru:993, либо подтвердите его " +
                    "у администраторов МЭИ. Сходство названия недостаточно.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Сервер: mail.mpei.ru:993",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                "Кому выдан: ${certificate.subject}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Кем выдан: ${certificate.issuer}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Действует до: " +
                    DateFormat.getDateInstance(DateFormat.MEDIUM)
                        .format(Date(certificate.validUntil)),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "SHA-256 отпечаток сертификата:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            SelectionContainer {
                Text(
                    certificate.sha256.chunked(2).joinToString(":"),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            certificate.chainIssuerSha256?.let { caFingerprint ->
                Text(
                    "SHA-256 сертификата CA (если сервер его отправил):",
                    style = MaterialTheme.typography.labelMedium,
                )
                SelectionContainer {
                    Text(
                        caFingerprint.chunked(2).joinToString(":"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (!allowed) {
                Text(
                    if (!certificate.currentlyValid) {
                        "Сертификат недействителен по сроку. Его нельзя подтвердить."
                    } else {
                        "Имя mail.mpei.ru отсутствует в сертификате. Подключение заблокировано."
                    },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { checked = !checked },
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { checked = it },
                    )
                    Text(
                        "Я сверил SHA-256 по независимому доверенному источнику",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = checked && !busy,
                    onClick = onApprove,
                ) {
                    Text("Доверять этому сертификату и подключиться")
                }
            }
            TextButton(onClick = onDismiss) {
                Text("Не доверять")
            }
            Text(
                "Доверие действует только для точного сертификата сервера " +
                    "mail.mpei.ru и не отключает проверку имени хоста. " +
                    "При замене сертификата потребуется новое подтверждение.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MailLogin(
    usernameHint: String,
    busy: Boolean,
    onLogin: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var username by rememberSaveable { mutableStateOf(usernameHint) }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Подключение почты МЭИ",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Используйте те же имя пользователя и пароль IMAP, что и в FairEmail. " +
                "Подключение: mail.mpei.ru · SSL/TLS · 993. SMTP не требуется.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Имя пользователя IMAP") },
            placeholder = { Text("Как в настройках FairEmail") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль IMAP") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onLogin(username, password) },
            enabled = !busy && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp))
            else Text("Подключить входящие")
        }
        Text(
            "Пароль шифруется ключом Android Keystore и хранится только на устройстве. " +
                "Приложение не отправляет письма и не изменяет их статус прочтения.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(90.dp))
    }
}

/**
 * Compact inbox surfaces have separate shapes and muted tonal emphasis.
 * Unread state remains explicit in typography, without badges or extra labels.
 */
@Composable
private fun MailInboxRow(
    summary: MailSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MailSenderAvatar(summary.sender, summary.unread)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        summary.sender.ifBlank { "Неизвестный отправитель" },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (summary.unread) {
                            FontWeight.Bold
                        } else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        DateFormat.getDateInstance(DateFormat.SHORT)
                            .format(Date(summary.sentAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    summary.subject.ifBlank { "<Без темы>" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (summary.unread) {
                        FontWeight.SemiBold
                    } else FontWeight.Normal,
                    color = if (summary.unread) {
                        MaterialTheme.colorScheme.onSurface
                    } else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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
    onDownload: (com.rteats.mpeineo.data.MailAttachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = state.message
    when {
        state.loadingMessage -> Box(modifier, contentAlignment = Alignment.Center) {
            ContainedLoadingIndicator()
        }
        detail == null -> Box(modifier, contentAlignment = Alignment.Center) {
            Text("Письмо не загружено")
        }
        else -> Column(
            modifier = modifier.verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                detail.subject.ifBlank { "<Без темы>" },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MailSenderAvatar(detail.sender, unread = false)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        detail.sender,
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
                    Surface(
                        onClick = { onDownload(attachment) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = 14.dp,
                                vertical = 13.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_mail_attachment),
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
                                ContainedLoadingIndicator(
                                    modifier = Modifier.size(26.dp),
                                )
                            } else Icon(
                                painterResource(R.drawable.ic_mail_download),
                                contentDescription = "Сохранить в Загрузки",
                            )
                        }
                    }
                }
                Text(
                    "Нажмите на вложение, чтобы сохранить его в Загрузки/MPEI Neo.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                )
            }
            SelectionContainer {
                Text(
                    annotatedMailLinks(
                        detail.body,
                        linkColor = MaterialTheme.colorScheme.primary,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
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
