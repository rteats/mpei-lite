package com.rteats.mpeineo.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.rteats.mpeineo.BuildConfig
import com.rteats.mpeineo.MpeiNeoApplication
import com.rteats.mpeineo.R
import com.rteats.mpeineo.data.MailNotificationPreferences
import com.rteats.mpeineo.data.MailNotificationScheduler

/**
 * EasyNotes-inspired surface rows, circular accent badges and trailing
 * switches/actions. Original Compose implementation using Material You colors.
 */
@Composable
private fun SettingsBadge(iconRes: Int) {
    Surface(
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(iconRes), contentDescription = null,
                modifier = Modifier.size(23.dp))
        }
    }
}

@Composable
private fun SettingsExpansion(
    title: String,
    subtitle: String,
    iconRes: Int,
    expanded: Boolean,
    onExpand: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onExpand)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                SettingsBadge(iconRes)
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Свернуть" else "Развернуть",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(165, easing = FastOutSlowInEasing)) +
                    fadeIn(animationSpec = tween(100)),
                exit = shrinkVertically(animationSpec = tween(125, easing = FastOutSlowInEasing)) +
                    fadeOut(animationSpec = tween(90)),
            ) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    content()
                }
            }
        }
    }
}

@Composable
private fun SettingsAction(
    title: String,
    detail: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    downloading: Boolean = false,
    progressPercent: Int? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsBadge(iconRes)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (downloading) {
                if (progressPercent != null) {
                    LinearProgressIndicator(
                        progress = { (progressPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
        }
        if (busy) {
            ContainedLoadingIndicator(modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    iconRes: Int,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsBadge(iconRes)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}

@Composable
internal fun SettingsScreen(
    updateViewModel: UpdateViewModel,
    mailViewModel: MailViewModel,
    onClearCache: () -> Unit,
    active: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val diagnostics = (context.applicationContext as MpeiNeoApplication).container.diagnostics
    val mailState by mailViewModel.state.collectAsState()
    val updateState by updateViewModel.state.collectAsState()

    // Stored per installed app, surviving process death and package updates.
    // RememberSaveable alone only survives saved Activity state.
    val sectionPrefs = remember(context) {
        context.getSharedPreferences("settings_sections", Context.MODE_PRIVATE)
    }
    var mailExpanded by remember { mutableStateOf(sectionPrefs.getBoolean("mail", true)) }
    var updatesExpanded by remember { mutableStateOf(sectionPrefs.getBoolean("updates", false)) }
    var storageExpanded by remember { mutableStateOf(sectionPrefs.getBoolean("storage", false)) }
    var logsExpanded by remember { mutableStateOf(sectionPrefs.getBoolean("logs", false)) }
    var aboutExpanded by remember { mutableStateOf(sectionPrefs.getBoolean("about", false)) }
    fun changeSection(key: String, next: Boolean) {
        sectionPrefs.edit().putBoolean(key, next).apply()
        when (key) {
            "mail" -> mailExpanded = next
            "updates" -> updatesExpanded = next
            "storage" -> storageExpanded = next
            "logs" -> logsExpanded = next
            "about" -> aboutExpanded = next
        }
    }
    var copied by remember { mutableStateOf(false) }
    var cacheCleared by remember { mutableStateOf(false) }
    var notificationError by remember { mutableStateOf<String?>(null) }
    val notificationPrefs = remember(context) { MailNotificationPreferences(context) }
    var notificationsEnabled by remember { mutableStateOf(notificationPrefs.enabled) }
    var pollMinutes by remember { mutableStateOf(notificationPrefs.intervalMinutes) }

    LaunchedEffect(mailState.configured) {
        if (!mailState.configured) notificationsEnabled = false
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && mailState.configured) {
            MailNotificationScheduler.setEnabled(context, true)
            notificationsEnabled = true
            notificationError = null
        } else {
            notificationError = "Разрешите уведомления для MPEI Neo в настройках Android."
        }
    }

    fun toggleNotifications(on: Boolean) {
        if (!on) {
            MailNotificationScheduler.setEnabled(context, false)
            notificationsEnabled = false
            notificationError = null
        } else if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            MailNotificationScheduler.setEnabled(context, true)
            notificationsEnabled = true
            notificationError = null
        }
    }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (context.packageManager.canRequestPackageInstalls()) {
            updateViewModel.installDownloadedUpdate(context)
        }
    }
    fun installDownloadedUpdate() {
        if (context.packageManager.canRequestPackageInstalls()) {
            updateViewModel.installDownloadedUpdate(context)
        } else {
            installPermissionLauncher.launch(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.packageName)),
            )
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("settings-overview"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Настройки", style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold)
        }
        item {
            SettingsExpansion(
                "Почта МЭИ",
                if (mailState.configured) mailState.username else "Аккаунт не подключён",
                R.drawable.ic_nav_mail,
                mailExpanded, { changeSection("mail", !mailExpanded) },
            ) {
                SettingsSwitchRow(
                    "Уведомления о новых письмах",
                    "Фоновая проверка IMAP, без постоянного подключения",
                    R.drawable.ic_settings_notifications,
                    notificationsEnabled, mailState.configured,
                    ::toggleNotifications,
                )
                if (notificationsEnabled && mailState.configured) {
                    Text("Интервал проверки",
                        modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                        style = MaterialTheme.typography.labelLarge)
                    // Compose Material Catalog's single-select FlowRow
                    // using connected shapes and visible tonal segments.
                    val intervals = listOf(15, 30, 60)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(
                            ButtonGroupDefaults.ConnectedSpaceBetween,
                        ),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        intervals.forEachIndexed { index, minutes ->
                            ToggleButton(
                                checked = pollMinutes == minutes,
                                onCheckedChange = {
                                    if (pollMinutes != minutes) {
                                        pollMinutes = minutes
                                        MailNotificationScheduler.changeInterval(context, minutes)
                                    }
                                },
                                modifier = Modifier.semantics { role = Role.RadioButton },
                                shapes = when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    intervals.lastIndex ->
                                        ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                                colors = ToggleButtonDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp),
                            ) {
                                Text("$minutes мин", maxLines = 1, softWrap = false)
                            }
                        }
                    }
                    Text(
                        "Android может откладывать проверки ради экономии батареи.",
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                notificationError?.let { error ->
                    Text(error, modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                if (mailState.configured) {
                    SettingsAction(
                        "Выйти из почты",
                        "Отключить почту и остановить проверку новых писем",
                        R.drawable.ic_mail_logout,
                        onClick = {
                            mailViewModel.logout()
                            notificationsEnabled = false
                        },
                    )
                } else {
                    Text("Для настройки уведомлений войдите в почту на вкладке «Почта».",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item {
            SettingsExpansion(
                "Обновления приложения",
                "Версия " + BuildConfig.VERSION_NAME + " · канал " + BuildConfig.UPDATE_CHANNEL,
                R.drawable.ic_settings_update,
                updatesExpanded, { changeSection("updates", !updatesExpanded) },
            ) {
                UpdateCard(updateState, updateViewModel::checkForUpdates,
                    updateViewModel::downloadUpdate, ::installDownloadedUpdate)
            }
        }
        item {
            SettingsExpansion(
                "Локальный кэш",
                "Расписание доступно без сети",
                R.drawable.ic_settings_cache,
                storageExpanded, { changeSection("storage", !storageExpanded) },
            ) {
                SettingsAction(
                    if (cacheCleared) "Кэш очищен" else "Очистить кэш",
                    "Удалить сохранённые недели расписания",
                    R.drawable.ic_settings_clear_cache,
                    onClick = { onClearCache(); cacheCleared = true },
                )
            }
        }
        item {
            SettingsExpansion(
                "Диагностика",
                "Копирование и очистка журнала",
                R.drawable.ic_settings_diagnostics,
                logsExpanded, { changeSection("logs", !logsExpanded) },
            ) {
                SettingsAction(
                    if (copied) "Скопировано" else "Копировать журнал",
                    "Скопировать диагностические события",
                    R.drawable.ic_settings_copy_log,
                    onClick = {
                        val content = diagnostics.readText().ifBlank { "Журнал пока пуст." }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("MPEI Neo diagnostics", content))
                        copied = true
                        diagnostics.log("DIAGNOSTICS", "log copied to clipboard")
                    },
                )
                SettingsAction("Очистить журнал", "Удалить диагностические события",
                    R.drawable.ic_settings_clear_log,
                    onClick = {
                        diagnostics.clear()
                        diagnostics.log("DIAGNOSTICS", "log cleared by user")
                        copied = false
                    },
                )
            }
        }
        item {
            SettingsExpansion(
                "О приложении",
                "MPEI Neo · " + BuildConfig.VERSION_NAME,
                R.drawable.ic_settings_about,
                aboutExpanded, { changeSection("about", !aboutExpanded) },
            ) {
                Text(
                    "MPEI Neo " + BuildConfig.VERSION_NAME + " (" +
                        BuildConfig.VERSION_CODE + ")",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Неофициальный клиент МЭИ. Пароли и коды 2FA БАРС не сохраняются. " +
                        "Цвета интерфейса берутся из палитры Material You.",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateCard(
    state: UpdateUiState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
) {
    val available = state.release?.versionCode?.let {
        it > BuildConfig.VERSION_CODE
    } == true
    val downloading = state.phase == UpdatePhase.DOWNLOADING
    val readyToInstall = state.phase == UpdatePhase.READY_TO_INSTALL &&
        !state.downloadedApkPath.isNullOrBlank()

    // All updater actions share the exact same row treatment as other
    // settings. Checking leaves a discovered/verified update intact.
    SettingsAction(
        title = "Проверить обновления",
        detail = when {
            state.checking -> "Проверяем GitHub Releases…"
            state.phase == UpdatePhase.UP_TO_DATE -> "Установлена актуальная версия"
            available -> "Доступна версия ${state.release?.versionName.orEmpty()}"
            else -> "Текущая версия: ${BuildConfig.VERSION_NAME}"
        },
        iconRes = R.drawable.ic_settings_check_updates,
        onClick = onCheck,
        enabled = !state.checking && !downloading,
        busy = state.checking,
    )

    if (available) {
        SettingsAction(
            title = when {
                readyToInstall -> "Установить обновление"
                downloading -> "Скачивание обновления"
                else -> "Скачать обновление"
            },
            detail = when {
                readyToInstall -> "APK скачан и проверен. Открыть установщик Android"
                downloading && state.progressPercent != null ->
                    "Загружено: ${state.progressPercent}%"
                downloading -> "Получаем APK…"
                else -> "Версия ${state.release?.versionName.orEmpty()}"
            },
            iconRes = if (readyToInstall) {
                R.drawable.ic_settings_install_update
            } else {
                R.drawable.ic_settings_download_update
            },
            onClick = if (readyToInstall) onInstall else onDownload,
            enabled = !downloading && !state.checking,
            downloading = downloading,
            progressPercent = state.progressPercent,
        )
    }

    state.error?.let { error ->
        Text(
            error,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    if (available && !downloading) {
        state.release?.notes?.takeIf(String::isNotBlank)?.let { notes ->
            Text(
                notes,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
