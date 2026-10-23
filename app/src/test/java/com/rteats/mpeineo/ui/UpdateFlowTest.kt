package com.rteats.mpeineo.ui

import com.rteats.mpeineo.data.GithubReleaseInfo
import com.rteats.mpeineo.update.InstallMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateFlowTest {
    private fun release(code: Int) = GithubReleaseInfo(
        versionCode = code,
        versionName = "0.4.0-dev.$code",
        tagName = "dev",
        title = "Dev $code",
        notes = "",
        releasePageUrl = "https://example.com/release",
        apkUrl = "https://example.com/app.apk",
        apkName = "app.apk",
        sha256 = null,
    )

    @Test fun updateChecksPreserveInstallerPreferences() {
        val configured = UpdateUiState(
            installMode = InstallMode.SHIZUKU,
            automaticUpdates = true,
            wifiOnly = false,
            shizukuStatus = "Shizuku подключён",
        )
        val afterCheck = configured.afterReleaseCheck(release(201), installedVersionCode = 190)
        assertEquals(InstallMode.SHIZUKU, afterCheck.installMode)
        assertTrue(afterCheck.automaticUpdates)
        assertFalse(afterCheck.wifiOnly)
        assertEquals("Shizuku подключён", afterCheck.shizukuStatus)
    }

    @Test fun noUpdateClearsStaleDownloadedApk() {
        val stale = UpdateUiState(
            phase = UpdatePhase.READY_TO_INSTALL,
            release = release(200),
            downloadedApkPath = "/cache/updates/app.apk",
        )
        val afterCheck = stale.afterReleaseCheck(release(200), installedVersionCode = 200)
        assertEquals(UpdatePhase.UP_TO_DATE, afterCheck.phase)
        assertNull(afterCheck.downloadedApkPath)
    }

    @Test fun firstSuccessfulCheckOffersDownload() {
        val result = UpdateUiState(
            phase = UpdatePhase.IDLE,
            checking = true,
        ).afterReleaseCheck(release(200), installedVersionCode = 194)
        assertEquals(UpdatePhase.AVAILABLE, result.phase)
        assertFalse(result.checking)
        assertEquals(200, result.release?.versionCode)
        assertNull(result.downloadedApkPath)
    }

    @Test fun recheckKeepsPreviouslyVerifiedDownloadOfSameRelease() {
        val checked = UpdateUiState(
            phase = UpdatePhase.READY_TO_INSTALL,
            checking = true,
            release = release(200),
            progressPercent = 100,
            downloadedApkPath = "/cache/updates/dev200.apk",
        ).afterReleaseCheck(release(200), installedVersionCode = 194)
        assertEquals(UpdatePhase.READY_TO_INSTALL, checked.phase)
        assertEquals("/cache/updates/dev200.apk", checked.downloadedApkPath)
        assertEquals(100, checked.progressPercent)
        assertFalse(checked.checking)
    }

    @Test fun newerReleaseReplacesReadyOldUpdateWithDownloadAction() {
        val checked = UpdateUiState(
            phase = UpdatePhase.READY_TO_INSTALL,
            release = release(200),
            downloadedApkPath = "/cache/updates/dev200.apk",
        ).afterReleaseCheck(release(201), installedVersionCode = 194)
        assertEquals(UpdatePhase.AVAILABLE, checked.phase)
        assertEquals(201, checked.release?.versionCode)
        assertNull(checked.downloadedApkPath)
    }

    @Test fun upToDateKeepsCheckButtonAvailableWithoutDownload() {
        val checked = UpdateUiState().afterReleaseCheck(
            release(194), installedVersionCode = 194,
        )
        assertEquals(UpdatePhase.UP_TO_DATE, checked.phase)
        assertFalse(checked.checking)
        assertTrue(checked.release != null)
    }

    @Test fun checkWithNoDownloadedPathDoesNotOfferInstall() {
        val checked = UpdateUiState(
            phase = UpdatePhase.READY_TO_INSTALL,
            release = release(200),
        ).afterReleaseCheck(release(200), installedVersionCode = 194)
        assertEquals(UpdatePhase.AVAILABLE, checked.phase)
        assertNull(checked.downloadedApkPath)
    }
}
