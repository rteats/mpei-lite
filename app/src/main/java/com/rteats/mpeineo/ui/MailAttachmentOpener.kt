package com.rteats.mpeineo.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Open an already-local attachment with the user's installed viewer. */
internal object MailAttachmentOpener {
    fun open(context: Context, uri: Uri, mimeType: String): Boolean {
        if (uri.scheme != "content") return false
        val type = mimeType.takeIf { it.contains('/') && it.isNotBlank() }
            ?: "application/octet-stream"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, type)
            clipData = ClipData.newRawUri("attachment", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}
