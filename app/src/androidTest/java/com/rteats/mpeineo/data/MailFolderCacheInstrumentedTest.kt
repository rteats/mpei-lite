package com.rteats.mpeineo.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MailFolderCacheInstrumentedTest {
    @Test fun openedFoldersSurviveRestartAndRemainSeparateUntilLogout() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val account = "folder-cache-test-${System.nanoTime()}"
        val folder = OwaMailFolder("deleteditems", "DELETED", "Удалённые", itemList = true)
        val draft = OwaMailFolder("drafts", "DRAFTS", "Черновики", itemList = true)
        val first = summary(1, "first")
        val second = summary(2, "second")
        val cache = MailCache(context)
        try {
            cache.saveOwaFolders(account, OwaMailFolder.defaults + folder + draft)
            cache.saveOwaFolderPage(account, folder, listOf(first), offset = 0)
            cache.saveOwaFolderPage(account, draft, listOf(second), offset = 0)
            val reopened = MailCache(context)
            assertEquals(listOf(first), reopened.owaFolder(account, folder))
            assertEquals(listOf(second), reopened.owaFolder(account, draft))
            assertEquals(OwaMailFolder.defaults + folder + draft, reopened.owaFolders(account))
            assertTrue(reopened.owaFolder(account + "-other", folder).isEmpty())
            assertTrue(reopened.owaFolders(account + "-other").isEmpty())

            // A new first page keeps already downloaded older rows during sync.
            reopened.saveOwaFolderPage(account, folder, listOf(second), offset = 0)
            assertEquals(listOf(second, first), cache.owaFolder(account, folder))
            assertEquals(listOf(second), cache.owaFolder(account, draft))
        } finally {
            cache.clear(account)
        }
        assertTrue(cache.owaFolder(account, folder).isEmpty())
        assertTrue(cache.owaFolders(account).isEmpty())
    }

    private fun summary(uid: Long, subject: String) = MailSummary(
        uid = uid, uidValidity = 10, sender = "sender", subject = subject,
        sentAt = 0, unread = false, hasAttachment = false, owaItemId = "id-$uid",
    )
}
