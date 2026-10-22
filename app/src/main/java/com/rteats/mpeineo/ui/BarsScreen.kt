package com.rteats.mpeineo.ui


import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
* Native BARS frontend backed by the official BARS web session.
*
* Authentication intentionally stays on the official BARS pages. This makes the app follow
* BARS' current password + 2FA flow instead of duplicating the private authentication protocol.
* Once authenticated, the same WebView session is used to extract the current marks into the
* native Compose UI. The WebView is kept alive so "Открыть БАРС" exposes that exact session.
*/
/** Pure guard: a disabled automatic update must not create a Home WebView. */
internal fun shouldInitializeBarsWebView(
  connectionRequired: Boolean,
  hasWebView: Boolean,
  active: Boolean,
  backgroundAllowed: Boolean,
): Boolean = connectionRequired && (hasWebView || active || backgroundAllowed)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BarsScreen(
  viewModel: BarsViewModel,
  autoRefreshEnabled: Boolean = true,
  active: Boolean = true,
  /** Keep the single WebView alive for automatic Home data refresh. */
  allowBackgroundSync: Boolean = false,
  onBrowserExit: () -> Unit = {},
  onAuthBrowserExit: () -> Unit = {},
  onSearch: () -> Unit = {},
  onDetailOpenChange: (Boolean) -> Unit = {},
  openDisciplineName: String? = null,
  openDisciplineRequest: Int = 0,
  onOpenDisciplineConsumed: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val state by viewModel.state.collectAsState()
  val transport = remember(viewModel) { BarsWebViewTransport(viewModel) }
  val webFlow = transport.webFlow
  val webView = transport.webView
  val browserSlide = rememberBackSlideState()
  var viewportPx by remember { mutableFloatStateOf(1f) }
  fun exitEmbeddedBrowser() {
    if (state.browserVisible) {
      transport.exitBrowser(onBrowserExit)
    } else {
      onBrowserExit()
      onAuthBrowserExit()
    }
  }
  LaunchedEffect(state.browserVisible) {
    if (state.browserVisible) browserSlide.enter() else browserSlide.reset()
  }
  val browserControlsVisible = active &&
    (state.browserVisible || state.authStage == BarsAuthStage.WEB_AUTH)
  // AndroidView callbacks must read the latest foreground/browser mode.
  transport.browserCanInteract = active &&
    (state.browserVisible || state.authStage == BarsAuthStage.WEB_AUTH)

  androidx.compose.runtime.LaunchedEffect(autoRefreshEnabled) {
    if (!autoRefreshEnabled && !webFlow.manuallyRequestedConnection &&
      state.disciplines.isNotEmpty()
    ) {
      viewModel.showCachedWithoutRefresh()
    }
  }

  // The super-app keeps one BARS composition mounted even at Home. Auto-refresh
  // may establish its authenticated WebView without entering the BARS service.
  // Explicitly disabling automatic updates still suppresses that background work.
  val connectionRequired = autoRefreshEnabled || state.disciplines.isEmpty() ||
    webFlow.manuallyRequestedConnection || state.browserVisible
  val shouldHaveWebView = shouldInitializeBarsWebView(
    connectionRequired = connectionRequired,
    hasWebView = webView != null,
    active = active,
    backgroundAllowed = allowBackgroundSync,
  )

  // The WebView stays mounted on Home; its lifecycle is transport-owned.
  androidx.compose.runtime.LaunchedEffect(active, allowBackgroundSync, webView, autoRefreshEnabled) {
    transport.onVisibilityChanged(active || allowBackgroundSync, autoRefreshEnabled)
  }

  androidx.compose.runtime.LaunchedEffect(state.browserVisible, state.authStage, webView) {
    transport.onBrowserModeChanged()
  }

  BackHandler(enabled = active && browserControlsVisible) {
    if (state.browserVisible && transport.browserToolbarState.canGoBack &&
        webView?.url != BARS_MARKS_URL) {
      transport.browserBack()
    } else browserSlide.exit(::exitEmbeddedBrowser)
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .onSizeChanged { viewportPx = it.width.toFloat().coerceAtLeast(1f) }
      .testTag("bars-screen"),
  ) {
    when (state.authStage) {
      BarsAuthStage.CHECKING -> BarsCheckingState(
        error = state.error,
        onRetry = transport::retrySessionCheck,
      )

      BarsAuthStage.WEB_AUTH -> {
        // The official BARS page is rendered by the persistent WebView below.
      }

      BarsAuthStage.AUTHENTICATED -> BarsNativeDashboard(
        state = state,
        active = active,
        onDetailOpenChange = onDetailOpenChange,
        openDisciplineName = openDisciplineName,
        openDisciplineRequest = openDisciplineRequest,
        onOpenDisciplineConsumed = onOpenDisciplineConsumed,
        showCachedRefreshErrors = autoRefreshEnabled || webFlow.manuallyRequestedConnection,
        onAcknowledgeGrades = viewModel::acknowledgeGradeChanges,
        onSearch = onSearch,
        onRefresh = { transport.refreshMarks() },
        onOpenBrowser = { transport.openBrowser() },
        onRefreshSummary = transport::refreshSummary,
      )
    }

    if (shouldHaveWebView) {
      AndroidView(
        // On refresh BARS needs a full-size WebView to lay out its DOM.
        // Transparent AndroidViews still intercept input when layered on top.
        // Only the explicit session browser or login should cover native UI.
        modifier = (when {
          state.browserVisible || state.authStage == BarsAuthStage.WEB_AUTH ->
            Modifier.fillMaxSize().padding(top = EMBEDDED_BROWSER_APPBAR_HEIGHT)
          webFlow.needsFullViewport -> Modifier
            .fillMaxSize()
            .alpha(0f)
          else -> Modifier
            .size(1.dp)
            .alpha(0f)
        }).zIndex(
          if (!state.browserVisible &&
            state.authStage != BarsAuthStage.WEB_AUTH
          ) -1f else 1f,
        ).graphicsLayer {
          translationX = if (browserControlsVisible) viewportPx * browserSlide.progress else 0f
        },
        factory = transport::create,
        update = {},
        onRelease = transport::release,
      )
      if (browserControlsVisible) {
        EmbeddedBrowserExitZone(
          onExit = { browserSlide.exit(::exitEmbeddedBrowser) },
          onDrag = { delta -> browserSlide.drag(delta, viewportPx) },
          onRelease = { browserSlide.release(::exitEmbeddedBrowser) },
          onCancel = browserSlide::cancel,
          modifier = Modifier.align(Alignment.CenterStart).zIndex(3f)
            .graphicsLayer { translationX = viewportPx * browserSlide.progress }
            .testTag("bars-browser-swipe-exit"),
        )
        EmbeddedBrowserAppBar(
          state = transport.browserToolbarState,
          onExit = { browserSlide.exit(::exitEmbeddedBrowser) },
          onBack = transport::browserBack,
          onForward = transport::browserForward,
          onReload = transport::browserReload,
          modifier = Modifier.align(Alignment.TopCenter).zIndex(2f)
            .graphicsLayer { translationX = viewportPx * browserSlide.progress }
            .testTag("bars-browser-controls"),
        )
      }
    }

  }
}

