package com.rteats.mpeineo.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rteats.mpeineo.AppIcons
import com.rteats.mpeineo.MainActivity
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * Background notifications use an authenticated OWA session. Only a hashed
 * mailbox identity, conversation IDs and timestamps are persisted for deltas.
 */
class MailNotificationPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("mail_notifications_v1", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }

    var intervalMinutes: Int
        get() = prefs.getInt("interval_minutes", 30).takeIf { it in ALLOWED_INTERVALS } ?: 30
        set(value) {
            require(value in ALLOWED_INTERVALS)
            prefs.edit().putInt("interval_minutes", value).apply()
        }

    @Synchronized
    fun checkpoint(account: String, validity: Long, messages: List<MailSummary>): List<MailSummary> {
        val highest = messages.maxOfOrNull { it.uid } ?: 0L
        val storedAccount = prefs.getString("account", null)
        val storedValidity = prefs.getLong("uid_validity", -1L)
        val lastUid = prefs.getLong("last_uid", -1L)

        // First successful poll or mailbox reset establishes baseline.
        val newMessages = if (storedAccount == account &&
            storedValidity == validity && lastUid >= 0L
        ) mailNewUids(messages, lastUid, validity)
        else emptyList()

        prefs.edit()
            .putString("account", account)
            .putLong("uid_validity", validity)
            .putLong("last_uid", maxOf(if (storedValidity == validity && storedAccount == account) lastUid else -1L, highest))
            .commit()
        return newMessages.sortedBy { it.uid }
    }

    /** OWA conversation IDs are hashes, not sequential IMAP UIDs.
     *  Calculate arrivals without acknowledging them until alerts have been posted.
     */
    @Synchronized
    fun pendingOwa(account: String, messages: List<MailSummary>): OwaNotificationChanges {
        val sameAccount = prefs.getString("owa_account", null) == account
        val oldTime = if (sameAccount) prefs.getLong("owa_latest_time", -1L) else -1L
        val known = if (sameAccount) prefs.getStringSet("owa_known", emptySet()).orEmpty()
            else emptySet()
        return calculateOwaNotificationChanges(messages, oldTime, known)
    }

    @Synchronized
    fun commitOwa(account: String, change: OwaNotificationChanges) {
        if (!enabled) return // Settings may have disabled notifications during the poll.
        val stored = prefs.edit()
            .putString("owa_account", account)
            .putLong("owa_latest_time", change.latestTime)
            .putStringSet("owa_known", change.latestIds)
            .commit()
        if (!stored) throw IllegalStateException("OWA notification checkpoint not persisted")
    }

    fun hasOwaBaseline(account: String): Boolean =
        prefs.getString("owa_account", null) == account &&
            prefs.getLong("owa_latest_time", -1L) >= 0L

    fun clearAccount() {
        prefs.edit()
            .putBoolean("enabled", false)
            .remove("account")
            .remove("uid_validity")
            .remove("last_uid")
            .remove("owa_account").remove("owa_latest_time").remove("owa_known")
            .commit()
    }

    companion object {
        val ALLOWED_INTERVALS = setOf(15, 30, 60)
    }
}

/** Determines which Exchange conversations arrived since the preceding successful poll.
 *  Keep the set of IDs at the high-watermark timestamp across scans, even if the
 *  server returns a different page; otherwise identical timestamps can re-alert.
 *  No email content or account details are persisted here.
 */
internal data class OwaNotificationChanges(
    val newMessages: List<MailSummary>,
    val latestTime: Long,
    val latestIds: Set<String>,
)

internal fun calculateOwaNotificationChanges(
    messages: List<MailSummary>,
    previousTime: Long,
    previousIds: Set<String>,
): OwaNotificationChanges {
    val observedLatest = messages.maxOfOrNull { it.sentAt } ?: 0L
    val latestTime = maxOf(previousTime, observedLatest, 0L)
    val newMessages = if (previousTime < 0L) emptyList() else messages.filter {
        it.sentAt > previousTime ||
            (it.sentAt == previousTime && it.uid.toString() !in previousIds)
    }.sortedWith(compareBy<MailSummary> { it.sentAt }.thenBy { it.uid })
    val currentLatestIds = messages.asSequence()
        .filter { it.sentAt == latestTime }
        .map { it.uid.toString() }
        .toSet()
    val latestIds = if (latestTime > previousTime) currentLatestIds
        else previousIds + currentLatestIds
    return OwaNotificationChanges(newMessages, latestTime, latestIds)
}

