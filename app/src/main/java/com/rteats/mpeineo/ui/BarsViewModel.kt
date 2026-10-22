package com.rteats.mpeineo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.os.SystemClock
import com.google.gson.Gson
import org.json.JSONObject
import com.rteats.mpeineo.data.DiagnosticLog
import com.rteats.mpeineo.data.BarsNetworkDiagnostics
import com.rteats.mpeineo.data.BarsSnapshotStore
import com.rteats.mpeineo.data.BarsCachedSnapshot
import com.rteats.mpeineo.data.barsSnapshotAccountKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal const val BARS_BASE_URL = "https://bars.mpei.ru"
internal const val BARS_LOGIN_URL = "$BARS_BASE_URL/bars_web/"
internal const val BARS_MARKS_URL = "$BARS_BASE_URL/bars_web/ST_Study/Main/Main"
internal const val BARS_SUMMARY_URL = "$BARS_BASE_URL/bars_web/ST_Study/Main/Summary"

internal enum class BarsAuthStage {
    CHECKING,
    WEB_AUTH,
    AUTHENTICATED,
}

internal data class BarsUiState(
    val authStage: BarsAuthStage = BarsAuthStage.CHECKING,
    val profileName: String = "",
    val profileGroup: String = "",
    val semester: String = "",
    val studentDetails: List<BarsStudentDetail> = emptyList(),
    val disciplines: List<BarsDiscipline> = emptyList(),
    val gradeChanges: Map<String, List<BarsGradeChange>> = emptyMap(),
    val isLoading: Boolean = true,
    val browserVisible: Boolean = false,
    val sessionUrl: String = BARS_MARKS_URL,
    val lastUpdatedAtMillis: Long = 0L,
    val rawMain: String = "",
    val rawSummary: String = "",
    val rawCapturedAtMillis: Long = 0L,
    val lastMainCapturedAtMillis: Long = 0L,
    val lastSummaryCapturedAtMillis: Long = 0L,
    val rawStatus: String = "",
    val isSummaryLoading: Boolean = false,
    val summaryError: String? = null,
    val error: String? = null,
    val loginRequired: Boolean = false,
) {
    fun hasFreshCache(nowMillis: Long = System.currentTimeMillis()): Boolean =
        lastUpdatedAtMillis > 0L &&
            nowMillis - lastUpdatedAtMillis in 0 until BARS_CACHE_TTL_MS

    val controlSchedule: List<BarsControlScheduleItem>
        get() = disciplines
            .flatMap { discipline ->
                discipline.activities
                    .filter {
                        it.type == BarsActivityType.CONTROL_ACTIVITY &&
                            !it.weekNum.isNullOrBlank()
                    }
                    .map { activity ->
                        BarsControlScheduleItem(
                            discipline = discipline.disciplineName,
                            activity = activity.name.orEmpty(),
                            weekNum = activity.weekNum.orEmpty(),
                            weight = activity.weight.orEmpty(),
                            markAndDate = activity.markAndDate.orEmpty(),
                        )
                    }
            }
            .sortedWith(
                compareBy<BarsControlScheduleItem> {
                    it.weekNum.extractFirstIntOrNull() ?: Int.MAX_VALUE
                }.thenBy { it.discipline }
            )
}

internal data class BarsDiscipline(
    val disciplineName: String = "",
    val personName: String = "",
    val assessmentType: String = "",
    val activities: List<BarsActivity> = emptyList(),
) {
    val currentMarks: List<String>
        get() = activities
            .filter {
                it.type == BarsActivityType.CONTROL_ACTIVITY &&
                    !it.markAndDate.isNullOrBlank()
            }
            .mapNotNull { activity ->
                activity.markAndDate?.extractFirstNumber()
            }

    val markValues: List<Float>
        get() = currentMarks.mapNotNull { it.toFloatOrNull() }

    val finalMark: String?
        get() = activities
            .lastOrNull {
                it.type == BarsActivityType.FINAL_MARK &&
                    !it.markAndDate.isNullOrBlank()
            }
            ?.markAndDate
            ?.extractFirstNumber()

    val finalMarkValue: Float?
        get() = finalMark?.toFloatOrNull()

    val currentScore: String?
        get() = activities
            .lastOrNull {
                it.type == BarsActivityType.CURRENT_SCORE &&
                    !it.markAndDate.isNullOrBlank()
            }
            ?.markAndDate
            ?.extractFirstNumber()

    val currentScoreValue: Float?
        get() = currentScore?.toFloatOrNull()
}

