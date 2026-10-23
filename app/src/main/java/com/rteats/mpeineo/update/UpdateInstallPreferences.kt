package com.rteats.mpeineo.update

import android.content.Context
import com.rteats.mpeineo.BuildConfig

internal enum class InstallMode { STANDARD, SHIZUKU }

internal class UpdateInstallPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("update_install_v1", Context.MODE_PRIVATE)

    var mode: InstallMode
        get() = runCatching {
            InstallMode.valueOf(prefs.getString("mode", InstallMode.STANDARD.name)!!)
        }.getOrDefault(InstallMode.STANDARD)
        set(value) { prefs.edit().putString("mode", value.name).apply() }

    var automatic: Boolean
        get() = prefs.getBoolean("automatic", false)
        set(value) { prefs.edit().putBoolean("automatic", value).apply() }

    var unmeteredOnly: Boolean
        get() = prefs.getBoolean("unmetered_only", true)
        set(value) { prefs.edit().putBoolean("unmetered_only", value).apply() }

    val supportsAutomaticUpdates: Boolean
        get() = BuildConfig.UPDATE_CHANNEL in setOf("stable", "dev") &&
            !BuildConfig.APPLICATION_ID.matches(Regex(".*\\.pr\\d+$"))
}
