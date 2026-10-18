package com.rteats.mpeineo.data

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Saves user-requested ZIPs without broad storage permissions or a private file URI. */
internal object DiagnosticsZipExporter {
    const val MIME_TYPE = "application/zip"
    const val DOWNLOAD_FOLDER = "MPEI Neo/Diagnostics"

    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun saveToDownloads(context: Context, fileName: String, bytes: ByteArray): Uri =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE)
                put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/" + DOWNLOAD_FOLDER + "/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Не удалось создать файл в Загрузках")
            try {
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: throw IllegalStateException("Не удалось записать диагностический ZIP")
                val ready = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                check(resolver.update(uri, ready, null, null) > 0) {
                    "Не удалось завершить сохранение ZIP"
                }
                uri
            } catch (failure: Exception) {
                resolver.delete(uri, null, null)
                throw failure
            }
        }

    /** Android 8–9: the Storage Access Framework chooses a user-visible folder. */
    suspend fun saveToPickedDocument(context: Context, uri: Uri, bytes: ByteArray) =
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                ?: throw IllegalStateException("Не удалось записать диагностический ZIP")
        }

    fun share(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "MPEI Neo diagnostics", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Отправить диагностический ZIP"))
    }
}
