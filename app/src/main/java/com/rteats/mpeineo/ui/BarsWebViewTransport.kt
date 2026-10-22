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
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * Owns the actual Android BARS WebView and all transport callbacks. This
 * instance survives native Home/BARS navigation with its cookies and 2FA.
 *
 * The Compose screen owns presentation and invokes commands; BARS-specific
 * authentication, extraction, navigation watchdogs, JS bridges and disposal
 * live here. A fresh WebView is created only when AndroidView.factory runs.
 */
internal class BarsWebViewTransport(private val viewModel: BarsViewModel) {
  var webView by mutableStateOf<WebView?>(null)
    private set
  var navigationController by mutableStateOf<BarsNavigationController?>(null)
    private set
  var marksReadiness by mutableStateOf<BarsMarksReadiness?>(null)
    private set
  var retryNavigation by mutableStateOf<(() -> Unit)?>(null)
    private set

  val webFlow = BarsWebFlowState()
  val session = BarsSessionCoordinator()
  val extractionGate = BarsExtractionGate()
  val autoRefreshGate = BarsAutoRefreshGate()

  /** Updated from Compose on each render without capturing stale UI state. */
  var browserCanInteract: Boolean = false

  private fun liveState(): BarsUiState = viewModel.state.value

  fun interruptAutomatedCapture(reason: String) {
    val interrupted = session.enterInteractiveBrowser()
    navigationController?.cancelAutomaticNavigation()
    extractionGate.invalidate()
    marksReadiness?.navigationStarted()
    if (interrupted || liveState().isSummaryLoading) {
      viewModel.summaryCaptureCancelled()
      viewModel.logWebEvent("automated Summary cancelled reason=$reason")
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
    if (webView !== view || liveState().browserVisible ||
      !BarsDocumentPolicy.isCurrent(view.url.orEmpty(), BARS_MARKS_URL)
    ) return

    // Route discovery is asynchronous. Keep the authenticated document laid
    // out at its normal size until discovery/navigation is finished: reducing
    // Chromium to 1dp can remove or postpone responsive menu markup.
    webFlow.needsFullViewport = true
    val generation = session.startSummaryDiscovery()

    fun discover(attempt: Int) {
      if (webView !== view || navigationController !== controller ||
        !session.canNavigateToSummary(
          generation, liveState().browserVisible,
          BarsDocumentPolicy.isCurrent(view.url.orEmpty(), BARS_MARKS_URL),
        )
      ) return

      view.evaluateJavascript(BARS_AUTHENTICATED_ROUTES_SCRIPT) { result ->
        if (webView !== view || navigationController !== controller ||
          !session.canNavigateToSummary(
            generation, liveState().browserVisible,
            BarsDocumentPolicy.isCurrent(view.url.orEmpty(), BARS_MARKS_URL),
          )
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


  @SuppressLint("SetJavaScriptEnabled")
  fun create(context: Context): WebView {
    return WebView(context).also { view ->
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
              "browser focus changed focused=$focused browser=${liveState().browserVisible} " +
                "stage=${liveState().authStage}"
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
                  "browser=${liveState().browserVisible} stage=${liveState().authStage}"
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
              "browser=${liveState().browserVisible} stage=${liveState().authStage}",
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
              if (!liveState().browserVisible &&
                session.summaryFetchAttempted && liveState().isSummaryLoading
              ) {
                session.retrySummaryExtraction()
                viewModel.summaryCaptureFailed(
                  "Не удалось загрузить Сводку: сервер БАРС не ответил.",
                )
                // A Summary failure should not erase successfully cached marks.
                restoreOfficialMarks("restore marks after Summary timeout")
              } else if (!liveState().browserVisible) {
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
            if (!session.beginMarksExtraction(page)) return
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

                  if (liveState().browserVisible) {
                    readiness.navigationStarted()
                    // A user can sign in or change students without closing WebView.
                    startMarksExtraction(page, "interactive-webview")
                    // Keep interactive navigation entirely under user control.
                    viewModel.logWebEvent(
                      "browser mode; leaving navigation untouched path=$currentLocation",
                    )
                    return@post
                  }

                  // Summary has independent AJAX-populated grade and attendance tables.
                  if (page.isSummaryPage && session.summaryFetchAttempted) {
                    if (session.beginSummaryExtraction(page.isSummaryReady)) {
                      viewModel.logWebEvent("summary tables ready")
                      view.evaluateJavascript(BARS_SUMMARY_RAW_SCRIPT, null)
                    }
                    return@post
                  }

                  when (classifyBarsPage(page)) {
                    BarsPageKind.MARKS_READY -> {
                      session.onMarksRouteObserved()
                      readiness.reset()
                      if (session.summaryFetchAttempted && liveState().isSummaryLoading) {
                        // Redirected back to Main without a Summary payload.
                        viewModel.summaryCaptureFailed(
                          "Сводка не вернула данные. Нажмите «Обновить», чтобы повторить.",
                        )
                      }
                      startMarksExtraction(page, "native")
                    }
                    BarsPageKind.AUTH_FLOW -> {
                      session.onMarksRouteObserved()
                      readiness.reset()
                      if (session.summaryFetchAttempted && liveState().isSummaryLoading) {
                        viewModel.summaryCaptureFailed(
                          "Для загрузки Сводки требуется повторный вход в БАРС.",
                        )
                      }
                      webFlow.officialMainUrl = null
                      viewModel.webAuth(page.url)
                    }
                    BarsPageKind.MARKS_LOADING -> {
                      session.onMarksRouteObserved()
                      viewModel.waitingForMarks(page.url)
                      if (readiness.waitForMarks()) {
                        viewModel.logWebEvent(
                          "marks route loaded; waiting for marks DOM",
                        )
                      }
                    }
                    BarsPageKind.OTHER_BARS_PAGE -> {
                      readiness.navigationStarted()
                      if (session.beginRedirectToMarks()) {
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
                  if (session.summaryFetchAttempted && !liveState().isSummaryLoading) {
                    webFlow.needsFullViewport = false
                  }
                  readiness.reset()
                  viewModel.logWebEvent(
                    "extraction bridge returned data browser=${liveState().browserVisible}",
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
                    (liveState().browserVisible && source != "MAIN")) return@post
                  // While browsing, collect Main data but never initiate or
                  // complete automatic Summary navigation on the user's behalf.
                  val valid = when (source) {
                    "MAIN" -> BarsDocumentPolicy.isCurrent(view.url.orEmpty(), BARS_MARKS_URL)
                    "SUMMARY" -> BarsDocumentPolicy.isCurrent(view.url.orEmpty(), BARS_SUMMARY_URL)
                    else -> false
                  }
                  if (!valid) return@post
                  if (source == "SUMMARY" &&
                    !session.canAcceptSummaryPayload(liveState().browserVisible)
                  ) {
                    viewModel.logWebEvent("ignored obsolete Summary payload")
                    return@post
                  }
                  viewModel.rawSnapshotReceived(source, raw)
                  if (source == "MAIN" && !session.summaryFetchAttempted &&
                    !liveState().browserVisible
                  ) {
                    viewModel.logWebEvent("automatic Summary capture: resolving official link")
                    requestOfficialSummary(view, "automatic Summary capture")
                  }
                  if (source == "SUMMARY" && session.summaryFetchAttempted &&
                    !liveState().browserVisible) {
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
                  if (liveState().isLoading) {
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
              session.onDocumentStarted()
              extractionGate.invalidate()
              readiness.navigationStarted()
              if (isTrustedBarsDocument(url)) webFlow.mainFrameFailed = false
              controller.onStarted(url)
              webFlow.needsFullViewport = true
              viewModel.updateSessionUrl(url)
              viewModel.logWebEvent(
                "page started id=${controller.requestId} path=${safeBarsLocation(url)} " +
                  "browser=${liveState().browserVisible} progress=${view.progress}",
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
                session.summaryFetchAttempted
              ) {
                view.evaluateJavascript(BARS_SUMMARY_DIAGNOSTICS_SCRIPT, null)
                val deadlineNavigationId = controller.requestId
                // Starting the extraction script is not proof of success.
                // If its bridge never returns, restore the native screen too.
                view.postDelayed({
                  if (webView === view &&
                    BarsDocumentPolicy.isCurrent(view.url.orEmpty(), BARS_SUMMARY_URL) &&
                    session.summaryFetchAttempted && liveState().isSummaryLoading &&
                    controller.requestId == deadlineNavigationId && !liveState().browserVisible
                  ) {
                    view.evaluateJavascript(BARS_SUMMARY_DIAGNOSTICS_SCRIPT, null)
                    session.retrySummaryExtraction()
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
                  if (!retryScheduled && !liveState().browserVisible) {
                    if (session.summaryFetchAttempted &&
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

              if (!liveState().browserVisible) {
                if (session.summaryFetchAttempted &&
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
  }

  /** Called once when AndroidView releases its owned browser instance. */
  fun release(view: WebView) {
          viewModel.logWebEvent(
            "webview released stage=${liveState().authStage} browser=${liveState().browserVisible} " +
              "cacheFresh=${liveState().hasFreshCache()} loading=${liveState().isLoading} " +
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


