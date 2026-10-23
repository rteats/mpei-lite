package com.rteats.mpeineo.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rteats.mpeineo.AppContainer
import com.rteats.mpeineo.BuildConfig
import com.rteats.mpeineo.data.GithubReleaseInfo
import com.rteats.mpeineo.update.InstallMode
import com.rteats.mpeineo.update.ShizukuInstallClient
import com.rteats.mpeineo.update.UpdateAutomationScheduler
import com.rteats.mpeineo.update.UpdateInstallPreferences
import com.rteats.mpeineo.update.UpdateInstallValidator
import com.rteats.mpeineo.update.UpdateForeground
import com.rteats.mpeineo.update.UpdateRelaunchState
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

internal enum class UpdatePhase {
    IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY_TO_INSTALL, INSTALLING, ERROR,
}

internal data class UpdateUiState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val checking: Boolean = false,
    val release: GithubReleaseInfo? = null,
    val progressPercent: Int? = null,
    val downloadedApkPath: String? = null,
    val error: String? = null,
    val installMode: InstallMode = InstallMode.STANDARD,
    val automaticUpdates: Boolean = false,
    val wifiOnly: Boolean = true,
    val shizukuStatus: String = "",
)

internal fun UpdateUiState.afterReleaseCheck(
    latest: GithubReleaseInfo,
    installedVersionCode: Int,
): UpdateUiState {
    if (latest.versionCode <= installedVersionCode) {
        return copy(
            phase = UpdatePhase.UP_TO_DATE, checking = false, release = latest,
            progressPercent = null, downloadedApkPath = null, error = null,
        )
    }
    val keepDownloaded = phase == UpdatePhase.READY_TO_INSTALL &&
        release?.versionCode == latest.versionCode && !downloadedApkPath.isNullOrBlank()
    return if (keepDownloaded) copy(checking = false, error = null, release = latest)
    else copy(
        phase = UpdatePhase.AVAILABLE, checking = false, release = latest,
        progressPercent = null, downloadedApkPath = null, error = null,
    )
}