internal data class BarsActivity(
    val type: BarsActivityType = BarsActivityType.UNDEFINED,
    val name: String? = null,
    val weight: String? = null,
    val weekNum: String? = null,
    val markAndDate: String? = null,
) {
    val markValue: Float?
        get() = markAndDate?.extractFirstNumber()?.toFloatOrNull()
}

internal enum class BarsActivityType {
    UNDEFINED,
    CONTROL_ACTIVITY,
    CURRENT_SCORE,
    CONTROL_WEEK,
    INTERMEDIATE_MARK,
    FINAL_MARK,
}

internal data class BarsControlScheduleItem(
    val discipline: String,
    val activity: String,
    val weekNum: String,
    val weight: String,
    val markAndDate: String,
)

internal data class BarsStudentDetail(
    val label: String = "",
    val value: String = "",
)

internal data class BarsExtractionPayload(
    val name: String = "",
    val group: String = "",
    val semester: String = "",
    val studentDetails: List<BarsStudentDetail> = emptyList(),
    val disciplines: List<BarsDiscipline> = emptyList(),
)

internal data class BarsPageState(
    val url: String = "",
    val path: String = "",
    val isLoginPage: Boolean = false,
    val isAuthFlow: Boolean = false,
    val isStudentList: Boolean = false,
    val isMarksPage: Boolean = false,
    val isSummaryPage: Boolean = false,
    val isSummaryReady: Boolean = false,
)

internal object BarsPayloadParser {
    private val gson = Gson()

    fun parseExtraction(json: String): BarsExtractionPayload =
        gson.fromJson(json, BarsExtractionPayload::class.java)

    /**
     * BARS page state comes from a JavaScript bridge and contains only
     * scalar fields. Parse explicitly instead of relying on Gson reflection:
     * R8 can abstract/rename an unreferenced DTO and break cold-start refresh.
     */
    fun parsePageState(json: String): BarsPageState {
        val data = JSONObject(json)
        return BarsPageState(
            url = data.optString("url"),
            path = data.optString("path"),
            isLoginPage = data.optBoolean("isLoginPage"),
            isAuthFlow = data.optBoolean("isAuthFlow"),
            isStudentList = data.optBoolean("isStudentList"),
            isMarksPage = data.optBoolean("isMarksPage"),
            isSummaryPage = data.optBoolean("isSummaryPage"),
            isSummaryReady = data.optBoolean("isSummaryReady"),
        )
    }
}

internal fun isCompleteBarsSummarySnapshot(text: String): Boolean =
    text.contains("=== Матрица оценок по неделям") &&
        text.contains("=== Посещаемость по журналу")

