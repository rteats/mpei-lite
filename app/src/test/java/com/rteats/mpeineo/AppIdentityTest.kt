package com.rteats.mpeineo

import org.junit.Assert.assertEquals
import org.junit.Test

class AppIdentityTest {
    @Test fun updatesUseTheSingleMpeiLitePackage() {
        assertEquals("com.rteats.mpeilite", BuildConfig.APPLICATION_ID)
    }
}
