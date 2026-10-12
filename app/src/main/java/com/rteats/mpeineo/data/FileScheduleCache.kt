package com.rteats.mpeineo.data

import android.content.Context
import com.google.gson.Gson
import com.rteats.mpeineo.model.ScheduleTarget
import com.rteats.mpeineo.model.ScheduleWeek
import com.rteats.mpeineo.model.isUsable
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Downloaded timetable data is disposable Android cache. The previous filesDir
 * cache is migrated on first use so upgrading does not discard offline weeks.
 */
class FileScheduleCache(
    context: Context,
    private val gson: Gson,
) : ScheduleCacheStore {
    private val directory = File(context.cacheDir, "schedule-cache")
    private val legacyDirectory = File(context.filesDir, "schedule-cache")
    private val maintenanceMutex = Mutex()
    @Volatile private var lastMaintenanceAtMillis = 0L

    override suspend fun read(target: ScheduleTarget, weekStart: LocalDate): ScheduleWeek? =
        withContext(Dispatchers.IO) {
            maintainIfDue()
            val file = fileFor(target, weekStart)
            if (!file.exists()) return@withContext null
            val parsed = runCatching {
                gson.fromJson(file.readText(), ScheduleWeek::class.java)
            }.getOrNull()
            if (parsed?.isUsable() == true) {
                parsed
            } else {
                file.delete()
                null
            }
        }

    override suspend fun write(week: ScheduleWeek) = withContext(Dispatchers.IO) {
        maintainIfDue()
        directory.mkdirs()
        val file = fileFor(week.target, LocalDate.parse(week.weekStart))
        val temp = File(file.parentFile, "${file.name}.tmp")
        try {
            temp.writeText(gson.toJson(week))
            if (!temp.renameTo(file)) {
                file.writeText(temp.readText())
            }
        } finally {
            temp.delete()
        }
    }

    private suspend fun maintainIfDue() {
        val now = System.currentTimeMillis()
        if (!maintenanceDue(now)) return
        maintenanceMutex.withLock {
            val current = System.currentTimeMillis()
            if (!maintenanceDue(current)) return@withLock
            migrateLegacyCache()
            pruneObsoleteWeeks(LocalDate.now(), current)
            lastMaintenanceAtMillis = current
        }
    }

    private fun maintenanceDue(now: Long): Boolean =
        lastMaintenanceAtMillis == 0L || now < lastMaintenanceAtMillis ||
            now - lastMaintenanceAtMillis >= MAINTENANCE_INTERVAL_MILLIS

    private fun migrateLegacyCache() {
        if (!legacyDirectory.isDirectory) return
        val files = legacyDirectory.listFiles() ?: return
        if (files.isEmpty()) {
            legacyDirectory.delete()
            return
        }
        directory.mkdirs()
        files.forEach { old ->
            if (!old.isFile) return@forEach
            if (old.name.endsWith(".tmp")) {
                old.delete()
                return@forEach
            }
            if (!old.name.endsWith(".json")) return@forEach
            val destination = File(directory, old.name)
            if (destination.exists()) {
                // The new cache is authoritative once present.
                old.delete()
                return@forEach
            }
            val temporary = File(directory, "${old.name}.migrating")
            val copied = runCatching {
                old.copyTo(temporary, overwrite = true)
                if (!temporary.renameTo(destination)) {
                    temporary.copyTo(destination, overwrite = true)
                }
            }.isSuccess
            temporary.delete()
            // Preserve the original if writing the new cache failed.
            if (copied) old.delete()
        }
        if (legacyDirectory.listFiles()?.isEmpty() == true) {
            legacyDirectory.delete()
        }
    }

    private fun pruneObsoleteWeeks(today: LocalDate, nowMillis: Long) {
        val currentMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        directory.listFiles()?.forEach { cached ->
            if (!cached.isFile) return@forEach
            if (cached.name.endsWith(".json") &&
                shouldPruneScheduleCacheWeek(cached.name, currentMonday)
            ) {
                cached.delete()
            } else if ((cached.name.endsWith(".tmp") || cached.name.endsWith(".migrating")) &&
                nowMillis - cached.lastModified() > TEMP_FILE_MAX_AGE_MILLIS
            ) {
                cached.delete()
            }
        }
    }

    private fun fileFor(target: ScheduleTarget, weekStart: LocalDate): File {
        val safeName = "${target.type.apiName}-${target.id}-${weekStart}.json"
        return File(directory, safeName)
    }

    private companion object {
        const val MAINTENANCE_INTERVAL_MILLIS = 24L * 60 * 60 * 1000
        const val TEMP_FILE_MAX_AGE_MILLIS = 24L * 60 * 60 * 1000
    }
}
