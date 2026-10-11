package com.rteats.mpeineo

import android.app.Application
import com.google.gson.GsonBuilder
import com.rteats.mpeineo.data.BarsNetworkDiagnostics
import com.rteats.mpeineo.data.DiagnosticLog
import com.rteats.mpeineo.data.FileScheduleCache
import com.rteats.mpeineo.data.GithubUpdateRepository
import com.rteats.mpeineo.data.MailRepository
import com.rteats.mpeineo.data.MpeiScheduleRemote
import com.rteats.mpeineo.data.ScheduleRepository
import com.rteats.mpeineo.data.UserPreferences
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class MpeiNeoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        installCrashLogging()

        val cleanup = container.updater.cleanupAfterLaunch(BuildConfig.VERSION_CODE)
        container.diagnostics.log(
            "APP",
            "started version=${BuildConfig.VERSION_NAME} code=${BuildConfig.VERSION_CODE} channel=${BuildConfig.UPDATE_CHANNEL}",
        )
        container.diagnostics.log(
            "UPDATE",
            "startup cleanup deleted=${cleanup.deletedFiles} completedUpdate=${cleanup.completedInstalledUpdate}",
        )
    }

    /**
     * Android's uncaught-exception handler writes to Logcat, which cannot be
     * recovered from the app after the process dies. Persist a bounded crash
     * trace in the existing Diagnostics journal first, then always delegate to
     * the system handler so normal crash reporting is preserved.
     */
    private fun installCrashLogging() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, failure ->
            try {
                val description = buildString {
                    append("thread=").append(thread.name.take(80))
                    append(" exception=").append(failure.javaClass.name)
                    append(" message=").append(failure.message.orEmpty().take(240))
                    failure.stackTrace.take(18).forEach { element ->
                        append(" | at ").append(element)
                    }
                    failure.cause?.let { cause ->
                        append(" | causedBy=").append(cause.javaClass.name)
                        append(": ").append(cause.message.orEmpty().take(160))
                        cause.stackTrace.take(5).forEach { element ->
                            append(" | at ").append(element)
                        }
                    }
                }
                container.diagnostics.log("CRASH", description)
            } catch (_: Throwable) {
                // An exception in diagnostics must not prevent Android from
                // handling the original crash.
            }
            previous?.uncaughtException(thread, failure)
        }
    }
}

class AppContainer(application: Application) {
    private val gson = GsonBuilder().create()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val diagnostics = DiagnosticLog(application)
    val mailRepository = MailRepository(application)
    val barsNetworkDiagnostics = BarsNetworkDiagnostics(application, diagnostics)
    val preferences = UserPreferences(application, gson)
    val updater = GithubUpdateRepository(
        application = application,
        httpClient = httpClient,
        gson = gson,
        diagnostics = diagnostics,
    )
    val repository = ScheduleRepository(
        remote = MpeiScheduleRemote(httpClient, gson),
        cache = FileScheduleCache(application, gson),
    )
}
