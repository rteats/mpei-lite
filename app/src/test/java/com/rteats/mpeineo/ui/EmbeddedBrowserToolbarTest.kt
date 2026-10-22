package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmbeddedBrowserToolbarTest {
    @Test fun copiesExactHttpAndHttpsPageAddresses() {
        assertEquals(
            "https://bars.mpei.ru/bars_web/ST_Study/Main/Main?student=123",
            copyableBrowserUrl("https://bars.mpei.ru/bars_web/ST_Study/Main/Main?student=123"),
        )
        assertEquals(
            "https://mail.mpei.ru/owa/#path=/mail",
            copyableBrowserUrl("https://mail.mpei.ru/owa/#path=/mail"),
        )
    }

    @Test fun neverCopiesInternalOrNonWebSchemes() {
        listOf(null, "", " ", "about:blank", "javascript:alert(1)", "data:text/plain,test",
            "file:///data/user/0/test", "https://", "mailto:a@example.com", "not a URL")
            .forEach { assertNull(copyableBrowserUrl(it)) }
    }

    @Test fun showsHostWithoutSensitivePathOrQuery() {
        assertEquals(
            "mail.mpei.ru",
            browserSiteLabel("https://mail.mpei.ru/owa/auth/login?token=secret"),
        )
        assertEquals("bars.mpei.ru", browserSiteLabel("https://bars.mpei.ru/bars_web/"))
        assertEquals("Браузер", browserSiteLabel("about:blank"))
    }
}
