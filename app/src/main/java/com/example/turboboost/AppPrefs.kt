package com.example.turboboost

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class GameProfile(
    val pkg: String,
    val mode: Int,
    val sense: Int,
    val thermal: Int,
    val turbo: Boolean,
    val hz: Int,
    val gpuBoost: Boolean,
    val bgLimit: Boolean
)

class AppPrefs(ctx: Context) {

    private val prefs = ctx.getSharedPreferences("turboboost", Context.MODE_PRIVATE)

    fun isActive(): Boolean = prefs.getBoolean("active", false)
    fun setActive(v: Boolean) = prefs.edit().putBoolean("active", v).apply()

    fun isTurboActive(): Boolean = prefs.getBoolean("turbo_active", false)
    fun setTurboActive(v: Boolean) = prefs.edit().putBoolean("turbo_active", v).apply()

    fun getTargetHz(): Int = prefs.getInt("target_hz", 120)
    fun setTargetHz(hz: Int) = prefs.edit().putInt("target_hz", hz).apply()

    fun getLastPackage(): String = prefs.getString("last_pkg", "") ?: ""
    fun setLastPackage(pkg: String) { prefs.edit().putString("last_pkg", pkg).commit() }

    fun getSenseLevel(): Int = prefs.getInt("sense_level", -1)
    fun setSenseLevel(level: Int) = prefs.edit().putInt("sense_level", level).apply()

    fun getThermalLevel(): Int = prefs.getInt("thermal_level", -1)
    fun setThermalLevel(level: Int) = prefs.edit().putInt("thermal_level", level).apply()

    fun getPerfMode(): Int = prefs.getInt("perf_mode", 0)
    fun setPerfMode(mode: Int) = prefs.edit().putInt("perf_mode", mode).apply()

    fun isGpuBoost(): Boolean = prefs.getBoolean("gpu_boost", false)
    fun setGpuBoost(v: Boolean) = prefs.edit().putBoolean("gpu_boost", v).apply()

    fun isBackgroundLimit(): Boolean = prefs.getBoolean("bg_limit", false)
    fun setBackgroundLimit(v: Boolean) = prefs.edit().putBoolean("bg_limit", v).apply()

    fun isFpsMonitor(): Boolean = prefs.getBoolean("fps_monitor", true)
    fun setFpsMonitor(v: Boolean) = prefs.edit().putBoolean("fps_monitor", v).apply()

    fun getLastPing(): String = prefs.getString("last_ping", "") ?: ""
    fun setLastPing(v: String) = prefs.edit().putString("last_ping", v).apply()

    fun getLastFps(): Int = prefs.getInt("last_fps", 0)
    fun setLastFps(v: Int) = prefs.edit().putInt("last_fps", v).apply()

    fun getLastCacheTrim(): Long = prefs.getLong("last_cache_trim", 0L)
    fun setLastCacheTrim(v: Long) = prefs.edit().putLong("last_cache_trim", v).apply()

    fun getActiveProfilePkg(): String = prefs.getString("active_profile_pkg", "") ?: ""
    fun setActiveProfilePkg(pkg: String) = prefs.edit().putString("active_profile_pkg", pkg).apply()

    fun getUiScrollY(): Int = prefs.getInt("ui_scroll_y", 0)
    fun setUiScrollY(y: Int) = prefs.edit().putInt("ui_scroll_y", y).apply()

    fun getProfiles(): List<GameProfile> {
        val raw = prefs.getString("profiles", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<GameProfile>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    GameProfile(
                        o.optString("pkg"),
                        o.optInt("mode", 0),
                        o.optInt("sense", 0),
                        o.optInt("thermal", 0),
                        o.optBoolean("turbo", false),
                        o.optInt("hz", 120),
                        o.optBoolean("gpuBoost", false),
                        o.optBoolean("bgLimit", false)
                    )
                )
            }
            list
        } catch (e: Throwable) {
            emptyList()
        }
    }

    fun saveProfile(p: GameProfile) {
        val list = getProfiles().toMutableList()
        list.removeAll { it.pkg == p.pkg }
        list.add(p)
        val arr = JSONArray()
        for (item in list) {
            val o = JSONObject()
            o.put("pkg", item.pkg)
            o.put("mode", item.mode)
            o.put("sense", item.sense)
            o.put("thermal", item.thermal)
            o.put("turbo", item.turbo)
            o.put("hz", item.hz)
            o.put("gpuBoost", item.gpuBoost)
            o.put("bgLimit", item.bgLimit)
            arr.put(o)
        }
        prefs.edit().putString("profiles", arr.toString()).commit()
    }

    fun deleteProfile(pkg: String) {
        val list = getProfiles().filter { it.pkg != pkg }
        val arr = JSONArray()
        for (item in list) {
            val o = JSONObject()
            o.put("pkg", item.pkg)
            o.put("mode", item.mode)
            o.put("sense", item.sense)
            o.put("thermal", item.thermal)
            o.put("turbo", item.turbo)
            o.put("hz", item.hz)
            o.put("gpuBoost", item.gpuBoost)
            o.put("bgLimit", item.bgLimit)
            arr.put(o)
        }
        prefs.edit().putString("profiles", arr.toString()).commit()
    }

    fun findProfile(pkg: String): GameProfile? = getProfiles().firstOrNull { it.pkg == pkg }
}
