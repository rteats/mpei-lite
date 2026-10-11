package com.rteats.mpeineo.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarsDocumentPolicyTest {
    @Test fun authenticBarsDocumentsAreTrusted() {
        assertTrue(BarsDocumentPolicy.isTrusted("https://bars.mpei.ru/bars_web/"))
        assertTrue(BarsDocumentPolicy.isTrusted("https://bars.mpei.ru/bars_web/Auth/Login"))
    }

    @Test fun lookalikeHostsAndInternalDocumentsAreRejected() {
        assertFalse(BarsDocumentPolicy.isTrusted("https://bars.mpei.ru.evil.example/bars_web/"))
        assertFalse(BarsDocumentPolicy.isTrusted("http://bars.mpei.ru/bars_web/"))
        assertFalse(BarsDocumentPolicy.isTrusted("about:blank"))
        assertFalse(BarsDocumentPolicy.isTrusted("https://other.example/"))
    }

    @Test fun stalePageCompletionDoesNotMatchCurrentDocument() {
        assertFalse(BarsDocumentPolicy.isCurrent(
            "https://bars.mpei.ru/bars_web/",
            "https://bars.mpei.ru/bars_web/ST_Study/Main/Main",
        ))
    }

    @Test fun queryOnlyChangesDoNotInvalidateCurrentDocument() {
        assertTrue(BarsDocumentPolicy.isCurrent(
            "https://bars.mpei.ru/bars_web/Auth/Login?state=old",
            "https://bars.mpei.ru/bars_web/Auth/Login?state=new",
        ))
    }

    @Test fun unknownPageCannotCompleteNavigation() {
        assertFalse(BarsDocumentPolicy.isCurrent("about:blank", "about:blank"))
    }
}
