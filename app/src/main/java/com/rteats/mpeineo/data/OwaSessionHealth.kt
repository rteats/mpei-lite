package com.rteats.mpeineo.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Lightweight status check even when the Mail tab is not open.
 * This worker never submits passwords or forces a reauthentication.
 * Android controls the actual execution time; the period is not exact.
 */
internal class OwaSessionHealthWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        if (!OwaSessionHealth.hasKnownAccount(app)) return Result.success()
        val diagnostics = DiagnosticLog(app.applicationContext as android.app.Application)
        val result = runCatching {
            OwaAuthProbe(app, diagnostics).check(OwaAuthProbe.Source.BACKGROUND)
        }
        result.onFailure { failure ->
            diagnostics.log("OWA_AUTH",
                "background_probe failed type=" + failure.javaClass.simpleName)
        }
        // A VPN timeout or rejected auth does not justify WorkManager backoff.
        // The next period (or foreground check) can assess the state again.
        return Result.success()
    }
}

internal object OwaSessionHealth {
    private const val UNIQUE_WORK = "mpei_owa_session_health_v1"
    private const val CHECK_INTERVAL_MINUTES = 30L

    fun hasKnownAccount(context: Context): Boolean =
        context.getSharedPreferences("owa_mail_account_v1", Context.MODE_PRIVATE)
            .getString("last_account", null) != null ||
            SavedCredentialStore(context, SavedLoginService.MAIL).hasSavedLogin()

    fun start(context: Context) {
        if (!hasKnownAccount(context)) return
        val request = PeriodicWorkRequestBuilder<OwaSessionHealthWorker>(
            CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES,
        ).setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK, ExistingPeriodicWorkPolicy.KEEP, request,
        )
    }

    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
    }
}
