package com.rteats.mpeineo.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PrivilegedInstallCommandTest {
    @Test fun installUsesCompletedApkFileInsteadOfStreamingStdin() {
        val path = "/data/local/tmp/mpei-neo-update-123.apk"
        val command = privilegedInstallCommand(path)
        assertEquals(listOf("pm", "install", "-r", path), command)
        assertFalse(command.contains("-S"))
        assertFalse(command.contains("-"))
    }
}
