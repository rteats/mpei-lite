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
import androidx.compose.material3.OutlinedTextField
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
 * Native BARS Compose presentation. WebView lifetime/navigation remain in BarsScreen.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BarsCheckingState(
  error: String?,
  onRetry: () -> Unit,
) {
  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier.padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (error == null) {
        ContainedLoadingIndicator()
        Text(
          "Проверяем сессию БАРС…",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        ServiceStatus(
          title = "Не удалось подключиться к БАРС",
          detail = error,
          tone = ServiceStatusTone.ERROR,
          actionLabel = "Повторить",
          onAction = onRetry,
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BarsNativeDashboard(
  state: BarsUiState,
  active: Boolean,
  onDetailOpenChange: (Boolean) -> Unit,
  openDisciplineName: String?,
  openDisciplineRequest: Int,
  onOpenDisciplineConsumed: () -> Unit,
  showCachedRefreshErrors: Boolean,
  onRefresh: () -> Unit,
  onOpenBrowser: () -> Unit,
  onAcknowledgeGrades: (String) -> Unit,
  onRefreshSummary: () -> Unit,
) {
  var selectedDisciplineName by rememberSaveable { mutableStateOf<String?>(null) }
  var selectedSection by rememberSaveable { mutableStateOf("subjects") }
  var disciplineQuery by rememberSaveable { mutableStateOf("") }
  var disciplineSearchOpen by rememberSaveable { mutableStateOf(false) }
  // Saveable across detail navigation and Activity recreation. The key includes
  // actual BARS grades/weights so a new mark invalidates stale predictions.
  var autoScenarios by rememberSaveable {
    mutableStateOf<Map<String, String>>(emptyMap())
  }
  val report = remember(state.rawSummary) { BarsReportParser.parseSummary(state.rawSummary) }
  val controls = remember(state.disciplines) { state.controlSchedule }
  val gradeReportScroll = rememberScrollState()
  val attendanceReportScroll = rememberScrollState()
  val dashboardListState = rememberLazyListState()
  val selectedDiscipline = state.disciplines.firstOrNull {
    it.disciplineName == selectedDisciplineName
  }
  val motion = MaterialTheme.motionScheme

  androidx.compose.runtime.LaunchedEffect(openDisciplineRequest, openDisciplineName,
      state.disciplines, active) {
    if (active && openDisciplineName != null &&
        state.disciplines.any { it.disciplineName == openDisciplineName }) {
      selectedDisciplineName = openDisciplineName
      onOpenDisciplineConsumed()
    }
  }

  androidx.compose.runtime.SideEffect {
    onDetailOpenChange(selectedDiscipline != null)
  }

  // Android Back returns to the overview before leaving the tab.
  // The official WebView keeps priority when the browser is visible.
  BackHandler(
    enabled = active && selectedDiscipline != null && !state.browserVisible,
  ) {
    selectedDisciplineName?.let(onAcknowledgeGrades)
    selectedDisciplineName = null
  }

  AnimatedContent(
    targetState = selectedDiscipline?.disciplineName,
    modifier = Modifier.fillMaxSize(),
    contentKey = { it ?: "bars-overview" },
    transitionSpec = {
      val opening = targetState != null
      (slideInHorizontally(
        initialOffsetX = { if (opening) it / 7 else -it / 7 },
        animationSpec = motion.defaultSpatialSpec(),
      ) + fadeIn(animationSpec = motion.defaultEffectsSpec())) togetherWith
        (slideOutHorizontally(
          targetOffsetX = { if (opening) -it / 7 else it / 7 },
          animationSpec = motion.defaultSpatialSpec(),
        ) + fadeOut(animationSpec = motion.defaultEffectsSpec()))
    },
    label = "bars-discipline",
  ) { expandedName ->
    val expandedDiscipline = state.disciplines.firstOrNull {
      it.disciplineName == expandedName
    }
    if (expandedDiscipline != null) {
      val planKey = autoScenarioKey(
        expandedDiscipline,
        account = state.profileName + "|" + state.profileGroup,
        semester = state.semester,
      )
      DisciplineExpandedCard(
        discipline = expandedDiscipline,
        changes = state.gradeChanges[expandedDiscipline.disciplineName].orEmpty(),
        savedAutoScenario = autoScenarios[planKey],
        onAutoScenarioChange = { encoded ->
          autoScenarios = (autoScenarios + (planKey to encoded))
            .toList().takeLast(32).toMap()
        },
        onCollapse = {
          onAcknowledgeGrades(expandedDiscipline.disciplineName)
          selectedDisciplineName = null
        },
      )
    } else {
      val refreshState = rememberPullToRefreshState()
      PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = onRefresh,
        state = refreshState,
        indicator = {
          PullToRefreshDefaults.LoadingIndicator(
            state = refreshState,
            isRefreshing = state.isLoading,
            modifier = Modifier.align(Alignment.TopCenter),
          )
        },
        modifier = Modifier.fillMaxSize(),
      ) {
        LazyColumn(
          state = dashboardListState,
          modifier = Modifier.fillMaxSize(),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(
            bottom = 112.dp,
          ),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (state.loginRequired) {
            item(key = "session-expired") {
              ServiceStatus(
                title = "Сессия БАРС истекла",
                detail = "Сохранённые оценки доступны.",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                tone = ServiceStatusTone.RECOVERY,
                actionLabel = "Войти",
                onAction = onOpenBrowser,
              )
            }
          }
          item(key = "profile") {
            BarsUserHeader(state = state, onOpenBrowser = onOpenBrowser)
          }

          state.error?.takeIf {
            state.disciplines.isEmpty() || showCachedRefreshErrors
          }?.let { message ->
            item(key = "error") {
              val hasCachedGrades = state.disciplines.isNotEmpty()
              ServiceStatus(
                title = if (hasCachedGrades) {
                  "Не удалось обновить БАРС"
                } else "Не удалось загрузить БАРС",
                detail = if (hasCachedGrades) "Показаны сохранённые оценки." else message,
                modifier = Modifier.padding(horizontal = 8.dp),
                tone = if (hasCachedGrades) {
                  ServiceStatusTone.RECOVERY
                } else ServiceStatusTone.ERROR,
                actionLabel = "Повторить",
                onAction = onRefresh,
              )
            }
          }

          if (state.isLoading && state.disciplines.isNotEmpty() &&
            showCachedRefreshErrors
          ) {
            item(key = "refreshing") {
              LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
                  .padding(horizontal = 10.dp),
              )
            }
          }

          item(key = "sections") {
            BarsReportChooser(
              selected = selectedSection,
              onSelected = { selectedSection = it },
            )
          }

          if (selectedSection != "subjects") {
            barsReportEntries(
              section = selectedSection,
              report = report,
              controls = controls,
              gradeScroll = gradeReportScroll,
              attendanceScroll = attendanceReportScroll,
              onOpenDiscipline = { selectedDisciplineName = it },
              isSummaryLoading = state.isSummaryLoading,
              summaryError = state.summaryError,
              onRetrySummary = onRefreshSummary,
            )
          } else if (state.disciplines.isEmpty() && !state.isLoading) {
            item(key = "empty") {
              EmptyBarsCard("В текущем семестре дисциплины не найдены.")
            }
          } else {
            if (state.disciplines.size >= 6) {
              item(key = "discipline-search") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                  if (!disciplineSearchOpen) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.End,
                    ) {
                      TextButton(onClick = { disciplineSearchOpen = true }) {
                        AppIcon(AppIcons.Search, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Найти дисциплину")
                      }
                    }
                  } else {
                    OutlinedTextField(
                      value = disciplineQuery,
                      onValueChange = { disciplineQuery = it },
                      singleLine = true,
                      label = { Text("Дисциплина или преподаватель") },
                      modifier = Modifier.fillMaxWidth().testTag("bars-discipline-search"),
                      trailingIcon = {
                        IconButton(onClick = {
                          disciplineQuery = ""
                          disciplineSearchOpen = false
                        }) { AppIcon(AppIcons.Back, "Закрыть поиск") }
                      },
                    )
                  }
                }
              }
            }
            if (disciplineQuery.isBlank()) item(key = "auto-focus") {
              BarsAutoFocus(
                disciplines = state.disciplines,
                onOpenDiscipline = { selectedDisciplineName = it },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              )
            }
            val matchingDisciplines = if (disciplineQuery.isBlank()) state.disciplines
              else state.disciplines.filter {
                it.disciplineName.contains(disciplineQuery.trim(), ignoreCase = true) ||
                  it.personName.contains(disciplineQuery.trim(), ignoreCase = true)
              }
            if (matchingDisciplines.isEmpty()) item(key = "discipline-search-empty") {
              EmptyBarsCard("Совпадений нет среди загруженных дисциплин.")
            }
            items(
              items = matchingDisciplines,
              key = { it.disciplineName },
            ) { discipline ->
              DisciplineCard(
                discipline = discipline,
                changes = state.gradeChanges[discipline.disciplineName].orEmpty(),
                onClick = {
                  selectedDisciplineName = discipline.disciplineName
                },
                modifier = Modifier.padding(horizontal = 8.dp)
                  .animateItem(),
              )
            }
          }
        }
      }
    }
  }
}

