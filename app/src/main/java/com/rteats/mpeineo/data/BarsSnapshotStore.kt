package com.rteats.mpeineo.data

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import com.rteats.mpeineo.ui.BarsExtractionPayload
import java.io.File

/** Only extracted grades and their timestamp; never BARS cookies or credentials. */
internal data class BarsCachedSnapshot(
    val updatedAtMillis: Long,
    val payload: BarsExtractionPayload,
)

internal data class BarsRawSnapshot(
    val main: String = "",
    val summary: String = "",
    val updatedAtMillis: Long = 0L,
)

internal class BarsSnapshotStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "bars_grade_snapshot.json"))
    private val gson = Gson()
    private val rawFile = AtomicFile(File(context.noBackupFilesDir, "bars_raw_snapshot.json"))

    fun read(): BarsCachedSnapshot? = runCatching {
        if (!file.baseFile.exists() || file.baseFile.length() > 1_000_000L) return null
        val saved = gson.fromJson(
            file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() },
            BarsCachedSnapshot::class.java,
        )
        saved?.takeIf {
            it.updatedAtMillis > 0L &&
                it.updatedAtMillis <= System.currentTimeMillis() &&
                !it.payload.disciplines.isNullOrEmpty()
        }
    }.getOrNull()

    @Synchronized
    fun write(snapshot: BarsCachedSnapshot) {
        // Delayed disk jobs must never replace more recent grades.
        if ((read()?.updatedAtMillis ?: 0L) > snapshot.updatedAtMillis) return
        val bytes = gson.toJson(snapshot).toByteArray(Charsets.UTF_8)
        if (bytes.size > 1_000_000) return
        var stream: java.io.FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Exception) {
            stream?.let(file::failWrite)
        }
    }

    fun readRaw(): BarsRawSnapshot? = runCatching {
        if (!rawFile.baseFile.exists() || rawFile.baseFile.length() > 900_000L) return null
        gson.fromJson(
            rawFile.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() },
            BarsRawSnapshot::class.java,
        )?.takeIf { it.updatedAtMillis > 0L &&
            it.updatedAtMillis <= System.currentTimeMillis() }
    }.getOrNull()

    @Synchronized
    fun writeRaw(source: String, text: String) {
        if (text.length > 380_000 || source !in setOf("MAIN", "SUMMARY")) return
        val previous = readRaw() ?: BarsRawSnapshot()
        val next = when (source) {
            "MAIN" -> previous.copy(main = text, updatedAtMillis = System.currentTimeMillis())
            else -> previous.copy(summary = text, updatedAtMillis = System.currentTimeMillis())
        }
        val bytes = gson.toJson(next).toByteArray(Charsets.UTF_8)
        if (bytes.size > 900_000) return
        var stream: java.io.FileOutputStream? = null
        try {
            stream = rawFile.startWrite()
            stream.write(bytes)
            rawFile.finishWrite(stream)
        } catch (_: Exception) {
            stream?.let(rawFile::failWrite)
        }
    }
}
