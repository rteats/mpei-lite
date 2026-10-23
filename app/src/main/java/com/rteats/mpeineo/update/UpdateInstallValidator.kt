package com.rteats.mpeineo.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.rteats.mpeineo.BuildConfig
import java.io.File

/** Block wrong build variants, downgrade attempts and unsigned/foreign APKs. */
internal object UpdateInstallValidator {
    fun check(context: Context, file: File, expectedVersion: Int) {
        require(file.isFile && file.length() > 0L) { "APK file is missing" }
        require(expectedVersion > BuildConfig.VERSION_CODE) { "Update must be newer than installed version" }
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        @Suppress("DEPRECATION")
        val incoming = pm.getPackageArchiveInfo(file.absolutePath, flags)
            ?: error("Invalid APK archive")
        @Suppress("DEPRECATION")
        val installed = pm.getPackageInfo(context.packageName, flags)
        require(incoming.packageName == context.packageName) {
            "APK belongs to a different package (Dev/Stable/PR mismatch)"
        }
        require(PackageInfoCompat.getLongVersionCode(incoming) == expectedVersion.toLong()) {
            "APK version does not match published release"
        }
        val newSignatures = signatureBytes(incoming)
        val oldSignatures = signatureBytes(installed)
        require(newSignatures.isNotEmpty() &&
            newSignatures.size == oldSignatures.size &&
            newSignatures.all { incomingKey -> oldSignatures.any { it.contentEquals(incomingKey) } }
        ) { "APK signing certificate does not match installed application" }
    }

    @Suppress("DEPRECATION")
    private fun signatureBytes(info: PackageInfo): List<ByteArray> =
        if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
        } else {
            info.signatures?.map { it.toByteArray() }.orEmpty()
        }
}
