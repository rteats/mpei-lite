package com.rteats.mpeineo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.google.gson.Gson
import com.rteats.mpeineo.MainActivity
import com.rteats.mpeineo.MpeiNeoApplication
import com.rteats.mpeineo.R
import com.rteats.mpeineo.data.FileScheduleCache
import com.rteats.mpeineo.ui.nextHomeLesson
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Offline-only widget: never starts timetable or BARS network requests. */
class NextLessonWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                update(context.applicationContext, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val zone = ZoneId.of("Europe/Moscow")
        private val timeFormat = DateTimeFormatter.ofPattern("H:mm")

        /** Refresh after selection or a successful cache update, not on every recomposition. */
        fun requestRefresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, NextLessonWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, NextLessonWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                },
            )
        }

        private suspend fun update(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray,
        ) {
            val app = context.applicationContext as? MpeiNeoApplication ?: return
            val target = runCatching { app.container.preferences.selected.first() }.getOrNull()
            val now = LocalDateTime.now(zone)
            val monday = LocalDate.now(zone).let {
                it.minusDays((it.dayOfWeek.value - 1).toLong())
            }
            val cache = FileScheduleCache(context, Gson())
            val weeks = if (target != null) {
                listOf(monday, monday.plusWeeks(1)).mapNotNull { start ->
                    runCatching { cache.read(target, start) }.getOrNull()
                }
            } else emptyList()
            val upcoming = nextHomeLesson(weeks, now)
            val openApp = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_next_lesson)
                val title = when {
                    upcoming != null -> upcoming.lesson.name.ifBlank { "Пара" }
                    target == null -> "Выберите расписание"
                    else -> "Нет ближайших пар в кэше"
                }
                val time = if (upcoming != null) {
                    val prefix = if (upcoming.isOngoing) "Сейчас" else
                        upcoming.date.format(DateTimeFormatter.ofPattern("dd.MM"))
                    "$prefix · ${upcoming.lesson.startTime}–${upcoming.lesson.endTime}"
                } else "Открыть MPEI Neo"
                val details = upcoming?.lesson?.let { lesson ->
                    listOf(lesson.kind, lesson.place).filter(String::isNotBlank).joinToString(" · ")
                }.orEmpty()
                views.setTextViewText(R.id.widget_title, title)
                views.setTextViewText(R.id.widget_time, time)
                views.setTextViewText(R.id.widget_details, details)
                views.setOnClickPendingIntent(R.id.widget_root, openApp)
                manager.updateAppWidget(id, views)
            }
        }
    }
}
