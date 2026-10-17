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

    fun getSenseLevel(): Int = prefs.getInt("sense_level", -1)

    fun setSenseLevel(level: Int) {
        prefs.edit().putInt("sense_level", level).apply()
    }

    fun getThermalLevel(): Int = prefs.getInt("thermal_level", -1)

    fun setThermalLevel(level: Int) {
        prefs.edit().putInt("thermal_level", level).apply()
    }
}
