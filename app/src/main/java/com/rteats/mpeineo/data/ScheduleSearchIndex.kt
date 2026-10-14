package com.rteats.mpeineo.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rteats.mpeineo.model.ScheduleTarget
import com.rteats.mpeineo.model.ScheduleTargetType
import com.rteats.mpeineo.model.isUsable
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Bounded persistent index of targets actually discovered via MPEI's search API.
 * Unlike cached agenda weeks this survives normal Android cache eviction.
 * No network requests are made while searching the index.
 */
class ScheduleSearchIndex(context: Context, private val gson: Gson) {
    private val file = File(context.filesDir, "schedule-search-index-v1.json")
    private val mutex = Mutex()
    private var loaded: List<ScheduleTarget>? = null

    private suspend fun entries(): List<ScheduleTarget> {
        loaded?.let { return it }
        return withContext(Dispatchers.IO) {
            val current = runCatching {
                if (!file.isFile || file.length() > MAX_BYTES) emptyList()
                else {
                    val type = object : TypeToken<List<ScheduleTarget>>() {}.type
                    gson.fromJson<List<ScheduleTarget>>(file.readText(), type).orEmpty()
                }
            }.getOrDefault(emptyList())
                .filter { it.isUsable() }
                .distinctBy { it.type to it.id }
                .take(MAX_ENTRIES)
            loaded = current
            current
        }
    }

    suspend fun search(query: String, type: ScheduleTargetType?): List<ScheduleTarget> =
        mutex.withLock { searchIndexedScheduleTargets(entries(), query, type) }

    suspend fun remember(results: List<ScheduleTarget>) {
        if (results.isEmpty()) return
        mutex.withLock {
            val updated = (results.filter { it.isUsable() } + entries())
                .distinctBy { it.type to it.id }
                .take(MAX_ENTRIES)
            loaded = updated
            withContext(Dispatchers.IO) {
                runCatching {
                    val payload = gson.toJson(updated)
                    val temp = File(file.parentFile, file.name + ".tmp")
                    temp.writeText(payload)
                    if (!temp.renameTo(file)) {
                        file.writeText(payload)
                        temp.delete()
                    }
                }
            }
        }
    }

    private companion object {
        const val MAX_ENTRIES = 750
        const val MAX_BYTES = 1_000_000L
    }
}

internal fun searchIndexedScheduleTargets(
    targets: List<ScheduleTarget>,
    query: String,
    type: ScheduleTargetType?,
): List<ScheduleTarget> {
    val needle = query.trim().lowercase(Locale.ROOT)
    if (needle.length < 2) return emptyList()
    return targets.asSequence()
        .filter { type == null || it.type == type }
        .filter {
            it.name.lowercase(Locale.ROOT).contains(needle) ||
                it.description.lowercase(Locale.ROOT).contains(needle)
        }
        .sortedWith(compareBy<ScheduleTarget> {
            if (it.name.lowercase(Locale.ROOT).startsWith(needle)) 0 else 1
        }.thenBy { it.type.ordinal }.thenBy { it.name })
        .take(50).toList()
}
