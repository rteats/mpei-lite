package com.rteats.mpeineo.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DevUpdateManifestTest {
    private val checksum = "b".repeat(64)
    private val gson = Gson()

    @Test fun parsesPublishedManifestWithoutGithubApi() {
        val data = parseDevUpdateManifest(
            """{"versionCode":163,"versionName":"2026.10.31.163","sha256":"$checksum"}""",
            gson,
        )
        assertEquals(163, data.versionCode)
        assertEquals("latest", data.tagName)
        assertEquals("2026.10.31.163", data.versionName)
        assertEquals(checksum, data.sha256)
        assertTrue(data.apkUrl.endsWith("/releases/download/latest/MPEI-Lite.apk"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMismatchedVersion() {
        parseDevUpdateManifest(
            """{"versionCode":162,"versionName":"2026.10.31.163","sha256":"$checksum"}""",
            gson,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMissingChecksum() {
        parseDevUpdateManifest(
            """{"versionCode":163,"versionName":"2026.10.31.163","sha256":""}""",
            gson,
        )
    }
}