private fun logMailNotification(context: Context, event: String) {
    (context.applicationContext as? Application)?.let {
        DiagnosticLog(it).log("MAIL_NOTIFY", event)
    }
}

object MailNotificationScheduler {
    // Keep the old WorkManager key so upgrades do not create duplicate workers.
    private const val UNIQUE_WORK = "mpei_imap_notification_poll"
    const val CHANNEL_ID = "mpei_mail_new_messages"

    fun setEnabled(context: Context, value: Boolean) {
        val prefs = MailNotificationPreferences(context)
        if (value) {
            prefs.enabled = true
            schedule(context)
            logMailNotification(context,
                "schedule_enabled intervalMin=${prefs.intervalMinutes} permission=${canNotify(context)}")
        } else {
            // Re-enabling must baseline from the current mailbox rather than
            // notifying for all messages received while notifications were off.
            prefs.clearAccount()
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
            logMailNotification(context, "schedule_disabled checkpointCleared=true")
        }
    }

    fun changeInterval(context: Context, intervalMinutes: Int) {
        MailNotificationPreferences(context).intervalMinutes = intervalMinutes
        if (MailNotificationPreferences(context).enabled) {
            schedule(context)
            logMailNotification(context, "schedule_interval_changed intervalMin=$intervalMinutes")
        }
    }

    fun stopAndClear(context: Context) {
        MailNotificationPreferences(context).clearAccount()
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
        logMailNotification(context, "schedule_stopped logout=true checkpointCleared=true")
    }

    private fun schedule(context: Context) {
        val minutes = MailNotificationPreferences(context).intervalMinutes.toLong()
        val request = PeriodicWorkRequestBuilder<MailNotificationWorker>(
            minutes, TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK, ExistingPeriodicWorkPolicy.UPDATE, request,
        )
    }

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Новые письма МЭИ",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Уведомления о новых входящих письмах"
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                },
            )
        }
    }

    @SuppressLint("MissingPermission")
    internal fun show(
        context: Context,
        newMessages: List<MailSummary>,
        previews: Map<Long, String> = emptyMap(),
    ): Int {
        if (newMessages.isEmpty() || !canNotify(context)) return 0
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        val groupKey = "mpei_mail"
        val messages = newMessages.sortedBy { it.uid }
        // Replace the previous generic banner with sender-first message alerts.
        manager.cancel(9821)

        messages.forEach { message ->
            val subject = mailNotificationSubject(message.subject)
            val snippet = previews[message.uid].orEmpty()
            val id = notificationId(message.uid, message.uidValidity)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(AppIcons.Mail)
                .setContentTitle(message.sender.ifBlank { "Неизвестный отправитель" })
                .setContentText(mailNotificationLine(subject, snippet))
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(
                            message.sender.ifBlank { "Неизвестный отправитель" },
                        )
                        .bigText(if (snippet.isBlank()) subject else "$subject\n$snippet"),
                )
                .setSubText("Почта МЭИ")
                .setWhen(message.sentAt)
                .setShowWhen(true)
                .setCategory(NotificationCompat.CATEGORY_EMAIL)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(mailNotificationIntent(context, message.uid, message.uidValidity, id))
                .setAutoCancel(true)
                .setGroup(groupKey)
                .build()
            manager.notify(id, notification)
        }

        if (messages.size > 1) {
            val lines = messages.takeLast(5).asReversed().map { message ->
                message.sender.ifBlank { "Неизвестный отправитель" } +
                    " · " + mailNotificationSubject(message.subject)
            }
            val summary = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(AppIcons.Mail)
                .setContentTitle("Новых писем: ${messages.size}")
                .setContentText(lines.first())
                .setStyle(NotificationCompat.InboxStyle().also { style ->
                    lines.forEach { style.addLine(it) }
                    style.setSummaryText("Почта МЭИ")
                })
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(mailNotificationIntent(context, -1L, -1L, 9821))
                .setAutoCancel(true)
                .setGroup(groupKey)
                .setGroupSummary(true)
                .build()
            manager.notify(9821, summary)
        }
        return messages.size
    }
}

