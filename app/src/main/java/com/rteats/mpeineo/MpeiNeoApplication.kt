package com.rteats.mpeineo

import android.app.Application
import android.content.ComponentCallbacks2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import com.rteats.mpeineo.ui.clearCampusRasterOnLowMemory
import com.google.gson.GsonBuilder
import com.rteats.mpeineo.data.BarsNetworkDiagnostics
import com.rteats.mpeineo.data.BarsSnapshotStore
import com.rteats.mpeineo.data.DiagnosticLog
import com.rteats.mpeineo.data.UiFrameProfiler
import com.rteats.mpeineo.data.FileScheduleCache
import com.rteats.mpeineo.data.GithubUpdateRepository
import com.rteats.mpeineo.data.MailRepository
import com.rteats.mpeineo.data.MailNotificationScheduler
import com.rteats.mpeineo.data.OwaAuthProbe
import com.rteats.mpeineo.data.OwaMailRepository
import com.rteats.mpeineo.data.OwaInboxProbe
import com.rteats.mpeineo.data.MpeiScheduleRemote
import com.rteats.mpeineo.data.ScheduleRepository
import com.rteats.mpeineo.data.ScheduleSearchIndex
import com.rteats.mpeineo.data.UserPreferences
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class MpeiNeoApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val maintenanceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val maintenanceStarted = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        installCrashLogging()
        container.diagnostics.log(
            "APP",
            "started version=${BuildConfig.VERSION_NAME} code=${BuildConfig.VERSION_CODE} channel=${BuildConfig.UPDATE_CHANNEL}",
        )
    }

    /**
     * Nonessential disk and WorkManager operations must not block the first UI frame.
     * Called once after initial composition has had a chance to render. A restart
     * creates a fresh Application and repeats the maintenance normally.
     */
    fun startPostLaunchMaintenance() {
        if (!maintenanceStarted.compareAndSet(false, true)) return
        maintenanceScope.launch {
            runCatching {
                val cleanup = container.updater.cleanupAfterLaunch(BuildConfig.VERSION_CODE)
                container.diagnostics.log(
                    "UPDATE",
                    "post-launch cleanup deleted=${cleanup.deletedFiles} completedUpdate=${cleanup.completedInstalledUpdate}",
                )
            }.onFailure {
                container.diagnostics.log("UPDATE", "post-launch cleanup failed type=${it.javaClass.simpleName}")
            }
            runCatching {
                // KEEP retains a healthy periodic mail notification registration.
                MailNotificationScheduler.reconcileOnStartup(this@MpeiNeoApplication)
                androidx.work.WorkManager.getInstance(this@MpeiNeoApplication)
                    .cancelUniqueWork("automatic_apk_updates")
            }.onFailure {
                container.diagnostics.log("APP", "post-launch work recovery failed type=${it.javaClass.simpleName}")
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            clearCampusRasterOnLowMemory()
            if (::container.isInitialized) {
                container.diagnostics.log("PERF", "campus_raster cache=evicted trimLevel=$level")
            }
        }
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
    val appContext = application.applicationContext
    private val gson = GsonBuilder().create()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val diagnostics = DiagnosticLog(application)
    val uiFrameProfiler = UiFrameProfiler(diagnostics)
    val mailRepository = MailRepository(application, diagnostics)
    val owaAuthProbe = OwaAuthProbe(application, diagnostics)
    val owaMailRepository = OwaMailRepository(application, diagnostics)
    val owaInboxProbe = OwaInboxProbe(application, diagnostics)
    val barsNetworkDiagnostics = BarsNetworkDiagnostics(application, diagnostics)
    internal val barsSnapshots = BarsSnapshotStore(application)
    val preferences = UserPreferences(application, gson)
    // The updater is only needed by Settings and post-launch housekeeping.
    // Avoid reading its preferences or creating its download directory at launch.
    val updater by lazy {
        GithubUpdateRepository(
            application = application,
            httpClient = httpClient,
            gson = gson,
            diagnostics = diagnostics,
        )
    }
    val repository = ScheduleRepository(
        remote = MpeiScheduleRemote(httpClient, gson),
        cache = FileScheduleCache(application, gson),
        searchIndex = ScheduleSearchIndex(application, gson),
    )
}
