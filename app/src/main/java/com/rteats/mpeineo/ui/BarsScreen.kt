package com.rteats.mpeineo.ui


import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.ConsoleMessage
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceResponse
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt
import java.text.DateFormat
import java.util.Date

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
  val session = transport.session
  val webView = transport.webView
  val navigationController = transport.navigationController
  val marksReadiness = transport.marksReadiness
  val retryNavigation = transport.retryNavigation
  val autoRefreshGate = transport.autoRefreshGate
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

  val cacheFresh = state.hasFreshCache()
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

  // A background Home sync must not pause JS while Main/Summary are loading.
  // The same WebView survives entering/leaving BARS and retains its cookies.
  androidx.compose.runtime.LaunchedEffect(active, allowBackgroundSync, webView, autoRefreshEnabled) {
    webView?.let { view ->
      if (active || allowBackgroundSync) view.onResume() else view.onPause()
      if ((active || allowBackgroundSync) && state.authStage == BarsAuthStage.AUTHENTICATED &&
        !state.loginRequired &&
        autoRefreshGate.shouldRefresh(
          enabled = autoRefreshEnabled,
          nowElapsedMs = SystemClock.elapsedRealtime(),
          sessionReady = navigationController?.hasCompletedDocument == true &&
            retryNavigation != null,
          hasCachedGrades = state.lastUpdatedAtMillis > 0L &&
            state.disciplines.isNotEmpty(),
          cacheFresh = state.hasFreshCache(),
          isLoading = state.isLoading,
          browserVisible = state.browserVisible,
        )
      ) {
        viewModel.logWebEvent("cached grades expired; refreshing on tab activation")
        webFlow.prepareMarkRefresh()
        session.prepareMarkRefresh()
        marksReadiness?.reset()
        viewModel.startRefresh()
        retryNavigation?.invoke()
      }
    }
  }

  androidx.compose.runtime.LaunchedEffect(state.browserVisible, state.authStage, webView) {
    if (state.browserVisible || state.authStage == BarsAuthStage.WEB_AUTH) {
      transport.interruptAutomatedCapture("interactive mode")
    }
    if (webFlow.browserWasVisible && !state.browserVisible) {
      webView?.let { view ->
        if (BarsDocumentPolicy.isTrusted(view.url.orEmpty())) {
          // Login/2FA may have finished while showing the official
          // browser. Recheck its final page when returning to grades.
          view.evaluateJavascript(BARS_PAGE_STATE_SCRIPT, null)
        }
      }
    }
    webFlow.browserWasVisible = state.browserVisible
  }

  BackHandler(enabled = active && state.browserVisible) {
    val view = webView
    if (view?.canGoBack() == true && view.url != BARS_MARKS_URL) {
      view.goBack()
    } else {
      // Arm the pager exit guard before WebView loses focus or is destroyed.
      onBrowserExit()
      view?.clearFocus()
      webFlow.needsFullViewport = false
      viewModel.hideBrowser()
    }
  }


  Box(
    modifier = modifier
      .fillMaxSize()
      .testTag("bars-screen"),
  ) {
    when (state.authStage) {
      BarsAuthStage.CHECKING -> BarsCheckingState(
        error = state.error,
        onRetry = {
          webFlow.prepareCheckRetry()
          session.prepareCheckRetry()
          marksReadiness?.reset()
          viewModel.checking()
          retryNavigation?.invoke()
        },
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
        onRefresh = {
          webFlow.manuallyRequestedConnection = true
          session.prepareMarkRefresh()
          webFlow.mainFrameFailed = false
          if (state.loginRequired) {
            transport.interruptAutomatedCapture("login required")
            viewModel.showBrowser()
          } else {
            webFlow.needsFullViewport = true
            marksReadiness?.reset()
            viewModel.startRefresh()
            retryNavigation?.invoke()
          }
        },
        onOpenBrowser = {
          transport.interruptAutomatedCapture("user opened browser")
          viewModel.showBrowser()
        },
        onRefreshSummary = {
          webFlow.manuallyRequestedConnection = true
          if (state.loginRequired) {
            transport.interruptAutomatedCapture("login required")
            viewModel.showBrowser()
          } else if (!state.isSummaryLoading) {
            val view = webView
            session.resetSummaryCapture()
            webFlow.mainFrameFailed = false
            if (view != null &&
              BarsDocumentPolicy.isCurrent(view.url.orEmpty(), BARS_MARKS_URL) &&
              !state.isLoading && navigationController != null
            ) {
              transport.requestOfficialSummary(view, "manual Summary refresh")
            } else {
              // The live document is not Main yet. Revalidate marks first;
              // their extraction callback will chain into Summary.
              webFlow.needsFullViewport = true
              marksReadiness?.reset()
              viewModel.startRefresh()
              retryNavigation?.invoke()
            }
          }
        },
      )
    }

    if (shouldHaveWebView) {
      AndroidView(
        // On refresh BARS needs a full-size WebView to lay out its DOM.
        // Transparent AndroidViews still intercept input when layered on top.
        // Only the explicit session browser or login should cover native UI.
        modifier = (when {
          state.browserVisible -> Modifier.fillMaxSize()
          state.authStage == BarsAuthStage.WEB_AUTH -> Modifier.fillMaxSize()
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
        ),
      factory = transport::create,
      update = {},
      onRelease = transport::release,
      )
    }

  }
}