private fun notificationId(uid: Long, validity: Long): Int =
    (uid xor (uid ushr 32) xor validity xor (validity ushr 32))
        .toInt().and(Int.MAX_VALUE).let { if (it == 9821 || it == 0) it + 9822 else it }

private fun mailNotificationIntent(
    context: Context,
    uid: Long,
    validity: Long,
    requestCode: Int,
): PendingIntent = PendingIntent.getActivity(
    context,
    requestCode,
    Intent(context, MainActivity::class.java).apply {
        action = "com.rteats.mpeineo.OPEN_MAIL.$validity.$uid"
        putExtra(MainActivity.EXTRA_OPEN_MAIL, true)
        if (uid > 0L && validity > 0L) {
            putExtra(MainActivity.EXTRA_MAIL_UID, uid)
            putExtra(MainActivity.EXTRA_MAIL_VALIDITY, validity)
        }
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_NEW_TASK
    },
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
)

class MailNotificationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = MailNotificationPreferences(applicationContext)
        if (!prefs.enabled) return Result.success()
        val started = SystemClock.elapsedRealtime()
        val diagnostics = DiagnosticLog(applicationContext as Application)
        fun log(event: String) = diagnostics.log("MAIL_NOTIFY", event)
        log("poll_start attempt=$runAttemptCount intervalMin=${prefs.intervalMinutes}")
        if (!MailNotificationScheduler.canNotify(applicationContext)) {
            log("poll_skip reason=notification_permission durationMs=${SystemClock.elapsedRealtime() - started}")
            return Result.success()
        }
        val repository = OwaMailRepository(applicationContext, diagnostics)
        return try {
            val messages = repository.inbox(maxConversations = 60)
            val account = repository.currentAccountKey()
                ?: throw IllegalStateException("OWA account missing after inbox")
            if (!prefs.enabled) {
                log("poll_skip reason=disabled_during_fetch")
                return Result.success()
            }
            val baseline = !prefs.hasOwaBaseline(account)
            val changes = prefs.pendingOwa(account, messages)
            val newMessages = changes.newMessages
            var previewFailures = 0
            var previewsFetched = 0
            var posted = 0
            if (newMessages.isNotEmpty()) {
                val previews = newMessages.takeLast(5).mapNotNull { summary ->
                    try {
                        val body = repository.message(summary).body
                        previewsFetched++
                        summary.uid to mailNotificationSnippet(body, 180)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        previewFailures++
                        log("preview_failure type=${failure.javaClass.simpleName}" +
                            " category=${(failure as? OwaMailRepository.ServiceFailure)?.category.orEmpty()}")
                        null
                    }
                }.toMap()
                if (!prefs.enabled) {
                    log("poll_skip reason=disabled_during_previews")
                    return Result.success()
                }
                posted = MailNotificationScheduler.show(applicationContext, newMessages, previews)
                if (posted != newMessages.size) {
                    log("poll_skip reason=notification_unavailable pending=${newMessages.size}")
                    return Result.success()
                }
            }
            prefs.commitOwa(account, changes)
            log("poll_success baseline=$baseline scanned=${messages.size}" +
                " new=${newMessages.size} posted=$posted previewOk=$previewsFetched" +
                " previewFailed=$previewFailures" +
                " durationMs=${SystemClock.elapsedRealtime() - started}")
            Result.success()
        } catch (cancelled: CancellationException) {
            log("poll_cancelled durationMs=${SystemClock.elapsedRealtime() - started}")
            throw cancelled
        } catch (_: OwaMailRepository.LoginRequired) {
            log("poll_skip reason=login_required durationMs=${SystemClock.elapsedRealtime() - started}")
            Result.success()
        } catch (failure: Exception) {
            log("poll_retry type=${failure.javaClass.simpleName}" +
                " category=${(failure as? OwaMailRepository.ServiceFailure)?.category.orEmpty()}" +
                " attempt=$runAttemptCount durationMs=${SystemClock.elapsedRealtime() - started}")
            Result.retry()
        }
    }
}

/** Pure UID delta for deterministic tests and worker checkpointing. */
internal fun mailNewUids(
    messages: List<MailSummary>,
    lastUid: Long,
    uidValidity: Long,
): List<MailSummary> =
    messages.filter { it.uidValidity == uidValidity && it.uid > lastUid }
        .sortedBy { it.uid }
