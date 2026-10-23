package com.rteats.mpeineo.update

import android.content.Context

/**
 * Stores only public route enum names, never WebView URLs, search terms,
 * credentials, or message details. Restoration is armed only for an update.
 */
internal object UpdateRelaunchState {
    private const val NAME = "update_route_relaunch"
    private val allowed = setOf("SCHEDULE", "BARS", "MAIL", "MAPS", "SETTINGS")

    fun record(context: Context, routes: List<String>) {
        val names = routes.filter { it in allowed }.takeLast(2)
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString("current_routes", names.joinToString(",")).apply()
    }

    fun arm(context: Context, expectedVersion: Int) {
        val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("restore_routes", prefs.getString("current_routes", "").orEmpty())
            .putInt("expected_version", expectedVersion)
            .putLong("armed_at", System.currentTimeMillis())
            .commit()
    }

    fun consume(context: Context, currentVersion: Int): List<String> {
        val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        val expected = prefs.getInt("expected_version", Int.MAX_VALUE)
        val armedAt = prefs.getLong("armed_at", 0L)
        val valid = expected > 0 && currentVersion >= expected &&
            System.currentTimeMillis() - armedAt in 0L..(2L * 24L * 60L * 60L * 1000L)
        val routes = if (valid) prefs.getString("restore_routes", "").orEmpty()
            .split(",").filter { it in allowed }.takeLast(2) else emptyList()
        if (valid || (armedAt != 0L && System.currentTimeMillis() - armedAt > 172800000L)) {
            prefs.edit().remove("restore_routes").remove("expected_version")
                .remove("armed_at").apply()
        }
        return routes
    }
}
