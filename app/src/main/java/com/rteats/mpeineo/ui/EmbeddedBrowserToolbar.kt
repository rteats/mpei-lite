package com.rteats.mpeineo.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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

/** Shared browser controls for BARS and OWA, not the app's service Back action. */
internal data class EmbeddedBrowserState(
    val url: String = "",
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
)

/** Only web page addresses are copied, never javascript:, data: or internal URLs. */
internal fun copyableBrowserUrl(raw: String?): String? {
    val url = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() !in listOf("http", "https")) return null
    if (uri.host.isNullOrBlank()) return null
    return url
}

/** Show the current site, not the path/query (which can contain auth tokens). */
internal fun browserSiteLabel(url: String): String =
    copyableBrowserUrl(url)?.let { URI(it).host } ?: "Браузер"

internal val EMBEDDED_BROWSER_TOOLBAR_HEIGHT = 64.dp

@Composable
internal fun EmbeddedBrowserToolbar(
    state: EmbeddedBrowserState,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copyUrl = copyableBrowserUrl(state.url)
    Surface(
        modifier = modifier.fillMaxWidth().testTag("embedded-browser-toolbar"),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .height(EMBEDDED_BROWSER_TOOLBAR_HEIGHT)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text(
                text = browserSiteLabel(state.url),
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(
                onClick = onBack,
                enabled = state.canGoBack,
                modifier = Modifier.testTag("browser-back"),
            ) {
                AppIcon(AppIcons.Back, contentDescription = "Назад")
            }
            IconButton(
                onClick = onForward,
                enabled = state.canGoForward,
                modifier = Modifier.testTag("browser-forward"),
            ) {
                AppIcon(AppIcons.Forward, contentDescription = "Вперёд")
            }
            IconButton(
                onClick = onReload,
                modifier = Modifier.testTag("browser-reload"),
            ) {
                AppIcon(AppIcons.Refresh, contentDescription = "Обновить страницу")
            }
            IconButton(
                onClick = {
                    if (copyUrl != null) {
                        clipboard.setText(AnnotatedString(copyUrl))
                        Toast.makeText(context, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = copyUrl != null,
                modifier = Modifier.testTag("browser-copy-url"),
            ) {
                AppIcon(AppIcons.Copy, contentDescription = "Копировать адрес страницы")
            }
        }
    }
}
