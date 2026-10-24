package com.rteats.mpeineo.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MailNoticePolicyTest {
    @Test fun sentAndSavedActionsUseTransientSnackbar() {
        assertTrue(mailNoticeInSnackbar("Письмо отправлено"))
        assertTrue(mailNoticeInSnackbar("Сохранено: file.pdf"))
    }

    @Test fun offlineNoticesStayVisibleAndNoUnrelatedMessageIsTransient() {
        assertFalse(mailNoticeInSnackbar("Показана сохранённая переписка"))
        assertFalse(mailNoticeInSnackbar(null))
        assertFalse(mailNoticeInSnackbar("Не удалось отправить письмо"))
    }
}
