package com.rteats.mpeineo.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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

/**
 * Background mail notifications are opt-in. Store only an IMAP UID checkpoint,
 * never a message body, cookie, password, or entire inbox in preferences.
 * IMAP UID and UIDVALIDITY together identify messages within one mailbox.
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

    fun clearAccount() {
        prefs.edit()
            .putBoolean("enabled", false)
            .remove("account")
            .remove("uid_validity")
            .remove("last_uid")
            .commit()
    }

    companion object {
        val ALLOWED_INTERVALS = setOf(15, 30, 60)
    }
}

object MailNotificationScheduler {
    private const val UNIQUE_WORK = "mpei_imap_notification_poll"
    const val CHANNEL_ID = "mpei_mail_new_messages"

    fun setEnabled(context: Context, value: Boolean) {
        val prefs = MailNotificationPreferences(context)
        if (value) {
            prefs.enabled = true
            schedule(context)
        } else {
            // Re-enabling must baseline from the current mailbox rather than
            // notifying for all messages received while notifications were off.
            prefs.clearAccount()
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
        }
    }

    fun changeInterval(context: Context, intervalMinutes: Int) {
        MailNotificationPreferences(context).intervalMinutes = intervalMinutes
        if (MailNotificationPreferences(context).enabled) schedule(context)
    }

    fun stopAndClear(context: Context) {
        MailNotificationPreferences(context).clearAccount()
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
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
    ) {
        if (newMessages.isEmpty() || !canNotify(context)) return
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
        val repository = MailRepository(applicationContext)
        val credentials = repository.credentials.load() ?: return Result.success()

        return try {
            // Headers only; READ_ONLY + PEEK does not mark messages as seen.
            val snapshot = repository.inboxSnapshot(credentials, maxMessages = 60)
            val stillEnabled = MailNotificationPreferences(applicationContext).enabled
            val stillAccount = repository.credentials.load()?.username == credentials.username
            if (stillEnabled && stillAccount) {
                val newMessages = prefs.checkpoint(
                    credentials.username, snapshot.uidValidity, snapshot.messages,
                )
                if (newMessages.isNotEmpty()) {
                    // Fetch bodies only after identifying new UIDs; preview
                    // errors must not prevent ordinary new-mail notifications.
                    val previews = runCatching {
                        repository.notificationPreviews(
                            credentials,
                            snapshot.uidValidity,
                            newMessages.map { it.uid },
                        )
                    }.getOrDefault(emptyMap())
                    MailNotificationScheduler.show(applicationContext, newMessages, previews)
                }
            }
            Result.success()
        } catch (_: Exception) {
            // Keep the prior UID checkpoint so transient IMAP/TLS errors never
            // generate false notifications. WorkManager will retry later.
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
