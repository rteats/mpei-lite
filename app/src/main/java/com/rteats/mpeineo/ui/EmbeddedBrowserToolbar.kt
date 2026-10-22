package com.rteats.mpeineo.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons
import java.net.URI

/** Shared browser state, independent of BARS or OWA session credentials. */
internal data class EmbeddedBrowserState(
    val url: String = "",
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
)

/** Only HTTP(S) web URLs can leave the app or enter the clipboard. */
internal fun copyableBrowserUrl(raw: String?): String? {
    val url = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() !in listOf("http", "https")) return null
    if (uri.host.isNullOrBlank()) return null
    return url
}

/** Never render paths/queries in the app bar: auth links may carry tokens. */
internal fun browserSiteLabel(url: String): String =
    copyableBrowserUrl(url)?.let { URI(it).host } ?: "Браузер"

/** Launch an external browser only on explicit user action. No cookies shared. */
internal fun launchExternalBrowser(context: Context, currentUrl: String?): Boolean {
    val url = copyableBrowserUrl(currentUrl) ?: return false
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

// Compact browser-specific top app bar replaces the global search bar while open.
internal val EMBEDDED_BROWSER_APPBAR_HEIGHT = 64.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EmbeddedBrowserAppBar(
    state: EmbeddedBrowserState,
    onExit: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    val copyUrl = copyableBrowserUrl(state.url)

    TopAppBar(
        modifier = modifier.fillMaxWidth().testTag("embedded-browser-appbar"),
        // The parent Scaffold already applies status-bar safe drawing insets.
        windowInsets = WindowInsets(0, 0, 0, 0),
        title = {
            Text(
                browserSiteLabel(state.url),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onExit,
                modifier = Modifier.testTag("browser-exit"),
            ) {
                AppIcon(AppIcons.Back, contentDescription = "Вернуться в приложение")
            }
        },
        actions = {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.testTag("browser-overflow"),
            ) {
                AppIcon(AppIcons.Overflow, contentDescription = "Меню браузера")
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Назад по странице") },
                    leadingIcon = { AppIcon(AppIcons.Back, contentDescription = null) },
                    enabled = state.canGoBack,
                    onClick = { menuExpanded = false; onBack() },
                    modifier = Modifier.testTag("browser-back"),
                )
                DropdownMenuItem(
                    text = { Text("Вперёд") },
                    leadingIcon = { AppIcon(AppIcons.Forward, contentDescription = null) },
                    enabled = state.canGoForward,
                    onClick = { menuExpanded = false; onForward() },
                    modifier = Modifier.testTag("browser-forward"),
                )
                DropdownMenuItem(
                    text = { Text("Обновить страницу") },
                    leadingIcon = { AppIcon(AppIcons.Refresh, contentDescription = null) },
                    onClick = { menuExpanded = false; onReload() },
                    modifier = Modifier.testTag("browser-reload"),
                )
                DropdownMenuItem(
                    text = { Text("Копировать URL") },
                    leadingIcon = { AppIcon(AppIcons.Copy, contentDescription = null) },
                    enabled = copyUrl != null,
                    onClick = {
                        menuExpanded = false
                        copyUrl?.let {
                            clipboard.setText(AnnotatedString(it))
                            Toast.makeText(context, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("browser-copy-url"),
                )
                DropdownMenuItem(
                    text = { Text("Открыть в браузере") },
                    leadingIcon = { AppIcon(AppIcons.OpenPortal, contentDescription = null) },
                    enabled = copyUrl != null,
                    onClick = {
                        menuExpanded = false
                        if (!launchExternalBrowser(context, copyUrl)) {
                            Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("browser-external"),
                )
            }
        },
    )
}
