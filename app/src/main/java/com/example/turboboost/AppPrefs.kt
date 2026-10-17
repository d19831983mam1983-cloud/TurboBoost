package com.example.turboboost

import android.content.Context

class AppPrefs(ctx: Context) {

    private val prefs = ctx.getSharedPreferences("turboboost", Context.MODE_PRIVATE)

    fun isActive(): Boolean = prefs.getBoolean("active", false)

    fun setActive(v: Boolean) {
        prefs.edit().putBoolean("active", v).apply()
    }
}
