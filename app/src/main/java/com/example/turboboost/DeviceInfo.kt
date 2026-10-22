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

    private fun detectTier(): Int {
        val chip = chipCache.lowercase()
        val gpu = gpuCache.lowercase()
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
            val method = pm.javaClass.getMethod(
                "setApplicationEnabledSetting",
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            method.invoke(pm, "com.xiaomi.joyose", 2, 0)
            SystemReader.read("am force-stop com.xiaomi.joyose")
            "Joyose отключён (Binder)"
        } catch (e: Throwable) {
            SystemReader.read("pm disable-user --user 0 com.xiaomi.joyose")
            "Joyose отключён (shell)"
        }
    }

    fun enableJoyose(): String {
        if (!joyoseCache) return "Joyose не найден"
        return try {
            val pmClass = Class.forName("android.app.ActivityThread")
            val pm = pmClass.getMethod("getPackageManager").invoke(null)
            val method = pm.javaClass.getMethod(
                "setApplicationEnabledSetting",
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            method.invoke(pm, "com.xiaomi.joyose", 1, 0)
            "Joyose включён (Binder)"
        } catch (e: Throwable) {
            SystemReader.read("pm enable --user 0 com.xiaomi.joyose")
            "Joyose включён (shell)"
        }
    }

    fun isMglruAvailable(): Boolean = SystemReader.test(mglruPath)

    fun toggleMglru(enable: Boolean): String {
        if (!isMglruAvailable()) return "MGLRU не поддерживается ядром"
        val v = if (enable) "y" else "n"
        SystemReader.read("echo $v > $mglruPath")
        return "MGLRU: ${if (enable) "вкл" else "выкл"}"
    }

    fun checkSystemState(): Boolean {
        if (!ShizukuHelper.hasPermission()) return false
        return SystemReader.isReady()
    }

    fun detectGpu(): String {
        if (SystemReader.test("/sys/class/kgsl/kgsl-3d0")) {
            val name = SystemReader.read("cat /sys/class/kgsl/kgsl-3d0/gpu_model 2>/dev/null")
            return if (name.isNotBlank()) name else "Adreno"
        }
        if (SystemReader.test("/sys/class/misc/mali0")) return "Mali"
        if (SystemReader.test("/sys/class/misc/pvrsrvkm")) return "PowerVR"
        val sf = SystemReader.read("dumpsys SurfaceFlinger | grep -iE 'GLES|Vulkan'")
        if (sf.contains("Mali", true)) return "Mali"
        if (sf.contains("Adreno", true)) return "Adreno"
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
        return if (vulkan) {
            SystemReader.read("setprop debug.hwui.renderer skiavk; am crash com.android.systemui"); "Рендерер: Vulkan"
        } else {
            SystemReader.read("setprop debug.hwui.renderer skiagl; am crash com.android.systemui"); "Рендерер: OpenGL"
        }
    }    fun activateMaxPerformance(hz: Int, mode: Int, gpuBoost: Boolean): String {
        val tier = tierCache
        val bgLimit = when (tier) { 0 -> "64"; 2 -> "128"; else -> "96" }
        val phantom = when (tier) { 0 -> "96"; 2 -> "192"; else -> "128" }
        val sb = StringBuilder()
        
        sb.append("settings put global activity_manager_constants max_cached_processes=$bgLimit; ")
        sb.append("device_config put activity_manager max_phantom_processes $phantom; ")
        sb.append("settings put system peak_refresh_rate $hz; settings put system min_refresh_rate $hz; ")
        sb.append("settings put global window_animation_scale 0.5; settings put global transition_animation_scale 0.5; settings put global animator_duration_scale 0.5; ")
        sb.append("am kill-all")

        SystemReader.read(sb.toString())

        return "Класс: ${tierName()} | RAM: очищена | Hz: $hz"
    }

    fun deactivateMaxPerformance(): String {
        val sb = StringBuilder()
        sb.append("settings delete global activity_manager_constants; ")
        sb.append("device_config delete activity_manager max_phantom_processes; ")
        sb.append("settings delete system peak_refresh_rate; settings delete system min_refresh_rate; ")
        sb.append("settings put global window_animation_scale 1.0; settings put global transition_animation_scale 1.0; settings put global animator_duration_scale 1.0")
        SystemReader.read(sb.toString())
        return "Настройки возвращены"
    }

    fun prioritizeGameThreads(pkg: String): String {
        if (pkg.isBlank()) return "Пакет не указан"
        val pid = SystemReader.read("pidof $pkg").trim()
        if (pid.isBlank()) return "Игра не запущена"

        val allCores = clustersCache.map { it.first }.sorted()
        if (allCores.isEmpty()) return "Ядра не определены"

        var mask = 0
        for (core in allCores) {
            mask = mask or (1 shl core)
        }
        val hexMask = Integer.toHexString(mask)

        var tids = SystemReader.read("ps -T -p $pid -o TID").lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.toIntOrNull() != null && it.toInt() != pid.toInt() }

        if (tids.isEmpty()) {
            tids = SystemReader.read("top -H -b -n 1 -p $pid").lines()
                .map { it.trim().split(Regex("\\s+")) }
                .filter { it.size > 1 && it[0].toIntOrNull() != null }
                .map { it[0] }
                .filter { it != pid }
        }

        if (tids.isEmpty()) return "Потоки не найдены"

        val sb = StringBuilder()
        for (tid in tids) {
            sb.append("taskset -p $hexMask $tid 2>/dev/null; ")
        }
        SystemReader.read(sb.toString())

        return "Игра $pkg: ${tids.size} потоков на ${allCores.size} ядрах"
    }

    fun pinGameToPrimeCore(pkg: String): String {
        if (pkg.isBlank()) return "Пакет не указан"
        val pid = SystemReader.read("pidof $pkg").trim()
        if (pid.isBlank()) return "Игра не запущена"

        val big = clustersCache.filter { it.second == "big" }.map { it.first }.sorted()
        if (big.isEmpty()) return "Нет big-ядер"

        var mask = 0
        for (core in big) {
            mask = mask or (1 shl core)
        }
        val hexMask = Integer.toHexString(mask)

        val allTids = SystemReader.read("ps -T -p $pid -o TID,CMD").lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val parts = line.split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val tid = parts[0].toIntOrNull()
                    val name = parts[1]
                    if (tid != null && tid != pid.toInt()) tid to name else null
                } else null
            }

        if (allTids.isEmpty()) return "Потоки не найдены"

        var target = allTids.firstOrNull { (_, name) ->
            name.contains("GameThread", true) || name.contains("UnityMain", true) || name == "main"
        }
        if (target == null) {
            target = allTids.firstOrNull { (_, name) ->
                name.contains("RenderThread", true) || name.contains("UnityGfx", true)
            }
        }
        if (target == null) {
            target = allTids.firstOrNull()
        }

        if (target == null) return "Главный поток не найден"

        val (tid, name) = target
        SystemReader.read("taskset -p $hexMask $tid 2>/dev/null; renice -n -15 -p $tid 2>/dev/null")

        val coreNames = big.joinToString(",")
        return "Отклик: $name (TID $tid) -> big-ядра [$coreNames]"
    }

    fun boostInputDispatcher(): String {
        val sb = StringBuilder()
        val inputPid = SystemReader.read("pidof inputflinger").trim()
        if (inputPid.isNotBlank()) {
            sb.append("renice -n -15 -p $inputPid 2>/dev/null; ")
            val tids = SystemReader.read("ps -T -p $inputPid -o TID,CMD").lines()
                .map { it.trim() }
                .filter { it.contains("InputDispatcher", true) }
                .mapNotNull { it.split(Regex("\\s+")).firstOrNull()?.toIntOrNull() }
            for (tid in tids) {
                sb.append("renice -n -15 -p $tid 2>/dev/null; ")
            }
        }
        SystemReader.read(sb.toString())
        return if (inputPid.isNotBlank()) "InputDispatcher: усилен" else "inputflinger не найден"
    }

    fun boostThreadPriorities(): String {
        val sb = StringBuilder()
        val sfNice = when (tierCache) { 0 -> -10; 2 -> -15; else -> -12 }
        val sysNice = when (tierCache) { 0 -> -5; 2 -> -8; else -> -6 }
        val surface = SystemReader.read("pidof surfaceflinger"); if (surface.isNotBlank()) sb.append("renice -n $sfNice -p $surface 2>/dev/null; ")
        val sys = SystemReader.read("pidof system_server"); if (sys.isNotBlank()) sb.append("renice -n $sysNice -p $sys 2>/dev/null; ")
        SystemReader.read(sb.toString())
        return "Приоритеты: усилены"
    }

    fun resetThreadPriorities(): String {
        val surface = SystemReader.read("pidof surfaceflinger"); if (surface.isNotBlank()) SystemReader.read("renice -n 0 -p $surface 2>/dev/null")
        val sys = SystemReader.read("pidof system_server"); if (sys.isNotBlank()) SystemReader.read("renice -n 0 -p $sys 2>/dev/null")
        return "Приоритеты: сброшены"
    }

    fun restrictPowerKeeper(): String {
        val pkg = "com.miui.powerkeeper"
        val installed = SystemReader.read("pm list packages $pkg").contains(pkg)
        if (!installed) return "PowerKeeper не найден"
        SystemReader.read("appops set $pkg WRITE_SETTINGS deny; appops set $pkg GET_USAGE_STATS deny; appops set $pkg RUN_IN_BACKGROUND deny")
        return "PowerKeeper: ограничен"
    }

    fun restorePowerKeeper(): String {
        val pkg = "com.miui.powerkeeper"
        SystemReader.read("appops set $pkg WRITE_SETTINGS allow; appops set $pkg GET_USAGE_STATS allow; appops set $pkg RUN_IN_BACKGROUND allow")
        return "PowerKeeper: восстановлен"
    }

    fun cleanupDozeWhitelist(): String {
        val targets = listOf("com.facebook.katana", "com.facebook.orca", "com.instagram.android")
        val sb = StringBuilder()
        for (pkg in targets) {
            val installed = SystemReader.read("pm list packages $pkg").contains(pkg)
            if (installed) {
                SystemReader.read("dumpsys deviceidle whitelist -$pkg")
                sb.append("$pkg: убран\n")
            }
        }
        return if (sb.isNotEmpty()) sb.toString() else "Нечего чистить"
    }

    fun restrictGms(): String {
        val pkg = "com.google.android.gms"
        val installed = SystemReader.read("pm list packages $pkg").contains(pkg)
        if (!installed) return "GMS не найден"
        SystemReader.read("am set-standby-bucket $pkg rare")
        return "GMS: rare (экономия)"
    }

    fun restoreGms(): String {
        SystemReader.read("am set-standby-bucket com.google.android.gms active")
        return "GMS: active"
    }

    fun unlockGameFps(pkg: String, hz: Int): String {
        if (pkg.isBlank()) return "Пакет не указан"
        val safeHz = when { hz >= 144 -> 120; hz >= 120 -> 120; hz >= 90 -> 90; else -> 60 }
        SystemReader.read("cmd game mode performance $pkg; cmd game set --fps $safeHz $pkg; device_config put game_overlay $pkg mode=2,fps=$safeHz")
        return "Игра $pkg: Performance | FPS cap $safeHz"
    }

    fun resetGameFps(pkg: String): String {
        if (pkg.isBlank()) return "Пакет не указан"
        SystemReader.read("cmd game mode standard $pkg; device_config delete game_overlay $pkg")
        return "Игра $pkg: standard"
    }

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
        return if (enable) {
            SystemReader.read("setprop debug.sf.auto_latch_unsignaled true")
            "SurfaceFlinger: Boost"
        } else {
            SystemReader.read("setprop debug.sf.auto_latch_unsignaled false")
            "SurfaceFlinger: Default"
        }
    }

    fun compilePackage(pkg: String): String { 
        if (pkg.isBlank()) return "Введите пакет"
        return if (SystemReader.read("cmd package compile -m speed -f $pkg").isBlank()) "Компиляция запущена" else "Компиляция завершена"
    }

    fun tryKeepAlive(pkg: String): String { 
        if (pkg.isBlank()) return "Введите пакет"
        SystemReader.read("appops set $pkg RUN_IN_BACKGROUND allow; appops set $pkg KEEP_ALIVE allow; cmd deviceidle whitelist +$pkg")
        return "Удержание: $pkg"
    }

    fun trimCaches(): String { 
        SystemReader.read("cmd package trim-caches 1000M")
        return "Кэш очищен"
    }

    fun restrictBackground(): String { 
        SystemReader.read("settings put global background_process_limit 2")
        return "Фон ограничен"
    }

    fun restoreBackground(): String { 
        SystemReader.read("settings delete global background_process_limit")
        return "Фон восстановлен"
    }

    fun listGames(): List<Pair<String, String>> {
        val popular = listOf(
            "com.tencent.ig" to "PUBG Mobile",
            "com.pubg.krmobile" to "PUBG KR",
            "com.rekoo.pubgm" to "PUBG (ReKoo)",
            "com.vng.pubgmobile" to "PUBG VN",
            "com.axlebolt.standoff2" to "Standoff 2",
            "com.ScarletWork.CounterFlame" to "CounterFlame",
            "com.tencent.stc.cfl" to "CrossFire: Legends",
            "com.tencent.tmgp.cf" to "CrossFire (CN)",
            "com.supercell.brawlstars" to "Brawl Stars",
            "com.supercell.clashofclans" to "Clash of Clans",
            "com.supercell.clashroyale" to "Clash Royale",
            "com.wargaming.wot.blitz" to "Tanks Blitz",
            "com.riotgames.league.wildrift" to "League of Legends: Wild Rift",
            "com.mobile.legends" to "Mobile Legends",
            "com.miHoYo.GenshinImpact" to "Genshin Impact",
            "com.miHoYo.hkrpg" to "Honkai: Star Rail",
            "com.epicgames.fortnite" to "Fortnite",
            "com.activision.callofduty.shooter" to "Call of Duty: Mobile",
            "com.gameloft.android.ANMP.GloftA9HM" to "Asphalt 9",
            "com.ea.gp.apexlegendsmobilefps" to "Apex Legends Mobile",
            "com.roblox.client" to "Roblox"
        )

        val pm = ctx.packageManager
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA).map { it.packageName }.toSet()
        val result = mutableListOf<Pair<String, String>>()

        for ((pkg, name) in popular) {
            if (installed.contains(pkg)) result.add(pkg to name)
        }

        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (a in apps) {
            if (a.flags and ApplicationInfo.FLAG_SYSTEM != 0 || a.category != ApplicationInfo.CATEGORY_GAME) continue
            if (result.any { it.first == a.packageName }) continue
            result.add(a.packageName to pm.getApplicationLabel(a).toString())
        }

        return result.sortedBy { it.second.lowercase() }
    }

    fun openGameSpace(): String {
        val pkgs = listOf("com.miui.securitycenter", "com.coloros.gamespace", "com.coloros.gamespaceui", "com.transsion.game", "com.transsion.gamemode")
        for (pkg in pkgs) {
            try {
                val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
                if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i); return "Game Turbo открыт" }
            } catch (e: Throwable) {}
        }
        return "Game Space не найден"
    }

    fun openDeveloperOptions() {
        try { 
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        } catch (e: Exception) {}
    }
}