internal class UpdateViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val settings = UpdateInstallPreferences(container.appContext)
    private val _state = MutableStateFlow(
        UpdateUiState(
            installMode = settings.mode,
            automaticUpdates = settings.automatic,
            wifiOnly = settings.unmeteredOnly,
            shizukuStatus = ShizukuInstallClient.status(),
        ),
    )
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        refreshShizukuStatus()
    }
    private val binderListener = Shizuku.OnBinderReceivedListener { refreshShizukuStatus() }
    private val deadListener = Shizuku.OnBinderDeadListener { refreshShizukuStatus() }

    init {
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addBinderDeadListener(deadListener)
    }

    override fun onCleared() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeBinderDeadListener(deadListener)
        super.onCleared()
    }

    fun refreshShizukuStatus() {
        _state.update { it.copy(shizukuStatus = ShizukuInstallClient.status()) }
    }

    fun requestShizukuPermission() {
        runCatching { ShizukuInstallClient.requestPermission() }
            .onFailure { problem ->
                _state.update { it.copy(error = problem.message ?: "Не удалось запросить Shizuku") }
            }
        refreshShizukuStatus()
    }

    fun setInstallMode(mode: InstallMode) {
        settings.mode = mode
        if (mode != InstallMode.SHIZUKU) settings.automatic = false
        UpdateAutomationScheduler.reconcile(container.appContext)
        _state.update {
            it.copy(
                installMode = mode, automaticUpdates = settings.automatic,
                shizukuStatus = ShizukuInstallClient.status(),
            )
        }
    }

    fun setAutomaticUpdates(enabled: Boolean) {
        settings.automatic = enabled && settings.supportsAutomaticUpdates &&
            settings.mode == InstallMode.SHIZUKU
        UpdateAutomationScheduler.reconcile(container.appContext)
        _state.update { it.copy(automaticUpdates = settings.automatic) }
    }

    fun setWifiOnly(enabled: Boolean) {
        settings.unmeteredOnly = enabled
        UpdateAutomationScheduler.reconcile(container.appContext)
        _state.update { it.copy(wifiOnly = enabled) }
    }

    fun checkForUpdates() {
        if (_state.value.checking ||
            _state.value.phase == UpdatePhase.DOWNLOADING ||
            _state.value.phase == UpdatePhase.INSTALLING
        ) return
        container.diagnostics.log("UPDATE", "manual check channel=${BuildConfig.UPDATE_CHANNEL}")
        _state.update { it.copy(checking = true, error = null) }
        viewModelScope.launch {
            try {
                val latest = container.updater.getLatestRelease(BuildConfig.UPDATE_CHANNEL)
                _state.update { it.afterReleaseCheck(latest, BuildConfig.VERSION_CODE) }
            } catch (error: CancellationException) {
                _state.update { it.copy(checking = false) }
                throw error
            } catch (error: Throwable) {
                _state.update {
                    it.copy(
                        checking = false,
                        phase = if (it.phase in setOf(UpdatePhase.IDLE, UpdatePhase.CHECKING,
                                UpdatePhase.UP_TO_DATE)) UpdatePhase.ERROR else it.phase,
                        error = error.message ?: "Не удалось проверить обновления",
                    )
                }
            }
        }
    }

    fun downloadUpdate() {
        val release = _state.value.release ?: return
        if (_state.value.phase in setOf(UpdatePhase.DOWNLOADING,
                UpdatePhase.READY_TO_INSTALL, UpdatePhase.INSTALLING) ||
            _state.value.checking || release.versionCode <= BuildConfig.VERSION_CODE
        ) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    phase = UpdatePhase.DOWNLOADING,
                    progressPercent = null, downloadedApkPath = null, error = null,
                )
            }
            try {
                val apk = container.updater.downloadRelease(release) { percent ->
                    _state.update { current ->
                        if (current.phase == UpdatePhase.DOWNLOADING)
                            current.copy(progressPercent = percent) else current
                    }
                }
                UpdateInstallValidator.check(container.appContext, apk, release.versionCode)
                _state.update {
                    it.copy(
                        phase = UpdatePhase.READY_TO_INSTALL, progressPercent = 100,
                        downloadedApkPath = apk.absolutePath,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _state.update {
                    it.copy(
                        phase = UpdatePhase.ERROR,
                        error = error.message ?: "Не удалось скачать обновление",
                    )
                }
            }
        }
    }

    fun installDownloadedUpdate(context: Context) {
        val snapshot = _state.value
        if (snapshot.phase != UpdatePhase.READY_TO_INSTALL) return
        val path = snapshot.downloadedApkPath ?: return
        val release = snapshot.release ?: return
        val file = File(path)

        runCatching {
            UpdateInstallValidator.check(context, file, release.versionCode)
            UpdateRelaunchState.arm(context, release.versionCode)
        }.onFailure { error ->
            _state.update {
                it.copy(phase = UpdatePhase.ERROR, error = error.message ?: "APK не прошёл проверку")
            }
            return
        }

        fun openSystemInstaller(reason: String) {
            container.diagnostics.log("UPDATE", "Android installer fallback: $reason")
            runCatching { context.startActivity(container.updater.createInstallIntent(file)) }
                .onFailure { problem ->
                    _state.update {
                        it.copy(
                            phase = UpdatePhase.ERROR,
                            error = problem.message ?: "Не удалось открыть установщик Android",
                        )
                    }
                }
        }

        if (settings.mode != InstallMode.SHIZUKU || !ShizukuInstallClient.ready()) {
            openSystemInstaller(if (settings.mode == InstallMode.SHIZUKU) {
                ShizukuInstallClient.status()
            } else "selected standard installation")
            return
        }
        _state.update { it.copy(phase = UpdatePhase.INSTALLING, error = null) }
        val relaunch = UpdateForeground.shouldRelaunch(context)
        container.diagnostics.log("UPDATE", "Shizuku install requested relaunch=$relaunch")
        viewModelScope.launch {
            try {
                ShizukuInstallClient.install(context.applicationContext, file, relaunch)
                // Usually unreachable: package replacement terminates the calling process.
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                container.diagnostics.log(
                    "UPDATE", "Shizuku failure ${error.javaClass.simpleName}: ${error.message}",
                )
                _state.update {
                    it.copy(
                        phase = UpdatePhase.READY_TO_INSTALL,
                        error = "Shizuku: ${error.message}. Используется установщик Android.",
                    )
                }
                openSystemInstaller(error.javaClass.simpleName)
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return UpdateViewModel(container) as T
                }
            }
    }
}