/**
* Expressive BARS dashboard: dominant profile surface + factual semester
* summary + approachable, interactive discipline cards.
*/
/** A quiet data-first profile summary, rather than unparsed BARS text chips. */
@Composable
private fun BarsUserHeader(
  state: BarsUiState,
  onOpenBrowser: () -> Unit,
) {
  var showMore by rememberSaveable { mutableStateOf(false) }
  val extras = remember(state.rawMain) { BarsReportParser.parseMain(state.rawMain) }
  val facts = remember(extras, state.studentDetails) {
    barsProfileFacts(extras, state.studentDetails)
  }
  val filtered = facts.filterNot {
    (it.kind == BarsProfileKind.GROUP && state.profileGroup.isNotBlank()) ||
      (it.kind == BarsProfileKind.SEMESTER && state.semester.isNotBlank())
  }
  val primary = filtered.filter { it.kind.featurePriority() < 100 }
    .sortedBy { it.kind.featurePriority() }.take(2)
  val secondary = filtered.filterNot { it in primary }
  val colors = MaterialTheme.colorScheme

  Column(
    modifier = Modifier.fillMaxWidth()
      .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          state.profileName.ifBlank { "Студент МЭИ" },
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.SemiBold,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        val identity = listOf(state.profileGroup, state.semester)
          .filter { it.isNotBlank() }.joinToString(" · ")
        if (identity.isNotBlank()) {
          Text(
            identity,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
          )
        }
      }
      PortalHeaderAction(label = "БАРС", onClick = onOpenBrowser)
    }

    if (primary.isNotEmpty()) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        primary.chunked(2).forEach { rowFacts ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            rowFacts.forEach { fact ->
              BarsProfileFactCard(
                fact = fact,
                modifier = Modifier.weight(1f),
              )
            }
            if (rowFacts.size == 1) Spacer(Modifier.weight(1f))
          }
        }
      }
    }

    val graded = state.disciplines.count {
      it.finalMarkValue != null || it.markValues.isNotEmpty()
    }
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        state.disciplines.size.toString() + " дисциплин · " +
          graded + " с оценками",
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.labelMedium,
        color = colors.onSurfaceVariant,
      )
      if (state.lastUpdatedAtMillis > 0) {
        Text(
          DateFormat.getTimeInstance(DateFormat.SHORT)
            .format(Date(state.lastUpdatedAtMillis)),
          style = MaterialTheme.typography.labelSmall,
          color = colors.onSurfaceVariant,
        )
      }
    }

    if (secondary.isNotEmpty()) {
      TextButton(
        onClick = { showMore = !showMore },
        modifier = Modifier.padding(start = 0.dp),
      ) {
        AppIcon(
          AppIcons.Person,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text("Сведения об обучении")
        Spacer(Modifier.width(4.dp))
        AppIcon(
          if (showMore) AppIcons.ExpandLess else AppIcons.ExpandMore,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
        )
      }
      AnimatedVisibility(visible = showMore) {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.extraLarge,
          color = colors.surfaceContainerLow,
        ) {
          Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            secondary.forEachIndexed { index, fact ->
              if (index != 0) HorizontalDivider(
                color = colors.outlineVariant.copy(alpha = 0.45f),
              )
              Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                AppIcon(
                  barsProfileFactIcon(fact.kind),
                  contentDescription = null,
                  tint = colors.onSurfaceVariant,
                  modifier = Modifier.size(20.dp),
                )
                Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                  Text(
                    fact.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                  )
                  Text(
                    fact.value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
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

@Composable
private fun BarsProfileFactCard(
  fact: BarsProfileFact,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  Surface(
    modifier = modifier.heightIn(min = 105.dp),
    shape = MaterialTheme.shapes.extraLarge,
    color = colors.surfaceContainerLow,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = colors.primaryContainer,
          contentColor = colors.onPrimaryContainer,
        ) {
          Box(
            modifier = Modifier.size(34.dp),
            contentAlignment = Alignment.Center,
          ) {
            AppIcon(
              barsProfileFactIcon(fact.kind),
              contentDescription = null,
              modifier = Modifier.size(20.dp),
            )
          }
        }
        Text(
          fact.label,
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.labelMedium,
          color = colors.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Text(
        fact.value,
        style = MaterialTheme.typography.titleMedium,
        color = colors.onSurface,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

private fun barsProfileFactIcon(kind: BarsProfileKind): Int = when (kind) {
  BarsProfileKind.COURSE -> AppIcons.Education
  BarsProfileKind.RATING -> AppIcons.Ranking
  BarsProfileKind.SCORE -> AppIcons.Analytics
  BarsProfileKind.FORM -> AppIcons.StudyForm
  BarsProfileKind.STATUS -> AppIcons.Verified
  BarsProfileKind.FACULTY, BarsProfileKind.INSTITUTE -> AppIcons.Faculty
  BarsProfileKind.PROGRAM -> AppIcons.Education
  BarsProfileKind.GROUP -> AppIcons.GroupList
  BarsProfileKind.SEMESTER -> AppIcons.Schedule
  BarsProfileKind.STUDENT_ID -> AppIcons.StudentId
  BarsProfileKind.OTHER -> AppIcons.Person
}

/**
* Scannable subject-first card: title, real final mark, quiet metadata,
* then raw grades. The final mark must not be confused with an average.
*/
@Composable
private fun DisciplineCard(
  discipline: BarsDiscipline,
  changes: List<BarsGradeChange>,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    onClick = onClick,
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.extraLarge,
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
      verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          discipline.disciplineName,
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        val finalMark = discipline.finalMarkValue
        if (finalMark != null) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
          ) {
            Text(
              "Итог",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GradeChip(formatBarsGrade(finalMark.toDouble()), finalMark)
          }
        } else {
          AppIcon(
            AppIcons.Forward,
            contentDescription = "Подробнее",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
          )
        }
      }
      val subtitle = listOf(
        discipline.assessmentType,
        discipline.personName,
      ).filter(String::isNotBlank).joinToString(" · ")
      if (subtitle.isNotBlank()) {
        Text(
          subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (changes.isNotEmpty()) {
        Text(gradeChangesCaption(changes),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary)
      }
      if (discipline.markValues.isEmpty()) {
        Text(
          "Оценок пока нет",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          discipline.markValues.take(8).forEach { mark ->
            GradeChip(formatBarsGrade(mark.toDouble()), mark)
          }
          if (discipline.markValues.size > 8) {
            NeutralChip("+" + (discipline.markValues.size - 8))
          }
        }
      }
    }
  }
}

/**
* Expanded discipline remains an in-place BARS card, not a navigation
* destination. Its title stays pinned and collapses the card on tap.
* Content can scroll within the card, without reaching other disciplines.
*/
/**
* Discipline detail stays inside the BARS tab, with a pinned return header.
* Grade data is shown before supplementary control/assessment information.
*/
@Composable
private fun DisciplineExpandedCard(
  discipline: BarsDiscipline,
  changes: List<BarsGradeChange>,
  savedAutoScenario: String?,
  onAutoScenarioChange: (String) -> Unit,
  onCollapse: () -> Unit,
) {
  val controls = discipline.activities.filter {
    it.type == BarsActivityType.CONTROL_ACTIVITY
  }
  val otherActivities = discipline.activities.filter {
    it.type != BarsActivityType.CONTROL_ACTIVITY
  }
  Column(
    modifier = Modifier.fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .padding(horizontal = 8.dp)
      .testTag("bars-discipline-expanded"),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Surface(
      onClick = onCollapse,
      modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
      shape = MaterialTheme.shapes.extraLarge,
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 17.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        AppIcon(
          AppIcons.Back,
          contentDescription = "К списку дисциплин",
          tint = MaterialTheme.colorScheme.primary,
        )
        Text(
          discipline.disciplineName,
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.SemiBold,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    Column(
      modifier = Modifier.fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 6.dp, vertical = 4.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // The next actionable decision is the 4.2 plan, not a raw grade dump.
      // Aggregate assessments are excluded by the established BARS rule.
      if (shouldShowGradeForecastForAssessment(discipline.assessmentType)) {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.extraLarge,
          color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
          BarsAutoPlanner(
            discipline = discipline,
            savedScenario = savedAutoScenario,
            onScenarioChange = onAutoScenarioChange,
            modifier = Modifier.padding(16.dp),
          )
        }
      }

      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          val subtitle = listOf(
            discipline.assessmentType,
            discipline.personName,
          ).filter(String::isNotBlank).joinToString(" · ")
          if (subtitle.isNotBlank()) {
            Text(
              subtitle,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Text(
            "Оценки",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          if (changes.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
              changes.forEach { change ->
                Text(change.activityName + ": " +
                  (change.before?.let { "$it → " } ?: "") + change.after,
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.primary)
              }
            }
          }
          if (controls.isEmpty()) {
            Text(
              "Контрольных мероприятий нет",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              controls.forEach { control ->
                val mark = control.markValue
                if (mark != null) {
                  GradeChip(formatBarsGrade(mark.toDouble()), mark)
                } else {
                  NeutralChip("—")
                }
              }
            }
          }
          discipline.finalMarkValue?.let { final ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                "Итог",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              GradeChip(formatBarsGrade(final.toDouble()), final)
            }
          }
          discipline.currentScoreValue?.let { score ->
            Text(
              "Текущий балл БАРС: " +
                formatBarsGrade(score.toDouble()),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            "Контрольные мероприятия",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          if (controls.isEmpty()) {
            Text(
              "Контрольных мероприятий нет",
              modifier = Modifier.padding(top = 10.dp),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            controls.forEachIndexed { index, activity ->
              ActivityDetailRow(activity)
              if (index < controls.lastIndex) {
                HorizontalDivider(
                  color = MaterialTheme.colorScheme.outlineVariant,
                )
              }
            }
          }
        }
      }

      if (otherActivities.isNotEmpty()) {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.extraLarge,
          color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
              "Дополнительные оценки",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
            )
            otherActivities.forEachIndexed { index, activity ->
              ActivityDetailRow(activity)
              if (index < otherActivities.lastIndex) {
                HorizontalDivider(
                  color = MaterialTheme.colorScheme.outlineVariant,
                )
              }
            }
          }
        }
      }

      // Keep the bottom content above the shared floating portal action.
      Spacer(Modifier.height(96.dp))
    }
  }
}

@Composable
private fun ActivityDetailRow(
  activity: BarsActivity,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      Text(
        text = activity.name?.takeIf { it.isNotBlank() }
          ?: "Контрольное мероприятие",
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
      )

      val details = buildList {
        activity.weight?.takeIf { it.isNotBlank() }?.let {
          add("Вес: $it")
        }
        activity.weekNum?.takeIf { it.isNotBlank() }?.let {
          add("Неделя: $it")
        }
        activity.markAndDate?.takeIf { it.isNotBlank() }?.let {
          add("Запись БАРС: $it")
        }
      }

      if (details.isNotEmpty()) {
        Text(
          text = details.joinToString(" • "),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    activity.markValue?.let { mark ->
      GradeChip(
        text = mark.roundToInt().toString(),
        mark = mark,
      )
    }
  }
}

@Composable
private fun FinalGradeRow(
  activity: BarsActivity,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = activity.name?.takeIf { it.isNotBlank() }
        ?: activity.type.displayName(),
      modifier = Modifier.weight(1f),
      style = MaterialTheme.typography.bodyLarge,
    )
    activity.markValue?.let { mark ->
      GradeChip(
        text = mark.roundToInt().toString(),
        mark = mark,
      )
    }
  }
}

@Composable
private fun AssessmentTypeChip(
  text: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
  ) {
    Text(
      text = text,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Medium,
    )
  }
}

private fun formatBarsGrade(mark: Double): String =
  if (mark % 1.0 == 0.0) mark.toInt().toString() else formatBarsWeightedSum(mark)

@Composable
private fun GradeChip(
  text: String,
  mark: Float,
) {
  val scheme = MaterialTheme.colorScheme
  val dark = isSystemInDarkTheme()
  val (container, content) = when (mark.roundToInt()) {
    4, 5 -> if (dark) {
      Color(0xFF1B5E20) to Color(0xFFC8E6C9)
    } else {
      Color(0xFFC8E6C9) to Color(0xFF145A20)
    }

    3 -> if (dark) {
      Color(0xFF6B5500) to Color(0xFFFFE082)
    } else {
      Color(0xFFFFE082) to Color(0xFF5A4600)
    }

    0, 1, 2 -> if (dark) {
      Color(0xFF7F1D1D) to Color(0xFFFFDAD6)
    } else {
      Color(0xFFFFCDD2) to Color(0xFF7A1420)
    }

    else -> scheme.surfaceVariant to scheme.onSurfaceVariant
  }

  Surface(
    shape = RoundedCornerShape(10.dp),
    color = container,
    contentColor = content,
  ) {
    Text(
      text = text,
      modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
      style = MaterialTheme.typography.labelMedium,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.SemiBold,
    )
  }
}

@Composable
private fun NeutralChip(
  text: String,
) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
  ) {
    Text(
      text = text,
      modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
      style = MaterialTheme.typography.labelMedium,
      fontFamily = FontFamily.Monospace,
    )
  }
}

private fun BarsActivityType.displayName(): String =
  when (this) {
    BarsActivityType.CURRENT_SCORE -> "Балл текущего контроля"
    BarsActivityType.CONTROL_WEEK -> "Контрольная неделя"
    BarsActivityType.INTERMEDIATE_MARK -> "Промежуточная оценка"
    BarsActivityType.FINAL_MARK -> "Итоговая оценка"
    BarsActivityType.CONTROL_ACTIVITY -> "Контрольное мероприятие"
    BarsActivityType.UNDEFINED -> "Оценка"
  }

@Composable
private fun EmptyBarsCard(text: String) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp),
  ) {
    Text(
      text = text,
      modifier = Modifier.padding(16.dp),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

