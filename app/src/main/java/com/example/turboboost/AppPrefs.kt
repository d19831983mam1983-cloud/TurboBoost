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

    fun isAutoSession(): Boolean = prefs.getBoolean("auto_session", false)
    fun setAutoSession(v: Boolean) = prefs.edit().putBoolean("auto_session", v).apply()
    fun isOverlayEnabled(): Boolean = prefs.getBoolean("overlay_enabled", false)
    fun setOverlayEnabled(v: Boolean) = prefs.edit().putBoolean("overlay_enabled", v).apply()
    fun isDisplayTweaked(): Boolean = prefs.getBoolean("display_tweaked", false)
    fun setDisplayTweaked(v: Boolean) = prefs.edit().putBoolean("display_tweaked", v).apply()

    fun isGameOverlay(): Boolean = prefs.getBoolean("game_overlay", false)
    fun setGameOverlay(v: Boolean) = prefs.edit().putBoolean("game_overlay", v).apply()
    fun isCrosshair(): Boolean = prefs.getBoolean("crosshair", false)
    fun setCrosshair(v: Boolean) = prefs.edit().putBoolean("crosshair", v).apply()
    fun getCrosshairColor(): Int = prefs.getInt("crosshair_color", 0).coerceIn(0, 7)
    fun setCrosshairColor(v: Int) = prefs.edit().putInt("crosshair_color", v.coerceIn(0, 7)).apply()
    fun getCrosshairSize(): Int = prefs.getInt("crosshair_size", 3).coerceIn(1, 5)
    fun setCrosshairSize(v: Int) = prefs.edit().putInt("crosshair_size", v.coerceIn(1, 5)).apply()
    fun isSnow(): Boolean = prefs.getBoolean("snow", false)
    fun setSnow(v: Boolean) = prefs.edit().putBoolean("snow", v).apply()
    fun getSnowDensity(): Int = prefs.getInt("snow_density", 20).coerceIn(0, 100)
    fun setSnowDensity(v: Int) = prefs.edit().putInt("snow_density", v.coerceIn(0, 100)).apply()
    fun getSnowSpeed(): Int = prefs.getInt("snow_speed", 2).coerceIn(1, 5)
    fun setSnowSpeed(v: Int) = prefs.edit().putInt("snow_speed", v.coerceIn(1, 5)).apply()
    fun getSnowSize(): Int = prefs.getInt("snow_size", 2).coerceIn(1, 5)
    fun setSnowSize(v: Int) = prefs.edit().putInt("snow_size", v.coerceIn(1, 5)).apply()
    fun getParticleClearCenter(): Int = prefs.getInt("particle_clear", 30).coerceIn(10, 45)
    fun setParticleClearCenter(v: Int) = prefs.edit().putInt("particle_clear", v.coerceIn(10, 45)).apply()

    fun isFeat(key: String, def: Boolean = false): Boolean = prefs.getBoolean("feat_$key", def)
    fun setFeat(key: String, v: Boolean) = prefs.edit().putBoolean("feat_$key", v).apply()
    fun getFeatInt(key: String, def: Int = 0): Int = prefs.getInt("feati_$key", def)
    fun setFeatInt(key: String, v: Int) = prefs.edit().putInt("feati_$key", v).apply()

    fun getCrosshairStyle(): Int = prefs.getInt("crosshair_style", 1).coerceIn(0, 11)
    fun setCrosshairStyle(v: Int) = prefs.edit().putInt("crosshair_style", v.coerceIn(0, 11)).apply()
    fun getCrosshairOpacity(): Int = prefs.getInt("crosshair_opacity", 100).coerceIn(10, 100)
    fun setCrosshairOpacity(v: Int) = prefs.edit().putInt("crosshair_opacity", v.coerceIn(10, 100)).apply()
    fun isCrosshairOutline(): Boolean = prefs.getBoolean("crosshair_outline", true)
    fun setCrosshairOutline(v: Boolean) = prefs.edit().putBoolean("crosshair_outline", v).apply()
    fun getCrosshairGap(): Int = prefs.getInt("crosshair_gap", 2).coerceIn(0, 5)
    fun setCrosshairGap(v: Int) = prefs.edit().putInt("crosshair_gap", v.coerceIn(0, 5)).apply()
    fun getCrosshairOffsetX(): Int = prefs.getInt("crosshair_ox", 0).coerceIn(-200, 200)
    fun setCrosshairOffsetX(v: Int) = prefs.edit().putInt("crosshair_ox", v.coerceIn(-200, 200)).apply()
    fun getCrosshairOffsetY(): Int = prefs.getInt("crosshair_oy", 0).coerceIn(-200, 200)
    fun setCrosshairOffsetY(v: Int) = prefs.edit().putInt("crosshair_oy", v.coerceIn(-200, 200)).apply()
    fun getParticleMode(): Int = prefs.getInt("particle_mode", 0).coerceIn(0, 3)
    fun setParticleMode(v: Int) = prefs.edit().putInt("particle_mode", v.coerceIn(0, 3)).apply()
    fun isFpsHud(): Boolean = prefs.getBoolean("fps_hud", false)
    fun setFpsHud(v: Boolean) = prefs.edit().putBoolean("fps_hud", v).apply()
    fun isNetHud(): Boolean = prefs.getBoolean("net_hud", false)
    fun setNetHud(v: Boolean) = prefs.edit().putBoolean("net_hud", v).apply()
    fun isTempHud(): Boolean = prefs.getBoolean("temp_hud", false)
    fun setTempHud(v: Boolean) = prefs.edit().putBoolean("temp_hud", v).apply()
    fun isSessionTimer(): Boolean = prefs.getBoolean("session_timer", false)
    fun setSessionTimer(v: Boolean) = prefs.edit().putBoolean("session_timer", v).apply()
    fun isGridOverlay(): Boolean = prefs.getBoolean("grid_overlay", false)
    fun setGridOverlay(v: Boolean) = prefs.edit().putBoolean("grid_overlay", v).apply()
    fun isFovCircle(): Boolean = prefs.getBoolean("fov_circle", false)
    fun setFovCircle(v: Boolean) = prefs.edit().putBoolean("fov_circle", v).apply()
    fun isVignette(): Boolean = prefs.getBoolean("vignette", false)
    fun setVignette(v: Boolean) = prefs.edit().putBoolean("vignette", v).apply()
    fun isScanlines(): Boolean = prefs.getBoolean("scanlines", false)
    fun setScanlines(v: Boolean) = prefs.edit().putBoolean("scanlines", v).apply()
    fun isNightTint(): Boolean = prefs.getBoolean("night_tint", false)
    fun setNightTint(v: Boolean) = prefs.edit().putBoolean("night_tint", v).apply()
    fun getWatermarkStyle(): Int = prefs.getInt("wm_style", 1).coerceIn(0, 4)
    fun setWatermarkStyle(v: Int) = prefs.edit().putInt("wm_style", v.coerceIn(0, 4)).apply()
    fun getWatermarkOpacity(): Int = prefs.getInt("wm_opacity", 85).coerceIn(20, 100)
    fun setWatermarkOpacity(v: Int) = prefs.edit().putInt("wm_opacity", v.coerceIn(20, 100)).apply()
    fun getWatermarkPos(): Int = prefs.getInt("wm_pos", 0).coerceIn(0, 4)
    fun setWatermarkPos(v: Int) = prefs.edit().putInt("wm_pos", v.coerceIn(0, 4)).apply()
    fun isWmPulse(): Boolean = prefs.getBoolean("wm_pulse", true)
    fun setWmPulse(v: Boolean) = prefs.edit().putBoolean("wm_pulse", v).apply()
    fun isAutoHideHud(): Boolean = prefs.getBoolean("auto_hide_hud", false)
    fun setAutoHideHud(v: Boolean) = prefs.edit().putBoolean("auto_hide_hud", v).apply()
    fun isEdgeFlash(): Boolean = prefs.getBoolean("edge_flash", false)
    fun setEdgeFlash(v: Boolean) = prefs.edit().putBoolean("edge_flash", v).apply()
    fun isRecoilHelper(): Boolean = prefs.getBoolean("recoil_helper", false)
    fun setRecoilHelper(v: Boolean) = prefs.edit().putBoolean("recoil_helper", v).apply()
    fun isSafeZone(): Boolean = prefs.getBoolean("safe_zone", false)
    fun setSafeZone(v: Boolean) = prefs.edit().putBoolean("safe_zone", v).apply()
    fun getOverlayMasterOpacity(): Int = prefs.getInt("overlay_master_op", 100).coerceIn(30, 100)
    fun setOverlayMasterOpacity(v: Int) = prefs.edit().putInt("overlay_master_op", v.coerceIn(30, 100)).apply()
    fun isQuickTurbo(): Boolean = prefs.getBoolean("quick_turbo_btn", true)
    fun setQuickTurbo(v: Boolean) = prefs.edit().putBoolean("quick_turbo_btn", v).apply()
    fun isBatteryHud(): Boolean = prefs.getBoolean("battery_hud", false)
    fun setBatteryHud(v: Boolean) = prefs.edit().putBoolean("battery_hud", v).apply()
    fun isRamHud(): Boolean = prefs.getBoolean("ram_hud", false)
    fun setRamHud(v: Boolean) = prefs.edit().putBoolean("ram_hud", v).apply()
    fun isTouchBlocker(): Boolean = prefs.getBoolean("touch_blocker", false)
    fun setTouchBlocker(v: Boolean) = prefs.edit().putBoolean("touch_blocker", v).apply()
    fun isCompassHud(): Boolean = prefs.getBoolean("compass_hud", false)
    fun setCompassHud(v: Boolean) = prefs.edit().putBoolean("compass_hud", v).apply()
    fun isKillFlash(): Boolean = prefs.getBoolean("kill_flash", false)
    fun setKillFlash(v: Boolean) = prefs.edit().putBoolean("kill_flash", v).apply()
    fun isLowHpVignette(): Boolean = prefs.getBoolean("low_hp_vignette", false)
    fun setLowHpVignette(v: Boolean) = prefs.edit().putBoolean("low_hp_vignette", v).apply()
    fun isPixelFilter(): Boolean = prefs.getBoolean("pixel_filter", false)
    fun setPixelFilter(v: Boolean) = prefs.edit().putBoolean("pixel_filter", v).apply()
    fun isChromatic(): Boolean = prefs.getBoolean("chromatic", false)
    fun setChromatic(v: Boolean) = prefs.edit().putBoolean("chromatic", v).apply()
    fun isFogOverlay(): Boolean = prefs.getBoolean("fog_overlay", false)
    fun setFogOverlay(v: Boolean) = prefs.edit().putBoolean("fog_overlay", v).apply()
    fun isRadarFrame(): Boolean = prefs.getBoolean("radar_frame", false)
    fun setRadarFrame(v: Boolean) = prefs.edit().putBoolean("radar_frame", v).apply()
    fun isAmmoMock(): Boolean = prefs.getBoolean("ammo_mock", false)
    fun setAmmoMock(v: Boolean) = prefs.edit().putBoolean("ammo_mock", v).apply()
    fun isStopwatch(): Boolean = prefs.getBoolean("stopwatch", false)
    fun setStopwatch(v: Boolean) = prefs.edit().putBoolean("stopwatch", v).apply()
    fun isPanelLocked(): Boolean = prefs.getBoolean("panel_locked", false)
    fun setPanelLocked(v: Boolean) = prefs.edit().putBoolean("panel_locked", v).apply()
    fun getPanelTheme(): Int = prefs.getInt("panel_theme", 0).coerceIn(0, 3)
    fun setPanelTheme(v: Int) = prefs.edit().putInt("panel_theme", v.coerceIn(0, 3)).apply()
    fun isHapticStrong(): Boolean = prefs.getBoolean("haptic_strong", false)
    fun setHapticStrong(v: Boolean) = prefs.edit().putBoolean("haptic_strong", v).apply()
    fun isAutoClosePanel(): Boolean = prefs.getBoolean("auto_close_panel", false)
    fun setAutoClosePanel(v: Boolean) = prefs.edit().putBoolean("auto_close_panel", v).apply()
    fun isMinimalMode(): Boolean = prefs.getBoolean("minimal_mode", false)
    fun setMinimalMode(v: Boolean) = prefs.edit().putBoolean("minimal_mode", v).apply()
    fun isShowGameName(): Boolean = prefs.getBoolean("show_game_name", true)
    fun setShowGameName(v: Boolean) = prefs.edit().putBoolean("show_game_name", v).apply()
    fun isBounceLogo(): Boolean = prefs.getBoolean("bounce_logo", true)
    fun setBounceLogo(v: Boolean) = prefs.edit().putBoolean("bounce_logo", v).apply()
    fun isNeonBorder(): Boolean = prefs.getBoolean("neon_border", true)
    fun setNeonBorder(v: Boolean) = prefs.edit().putBoolean("neon_border", v).apply()
    fun isStatsBar(): Boolean = prefs.getBoolean("stats_bar", false)
    fun setStatsBar(v: Boolean) = prefs.edit().putBoolean("stats_bar", v).apply()
    fun isQuickHide15(): Boolean = prefs.getBoolean("quick_hide_15", true)
    fun setQuickHide15(v: Boolean) = prefs.edit().putBoolean("quick_hide_15", v).apply()
    fun isRecordSession(): Boolean = prefs.getBoolean("record_session", false)
    fun setRecordSession(v: Boolean) = prefs.edit().putBoolean("record_session", v).apply()
    fun getSessionStartMs(): Long = prefs.getLong("session_start_ms", 0L)
    fun setSessionStartMs(v: Long) = prefs.edit().putLong("session_start_ms", v).apply()

    fun getPointerSpeed(): Int = prefs.getInt("sense_pointer_speed", 0).coerceIn(-5, 5)
    fun setPointerSpeed(v: Int) = prefs.edit().putInt("sense_pointer_speed", v.coerceIn(-5, 5)).apply()
    fun getSenseTouchRate(): Int = prefs.getInt("sense_touch_rate", 0).coerceIn(0, 1000)
    fun setSenseTouchRate(v: Int) = prefs.edit().putInt("sense_touch_rate", v.coerceIn(0, 1000)).apply()
    fun isSenseSmooth(): Boolean = prefs.getBoolean("sense_smooth", false)
    fun setSenseSmooth(v: Boolean) = prefs.edit().putBoolean("sense_smooth", v).apply()
    fun isSenseNoInertia(): Boolean = prefs.getBoolean("sense_no_inertia", false)
    fun setSenseNoInertia(v: Boolean) = prefs.edit().putBoolean("sense_no_inertia", v).apply()
    fun isSenseRawInput(): Boolean = prefs.getBoolean("sense_raw_input", false)
    fun setSenseRawInput(v: Boolean) = prefs.edit().putBoolean("sense_raw_input", v).apply()

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
