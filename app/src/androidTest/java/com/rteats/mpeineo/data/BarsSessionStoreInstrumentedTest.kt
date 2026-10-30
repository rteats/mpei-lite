package com.rteats.mpeineo.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** No university account or network is needed for encryption tests. */
@RunWith(AndroidJUnit4::class)
class BarsSessionStoreInstrumentedTest {
    @Test fun encryptedSessionRoundTripAndLogout() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = BarsSessionStore(context)
        store.clear()
        try {
            val sample = BarsSavedSession(
                System.currentTimeMillis(),
                "https://bars.mpei.ru/bars_web/ST_Study/Main/Main?studentID=test",
                "https://bars.mpei.ru/bars_web/ST_Study/Main/Summary?studentID=test",
                listOf("ses_bars=test-only; path=/bars_web/; httponly; secure"),
            )
            store.write(sample)
            assertEquals(sample, store.read())
            val raw = context.noBackupFilesDir.resolve("bars_session_v1.bin").readBytes()
            assertTrue(!String(raw, Charsets.ISO_8859_1).contains("test-only"))
            store.clear()
            assertNull(store.read())
        } finally {
            store.clear()
        }
    }
}
