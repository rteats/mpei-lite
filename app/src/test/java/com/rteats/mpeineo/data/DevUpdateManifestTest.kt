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
            """{"versionCode":163,"versionName":"0.4.0-dev.163","sha256":"$checksum"}""",
            gson,
        )
        assertEquals(163, data.versionCode)
        assertEquals("dev", data.tagName)
        assertEquals("0.4.0-dev.163", data.versionName)
        assertEquals(checksum, data.sha256)
        assertTrue(data.apkUrl.endsWith("/releases/download/dev/MPEI-Neo-Dev.apk"))
    }

    @Test fun prefersSmallAbiApkWithPublishedDigest() {
        val armDigest = "a".repeat(64)
        val x86Digest = "c".repeat(64)
        val payload = """{"versionCode":163,"versionName":"0.4.0-dev.163",
            "sha256":"$checksum",
            "apkVariants":{"arm64-v8a":"$armDigest","x86_64":"$x86Digest"}}"""
        val chosen = parseDevUpdateManifest(payload, gson,
            listOf("arm64-v8a", "armeabi-v7a"))
        assertEquals("MPEI-Neo-Dev-arm64-v8a.apk", chosen.apkName)
        assertEquals(armDigest, chosen.sha256)
        assertTrue(chosen.apkUrl.endsWith("/MPEI-Neo-Dev-arm64-v8a.apk"))
    }

    @Test fun unsupportedOrMalformedAbiFallsBackToUniversal() {
        val payload = """{"versionCode":163,"versionName":"0.4.0-dev.163",
            "sha256":"$checksum",
            "apkVariants":{"arm64-v8a":"invalid"}}"""
        val chosen = parseDevUpdateManifest(payload, gson, listOf("arm64-v8a"))
        assertEquals("MPEI-Neo-Dev.apk", chosen.apkName)
        assertEquals(checksum, chosen.sha256)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMismatchedVersion() {
        parseDevUpdateManifest(
            """{"versionCode":162,"versionName":"0.4.0-dev.163","sha256":"$checksum"}""",
            gson,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMissingChecksum() {
        parseDevUpdateManifest(
            """{"versionCode":163,"versionName":"0.4.0-dev.163","sha256":""}""",
            gson,
        )
    }
}
