package com.rteats.mpeineo.data

import android.app.Application
import java.io.File
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Bounded, ordered diagnostics journal. Normal UI events are appended on a
 * single I/O thread instead of blocking Compose gestures on every file open.
 * Reading, clearing and fatal-crash recording are barriers: all earlier events
 * are complete before those operations finish.
 */
class DiagnosticLog(application: Application) {
    private val directory = File(application.filesDir, "diagnostics")
    private val file = File(directory, "runtime.log")

    fun log(category: String, message: String) {
        val safeCategory = category.replace(Regex("[^A-Za-z0-9_-]"), "_").take(24)
        val safeMessage = message.replace('\n', ' ').replace('\r', ' ')
            .take(if (safeCategory == "CRASH") 12_000 else 2_000)
        val entry = "${Instant.now()} [$safeCategory] $safeMessage\n"
        val write = Runnable {
            runCatching {
                synchronized(fileLock) {
                    directory.mkdirs()
                    file.appendText(entry)
                    trimIfNeededLocked()
                }
            }
        }
        if (safeCategory == "CRASH") {
            // Do not return from the uncaught-exception handler until the stack
            // trace is on disk. Earlier queued logs are flushed in FIFO order.
            try {
                writer.submit(write).get(3, TimeUnit.SECONDS)
            } catch (_: Exception) {
                // A blocked diagnostics thread must not suppress crash handling.
                runCatching { synchronized(fileLock) {
                    directory.mkdirs()
                    file.appendText(entry)
                } }
            }
        } else writer.execute(write)
    }

    fun readText(): String = writer.submit<String> {
        synchronized(fileLock) { if (file.exists()) file.readText() else "" }
    }.get()

    fun clear() {
        writer.submit {
            synchronized(fileLock) { file.delete() }
        }.get()
    }

    private fun trimIfNeededLocked() {
        if (!file.exists() || file.length() <= MAX_BYTES) return
        val kept = file.readLines().takeLast(MAX_LINES)
        file.writeText(
            if (kept.isEmpty()) "" else kept.joinToString(separator = "\n", postfix = "\n"),
        )
    }

    private companion object {
        val fileLock = Any()
        val writer = Executors.newSingleThreadExecutor { command ->
            Thread(command, "MpeiDiagnostics").apply { isDaemon = true }
        }
        const val MAX_BYTES = 1024L * 1024L
        const val MAX_LINES = 4_000
    }
}
