package com.example.turboboost

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings

class DeviceInfo(private val ctx: Context) {

    private val chipCache: String by lazy { Build.SOC_MODEL.ifBlank { Build.HARDWARE } }
    private val gpuCache: String by lazy { detectGpu() }
    private val clustersCache: List<Pair<Int, String>> by lazy { detectClusters() }
    private val joyoseCache: Boolean by lazy { hasJoyoseInternal() }
    private val tierCache: Int by lazy { detectTier() }
    private val mglruPath = "/sys/kernel/mm/lru_gen/enabled"

    fun getSupportedHz(): List<Int> {
        return try {
            val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
            val display = dm.getDisplay(android.view.Display.DEFAULT_DISPLAY)
            val rates = mutableSetOf<Int>()
            for (m in display.supportedModes) {
                val hz = Math.round(m.refreshRate)
                if (hz in 30..240) rates.add(hz)
            }
            if (rates.isEmpty()) listOf(60) else rates.sorted()
        } catch (e: Throwable) { listOf(60, 90, 120, 144) }
    }

    private fun detectTier(): Int {
        val chip = chipCache.lowercase(); val gpu = gpuCache.lowercase()
        if (chip.contains("unisoc") || chip.contains("t6") || chip.contains("t7") ||
            chip.contains("helio g") || chip.contains("mt67") || chip.contains("mt68") ||
            chip.contains("snapdragon 6") || chip.contains("snapdragon 4") ||
            gpu.contains("mali-g52") || gpu.contains("mali-g57") || gpu.contains("mali-g3")) return 0
        if (chip.contains("snapdragon 8 gen") || chip.contains("dimensity 9") ||
            chip.contains("dimensity 8") || chip.contains("kirin 9") ||
            gpu.contains("adreno 7") || gpu.contains("adreno 6")) return 2
        return 1
    }

    fun getTier(): Int = tierCache
    fun tierName(): String = when (tierCache) { 0 -> "Слабый"; 2 -> "Флагман"; else -> "Средний" }

    fun cpuTemp(): Float {
        val out = SystemReader.read("for z in /sys/class/thermal/thermal_zone*; do t=\$(cat \$z/temp 2>/dev/null); if [ -n \"\$t\" ]; then echo \"\$t\"; fi; done")
        if (out.isBlank()) return 0f
        var max = 0f
        for (line in out.lines()) {
            val raw = line.trim().toFloatOrNull() ?: continue
            val v = if (raw > 1000f) raw / 1000f else raw
            if (v > max && v < 150f) max = v
        }
        return max
    }

    fun batteryLevel(): Int {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 100
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return 100
        return level * 100 / scale
    }

