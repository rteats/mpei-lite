package com.rteats.mpeineo.ui

import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons

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
@SuppressLint("SetJavaScriptEnabled")
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
  val browserCanInteract by rememberUpdatedState(
    active && (state.browserVisible || state.authStage == BarsAuthStage.WEB_AUTH)
  )
  var webView by remember { mutableStateOf<WebView?>(null) }
  var navigationController by remember { mutableStateOf<BarsNavigationController?>(null) }
  var marksReadiness by remember { mutableStateOf<BarsMarksReadiness?>(null) }
  var retryNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }
  val webFlow = remember { BarsWebFlowState() }
  val extractionGate = remember { BarsExtractionGate() }
  val autoRefreshGate = remember { BarsAutoRefreshGate() }

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
        marksReadiness?.reset()
        viewModel.startRefresh()
        retryNavigation?.invoke()
      }
    }
  }

  androidx.compose.runtime.LaunchedEffect(state.browserVisible, webView) {
    if (state.browserVisible) marksReadiness?.navigationStarted()
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

  fun restoreOfficialMarks(reason: String) {
    // A bare Main route can be rejected without BARS' selected student.
    val target = webFlow.officialMainUrl?.takeIf {
      BarsAuthenticatedRoutePolicy.isStudentRoute(it, "Main")
    } ?: BARS_LOGIN_URL
    viewModel.logWebEvent(
      "restore marks officialLink=" + (target != BARS_LOGIN_URL),
    )
    navigationController?.load(target, reason)
  }

  fun requestOfficialSummary(view: WebView, reason: String) {
    val controller = navigationController ?: return
    if (webView !== view || state.browserVisible ||
      !BarsDocumentPolicy.isCurrent(view.url.orEmpty(), BARS_MARKS_URL)
    ) return

    // Route discovery is asynchronous. Keep the authenticated document laid
    // out at its normal size until discovery/navigation is finished: reducing
    // Chromium to 1dp can remove or postpone responsive menu markup.
    webFlow.summaryFetchAttempted = true
    webFlow.summaryExtractionStarted = false
    webFlow.needsFullViewport = true
    val generation = ++webFlow.summaryRouteDiscoveryGeneration

    fun discover(attempt: Int) {
      if (webView !== view || state.browserVisible ||
        navigationController !== controller ||
        !webFlow.summaryFetchAttempted || generation != webFlow.summaryRouteDiscoveryGeneration ||
        !BarsDocumentPolicy.isCurrent(view.url.orEmpty(), BARS_MARKS_URL)
      ) return

      view.evaluateJavascript(BARS_AUTHENTICATED_ROUTES_SCRIPT) { result ->
        if (webView !== view || state.browserVisible ||
          navigationController !== controller ||
          !webFlow.summaryFetchAttempted || generation != webFlow.summaryRouteDiscoveryGeneration ||
          !BarsDocumentPolicy.isCurrent(view.url.orEmpty(), BARS_MARKS_URL)
        ) return@evaluateJavascript

        val discovery = BarsAuthenticatedRoutePolicy.parseJsResult(result)
        val routes = BarsAuthenticatedRoutePolicy.validatedRoutes(discovery)
        viewModel.logWebEvent(
          "Summary route discovery attempt=${attempt + 1} " +
            (discovery?.safeDiagnostics() ?: "result=unreadable") +
            " valid=${routes != null}",
        )
        if (routes == null) {
          if (attempt < 3) {
            // BARS renders parts of its navigation asynchronously on some
            // devices. Probe again without reloading or disturbing marks.
            val delayMs = when (attempt) {
              0 -> 450L
              1 -> 1_200L
              else -> 2_400L
            }
            view.postDelayed({ discover(attempt + 1) }, delayMs)
          } else {
            viewModel.logWebEvent("Summary route unavailable after DOM retries")
            webFlow.needsFullViewport = false
            viewModel.summaryCaptureFailed(
              "БАРС не предоставил ссылку на Сводку для текущего студента. " +
                "Откройте БАРС или повторите обновление.",
            )
          }
          return@evaluateJavascript
        }

        webFlow.officialMainUrl = routes.main
        viewModel.summaryCaptureStarted()
        viewModel.logWebEvent(
          "Summary official navigation resolved=true; opening student-scoped page",
        )
        controller.load(routes.summary, reason)
      }
    }
    discover(0)
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
          webFlow.redirectingToMarks = false
          webFlow.summaryFetchAttempted = false
          webFlow.summaryExtractionStarted = false
          webFlow.mainFrameFailed = false
          if (state.loginRequired) {
            viewModel.showBrowser()
          } else {
            webFlow.needsFullViewport = true
            marksReadiness?.reset()
            viewModel.startRefresh()
            retryNavigation?.invoke()
          }
        },
        onOpenBrowser = {
          marksReadiness?.navigationStarted()
          viewModel.showBrowser()
        },
        onRefreshSummary = {
          webFlow.manuallyRequestedConnection = true
          if (state.loginRequired) {
            viewModel.showBrowser()
          } else if (!state.isSummaryLoading) {
            val view = webView
            webFlow.summaryFetchAttempted = false
            webFlow.summaryExtractionStarted = false
            webFlow.mainFrameFailed = false
            if (view != null &&
              BarsDocumentPolicy.isCurrent(view.url.orEmpty(), BARS_MARKS_URL) &&
              !state.isLoading && navigationController != null
            ) {
              requestOfficialSummary(view, "manual Summary refresh")
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
      factory = { context ->
        WebView(context).also { view ->
          // Let Chromium own pointer events when the official browser is visible.
          // This listener never consumes events, including nested HTML scrolling.
          val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
          var touchDownX = 0f
          var touchDownY = 0f
          var verticalGestureLogged = false
          view.setOnTouchListener { touched, event ->
            if (!browserCanInteract) return@setOnTouchListener false
            when (event.actionMasked) {
              MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                verticalGestureLogged = false
                touched.parent?.requestDisallowInterceptTouchEvent(true)
              }
              MotionEvent.ACTION_MOVE -> {
                if (!verticalGestureLogged &&
                    kotlin.math.abs(event.y - touchDownY) > touchSlop &&
                    kotlin.math.abs(event.y - touchDownY) >
                      kotlin.math.abs(event.x - touchDownX)) {
                  verticalGestureLogged = true
                  viewModel.logWebEvent(
                    "vertical browser gesture enabled=${touched.isEnabled} " +
                      "width=${touched.width} height=${touched.height} " +
                      "scrollY=${view.scrollY} contentHeight=${view.contentHeight}"
                  )
                }
              }
              MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                touched.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
          }
          view.setOnFocusChangeListener { _, focused ->
            viewModel.logWebEvent(
              "browser focus changed focused=$focused browser=${state.browserVisible} " +
                "stage=${state.authStage}"
            )
          }
          var previousWidth = -1
          var previousHeight = -1
          view.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            val width = right - left
            val height = bottom - top
            if (width != previousWidth || height != previousHeight) {
              previousWidth = width
              previousHeight = height
              viewModel.logWebEvent(
                "browser layout width=$width height=$height " +
                  "browser=${state.browserVisible} stage=${state.authStage}"
              )
            }
          }
          webView = view
          val hasBarsCookies =
            !CookieManager.getInstance().getCookie(BARS_BASE_URL).isNullOrBlank()
          val provider = WebView.getCurrentWebViewPackage()
          viewModel.logWebEvent(
            "webview created cookiesPresent=$hasBarsCookies " +
              "provider=${provider?.packageName ?: "unknown"} " +
              "providerVersion=${provider?.versionName ?: "unknown"} " +
              "browser=${state.browserVisible} stage=${state.authStage}",
          )
          viewModel.networkSnapshot("webview-created")

          view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)

            // MpeiX used the same workaround for BARS. Some server-side flows
            // distinguish the Android WebView token from a regular mobile browser.
            userAgentString = userAgentString.replace("; wv", "")
          }

          CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(view, false)
          }
          var subresourceErrorCount = 0
          val controller = BarsNavigationController(
            view = view,
            log = viewModel::logWebEvent,
            probe = viewModel::probeNetwork,
            onTimeout = {
              if (!state.browserVisible &&
                webFlow.summaryFetchAttempted && state.isSummaryLoading
              ) {
                webFlow.summaryExtractionStarted = false
                viewModel.summaryCaptureFailed(
                  "Не удалось загрузить Сводку: сервер БАРС не ответил.",
                )
                // A Summary failure should not erase successfully cached marks.
                restoreOfficialMarks("restore marks after Summary timeout")
              } else if (!state.browserVisible) {
                webFlow.needsFullViewport = false
                viewModel.extractionFailed(
                  "Тайм-аут подключения к БАРС. Сессия не сброшена. " +
                    "Проверьте bars.mpei.ru в браузере или попробуйте другую сеть.",
                )
              }
            },
          )
          navigationController = controller

          fun loadBarsWithWatchdog(
            targetUrl: String,
            reason: String,
            fallbackUrl: String? = null,
          ) {
            extractionGate.invalidate()
            controller.load(targetUrl, reason, fallbackUrl)
          }

          val readiness = BarsMarksReadiness(
            schedule = { callback, delayMs -> view.postDelayed(callback, delayMs) },
            cancel = { callback -> view.removeCallbacks(callback) },
            onRecover = {
              viewModel.logWebEvent(
                "marks DOM timeout; bootstrapping through BARS root",
              )
              loadBarsWithWatchdog(
                targetUrl = BARS_LOGIN_URL,
                reason = "marks DOM recovery",
              )
            },
            onFailure = {
              webFlow.needsFullViewport = false
              view.evaluateJavascript(BARS_STOP_STATE_OBSERVER_SCRIPT, null)
              viewModel.extractionFailed(
                "БАРС открыл страницу оценок, но таблица не загрузилась.",
              )
            },
          )
          marksReadiness = readiness

          // The Retry action must use this same timeout-protected
          // navigation path rather than calling WebView.loadUrl directly.
          retryNavigation = {
            if (webView === view) {
              loadBarsWithWatchdog(
                targetUrl = BARS_LOGIN_URL,
                reason = "manual retry",
                fallbackUrl = null,
              )
            }
          }

          // Grade extraction is read-only, including during interactive WebView login.
          // It must not open Summary or otherwise take over the user's browser.
          fun startMarksExtraction(page: BarsPageState, source: String) {
            if (!shouldExtractBarsMarks(page, webFlow.marksExtractionStarted)) return
            webFlow.marksExtractionStarted = true
            viewModel.logWebEvent("marks extraction started source=$source")
            // Interactive browsing can navigate away before the JS result;
            // do not leave native grades stuck in a loading state in that case.
            if (source == "native") viewModel.authenticatedPage(page.url)
            val extractionId = extractionGate.begin()
            view.evaluateJavascript(
              BARS_EXTRACT_SCRIPT.replace(
                BARS_EXTRACT_TOKEN_PLACEHOLDER,
                extractionId.toString(),
              ),
              null,
            )
          }

          view.addJavascriptInterface(
            BarsJavascriptBridge(
              onSummaryDiagnostic = { safeMessage ->
                viewModel.logWebEvent("Summary DOM " + safeMessage.take(180))
              },
              onPageState = { json ->
                view.post {
                  if (webView !== view) return@post
                  val page = runCatching {
                    BarsPayloadParser.parsePageState(json)
                  }.getOrElse { error ->
                    viewModel.extractionFailed(
                      error.message ?: "Не удалось определить состояние БАРС",
                    )
                    return@post
                  }

                  // stopLoading() can leave Chromium displaying a blank
                  // internal document. Its JS bridge must not transition
                  // native BARS state or overwrite the original failure.
                  if (!isTrustedBarsDocument(page.url)) {
                    val location = safeBarsLocation(page.url)
                    val ignoredState = "ignored invalid document $location"
                    if (webFlow.lastPageStateSignature != ignoredState) {
                      webFlow.lastPageStateSignature = ignoredState
                      viewModel.logWebEvent(ignoredState)
                    }
                    // An old callback may arrive after the fallback
                    // has navigated to a legitimate new document.
                    // Do not stop the new document's observer.
                    if (!isTrustedBarsDocument(view.url.orEmpty())) {
                      view.evaluateJavascript(
                        BARS_STOP_STATE_OBSERVER_SCRIPT,
                        null,
                      )
                    }
                    return@post
                  }

                  // Discard callbacks sent by an old page after a redirect.
                  val currentWebLocation = view.url.orEmpty()
                  if (
                    !isTrustedBarsDocument(currentWebLocation) ||
                    safeBarsLocation(page.url) !=
                      safeBarsLocation(currentWebLocation)
                  ) {
                    viewModel.logWebEvent(
                      "ignored stale page state path=${safeBarsLocation(page.url)}",
                    )
                    return@post
                  }

                  viewModel.updateSessionUrl(page.url)

                  val currentLocation = safeBarsLocation(page.url)
                  val marksLocation = safeBarsLocation(BARS_MARKS_URL)
                  val signature =
                    "$currentLocation|${page.isLoginPage}|${page.isAuthFlow}|${page.isStudentList}|${page.isMarksPage}|${page.isSummaryReady}"
                  if (signature != webFlow.lastPageStateSignature) {
                    webFlow.lastPageStateSignature = signature
                    viewModel.logWebEvent(
                      "pageState path=$currentLocation login=${page.isLoginPage} authFlow=${page.isAuthFlow} studentList=${page.isStudentList} marks=${page.isMarksPage} summary=${page.isSummaryPage} ready=${page.isSummaryReady}",
                    )
                  }

                  if (state.browserVisible) {
                    readiness.navigationStarted()
                    if (shouldExtractBarsMarks(page, webFlow.marksExtractionStarted)) {
                      // A user can sign in or change students without closing WebView.
                      startMarksExtraction(page, "interactive-webview")
                    }
                    // Keep interactive navigation entirely under user control.
                    viewModel.logWebEvent(
                      "browser mode; leaving navigation untouched path=$currentLocation",
                    )
                    return@post
                  }

                  // Summary has independent AJAX-populated grade and attendance tables.
                  if (page.isSummaryPage && webFlow.summaryFetchAttempted) {
                    if (page.isSummaryReady && !webFlow.summaryExtractionStarted) {
                      webFlow.summaryExtractionStarted = true
                      viewModel.logWebEvent("summary tables ready")
                      view.evaluateJavascript(BARS_SUMMARY_RAW_SCRIPT, null)
                    }
                    return@post
                  }

                  when (classifyBarsPage(page)) {
                    BarsPageKind.MARKS_READY -> {
                      webFlow.redirectingToMarks = false
                      readiness.reset()
                      if (webFlow.summaryFetchAttempted && state.isSummaryLoading) {
                        // Redirected back to Main without a Summary payload.
                        viewModel.summaryCaptureFailed(
                          "Сводка не вернула данные. Нажмите «Обновить», чтобы повторить.",
                        )
                      }
                      startMarksExtraction(page, "native")
                    }
                    BarsPageKind.AUTH_FLOW -> {
                      webFlow.redirectingToMarks = false
                      readiness.reset()
                      if (webFlow.summaryFetchAttempted && state.isSummaryLoading) {
                        viewModel.summaryCaptureFailed(
                          "Для загрузки Сводки требуется повторный вход в БАРС.",
                        )
                      }
                      webFlow.officialMainUrl = null
                      viewModel.webAuth(page.url)
                    }
                    BarsPageKind.MARKS_LOADING -> {
                      webFlow.redirectingToMarks = false
                      viewModel.waitingForMarks(page.url)
                      if (readiness.waitForMarks()) {
                        viewModel.logWebEvent(
                          "marks route loaded; waiting for marks DOM",
                        )
                      }
                    }
                    BarsPageKind.OTHER_BARS_PAGE -> {
                      readiness.navigationStarted()
                      if (!webFlow.redirectingToMarks) {
                        webFlow.redirectingToMarks = true
                        viewModel.waitingForMarks(page.url)
                        viewModel.logWebEvent(
                          "authenticated BARS root; opening marks route",
                        )
                        loadBarsWithWatchdog(
                          targetUrl = BARS_MARKS_URL,
                          reason = "open marks route",
                          fallbackUrl = BARS_LOGIN_URL,
                        )
                      }
                    }
                    BarsPageKind.UNTRUSTED -> {
                      viewModel.logWebEvent("ignored untrusted page state")
                    }
                  }
                }
              },
              onData = { token, json ->
                view.post {
                  if (webView !== view) return@post
                  if (!extractionGate.consume(token) ||
                    !BarsDocumentPolicy.isCurrent(
                      view.url.orEmpty(), BARS_MARKS_URL,
                    ) || webFlow.mainFrameFailed
                  ) {
                    viewModel.logWebEvent("ignored stale extraction result")
                    return@post
                  }
                  // Keep the offscreen WebView at full layout size until the
                  // Main -> Summary route has been resolved. On the return
                  // trip, marks are already cached and it can shrink again.
                  if (webFlow.summaryFetchAttempted && !state.isSummaryLoading) {
                    webFlow.needsFullViewport = false
                  }
                  readiness.reset()
                  viewModel.logWebEvent(
                    "extraction bridge returned data browser=${state.browserVisible}",
                  )
                  CookieManager.getInstance().flush()
                  viewModel.extractionReceived(json)
                  // Extract Main before visiting Summary. The flag prevents a
                  // second trip when the WebView returns to Main.
                  view.evaluateJavascript(BARS_MAIN_RAW_SCRIPT, null)
                }
              },
              onRawSnapshot = { source, raw ->
                view.post {
                  if (webView !== view || webFlow.mainFrameFailed ||
                    !BarsDocumentPolicy.isTrusted(view.url.orEmpty()) ||
                    (state.browserVisible && source != "MAIN")) return@post
                  // While browsing, collect Main data but never initiate or
                  // complete automatic Summary navigation on the user's behalf.
                  val valid = when (source) {
                    "MAIN" -> BarsDocumentPolicy.isCurrent(view.url.orEmpty(), BARS_MARKS_URL)
                    "SUMMARY" -> BarsDocumentPolicy.isCurrent(view.url.orEmpty(), BARS_SUMMARY_URL)
                    else -> false
                  }
                  if (!valid) return@post
                  viewModel.rawSnapshotReceived(source, raw)
                  if (source == "MAIN" && !webFlow.summaryFetchAttempted &&
                    !state.browserVisible
                  ) {
                    viewModel.logWebEvent("automatic Summary capture: resolving official link")
                    requestOfficialSummary(view, "automatic Summary capture")
                  }
                  if (source == "SUMMARY" && webFlow.summaryFetchAttempted &&
                    !state.browserVisible) {
                    viewModel.logWebEvent("summary captured; restoring marks")
                    restoreOfficialMarks("restore marks after Summary")
                  }
                }
              },
              onExtractionError = { token, message ->
                view.post {
                  if (webView !== view || !extractionGate.consume(token)) {
                    return@post
                  }
                  if (!BarsDocumentPolicy.isCurrent(
                      view.url.orEmpty(), BARS_MARKS_URL,
                    ) || webFlow.mainFrameFailed
                  ) return@post
                  webFlow.needsFullViewport = false
                  readiness.navigationStarted()
                  viewModel.logWebEvent("extraction error=$message")
                  viewModel.extractionFailed(message)
                }
              },
              onError = { message ->
                view.post {
                  if (webView !== view) return@post
                  // Errors from an obsolete page observer must not
                  // overwrite successfully cached marks.
                  viewModel.logWebEvent("javascript page observer error")
                  if (state.isLoading) {
                    webFlow.needsFullViewport = false
                    readiness.navigationStarted()
                    viewModel.extractionFailed(message)
                  }
                }
              },
            ),
            BARS_JS_INTERFACE,
          )

          view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
              val bucket = when {
                newProgress >= 100 -> 100
                newProgress >= 75 -> 75
                newProgress >= 50 -> 50
                newProgress >= 25 -> 25
                else -> 0
              }
              if (controller.recordProgress(bucket)) {
                viewModel.logWebEvent(
                  "progress=$newProgress bucket=$bucket " +
                    "path=${safeBarsLocation(view.url.orEmpty())} " +
                    "elapsedMs=${SystemClock.elapsedRealtime() - controller.lastLoadStartedAt}",
                )
              }
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
              if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR ||
                consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.WARNING
              ) {
                // Never write the console text: it can include private page data.
                viewModel.logWebEvent(
                  "console level=${consoleMessage.messageLevel()} " +
                    "source=${safeBarsLocation(consoleMessage.sourceId())} " +
                    "line=${consoleMessage.lineNumber()} message=redacted",
                )
              }
              return true
            }
          }

          view.webViewClient = object : WebViewClient() {
            override fun onPageStarted(
              view: WebView,
              url: String,
              favicon: Bitmap?,
            ) {
              if (webView !== view) return
              webFlow.marksExtractionStarted = false
              extractionGate.invalidate()
              readiness.navigationStarted()
              if (isTrustedBarsDocument(url)) webFlow.mainFrameFailed = false
              controller.onStarted(url)
              webFlow.needsFullViewport = true
              viewModel.updateSessionUrl(url)
              viewModel.logWebEvent(
                "page started id=${controller.requestId} path=${safeBarsLocation(url)} " +
                  "browser=${state.browserVisible} progress=${view.progress}",
              )
            }

            override fun onPageFinished(view: WebView, url: String) {
              if (webView !== view) return
              viewModel.updateSessionUrl(url)
              viewModel.logWebEvent(
                "page finished id=${controller.requestId} " +
                  "path=${safeBarsLocation(url)} " +
                  "elapsedMs=${SystemClock.elapsedRealtime() - controller.lastLoadStartedAt} " +
                  "progress=${view.progress} currentPath=${safeBarsLocation(view.url.orEmpty())}",
              )

              if (
                webFlow.mainFrameFailed ||
                !isTrustedBarsDocument(url)
              ) {
                viewModel.logWebEvent(
                  "ignoring failed or internal WebView document",
                )
                return
              }

              if (!controller.onFinished(url)) {
                viewModel.logWebEvent("ignoring stale page completion")
                return
              }
              view.evaluateJavascript(BARS_PAGE_STATE_SCRIPT, null)
              if (BarsDocumentPolicy.isCurrent(url, BARS_SUMMARY_URL) &&
                webFlow.summaryFetchAttempted
              ) {
                view.evaluateJavascript(BARS_SUMMARY_DIAGNOSTICS_SCRIPT, null)
                val deadlineNavigationId = controller.requestId
                // Starting the extraction script is not proof of success.
                // If its bridge never returns, restore the native screen too.
                view.postDelayed({
                  if (webView === view &&
                    BarsDocumentPolicy.isCurrent(view.url.orEmpty(), BARS_SUMMARY_URL) &&
                    webFlow.summaryFetchAttempted && state.isSummaryLoading &&
                    controller.requestId == deadlineNavigationId && !state.browserVisible
                  ) {
                    view.evaluateJavascript(BARS_SUMMARY_DIAGNOSTICS_SCRIPT, null)
                    webFlow.summaryExtractionStarted = false
                    viewModel.summaryCaptureFailed(
                      "Таблицы Сводки не загрузились. Можно повторить.",
                    )
                    viewModel.logWebEvent("Summary AJAX deadline; restoring marks")
                    restoreOfficialMarks("restore marks after Summary AJAX timeout")
                  }
                }, 20_000L)
              }
            }

            override fun onPageCommitVisible(view: WebView, url: String) {
              viewModel.logWebEvent(
                "page committed path=${safeBarsLocation(url)} progress=${view.progress}",
              )
            }

            override fun doUpdateVisitedHistory(
              view: WebView,
              url: String,
              isReload: Boolean,
            ) {
              viewModel.logWebEvent(
                "navigation history path=${safeBarsLocation(url)} reload=$isReload",
              )
            }

            override fun onReceivedHttpError(
              view: WebView,
              request: WebResourceRequest,
              errorResponse: WebResourceResponse,
            ) {
              if (request.isForMainFrame) {
                val code = errorResponse.statusCode
                viewModel.logWebEvent(
                  "main-frame HTTP error status=$code " +
                    "path=${safeBarsLocation(request.url.toString())}",
                )
                viewModel.probeNetwork("main-http-$code")
                if (code >= 400 && !webFlow.mainFrameFailed) {
                  readiness.navigationStarted()
                  webFlow.mainFrameFailed = true
                  val retryScheduled = controller.onMainFrameFailure(
                    url = request.url.toString(),
                    retryable = code in 500..599,
                  )
                  if (!retryScheduled && !state.browserVisible) {
                    if (webFlow.summaryFetchAttempted &&
                      BarsDocumentPolicy.isCurrent(request.url.toString(), BARS_SUMMARY_URL)
                    ) {
                      viewModel.summaryCaptureFailed(
                        "Сводка БАРС вернула ошибку HTTP $code.",
                      )
                      view.post {
                        if (webView === view) {
                          restoreOfficialMarks("restore marks after Summary HTTP failure")
                        }
                      }
                    } else {
                      webFlow.needsFullViewport = false
                      viewModel.extractionFailed(
                        "Сервер БАРС вернул ошибку HTTP $code. Повторите позже.",
                      )
                    }
                  }
                }
              }
            }

            override fun onReceivedSslError(
              view: WebView,
              handler: SslErrorHandler,
              error: SslError,
            ) {
              viewModel.logWebEvent(
                "SSL error primary=${error.primaryError} " +
                  "path=${safeBarsLocation(error.url.orEmpty())}",
              )
              viewModel.probeNetwork("webview-ssl-error")
              handler.cancel()
            }

            override fun onReceivedError(
              view: WebView,
              request: WebResourceRequest,
              error: WebResourceError,
            ) {
              if (webView !== view) return
              if (!request.isForMainFrame) {
                if (subresourceErrorCount++ < 5) {
                  viewModel.logWebEvent(
                    "subresource error code=${error.errorCode} " +
                      "path=${safeBarsLocation(request.url.toString())}",
                  )
                }
                return
              }

              if (webFlow.mainFrameFailed) return
              readiness.navigationStarted()
              webFlow.mainFrameFailed = true
              view.evaluateJavascript(
                BARS_STOP_STATE_OBSERVER_SCRIPT,
                null,
              )

              val failedUrl = request.url.toString()
              val canRetry = controller.onMainFrameFailure(
                url = failedUrl,
                retryable = isRetryableBarsWebError(error.errorCode),
              )
              viewModel.logWebEvent(
                "main-frame error path=" + safeBarsLocation(failedUrl) +
                  " code=" + error.errorCode + " retry=" + canRetry +
                  " progress=" + view.progress,
              )
              viewModel.probeNetwork("webview-error-" + error.errorCode)
              if (canRetry) return

              if (!state.browserVisible) {
                if (webFlow.summaryFetchAttempted &&
                  BarsDocumentPolicy.isCurrent(failedUrl, BARS_SUMMARY_URL)
                ) {
                  viewModel.summaryCaptureFailed(
                    "Не удалось загрузить Сводку (ошибка сети ${error.errorCode}).",
                  )
                  view.post {
                    if (webView === view) {
                      restoreOfficialMarks("restore marks after Summary network failure")
                    }
                  }
                } else {
                  webFlow.needsFullViewport = false
                  viewModel.extractionFailed(
                    if (error.errorCode == WebViewClient.ERROR_TIMEOUT) {
                      "Сервер БАРС не ответил (тайм-аут сети). " +
                        "Сессия сохранена. Попробуйте Wi-Fi или откройте сайт в браузере."
                    } else {
                      "Не удалось открыть БАРС: ${error.description}. Нажмите «Повторить»."
                    },
                  )
                }
              }
            }

            override fun shouldOverrideUrlLoading(
              view: WebView,
              request: WebResourceRequest,
            ): Boolean {
              val uri = request.url
              if (request.isForMainFrame) {
                viewModel.logWebEvent(
                  "navigation requested path=${safeBarsLocation(uri.toString())} " +
                    "gesture=${request.hasGesture()} main=true",
                )
              }
              if (uri.scheme == "https" && uri.host == "bars.mpei.ru") {
                return false
              }

              return runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                true
              }.getOrDefault(true)
            }
          }

          // A cookie may be stale, and a timed-out root URL
          // cannot be repaired by requesting a second protected route.
          viewModel.logWebEvent(
            "initial load path=${safeBarsLocation(BARS_LOGIN_URL)}",
          )
          loadBarsWithWatchdog(
            targetUrl = BARS_LOGIN_URL,
            reason = "initial session check",
            fallbackUrl = null,
          )
        }
      },
      update = { view ->
        webView = view
        // Keep Chromium enabled when moving between background sync and
        // interactive browsing. Toggling View.isEnabled can leave scroll
        // gestures unresponsive. Hidden WebViews stay behind native content
        // or offscreen at Home, so they cannot intercept native input.
      },
        onRelease = { view ->
          viewModel.logWebEvent(
            "webview released stage=${state.authStage} browser=${state.browserVisible} " +
              "cacheFresh=${state.hasFreshCache()} loading=${state.isLoading} " +
              "path=${safeBarsLocation(view.url.orEmpty())} progress=${view.progress}",
          )
          if (webView === view) retryNavigation = null
          if (webView === view) {
            extractionGate.invalidate()
            marksReadiness?.dispose()
            marksReadiness = null
          }
          if (navigationController?.belongsTo(view) == true) {
            navigationController?.close()
            navigationController = null
          }
          // A WebView with an editable element can retain native input
          // focus while AndroidView is detached, reopening the IME.
          // Drop focus and make it ineligible for focus restoration.
          view.clearFocus()
          view.isFocusableInTouchMode = false
          view.isFocusable = false
          view.onFocusChangeListener = null
          view.setOnTouchListener(null)
          view.stopLoading()
          view.removeJavascriptInterface(BARS_JS_INTERFACE)
          view.webViewClient = WebViewClient()
          view.destroy()
          if (webView === view) webView = null
        },
      )
    }

  }
}

private fun safeBarsLocation(rawUrl: String): String =
  runCatching {
    val uri = Uri.parse(rawUrl)
    "${uri.host.orEmpty()}${uri.path.orEmpty()}"
  }.getOrDefault("unparseable")

private fun isTrustedBarsDocument(url: String): Boolean =
  BarsDocumentPolicy.isTrusted(url)

private fun isRetryableBarsWebError(errorCode: Int): Boolean =
  errorCode == WebViewClient.ERROR_TIMEOUT ||
    errorCode == WebViewClient.ERROR_CONNECT ||
    errorCode == WebViewClient.ERROR_HOST_LOOKUP ||
    errorCode == WebViewClient.ERROR_UNKNOWN

