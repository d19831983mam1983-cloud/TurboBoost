package com.example.turboboost

import android.content.Context

class AppPrefs(ctx: Context) {

    private val prefs = ctx.getSharedPreferences("turboboost", Context.MODE_PRIVATE)

    fun isActive(): Boolean = prefs.getBoolean("active", false)

    fun setActive(v: Boolean) {
        prefs.edit().putBoolean("active", v).apply()
    }

    fun getTargetHz(): Int = prefs.getInt("target_hz", 120)

    fun setTargetHz(hz: Int) {
        prefs.edit().putInt("target_hz", hz).apply()
    }

    fun getLastPackage(): String = prefs.getString("last_pkg", "") ?: ""

    fun setLastPackage(pkg: String) {
        prefs.edit().putString("last_pkg", pkg).apply()
    }

    fun getLastUptime(): Long = prefs.getLong("last_uptime", 0L)

    fun saveUptime(uptime: Long) {
        prefs.edit().putLong("last_uptime", uptime).apply()
    }
}