internal class BarsViewModel(
    private val diagnostics: DiagnosticLog,
    private val network: BarsNetworkDiagnostics,
    private val snapshots: BarsSnapshotStore,
) : ViewModel() {

    private val cachedGrades = snapshots.read()
    private val cachedRaw = cachedGrades?.payload?.let { payload ->
        barsSnapshotAccountKey(payload.name, payload.group)
            ?.let(snapshots::readRaw)
    }
    private val _state = MutableStateFlow(
        cachedGrades?.let { cached ->
            BarsUiState(
                authStage = BarsAuthStage.AUTHENTICATED,
                profileName = cached.payload.name,
                profileGroup = cached.payload.group,
                semester = cached.payload.semester,
                studentDetails = cached.payload.studentDetails.orEmpty(),
                disciplines = cached.payload.disciplines,
                lastUpdatedAtMillis = cached.updatedAtMillis,
                rawMain = cachedRaw?.main.orEmpty(),
                rawSummary = cachedRaw?.summary.orEmpty(),
                rawCapturedAtMillis = cachedRaw?.updatedAtMillis ?: 0L,
                lastMainCapturedAtMillis = cachedRaw?.mainUpdatedAtMillis ?: 0L,
                lastSummaryCapturedAtMillis = cachedRaw?.summaryUpdatedAtMillis ?: 0L,
                isLoading = true,
            )
        } ?: BarsUiState(
            rawMain = cachedRaw?.main.orEmpty(),
            rawSummary = cachedRaw?.summary.orEmpty(),
            rawCapturedAtMillis = cachedRaw?.updatedAtMillis ?: 0L,
            lastMainCapturedAtMillis = cachedRaw?.mainUpdatedAtMillis ?: 0L,
            lastSummaryCapturedAtMillis = cachedRaw?.summaryUpdatedAtMillis ?: 0L,
        )
    )
    val state: StateFlow<BarsUiState> = _state.asStateFlow()

    private var lastWebEvent: String? = null
    private var lastProbeAtMillis = -10_000L

    fun checking(url: String? = null) {
        val current = _state.value
        val targetUrl = url ?: current.sessionUrl
        if (
            current.authStage == BarsAuthStage.CHECKING &&
            current.sessionUrl == targetUrl &&
            current.error == null
        ) {
            return
        }

        diagnostics.log("BARS", "state=CHECKING")
        _state.update {
            it.copy(
                authStage = BarsAuthStage.CHECKING,
                isLoading = true,
                sessionUrl = targetUrl,
                error = null,
            )
        }
    }

    fun webAuth(url: String) {
        val current = _state.value
        if (
            current.authStage == BarsAuthStage.WEB_AUTH &&
            current.sessionUrl == url &&
            current.error == null
        ) {
            return
        }

        diagnostics.log("BARS", "state=WEB_AUTH cached=" + current.disciplines.isNotEmpty())
        _state.update {
            if (it.disciplines.isNotEmpty()) {
                it.copy(
                    authStage = BarsAuthStage.AUTHENTICATED,
                    isLoading = false,
                    browserVisible = false,
                    sessionUrl = url,
                    loginRequired = true,
                    error = null,
                )
            } else {
                it.copy(
                    authStage = BarsAuthStage.WEB_AUTH,
                    isLoading = false,
                    browserVisible = false,
                    sessionUrl = url,
                    loginRequired = true,
                    error = null,
                )
            }
        }
    }

    fun authenticatedPage(url: String) {
        diagnostics.log("BARS", "state=AUTHENTICATED extracting=true")
        _state.update {
            it.copy(
                authStage = BarsAuthStage.AUTHENTICATED,
                isLoading = true,
                sessionUrl = url,
                loginRequired = false,
                error = null,
            )
        }
    }

    fun updateSessionUrl(url: String) {
        if (url.isBlank()) return
        _state.update { it.copy(sessionUrl = url) }
    }

    fun extractionReceived(json: String) {
        runCatching { BarsPayloadParser.parseExtraction(json) }
            .onSuccess { payload ->
                if (payload.disciplines.isEmpty()) {
                    extractionFailed("БАРС не вернул данных о дисциплинах.")
                    return@onSuccess
                }
                diagnostics.log(
                    "BARS",
                    "extraction success disciplines=${payload.disciplines.size}",
                )
                val updatedAt = System.currentTimeMillis()
                val old = _state.value
                val previous = if (old.disciplines.isEmpty()) null else
                    BarsExtractionPayload(
                        name = old.profileName,
                        group = old.profileGroup,
                        semester = old.semester,
                        disciplines = old.disciplines,
                    )
                val changed = detectBarsGradeChanges(previous, payload)
                val sameAccount = previous != null &&
                    (previous.name.isBlank() || payload.name.isBlank() ||
                        previous.name.trim() == payload.name.trim()) &&
                    (previous.group.isBlank() || payload.group.isBlank() ||
                        previous.group.trim() == payload.group.trim()) &&
                    (previous.semester.isBlank() || payload.semester.isBlank() ||
                        previous.semester.trim() == payload.semester.trim())
                val oldKey = barsSnapshotAccountKey(old.profileName, old.profileGroup)
                val newKey = barsSnapshotAccountKey(payload.name, payload.group)
                val accountChanged = oldKey != null && newKey != null && oldKey != newKey
                if (accountChanged) {
                    diagnostics.log("BARS", "student changed; discarding old Summary state")
                }
                if (changed.isNotEmpty()) {
                    diagnostics.log("BARS", "grade_changes disciplines=${changed.size}" +
                        " marks=${changed.values.sumOf { it.size }}")
                }
                viewModelScope.launch(Dispatchers.IO) {
                    snapshots.write(BarsCachedSnapshot(updatedAt, payload))
                }
                _state.update {
                    it.copy(
                        authStage = BarsAuthStage.AUTHENTICATED,
                        profileName = payload.name,
                        profileGroup = payload.group,
                        semester = payload.semester,
                        studentDetails = payload.studentDetails.orEmpty(),
                        disciplines = payload.disciplines,
                        rawMain = if (accountChanged) "" else it.rawMain,
                        rawSummary = if (accountChanged) "" else it.rawSummary,
                        rawCapturedAtMillis = if (accountChanged) 0L else it.rawCapturedAtMillis,
                        lastMainCapturedAtMillis = if (accountChanged) 0L else it.lastMainCapturedAtMillis,
                        lastSummaryCapturedAtMillis = if (accountChanged) 0L else it.lastSummaryCapturedAtMillis,
                        isSummaryLoading = if (accountChanged) false else it.isSummaryLoading,
                        summaryError = if (accountChanged) null else it.summaryError,
                        gradeChanges = if (sameAccount) it.gradeChanges + changed else changed,
                        isLoading = false,
                        lastUpdatedAtMillis = updatedAt,
                        loginRequired = false,
                        error = null,
                    )
                }
            }
            .onFailure { error ->
                extractionFailed(error.message ?: "Не удалось разобрать данные БАРС")
            }
    }

    /** The two Summary reports have a separate loading/error state from cached marks. */
    fun summaryCaptureStarted() {
        _state.update {
            it.copy(isSummaryLoading = true, summaryError = null, rawStatus = "")
        }
        diagnostics.log("BARS", "summary capture started")
    }

    /** Browser mode cancels automatic Summary work, not previously cached reports. */
    fun summaryCaptureCancelled() {
        _state.update {
            if (it.isSummaryLoading) it.copy(isSummaryLoading = false, summaryError = null)
            else it
        }
        diagnostics.log("BARS", "summary capture cancelled for interactive browser")
    }

    fun summaryCaptureFailed(reason: String) {
        _state.update {
            it.copy(isSummaryLoading = false, summaryError = reason, rawStatus = reason)
        }
        diagnostics.log("BARS", "summary capture failed reason=" + reason.take(120))
    }

    /** Intentionally never send profile, grade or attendance text to diagnostics. */
    fun rawSnapshotReceived(source: String, text: String) {
        if (source !in setOf("MAIN", "SUMMARY") || text.isBlank() ||
            text.length > 380_000) return
        // Never report an incomplete response as a successful Summary refresh.
        if (source == "SUMMARY" && !isCompleteBarsSummarySnapshot(text)) {
            summaryCaptureFailed("БАРС вернул неполную Сводку. Сохранённые данные не изменены.")
            return
        }
        val capturedAt = System.currentTimeMillis()
        _state.update {
            when (source) {
                "MAIN" -> it.copy(rawMain = text, rawCapturedAtMillis = capturedAt,
                    lastMainCapturedAtMillis = capturedAt, rawStatus = "")
                else -> it.copy(rawSummary = text, rawCapturedAtMillis = capturedAt,
                    lastSummaryCapturedAtMillis = capturedAt,
                    isSummaryLoading = false, summaryError = null, rawStatus = "")
            }
        }
        diagnostics.log("BARS", "raw capture source=$source chars=${text.length}")
        val accountKey = barsSnapshotAccountKey(_state.value.profileName, _state.value.profileGroup)
        if (accountKey != null) {
            viewModelScope.launch(Dispatchers.IO) {
                snapshots.writeRaw(source, text, accountKey, capturedAt)
            }
        }
    }

    fun rawCaptureStatus(message: String) {
        _state.update { it.copy(rawStatus = message) }
        diagnostics.log("BARS", "raw capture status=" + message.take(100))
    }

    fun acknowledgeGradeChanges(disciplineName: String) {
        _state.update { it.copy(gradeChanges = it.gradeChanges - disciplineName) }
    }

    fun extractionFailed(message: String) {
        // A page-state observer may report the same failure repeatedly.
        // Preserve previously extracted grades without flooding diagnostics.
        val current = _state.value
        if (!current.isLoading && current.error == message) return

        diagnostics.log("BARS", "error=$message cachedGrades=${current.lastUpdatedAtMillis > 0L}")
        _state.update {
            it.copy(
                isLoading = false,
                error = message,
            )
        }
    }

    /** Display an existing snapshot without implying a failed update. */
    fun showCachedWithoutRefresh() {
        _state.update { current ->
            if (current.disciplines.isNotEmpty()) {
                current.copy(isLoading = false, error = null)
            } else current
        }
    }

    fun startRefresh() {
        diagnostics.log("BARS", "refresh requested")
        _state.update { it.copy(isLoading = true, error = null) }
    }

    fun waitingForMarks(url: String) {
        val current = _state.value
        if (current.lastUpdatedAtMillis > 0L) {
            _state.update {
                it.copy(
                    isLoading = true,
                    sessionUrl = url,
                    error = null,
                )
            }
        } else {
            checking(url)
        }
    }

    fun logWebEvent(message: String) {
        if (message == lastWebEvent) return
        lastWebEvent = message
        diagnostics.log("BARS_WEB", message)
    }

    fun networkSnapshot(reason: String) {
        network.snapshot(reason)
    }

    fun probeNetwork(reason: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastProbeAtMillis < 5_000L) {
            diagnostics.log("BARS_NET", "probe rate-limited reason=$reason")
            return
        }
        lastProbeAtMillis = now
        viewModelScope.launch {
            network.probe(reason)
        }
    }

    fun showBrowser() {
        diagnostics.log("BARS", "session WebView opened")
        _state.update { it.copy(browserVisible = true) }
    }

    fun hideBrowser() {
        diagnostics.log("BARS", "session WebView hidden")
        _state.update { it.copy(browserVisible = false) }
    }

    companion object {
        fun factory(
            diagnostics: DiagnosticLog,
            network: BarsNetworkDiagnostics,
            snapshots: BarsSnapshotStore,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    BarsViewModel(diagnostics, network, snapshots) as T
            }
    }
}

private fun String.extractFirstNumber(): String? =
    replace(',', '.')
        .split(Regex("[\\s(/]+"))
        .firstOrNull { token -> token.toFloatOrNull() != null }

private fun String.extractFirstIntOrNull(): Int? =
    split(Regex("[^0-9]+"))
        .firstOrNull { it.isNotBlank() }
        ?.toIntOrNull()

private const val BARS_CACHE_TTL_MS = 5L * 60L * 1_000L
