package com.rteats.mpeineo.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rteats.mpeineo.AppContainer
import com.rteats.mpeineo.BuildConfig
import com.rteats.mpeineo.data.GithubReleaseInfo
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class UpdatePhase {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    AVAILABLE,
    DOWNLOADING,
    READY_TO_INSTALL,
    ERROR,
}

internal data class UpdateUiState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val checking: Boolean = false,
    val release: GithubReleaseInfo? = null,
    val progressPercent: Int? = null,
    val downloadedApkPath: String? = null,
    val error: String? = null,
)


/** Preserve a verified downloaded APK when rechecking the same release. */
internal fun UpdateUiState.afterReleaseCheck(
    latest: GithubReleaseInfo,
    installedVersionCode: Int,
): UpdateUiState {
    if (latest.versionCode <= installedVersionCode) {
        return UpdateUiState(phase = UpdatePhase.UP_TO_DATE, release = latest)
    }
    val keepDownloaded = phase == UpdatePhase.READY_TO_INSTALL &&
        release?.versionCode == latest.versionCode &&
        !downloadedApkPath.isNullOrBlank()
    return if (keepDownloaded) {
        copy(checking = false, error = null, release = latest)
    } else {
        UpdateUiState(phase = UpdatePhase.AVAILABLE, release = latest)
    }
}

internal class UpdateViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    fun checkForUpdates() {
        // A recheck must not cancel/replace an in-progress APK download.
        // Available and ready-to-install actions stay visible while checking.
        if (_state.value.checking || _state.value.phase == UpdatePhase.DOWNLOADING) return

        container.diagnostics.log("UPDATE", "manual check requested channel=${BuildConfig.UPDATE_CHANNEL}")
        _state.update { it.copy(checking = true, error = null) }
        viewModelScope.launch {
            try {
                val latest = container.updater.getLatestRelease(BuildConfig.UPDATE_CHANNEL)
                _state.update { current ->
                    current.afterReleaseCheck(latest, BuildConfig.VERSION_CODE)
                }
            } catch (error: CancellationException) {
                _state.update { it.copy(checking = false) }
                throw error
            } catch (error: Throwable) {
                _state.update {
                    it.copy(
                        checking = false,
                        phase = if (it.phase == UpdatePhase.IDLE ||
                            it.phase == UpdatePhase.CHECKING ||
                            it.phase == UpdatePhase.UP_TO_DATE
                        ) UpdatePhase.ERROR else it.phase,
                        error = error.message ?: "Не удалось проверить обновления",
                    )
                }
            }
        }
    }

    fun downloadUpdate() {
        val release = _state.value.release ?: return
        container.diagnostics.log(
            "UPDATE",
            "download requested version=${release.versionName} code=${release.versionCode}",
        )
        if (_state.value.phase == UpdatePhase.DOWNLOADING ||
            _state.value.phase == UpdatePhase.READY_TO_INSTALL ||
            _state.value.checking ||
            release.versionCode <= BuildConfig.VERSION_CODE
        ) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    phase = UpdatePhase.DOWNLOADING,
                    progressPercent = null,
                    downloadedApkPath = null,
                    error = null,
                )
            }

            try {
                val apk = container.updater.downloadRelease(release) { percent ->
                    _state.update { current ->
                        if (current.phase == UpdatePhase.DOWNLOADING) {
                            current.copy(progressPercent = percent)
                        } else {
                            current
                        }
                    }
                }

                _state.update {
                    it.copy(
                        phase = UpdatePhase.READY_TO_INSTALL,
                        progressPercent = 100,
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
        val path = _state.value.downloadedApkPath ?: return
        container.diagnostics.log("UPDATE", "opening Android installer")
        val file = File(path)
        runCatching {
            context.startActivity(container.updater.createInstallIntent(file))
        }.onFailure { error ->
            _state.update {
                it.copy(
                    phase = UpdatePhase.ERROR,
                    error = error.message ?: "Не удалось открыть установщик Android",
                )
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
