package com.rteats.mpeineo.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MailNotificationFormattingTest {
    @Test fun emptySubjectShowsAngleBracketFallback() {
        assertEquals("<Без темы>", mailNotificationSubject(""))
        assertEquals("<Без темы>", mailNotificationSubject("   "))
        assertEquals("Тема", mailNotificationSubject(" Тема "))
    }

    @Test fun snippetCollapsesWhitespaceAndTruncates() {
        assertEquals("Первая Вторая", mailNotificationSnippet(" Первая\n Вторая "))
        assertEquals("abc", mailNotificationSnippet("abcde", 3))
    }

    @Test fun combinedLineIncludesPreviewOnlyWhenPresent() {
        assertEquals("<Без темы>", mailNotificationLine("", ""))
        assertEquals("<Без темы> · Содержание", mailNotificationLine("", "Содержание"))
    }
}
