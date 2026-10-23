package com.rteats.mpeineo.update

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import com.rteats.mpeineo.BuildConfig
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.shizuku.Shizuku

/**
 * AIDL client for a Shizuku UserService. Binder transports the APK FD; the
 * privileged process never needs direct access to app-private cache paths.
 */
internal object ShizukuInstallClient {
    private const val PERMISSION_REQUEST = 24530

    fun ready(): Boolean = runCatching {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun status(): String = runCatching {
        when {
            !Shizuku.pingBinder() -> "Shizuku не запущен"
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
                "Нет разрешения Shizuku"
            else -> "Shizuku подключён"
        }
    }.getOrDefault("Shizuku недоступен")

    fun requestPermission() {
        if (runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            Shizuku.requestPermission(PERMISSION_REQUEST)
        }
    }

    suspend fun install(context: Context, file: File, relaunch: Boolean): Unit =
        suspendCancellableCoroutine { continuation ->
            if (!ready()) {
                continuation.resumeWith(Result.failure(IllegalStateException(status())))
                return@suspendCancellableCoroutine
            }

            val args = Shizuku.UserServiceArgs(
                ComponentName(context.packageName, PrivilegedUpdateService::class.java.name),
            ).processNameSuffix("updater")
                .debuggable(BuildConfig.DEBUG)
                .version(1)

            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    if (binder == null) {
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.failure(IllegalStateException("No Shizuku binder")))
                        }
                        return
                    }
                    try {
                        val service = IPrivilegedUpdater.Stub.asInterface(binder)
                        val callback = object : IUpdateResult.Stub() {
                            override fun onFinished(success: Boolean, message: String?) {
                                if (continuation.isActive) {
                                    if (success) continuation.resume(Unit)
                                    else continuation.resumeWith(Result.failure(
                                        IllegalStateException(message ?: "Silent install failed"),
                                    ))
                                }
                                runCatching { Shizuku.unbindUserService(args, this@object, false) }
                            }
                        }
                        // The service receives a duplicated FD and performs install on its own thread.
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                            service.install(fd, file.length(), context.packageName, relaunch, callback)
                        }
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWith(Result.failure(error))
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.failure(RemoteException("Shizuku service disconnected")))
                    }
                }
            }
            try {
                Shizuku.bindUserService(args, connection)
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(error))
            }
        }
}
