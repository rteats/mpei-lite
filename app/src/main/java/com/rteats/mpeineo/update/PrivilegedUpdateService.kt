package com.rteats.mpeineo.update

import android.os.ParcelFileDescriptor

/**
 * Runs under Shizuku's shell/root UID, not the app UID.
 * The binder FD remains readable after Android kills the old APK's process.
 * No Context-dependent APIs are used in this UserService.
 */
class PrivilegedUpdateService : IPrivilegedUpdater.Stub() {
    override fun install(
        apk: ParcelFileDescriptor,
        size: Long,
        packageName: String,
        relaunch: Boolean,
        callback: IUpdateResult,
    ) {
        // Return control to Binder immediately: the caller can be killed by package replacement.
        Thread({
            try {
                require(size > 0L)
                require(packageName.matches(Regex("[a-zA-Z0-9_.]+")))
                val process = ProcessBuilder(
                    "pm", "install", "-r", "-S", size.toString(), "-",
                ).redirectErrorStream(true).start()
                try {
                    ParcelFileDescriptor.AutoCloseInputStream(apk).use { input ->
                        process.outputStream.use { output ->
                            input.copyTo(output, DEFAULT_BUFFER_SIZE)
                        }
                    }
                    val output = process.inputStream.bufferedReader().use { it.readText().take(2048) }
                    val exitCode = process.waitFor()
                    check(exitCode == 0 && output.contains("Success")) {
                        "Package installer returned $exitCode: $output"
                    }
                } finally {
                    process.destroy()
                }

                if (relaunch) {
                    // Shell's Activity Manager survives an in-place app update.
                    val start = ProcessBuilder(
                        "am", "start", "--user", "current", "-n",
                        "$packageName/com.rteats.mpeineo.MainActivity",
                    ).redirectErrorStream(true).start()
                    val response = start.inputStream.bufferedReader().use { it.readText().take(1024) }
                    val code = start.waitFor()
                    if (code != 0 || response.contains("Error:", ignoreCase = true)) {
                        android.util.Log.w("MPEI_UPDATER", "relaunch failed: $response")
                    }
                }
                runCatching { callback.onFinished(true, "Installed") }
            } catch (error: Exception) {
                android.util.Log.e("MPEI_UPDATER", "silent install failed", error)
                runCatching {
                    callback.onFinished(
                        false, error.message?.take(300) ?: error.javaClass.simpleName,
                    )
                }
            } finally {
                runCatching { apk.close() }
            }
        }, "mpei-neo-privileged-install").start()
    }
}