    fun isCharging(): Boolean {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    fun chipName(): String = chipCache
    fun gpuName(): String = gpuCache
    fun cpuClusters(): List<Pair<Int, String>> = clustersCache

    fun hasJoyose(): Boolean = joyoseCache
    private fun hasJoyoseInternal(): Boolean = SystemReader.read("pm list packages com.xiaomi.joyose").contains("com.xiaomi.joyose")

    fun disableJoyose(): String {
        if (!joyoseCache) return "Joyose не найден"
        return try {
            val pmClass = Class.forName("android.app.ActivityThread")
            val pm = pmClass.getMethod("getPackageManager").invoke(null)
            val method = pm.javaClass.getMethod("setApplicationEnabledSetting", String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            method.invoke(pm, "com.xiaomi.joyose", 2, 0)
            SystemReader.read("am force-stop com.xiaomi.joyose")
            "Joyose отключён"
        } catch (e: Throwable) {
            SystemReader.read("pm disable-user --user 0 com.xiaomi.joyose")
            "Joyose отключён"
        }
    }

    fun enableJoyose(): String {
        if (!joyoseCache) return "Joyose не найден"
        return try {
            val pmClass = Class.forName("android.app.ActivityThread")
            val pm = pmClass.getMethod("getPackageManager").invoke(null)
            val method = pm.javaClass.getMethod("setApplicationEnabledSetting", String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            method.invoke(pm, "com.xiaomi.joyose", 1, 0)
            "Joyose включён"
        } catch (e: Throwable) {
            SystemReader.read("pm enable --user 0 com.xiaomi.joyose")
            "Joyose включён"
        }
    }

    fun isMglruAvailable(): Boolean = SystemReader.test(mglruPath)
    fun toggleMglru(enable: Boolean): String {
        if (!isMglruAvailable()) return "MGLRU не поддерживается"
        SystemReader.read("echo ${if (enable) "y" else "n"} > $mglruPath")
        return "MGLRU: ${if (enable) "вкл" else "выкл"}"
    }

    fun checkSystemState(): Boolean = ShizukuHelper.hasPermission() && SystemReader.isReady()

    fun detectGpu(): String {
        if (SystemReader.test("/sys/class/kgsl/kgsl-3d0")) {
            val name = SystemReader.read("cat /sys/class/kgsl/kgsl-3d0/gpu_model 2>/dev/null")
            return if (name.isNotBlank()) name else "Adreno"
        }
        if (SystemReader.test("/sys/class/misc/mali0")) return "Mali"
        if (SystemReader.test("/sys/class/misc/pvrsrvkm")) return "PowerVR"
        return "неизвестно"
    }

    private fun detectClusters(): List<Pair<Int, String>> {
        val result = mutableListOf<Pair<Int, String>>()
        val freqs = mutableListOf<Pair<Int, Long>>()
        for (i in 0 until 8) {
            val f = SystemReader.read("cat /sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq 2>/dev/null").toLongOrNull() ?: 0L
            if (f > 0L) freqs.add(i to f)
        }
        if (freqs.isEmpty()) {
            for (i in 0 until 8) if (SystemReader.test("/sys/devices/system/cpu/cpu$i")) result.add(i to if (i < 4) "little" else "big")
            return result
        }
        val minF = freqs.minOf { it.second }; val maxF = freqs.maxOf { it.second }; val mid = (minF + maxF) / 2
        for ((idx, f) in freqs) {
            val label = when { f <= mid -> "little"; f >= maxF - (maxF - minF) / 4 -> "big"; else -> "mid" }
            result.add(idx to label)
        }
        return result
    }

    fun switchRenderer(vulkan: Boolean): String {
        return if (vulkan) { SystemReader.read("setprop debug.hwui.renderer skiavk"); "Vulkan" }
        else { SystemReader.read("setprop debug.hwui.renderer skiagl"); "OpenGL" }
    }    fun activateMaxPerformance(hz: Int, mode: Int, gpuBoost: Boolean, pkg: String): String {
        val safePkg = pkg.trim().replace(" ", "")
        val bgLimit = if (mode == 0) "96" else "64"
        val phantom = if (mode == 0) "512" else "128"
        val sb = StringBuilder()
        sb.append("settings put global activity_manager_constants max_cached_processes=$bgLimit; ")
        sb.append("device_config put activity_manager max_phantom_processes $phantom; ")
        sb.append("settings put system peak_refresh_rate $hz; settings put system min_refresh_rate $hz; ")
        sb.append("settings put global window_animation_scale 0.5; settings put global transition_animation_scale 0.5; settings put global animator_duration_scale 0.5; ")
        if (safePkg.isNotBlank()) sb.append("cmd appops set $safePkg RUN_IN_BACKGROUND allow; cmd deviceidle whitelist +$safePkg; ")
        SystemReader.read(sb.toString())
        if (safePkg.isNotBlank()) compilePackageSpeedProfile(safePkg)
        return "Класс: ${tierName()} | RAM: настроена | Hz: $hz"
    }

    fun deactivateMaxPerformance(): String {
        SystemReader.read("settings delete global activity_manager_constants; device_config delete activity_manager max_phantom_processes; settings delete system peak_refresh_rate; settings delete system min_refresh_rate; settings put global window_animation_scale 1.0; settings put global transition_animation_scale 1.0; settings put global animator_duration_scale 1.0")
        return "Настройки возвращены"
    }

    fun pinGameToPrimeCore(pkg: String): String {
        if (pkg.isBlank()) return "Пакет не указан"
        val pid = SystemReader.read("pidof $pkg").trim()
        if (pid.isBlank()) return "Игра не запущена"
        val big = clustersCache.filter { it.second == "big" }.map { it.first }.sorted()
        if (big.isEmpty()) return "Нет big-ядер"
        var mask = 0
        for (core in big) mask = mask or (1 shl core)
        val hexMask = Integer.toHexString(mask)
        val allTids = SystemReader.read("ps -T -p $pid -o TID,CMD").lines().map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { line ->
            val parts = line.split(Regex("\\s+"))
            if (parts.size >= 2) { val tid = parts[0].toIntOrNull(); if (tid != null && tid != pid.toInt()) tid to parts[1] else null } else null
        }
        if (allTids.isEmpty()) return "Потоки не найдены"
        var target = allTids.firstOrNull { (_, name) -> name.contains("GameThread", true) || name.contains("UnityMain", true) || name == "main" }
        if (target == null) target = allTids.firstOrNull { (_, name) -> name.contains("RenderThread", true) || name.contains("UnityGfx", true) }
        if (target == null) target = allTids.firstOrNull()
        if (target == null) return "Поток не найден"
        SystemReader.read("taskset -p $hexMask ${target.first} 2>/dev/null; renice -n -15 -p ${target.first} 2>/dev/null")
        return "Отклик: ${target.second} -> big-ядра"
    }

    fun boostInputDispatcher(): String {
        val inputPid = SystemReader.read("pidof inputflinger").trim()
        if (inputPid.isNotBlank()) {
            SystemReader.read("renice -n -15 -p $inputPid 2>/dev/null")
            val tids = SystemReader.read("ps -T -p $inputPid -o TID,CMD").lines().map { it.trim() }.filter { it.contains("InputDispatcher", true) }.mapNotNull { it.split(Regex("\\s+")).firstOrNull()?.toIntOrNull() }
            for (tid in tids) SystemReader.read("renice -n -15 -p $tid 2>/dev/null")
        }
        return "InputDispatcher: усилен"
    }

    fun boostThreadPriorities(): String {
        val sfNice = when (tierCache) { 0 -> -10; 2 -> -18; else -> -15 }
        val sysNice = when (tierCache) { 0 -> -5; 2 -> -10; else -> -8 }
        val surface = SystemReader.read("pidof surfaceflinger"); if (surface.isNotBlank()) SystemReader.read("renice -n $sfNice -p $surface 2>/dev/null")
        val sys = SystemReader.read("pidof system_server"); if (sys.isNotBlank()) SystemReader.read("renice -n $sysNice -p $sys 2>/dev/null")
        return "Приоритеты: усилены"
    }

    fun resetThreadPriorities(): String {
        val surface = SystemReader.read("pidof surfaceflinger"); if (surface.isNotBlank()) SystemReader.read("renice -n 0 -p $surface 2>/dev/null")
        val sys = SystemReader.read("pidof system_server"); if (sys.isNotBlank()) SystemReader.read("renice -n 0 -p $sys 2>/dev/null")
        return "Приоритеты: сброшены"
    }

    fun restrictPowerKeeper(): String {
        val pkg = "com.miui.powerkeeper"
        if (!SystemReader.read("pm list packages $pkg").contains(pkg)) return "PowerKeeper не найден"
        SystemReader.read("appops set $pkg WRITE_SETTINGS deny; appops set $pkg GET_USAGE_STATS deny; appops set $pkg RUN_IN_BACKGROUND deny")
        return "PowerKeeper: ограничен"
    }

    fun restorePowerKeeper(): String {
        SystemReader.read("appops set com.miui.powerkeeper WRITE_SETTINGS allow; appops set com.miui.powerkeeper GET_USAGE_STATS allow; appops set com.miui.powerkeeper RUN_IN_BACKGROUND allow")
        return "PowerKeeper: восстановлен"
    }

    fun cleanupDozeWhitelist(): String {
        var count = 0
        for (pkg in listOf("com.facebook.katana", "com.facebook.orca", "com.instagram.android")) {
            if (SystemReader.read("pm list packages $pkg").contains(pkg)) { SystemReader.read("dumpsys deviceidle whitelist -$pkg"); count++ }
        }
        return if (count > 0) "Doze: очищен" else "Нечего чистить"
    }

    fun restrictGms(): String {
        if (!SystemReader.read("pm list packages com.google.android.gms").contains("com.google.android.gms")) return "GMS не найден"
        SystemReader.read("am set-standby-bucket com.google.android.gms rare")
        return "GMS: rare"
    }

    fun restoreGms(): String { SystemReader.read("am set-standby-bucket com.google.android.gms active"); return "GMS: active" }

    fun setDownscale(pkg: String, factor: Float): String {
        if (pkg.isBlank()) return "Пакет не указан"
        val safe = when { factor <= 0f -> "disable"; factor < 0.3f -> "0.3"; factor > 0.9f -> "0.9"; else -> String.format(java.util.Locale.US, "%.2f", factor) }
        return try { SystemReader.read("cmd game set --downscale $safe $pkg"); if (safe == "disable") "Downscale: выкл" else "Downscale: $safe" } catch (e: Throwable) { "Downscale: ошибка" }
    }

    fun compilePackageSpeedProfile(pkg: String): String {
        if (pkg.isBlank()) return "Введите пакет"
        return if (SystemReader.read("cmd package compile -m speed-profile -f $pkg").isBlank()) "Компиляция запущена" else "Готово"
    }

    fun runFstrim(): String { SystemReader.read("sm fstrim"); return "Fstrim: выполнен" }

    fun unlockGameFps(pkg: String, hz: Int): String {
        if (pkg.isBlank()) return "Пакет не указан"
        val safeHz = when { hz >= 144 -> 120; hz >= 120 -> 120; hz >= 90 -> 90; else -> 60 }
        SystemReader.read("cmd game mode performance $pkg; cmd game set --fps $safeHz $pkg; device_config put game_overlay $pkg mode=2,fps=$safeHz")
        return "FPS cap: $safeHz"
    }

    fun resetGameFps(pkg: String): String { if (pkg.isBlank()) return "Пакет не указан"; SystemReader.read("cmd game mode standard $pkg; device_config delete game_overlay $pkg"); return "standard" }

    fun applySense(level: Int): String {
        val tier = tierCache
        val slop: Int; val longPress: Int; val multiPress: Int; val pointer: Int; val pressure: String; val touchRate: Int
        when (level) {
            0 -> { slop = 8; longPress = 350; multiPress = 280; pointer = if (tier == 0) 2 else 3; pressure = "0.8"; touchRate = 120 }
            1 -> { slop = 7; longPress = 300; multiPress = 250; pointer = if (tier == 0) 3 else 5; pressure = "0.5"; touchRate = if (tier == 0) 120 else 180 }
            2 -> { slop = 6; longPress = 250; multiPress = 220; pointer = if (tier == 0) 5 else 7; pressure = "0.1"; touchRate = if (tier == 0) 180 else 240 }
            else -> { slop = 5; longPress = 200; multiPress = 200; pointer = if (tier == 0) 6 else 8; pressure = "0.05"; touchRate = if (tier == 0) 240 else 300 }
        }
        SystemReader.read("settings put secure touch_slop $slop; settings put secure long_press_timeout $longPress; settings put secure multi_press_timeout $multiPress; settings put secure pointer_speed $pointer; settings put system pointer_speed $pointer; settings put system touch.pressure.scale $pressure")
        SystemReader.read("settings put system touch_sampling_rate $touchRate")
        return "Сенса: ${arrayOf("Плавная", "Быстрая", "Резкая", "Макс")[level]} (${touchRate} Гц)"
    }

    fun applySurfaceFlingerBoost(enable: Boolean): String {
        return if (enable) { SystemReader.read("setprop debug.sf.auto_latch_unsignaled true"); "Boost" } else { SystemReader.read("setprop debug.sf.auto_latch_unsignaled false"); "Default" }
    }

    fun compilePackage(pkg: String): String { if (pkg.isBlank()) return "Введите пакет"; return if (SystemReader.read("cmd package compile -m speed -f $pkg").isBlank()) "Запущена" else "Готово" }
    fun tryKeepAlive(pkg: String): String { if (pkg.isBlank()) return "Введите пакет"; SystemReader.read("appops set $pkg RUN_IN_BACKGROUND allow; appops set $pkg KEEP_ALIVE allow; cmd deviceidle whitelist +$pkg"); return "Удержание: $pkg" }
    fun trimCaches(): String { SystemReader.read("cmd package trim-caches 1000M"); return "Кэш очищен" }
    fun restrictBackground(): String { SystemReader.read("settings put global background_process_limit 2"); return "Фон ограничен" }
    fun restoreBackground(): String { SystemReader.read("settings delete global background_process_limit"); return "Фон восстановлен" }

    fun listGames(): List<Pair<String, String>> {
        val pm = ctx.packageManager
        val result = mutableListOf<Pair<String, String>>()
        for (a in pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
            if (pm.getLaunchIntentForPackage(a.packageName) == null) continue
            result.add(a.packageName to pm.getApplicationLabel(a).toString())
        }
        return result.sortedBy { it.second.lowercase() }
    }

    fun openGameSpace(): String {
        for (pkg in listOf("com.miui.securitycenter", "com.coloros.gamespace", "com.coloros.gamespaceui", "com.transsion.game", "com.transsion.gamemode")) {
            try { val i = ctx.packageManager.getLaunchIntentForPackage(pkg); if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i); return "Открыт" } } catch (e: Throwable) {}
        }
        return "Не найден"
    }

    fun openDeveloperOptions() {
        try { val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS); intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(intent) } catch (e: Exception) {}
    }
}
