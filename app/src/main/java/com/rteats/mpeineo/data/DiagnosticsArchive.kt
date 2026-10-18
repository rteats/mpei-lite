package com.rteats.mpeineo.data

import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Only the existing bounded diagnostic journal and non-identifying build metadata. */
internal object DiagnosticsArchive {
    private val timestampFormat = DateTimeFormatter
        .ofPattern("yyyy-MM-dd_HH-mm-ss")
        .withZone(ZoneOffset.UTC)

    fun fileName(createdAt: Instant): String =
        "MPEI-Neo-diagnostics-${timestampFormat.format(createdAt)}-UTC.zip"

    fun create(
        journal: String,
        versionName: String,
        versionCode: Int,
        androidSdk: Int,
        createdAt: Instant,
    ): ByteArray {
        val manifest = """
            MPEI Neo diagnostics
            Created (UTC): ${createdAt}
            Version: ${versionName}
            Version code: ${versionCode}
            Android SDK: ${androidSdk}

            Contents: runtime.log from the in-app diagnostics journal.
            May contain private information. Review before sharing.
        """.trimIndent() + "\n"
        return ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                fun entry(name: String, content: String) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                entry("runtime.log", journal)
                entry("info.txt", manifest)
            }
            bytes.toByteArray()
        }
    }
}
