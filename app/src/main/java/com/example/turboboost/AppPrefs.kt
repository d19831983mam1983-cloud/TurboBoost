package com.example.turboboost

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class GameProfile(val pkg: String, val mode: Int, val sense: Int, val thermal: Int, val turbo: Boolean, val hz: Int, val gpuBoost: Boolean, val bgLimit: Boolean)

class AppPrefs(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("turboboost", Context.MODE_PRIVATE)
    fun isActive(): Boolean = prefs.getBoolean("active", false)
    fun setActive(v: Boolean) = prefs.edit().putBoolean("active", v).apply()
    fun getTargetHz(): Int = prefs.getInt("target_hz", 120)
    fun setTargetHz(hz: Int) = prefs.edit().putInt("target_hz", hz).apply()
    fun getLastPackage(): String { val v = prefs.getString("last_pkg", "") ?: ""; return if (GameBlocklist.isBlocked(v)) "" else v }
    fun setLastPackage(pkg: String) { val v = pkg ?: ""; prefs.edit().putString("last_pkg", if (GameBlocklist.isBlocked(v)) "" else v).commit() }
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
    fun getActiveProfilePkg(): String = prefs.getString("active_profile_pkg", "") ?: ""
    fun setActiveProfilePkg(pkg: String) = prefs.edit().putString("active_profile_pkg", pkg ?: "").apply()
    fun getUiScrollY(): Int = prefs.getInt("ui_scroll_y", 0)
    fun setUiScrollY(y: Int) = prefs.edit().putInt("ui_scroll_y", y).apply()
    fun isJoyoseDisabled(): Boolean = prefs.getBoolean("joyose_disabled", false)
    fun setJoyoseDisabled(v: Boolean) = prefs.edit().putBoolean("joyose_disabled", v).apply()
    fun isThreadsBoost(): Boolean = prefs.getBoolean("threads_boost", false)
    fun setThreadsBoost(v: Boolean) = prefs.edit().putBoolean("threads_boost", v).apply()
    fun isMglru(): Boolean = prefs.getBoolean("mglru", false)
    fun setMglru(v: Boolean) = prefs.edit().putBoolean("mglru", v).apply()
    fun getThemeIndex(): Int = prefs.getInt("theme_index", 0)
    fun setThemeIndex(i: Int) = prefs.edit().putInt("theme_index", i).apply()
    fun getBackgroundUri(): String = prefs.getString("bg_uri", "") ?: ""
    fun setBackgroundUri(uri: String) = prefs.edit().putString("bg_uri", uri ?: "").apply()
    fun getDownscaleLevel(): Int = prefs.getInt("downscale_level", 2)
    fun setDownscaleLevel(level: Int) = prefs.edit().putInt("downscale_level", level).apply()
    fun getGfx(): Int = prefs.getInt("gfx_preset", 0)
    fun setGfx(v: Int) = prefs.edit().putInt("gfx_preset", v).apply()
    fun getStretch(): Int = prefs.getInt("stretch_mode", 0)
    fun setStretch(v: Int) = prefs.edit().putInt("stretch_mode", v).apply()

    fun getSavedGames(): List<String> {
        val raw = prefs.getString("saved_games", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return try { val arr = JSONArray(raw); val list = mutableListOf<String>(); for (i in 0 until arr.length()) { val s = arr.optString(i, ""); if (s.isNotBlank() && !GameBlocklist.isBlocked(s)) list.add(s) }; list } catch (e: Throwable) { emptyList() }
    }

    fun saveGame(pkg: String) {
        if (pkg.isBlank() || GameBlocklist.isBlocked(pkg)) return
        val list = getSavedGames().toMutableList()
        if (!list.contains(pkg)) { list.add(pkg); val arr = JSONArray(); for (p in list) arr.put(p); prefs.edit().putString("saved_games", arr.toString()).commit() }
    }

    fun removeGame(pkg: String) {
        if (pkg.isBlank()) return
        val list = getSavedGames().filter { it != pkg }; val arr = JSONArray(); for (p in list) arr.put(p); prefs.edit().putString("saved_games", arr.toString()).commit()
    }

    fun getProfiles(): List<GameProfile> {
        val raw = prefs.getString("profiles", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw); val list = mutableListOf<GameProfile>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val pkg = o.optString("pkg", "")
                if (pkg.isBlank()) continue
                list.add(GameProfile(pkg, o.optInt("mode", 0), o.optInt("sense", 0), o.optInt("thermal", 0), o.optBoolean("turbo", false), o.optInt("hz", 120), o.optBoolean("gpuBoost", false), o.optBoolean("bgLimit", false)))
            }
            list
        } catch (e: Throwable) { emptyList() }
    }

    fun saveProfile(p: GameProfile) {
        if (p.pkg.isBlank() || GameBlocklist.isBlocked(p.pkg)) return
        val list = getProfiles().toMutableList(); list.removeAll { it.pkg == p.pkg }; list.add(p)
        val arr = JSONArray(); for (item in list) { val o = JSONObject(); o.put("pkg", item.pkg); o.put("mode", item.mode); o.put("sense", item.sense); o.put("thermal", item.thermal); o.put("turbo", item.turbo); o.put("hz", item.hz); o.put("gpuBoost", item.gpuBoost); o.put("bgLimit", item.bgLimit); arr.put(o) }
        prefs.edit().putString("profiles", arr.toString()).commit()
    }

    fun deleteProfile(pkg: String) {
        if (pkg.isBlank()) return
        val list = getProfiles().filter { it.pkg != pkg }; val arr = JSONArray()
        for (item in list) { val o = JSONObject(); o.put("pkg", item.pkg); o.put("mode", item.mode); o.put("sense", item.sense); o.put("thermal", item.thermal); o.put("turbo", item.turbo); o.put("hz", item.hz); o.put("gpuBoost", item.gpuBoost); o.put("bgLimit", item.bgLimit); arr.put(o) }
        prefs.edit().putString("profiles", arr.toString()).commit()
    }

    fun findProfile(pkg: String): GameProfile? = getProfiles().firstOrNull { it.pkg == pkg }
}
