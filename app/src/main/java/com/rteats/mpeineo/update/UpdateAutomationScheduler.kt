package com.rteats.mpeineo.update

import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rteats.mpeineo.BuildConfig
import com.rteats.mpeineo.MpeiNeoApplication
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

internal object UpdateForeground {
    @Volatile private var resumed = 0

    fun attach(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { resumed++ }
            override fun onActivityPaused(activity: Activity) { resumed = (resumed - 1).coerceAtLeast(0) }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    fun shouldRelaunch(context: Context): Boolean =
        resumed > 0 && !(context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
            .isKeyguardLocked
}

internal object UpdateAutomationScheduler {
    private const val UNIQUE_WORK = "automatic_apk_updates"

    fun reconcile(context: Context) {
        val prefs = UpdateInstallPreferences(context)
        val work = WorkManager.getInstance(context)
        if (!prefs.supportsAutomaticUpdates ||
            !prefs.automatic || prefs.mode != InstallMode.SHIZUKU
        ) {
            work.cancelUniqueWork(UNIQUE_WORK)
            return
        }
        val network = if (prefs.unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = PeriodicWorkRequestBuilder<AutomaticUpdateWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .build()
        work.enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

internal class AutomaticUpdateWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MpeiNeoApplication
        val config = UpdateInstallPreferences(app)
        if (!config.supportsAutomaticUpdates ||
            !config.automatic || config.mode != InstallMode.SHIZUKU
        ) return Result.success()
        // Never request Shizuku permission or open the Android installer in a worker.
        if (!ShizukuInstallClient.ready()) {
            app.container.diagnostics.log("UPDATE", "automatic skipped: ${ShizukuInstallClient.status()}")
            return Result.success()
        }
        return try {
            val release = app.container.updater.getLatestRelease(BuildConfig.UPDATE_CHANNEL)
            if (release.versionCode <= BuildConfig.VERSION_CODE) return Result.success()
            // Verify the release digest (when available), package name and signing certificate.
            val apk = app.container.updater.downloadRelease(release)
            UpdateInstallValidator.check(app, apk, release.versionCode)
            if (!config.automatic || !ShizukuInstallClient.ready()) return Result.success()
            UpdateRelaunchState.arm(app, release.versionCode)
            val relaunch = UpdateForeground.shouldRelaunch(app)
            app.container.diagnostics.log("UPDATE", "automatic install begin relaunch=$relaunch")
            ShizukuInstallClient.install(app, apk, relaunch)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            app.container.diagnostics.log(
                "UPDATE", "automatic install failed ${error.javaClass.simpleName}: ${error.message?.take(120)}",
            )
            Result.success() // Retest next period, avoid repeated downloads on device-specific failures.
        }
    }
}
