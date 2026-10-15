package com.rteats.mpeineo.data

import android.content.Context

enum class InterfaceTheme { SYSTEM, LIGHT, DARK }

class InterfacePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("interface_settings", Context.MODE_PRIVATE)

    val theme: InterfaceTheme
        get() = InterfaceTheme.entries.firstOrNull {
            it.name == prefs.getString("theme", InterfaceTheme.SYSTEM.name)
        } ?: InterfaceTheme.SYSTEM

    val navigationHaptics: Boolean
        get() = prefs.getBoolean("navigation_haptics", false)

    fun setTheme(value: InterfaceTheme) {
        prefs.edit().putString("theme", value.name).apply()
    }

    fun setNavigationHaptics(enabled: Boolean) {
        prefs.edit().putBoolean("navigation_haptics", enabled).apply()
    }
}
