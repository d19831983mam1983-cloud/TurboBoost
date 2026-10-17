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

    fun isSenseActive(): Boolean = prefs.getBoolean("sense_active", false)

    fun setSenseActive(v: Boolean) {
        prefs.edit().putBoolean("sense_active", v).apply()
    }
}
