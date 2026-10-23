package com.rteats.mpeineo.update

import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream

/**
 * Runs under Shizuku's shell/root UID, not the app UID.
 * The binder FD remains readable after Android kills the old APK's process.
 * No Context-dependent APIs are used in this UserService.
 *
 * Do not stream the APK into the `pm install` process via stdin: if `pm`
 * exits early, the writer sees EPIPE instead of the install failure. Stage
 * the APK in shell-accessible storage, then install the complete file.
 */
class PrivilegedUpdateService : IPrivilegedUpdater.Stub() {
    override fun install(
        apk: ParcelFileDescriptor,
        size: Long,
        packageName: String,
        relaunch: Boolean,
        callback: IUpdateResult,
    ) {
        // Return control to Binder immediately: package replacement may stop
        // the caller, but the Shizuku UserService finishes independently.
        Thread({
            var stagedApk: File? = null
            try {
                require(size > 0L) { "APK is empty" }
                require(packageName.matches(Regex("[a-zA-Z0-9_.]+"))) {
                    "Invalid package name"
                }

                // The source descriptor belongs to the user app. The shell UID
                // can read the duplicate descriptor but not its private path.
                // A unique, owner-only file in /data/local/tmp is readable by pm.
                val stagingDirectory = File("/data/local/tmp")
                require(stagingDirectory.isDirectory && stagingDirectory.canWrite()) {
                    "Shizuku cannot access APK staging directory"
                }
                val temp = File.createTempFile("mpei-neo-update-", ".apk", stagingDirectory)
                stagedApk = temp
                temp.setReadable(false, false)
                check(temp.setReadable(true, true)) { "Could not protect staged APK" }
                ParcelFileDescriptor.AutoCloseInputStream(apk).use { source ->
                    FileOutputStream(temp).use { target ->
                        val copied = source.copyTo(target)
                        check(copied == size) {
                            "Incomplete APK transfer: $copied of $size bytes"
                        }
                        target.fd.sync()
                    }
                }

                val (exitCode, output) = runPrivilegedCommand(
                    privilegedInstallCommand(temp.absolutePath),
                )
                check(exitCode == 0 && output.contains("Success", ignoreCase = true)) {
                    "Package installer failed (code $exitCode): $output"
                }

                // Installing the running app can terminate the caller before
                // it receives the success callback. A separate Shizuku process
                // can still relaunch it when requested.
                if (relaunch) {
                    val (startCode, startOutput) = runPrivilegedCommand(
                        listOf(
                            "am", "start", "--user", "current", "-n",
                            "$packageName/com.rteats.mpeineo.MainActivity",
                        ),
                    )
                    if (startCode != 0 || startOutput.contains("Error:", ignoreCase = true)) {
                        android.util.Log.w("MPEI_UPDATER", "relaunch failed: $startOutput")
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
                runCatching { stagedApk?.delete() }
                runCatching { apk.close() }
            }
        }, "mpei-neo-privileged-install").start()
    }
}

/** No stdin pipe: package manager opens the fully staged file itself. */
internal fun privilegedInstallCommand(apkPath: String): List<String> =
    listOf("pm", "install", "-r", apkPath)

private fun runPrivilegedCommand(arguments: List<String>): Pair<Int, String> {
    val process = ProcessBuilder(arguments).redirectErrorStream(true).start()
    return try {
        // The command writes human-readable diagnostics, not APK bytes.
        val response = process.inputStream.bufferedReader().use { it.readText().take(2048) }
        process.waitFor() to response.ifBlank { "(no output)" }
    } finally {
        process.destroy()
    }
}
