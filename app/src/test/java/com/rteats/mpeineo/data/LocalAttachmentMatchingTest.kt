package com.rteats.mpeineo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAttachmentMatchingTest {
    private fun upload(time: Long = 1_800_000L, name: String = "lab.pdf") =
        LocalSentUpload(
            subjectDigest = localSubjectDigest("Lab results"),
            sentAt = time,
            files = listOf(
                LocalUploadFile(name, 2048, "content://document/original", "owa-id"),
            ),
        )

    @Test fun matchesOnlyRecentUniqueExactSentMessage() {
        val snapshot = upload()
        assertEquals(
            snapshot,
            matchSentUpload(
                "Lab results",
                1_820_000L,
                listOf("lab.pdf" to 2048L),
                listOf(snapshot),
            ),
        )
        assertNull(matchSentUpload(
            "Lab results", 1_950_000L,
            listOf("lab.pdf" to 2048L), listOf(snapshot),
        ))
    }

    @Test fun rejectsDifferentSubjectNameSizeOrCount() {
        val snapshot = upload()
        assertNull(matchSentUpload(
            "Another message", 1_800_000L,
            listOf("lab.pdf" to 2048L), listOf(snapshot),
        ))
        assertNull(matchSentUpload(
            "Lab results", 1_800_000L,
            listOf("private.pdf" to 2048L), listOf(snapshot),
        ))
        assertNull(matchSentUpload(
            "Lab results", 1_800_000L,
            listOf("lab.pdf" to 1024L), listOf(snapshot),
        ))
        assertNull(matchSentUpload(
            "Lab results", 1_800_000L,
            listOf("lab.pdf" to 2048L, "other.pdf" to 17L),
            listOf(snapshot),
        ))
    }

    @Test fun ambiguousMatchesDoNotRevealAnyOriginalDocument() {
        val first = upload(1_800_000L)
        val another = upload(1_810_000L)
        assertNull(matchSentUpload(
            "Lab results", 1_807_000L,
            listOf("lab.pdf" to 2048L), listOf(first, another),
        ))
    }

    @Test fun missingDatesAndUnknownFilesNeverMatch() {
        val snapshot = upload()
        assertNull(matchSentUpload("Lab results", 0L, listOf("lab.pdf" to 2048L), listOf(snapshot)))
        assertNull(matchSentUpload("Lab results", 1_800_000L, emptyList(), listOf(snapshot)))
        assertNull(matchSentUpload("Lab results", 1_800_000L,
            listOf("lab.pdf" to -1L), listOf(snapshot)))
    }

    @Test fun fileOrderCanDifferButAttachmentSetMustMatch() {
        val files = listOf(
            LocalUploadFile("a.pdf", 5L, "content://a", null),
            LocalUploadFile("b.pdf", 17L, "content://b", null),
        )
        val snapshot = LocalSentUpload(
            localSubjectDigest("Project"), 1_800_000L, files,
        )
        assertNotNull(matchSentUpload(
            "Project", 1_800_000L, listOf("b.pdf" to 17L, "a.pdf" to 5L),
            listOf(snapshot),
        ))
        assertNull(matchSentUpload(
            "Project", 1_800_000L, listOf("a.pdf" to 5L, "a.pdf" to 17L),
            listOf(snapshot),
        ))
    }

    @Test fun subjectDigestDoesNotStorePlaintext() {
        val hash = localSubjectDigest("Sensitive subject")
        assertEquals(64, hash.length)
        assertTrue("Sensitive" !in hash)
        assertEquals(hash, localSubjectDigest(" Sensitive subject "))
    }
}
