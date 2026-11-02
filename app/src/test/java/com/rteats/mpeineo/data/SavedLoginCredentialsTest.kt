package com.rteats.mpeineo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SavedLoginCredentialsTest {
    @Test fun roundTripSupportsUnicodeAndSpecialCharacters() {
        val original = SavedLoginCredentials("студент@mpei.ru", "пасс&word=\u002B# 123")
        assertEquals(original, SavedLoginCredentials.deserialize(original.serialize()))
    }

    @Test fun rejectsWrongVersionAndMissingPassword() {
        assertThrows(IllegalArgumentException::class.java) {
            SavedLoginCredentials.deserialize(
                """{"v":2,"username":"test","password":"test"}""".toByteArray(),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SavedLoginCredentials.deserialize(
                """{"v":1,"username":"test","password":""}""".toByteArray(),
            )
        }
    }

    @Test fun storesForMailAndBarsDoNotShareTheSameKeyOrFile() {
        assertNotEquals(SavedLoginService.MAIL.filename, SavedLoginService.BARS.filename)
        assertNotEquals(SavedLoginService.MAIL.keyAlias, SavedLoginService.BARS.keyAlias)
    }
}
