package com.example.turboboost

import android.content.Context

class AppPrefs(ctx: Context) {

    private val prefs = ctx.getSharedPreferences("turboboost", Context.MODE_PRIVATE)

    fun isActive(): Boolean = prefs.getBoolean("active", false)

    fun setActive(v: Boolean) {
        prefs.edit().putBoolean("active", v).apply()
    }

    fun saveOrigGovernors(govs: String) {
        prefs.edit().putString("orig_govs", govs).apply()
    }

    fun getOrigGovernors(): String = prefs.getString("orig_govs", "") ?: ""

    fun hasOrigGovernors(): Boolean = prefs.contains("orig_govs")

    fun saveOrigGpuPath(path: String) {
        prefs.edit().putString("orig_gpu_path", path).apply()
    }

    fun getOrigGpuPath(): String = prefs.getString("orig_gpu_path", "") ?: ""
}
