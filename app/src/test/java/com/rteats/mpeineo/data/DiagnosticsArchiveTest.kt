package com.rteats.mpeineo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.zip.ZipInputStream

class DiagnosticsArchiveTest {
    private val time = Instant.parse("2026-10-19T12:34:56Z")

    @Test fun stableTimestampFilenameDoesNotExposeAccountDetails() {
        assertEquals(
            "MPEI-Neo-diagnostics-2026-10-19_12-34-56-UTC.zip",
            DiagnosticsArchive.fileName(time),
        )
    }

    @Test fun zipContainsCompleteJournalAndNonIdentifyingBuildInfo() {
        val log = "2026-10-19T12:30:00Z [BARS] refresh requested\n" +
            "2026-10-19T12:30:03Z [OWA_AUTH] state=SUCCESS\n"
        val bytes = DiagnosticsArchive.create(
            journal = log,
            versionName = "0.4.0-dev.294",
            versionCode = 294,
            androidSdk = 36,
            createdAt = time,
        )
        val entries = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
            }
        }
        assertEquals(setOf("runtime.log", "info.txt"), entries.keys)
        assertEquals(log, entries["runtime.log"])
        val metadata = entries.getValue("info.txt")
        assertTrue(metadata.contains("0.4.0-dev.294"))
        assertTrue(metadata.contains("Android SDK: 36"))
        assertTrue(metadata.contains("2026-10-19T12:34:56Z"))
        assertFalse(metadata.contains("OWA_AUTH"))
        assertFalse(metadata.contains("refresh requested"))
    }

    @Test fun emptyJournalStillProducesValidArchive() {
        val archive = DiagnosticsArchive.create("", "dev", 1, 26, time)
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            assertEquals("runtime.log", zip.nextEntry?.name)
            assertEquals("", zip.readBytes().toString(Charsets.UTF_8))
            zip.closeEntry()
            assertEquals("info.txt", zip.nextEntry?.name)
        }
    }
}
