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

    @Volatile private var chipCache: String? = null
    @Volatile private var gpuCache: String? = null
    @Volatile private var clustersCache: List<Pair<Int, String>>? = null
    @Volatile private var joyoseCache: Boolean? = null
    @Volatile private var tierCache: Int? = null
    private val mglruPath = "/sys/kernel/mm/lru_gen/enabled"

    private fun chip(): String {
        chipCache?.let { return it }
        var v = ""
        if (Build.VERSION.SDK_INT >= 31) {
            try { v = Build.SOC_MODEL ?: "" } catch (_: Throwable) {}
        }
        if (v.isBlank() || v.equals("unknown", true)) v = Build.HARDWARE ?: ""
        if (v.isBlank()) v = Build.BOARD ?: "unknown"
        chipCache = v
        return v
    }

    private fun gpu(): String {
        gpuCache?.let { return it }
        val v = detectGpu()
        gpuCache = v
        return v
    }

    private fun clusters(): List<Pair<Int, String>> {
        clustersCache?.let { return it }
        val v = detectClusters()
        clustersCache = v
        return v
    }

    private fun joyose(): Boolean {
        joyoseCache?.let { return it }
        val v = hasJoyoseInternal()
        joyoseCache = v
        return v
    }

    private fun tier(): Int {
        tierCache?.let { return it }
        val v = detectTierInternal()
        tierCache = v
        return v
    }

    private fun detectTierInternal(): Int {
        val chip = chip().lowercase()
        val gpu = gpu().lowercase()
        if (chip.contains("unisoc") || Regex("(^|[^a-z])t[67]\\d{2}").containsMatchIn(chip) ||
            chip.contains("helio g") || chip.contains("mt67") || chip.contains("mt68") ||
            chip.contains("snapdragon 6") || chip.contains("snapdragon 4") ||
            gpu.contains("mali-g52") || gpu.contains("mali-g57") || gpu.contains("mali-g3")) return 0
        if (chip.contains("snapdragon 8 gen") || chip.contains("dimensity 9") ||
            chip.contains("dimensity 8") || chip.contains("kirin 9") ||
            gpu.contains("adreno 7") || gpu.contains("adreno 6")) return 2
        return 1
    }

    @Suppress("DEPRECATION")
    fun supportedHz(): List<Int> {
        val rates = ArrayList<Int>()
        try {
            val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
            val d = dm.getDisplay(android.view.Display.DEFAULT_DISPLAY)
            if (d != null) {
                d.supportedModes.forEach { rates.add(Math.round(it.refreshRate)) }
                rates.add(Math.round(d.refreshRate))
            }
        } catch (e: Throwable) {
        }
        if (rates.isEmpty()) {
            try {
                val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                rates.add(Math.round(wm.defaultDisplay.refreshRate))
            } catch (e: Throwable) {
            }
        }
        val l = rates.filter { it >= 30 }.distinct().sorted()
        return if (l.isEmpty()) listOf(60) else l
    }

    fun clampHz(hz: Int): Int {
        val l = supportedHz()
        return l.lastOrNull { it <= hz } ?: l.first()
    }

    fun getTier(): Int = tier()
    fun tierName(): String = when (tier()) { 0 -> "Слабый"; 2 -> "Флагман"; else -> "Средний" }

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

    fun chipName(): String = chip()
    fun gpuName(): String = gpu()
    fun cpuClusters(): List<Pair<Int, String>> = clusters()

    fun hasJoyose(): Boolean = joyose()
    private fun hasJoyoseInternal(): Boolean = SystemReader.read("pm list packages com.xiaomi.joyose").contains("com.xiaomi.joyose")

    fun disableJoyose(): String {
        if (!joyose()) return "Joyose не найден"
        return try {
            val pmClass = Class.forName("android.app.ActivityThread")
            val pm = pmClass.getMethod("getPackageManager").invoke(null)
            val method = pm.javaClass.getMethod("setApplicationEnabledSetting", String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            method.invoke(pm, "com.xiaomi.joyose", 2, 0)
            SystemReader.read("am force-stop com.xiaomi.joyose")
            "Joyose отключён (Binder)"
        } catch (e: Throwable) {
            SystemReader.read("pm disable-user --user 0 com.xiaomi.joyose")
            "Joyose отключён (shell)"
        }
    }

    fun enableJoyose(): String {
        if (!joyose()) return "Joyose не найден"
        return try {
            val pmClass = Class.forName("android.app.ActivityThread")
            val pm = pmClass.getMethod("getPackageManager").invoke(null)
            val method = pm.javaClass.getMethod("setApplicationEnabledSetting", String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
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
        val res = SystemReader.read("echo $v > $mglruPath 2>&1")
        val check = SystemReader.read("cat $mglruPath 2>/dev/null")
        return if (check.contains(v)) "MGLRU: ${if (enable) "вкл" else "выкл"}" else "MGLRU: нет доступа на запись${if (res.isNotBlank()) " ($res)" else ""}"
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
    }

    fun gpuBoost(enable: Boolean): String {
        val sb = StringBuilder()
        if (SystemReader.test("/sys/class/kgsl/kgsl-3d0")) {
            if (enable) {
                sb.append("echo 1 > /sys/class/kgsl/kgsl-3d0/force_clk_on 2>/dev/null; ")
                sb.append("echo 0 > /sys/class/kgsl/kgsl-3d0/min_pwrlevel 2>/dev/null; ")
                sb.append("echo 0 > /sys/class/kgsl/kgsl-3d0/max_pwrlevel 2>/dev/null; ")
                sb.append("echo 10000 > /sys/class/kgsl/kgsl-3d0/idle_timer 2>/dev/null; ")
                sb.append("echo performance > /sys/class/kgsl/kgsl-3d0/devfreq/governor 2>/dev/null; ")
                SystemReader.read(sb.toString())
                return "GPU: boost"
            } else {
                sb.append("echo 0 > /sys/class/kgsl/kgsl-3d0/force_clk_on 2>/dev/null; ")
                sb.append("echo 1 > /sys/class/kgsl/kgsl-3d0/min_pwrlevel 2>/dev/null; ")
                sb.append("echo 0 > /sys/class/kgsl/kgsl-3d0/max_pwrlevel 2>/dev/null; ")
                sb.append("echo 64 > /sys/class/kgsl/kgsl-3d0/idle_timer 2>/dev/null; ")
                sb.append("echo msm-adreno-tz > /sys/class/kgsl/kgsl-3d0/devfreq/governor 2>/dev/null; ")
                SystemReader.read(sb.toString())
                return "GPU: stock"
            }
        }
        if (SystemReader.test("/sys/class/misc/mali0")) {
            if (enable) {
                sb.append("echo always_on > /sys/class/misc/mali0/device/power_policy 2>/dev/null; ")
                sb.append("echo performance > /sys/class/misc/mali0/device/devfreq/governor 2>/dev/null; ")
                SystemReader.read(sb.toString())
                return "GPU Mali: boost"
            } else {
                sb.append("echo coarse_demand > /sys/class/misc/mali0/device/power_policy 2>/dev/null; ")
                sb.append("echo simple_ondemand > /sys/class/misc/mali0/device/devfreq/governor 2>/dev/null; ")
                SystemReader.read(sb.toString())
                return "GPU Mali: stock"
            }
        }
        return "GPU: не поддерживается"
    }

    fun activateMaxPerformance(hzRaw: Int, mode: Int, gpuBoost: Boolean, pkg: String): String {
        val hz = clampHz(hzRaw)
        val bgLimit = if (mode == 0) "96" else "64"; val phantom = if (mode == 0) "512" else "128"; val sb = StringBuilder()
        sb.append("settings put global activity_manager_constants max_cached_processes=$bgLimit; ")
        sb.append("device_config put activity_manager max_phantom_processes $phantom; ")
        sb.append("settings put system peak_refresh_rate $hz; settings put system min_refresh_rate $hz; ")
        sb.append("settings put global window_animation_scale 0.5; settings put global transition_animation_scale 0.5; settings put global animator_duration_scale 0.5; ")
        if (pkg.isNotBlank()) { sb.append("cmd appops set $pkg RUN_IN_BACKGROUND allow; cmd deviceidle whitelist +$pkg; ") }
        SystemReader.read(sb.toString())
        if (gpuBoost) gpuBoost(true) else gpuBoost(false)
        if (pkg.isNotBlank()) { compilePackageSpeedProfile(pkg) }
        return "Класс: ${tierName()} | RAM: настроена | Hz: $hz | AOT: да | GPU: ${if (gpuBoost) "boost" else "stock"}"
    }

    fun deactivateMaxPerformance(): String {
        SystemReader.read("settings delete global activity_manager_constants; device_config delete activity_manager max_phantom_processes; settings delete system peak_refresh_rate; settings delete system min_refresh_rate; settings put global window_animation_scale 1.0; settings put global transition_animation_scale 1.0; settings put global animator_duration_scale 1.0")
        gpuBoost(false)
        resetSense()
        restorePing()
        return "Настройки возвращены"
    }

    fun applyMemoryTweaks(enable: Boolean): String {
        return if (enable) {
            SystemReader.read("settings put global activity_manager_constants max_cached_processes=96; device_config put activity_manager max_phantom_processes 512")
            "RAM: настроена"
        } else {
            SystemReader.read("settings delete global activity_manager_constants; device_config delete activity_manager max_phantom_processes")
            "RAM: сброшена"
        }
    }

    private fun firstPid(pkg: String): Int? =
        SystemReader.read("pidof $pkg").trim().split(Regex("\\s+")).firstOrNull()?.toIntOrNull()

    fun foregroundPackage(): String {
        val out = SystemReader.read("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'")
        if (out.isBlank()) return ""
        val re = Regex("u\\d+\\s+([A-Za-z0-9_.]+)/")
        for (line in out.lines()) {
            if (line.contains("mCurrentFocus")) {
                val m = re.find(line)
                if (m != null) return m.groupValues[1]
            }
        }
        for (line in out.lines()) {
            val m = re.find(line)
            if (m != null) return m.groupValues[1]
        }
        return ""
    }

    fun prioritizeGameThreads(pkg: String): String {
        if (pkg.isBlank()) return "Пакет не указан"
        val pidInt = firstPid(pkg) ?: return "Игра не запущена"
        val pid = pidInt.toString()
        val allCores = clusters().map { it.first }.sorted(); if (allCores.isEmpty()) return "Ядра не определены"
        var mask = 0; for (core in allCores) mask = mask or (1 shl core); val hexMask = Integer.toHexString(mask)
        var tids = SystemReader.read("ps -T -p $pid -o TID").lines().map { it.trim() }.filter { it.isNotEmpty() && it.toIntOrNull() != null && it.toInt() != pid.toInt() }
        if (tids.isEmpty()) { tids = SystemReader.read("top -H -b -n 1 -p $pid").lines().map { it.trim().split(Regex("\\s+")) }.filter { it.size > 1 && it[0].toIntOrNull() != null }.map { it[0] }.filter { it != pid } }
        if (tids.isEmpty()) return "Потоки не найдены"
        val sb = StringBuilder(); for (tid in tids) sb.append("taskset -p $hexMask $tid 2>/dev/null; "); SystemReader.read(sb.toString())
        return "Игра $pkg: ${tids.size} потоков на ${allCores.size} ядрах"
    }

    fun pinGameToPrimeCore(pkg: String): String {
        if (pkg.isBlank()) return "Пакет не указан"
        val pidInt = firstPid(pkg) ?: return "Игра не запущена"; val pid = pidInt.toString()
        val big = clusters().filter { it.second == "big" }.map { it.first }.sorted(); if (big.isEmpty()) return "Нет big-ядер"
        var mask = 0; for (core in big) mask = mask or (1 shl core); val hexMask = Integer.toHexString(mask)
        val allTids = SystemReader.read("ps -T -p $pid -o TID,CMD").lines().map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { line ->
            val parts = line.split(Regex("\\s+")); if (parts.size >= 2) { val tid = parts[0].toIntOrNull(); if (tid != null && tid != pid.toInt()) tid to parts[1] else null } else null
        }
        if (allTids.isEmpty()) return "Потоки не найдены"
        var target = allTids.firstOrNull { (_, name) -> name.contains("GameThread", true) || name.contains("UnityMain", true) || name == "main" }
        if (target == null) target = allTids.firstOrNull { (_, name) -> name.contains("RenderThread", true) || name.contains("UnityGfx", true) }
        if (target == null) target = allTids.firstOrNull(); if (target == null) return "Главный поток не найден"
        SystemReader.read("taskset -p $hexMask ${target.first} 2>/dev/null; renice -n -15 -p ${target.first} 2>/dev/null")
        return "Отклик: ${target.second} -> big-ядра"
    }

    fun boostInputDispatcher(): String {
        val sb = StringBuilder(); val inputPid = SystemReader.read("pidof inputflinger").trim()
        if (inputPid.isNotBlank()) {
            sb.append("renice -n -15 -p $inputPid 2>/dev/null; ")
            val tids = SystemReader.read("ps -T -p $inputPid -o TID,CMD").lines().map { it.trim() }.filter { it.contains("InputDispatcher", true) }.mapNotNull { it.split(Regex("\\s+")).firstOrNull()?.toIntOrNull() }
            for (tid in tids) sb.append("renice -n -15 -p $tid 2>/dev/null; ")
        }
        SystemReader.read(sb.toString()); return if (inputPid.isNotBlank()) "InputDispatcher: усилен" else "inputflinger не найден"
    }

    fun boostThreadPriorities(): String {
        val sb = StringBuilder(); val sfNice = when (tier()) { 0 -> -10; 2 -> -18; else -> -15 }; val sysNice = when (tier()) { 0 -> -5; 2 -> -10; else -> -8 }
        val surface = SystemReader.read("pidof surfaceflinger"); if (surface.isNotBlank()) sb.append("renice -n $sfNice -p $surface 2>/dev/null; ")
        val sys = SystemReader.read("pidof system_server"); if (sys.isNotBlank()) sb.append("renice -n $sysNice -p $sys 2>/dev/null; ")
        SystemReader.read(sb.toString()); return "Приоритеты: усилены"
    }

    fun resetThreadPriorities(): String {
        val surface = SystemReader.read("pidof surfaceflinger"); if (surface.isNotBlank()) SystemReader.read("renice -n 0 -p $surface 2>/dev/null")
        val sys = SystemReader.read("pidof system_server"); if (sys.isNotBlank()) SystemReader.read("renice -n 0 -p $sys 2>/dev/null")
        return "Приоритеты: сброшены"
    }    fun restrictPowerKeeper(): String {
        val pkg = "com.miui.powerkeeper"; if (!SystemReader.read("pm list packages $pkg").contains(pkg)) return "PowerKeeper не найден"
        SystemReader.read("appops set $pkg WRITE_SETTINGS deny; appops set $pkg GET_USAGE_STATS deny; appops set $pkg RUN_IN_BACKGROUND deny"); return "PowerKeeper: ограничен"
    }

    fun restorePowerKeeper(): String {
        SystemReader.read("appops set com.miui.powerkeeper WRITE_SETTINGS allow; appops set com.miui.powerkeeper GET_USAGE_STATS allow; appops set com.miui.powerkeeper RUN_IN_BACKGROUND allow"); return "PowerKeeper: восстановлен"
    }

    fun cleanupDozeWhitelist(): String {
        var count = 0
        for (pkg in listOf("com.facebook.katana", "com.facebook.orca", "com.instagram.android")) { if (SystemReader.read("pm list packages $pkg").contains(pkg)) { SystemReader.read("dumpsys deviceidle whitelist -$pkg"); count++ } }
        return if (count > 0) "Doze: очищен" else "Нечего чистить"
    }

    fun restrictGms(): String {
        if (!SystemReader.read("pm list packages com.google.android.gms").contains("com.google.android.gms")) return "GMS не найден"
        SystemReader.read("am set-standby-bucket com.google.android.gms rare"); return "GMS: rare"
    }

    fun restoreGms(): String { SystemReader.read("am set-standby-bucket com.google.android.gms active"); return "GMS: active" }

    private fun pingPrefs() = ctx.getSharedPreferences("turbo_ping", Context.MODE_PRIVATE)

    private fun saveAndSet(ns: String, key: String, value: String) {
        val p = pingPrefs()
        val pk = "prev|$ns|$key"
        if (!p.contains(pk)) {
            val prev = SystemReader.read("settings get $ns $key").trim()
            p.edit().putString(pk, if (prev.isEmpty()) "null" else prev).apply()
        }
        SystemReader.read("settings put $ns $key $value")
    }

    fun optimizePing(): String {
        SystemReader.read("cmd wifi force-low-latency-mode enabled 2>/dev/null; cmd wifi force-hi-perf-mode enabled 2>/dev/null")
        saveAndSet("global", "wifi_scan_always_enabled", "0")
        saveAndSet("global", "ble_scan_always_enabled", "0")
        saveAndSet("global", "wifi_networks_available_notification_on", "0")
        saveAndSet("global", "wifi_sleep_policy", "2")
        saveAndSet("secure", "adaptive_connectivity_enabled", "0")
        val p = pingPrefs()
        if (!p.getBoolean("ds_by_us", false)) {
            val st = SystemReader.read("cmd netpolicy get restrict-background")
            if (st.contains("disabled", ignoreCase = true)) {
                SystemReader.read("cmd netpolicy set restrict-background true")
                p.edit().putBoolean("ds_by_us", true).apply()
            }
        }
        return "Пинг: оптимизирован"
    }

    fun restorePing(): String {
        SystemReader.read("cmd wifi force-low-latency-mode disabled 2>/dev/null; cmd wifi force-hi-perf-mode disabled 2>/dev/null")
        val p = pingPrefs()
        val e = p.edit()
        for ((k, v) in p.all) {
            if (!k.startsWith("prev|")) continue
            val parts = k.split("|")
            if (parts.size != 3) continue
            val value = v as? String ?: continue
            if (value == "null") SystemReader.read("settings delete ${parts[1]} ${parts[2]}")
            else SystemReader.read("settings put ${parts[1]} ${parts[2]} $value")
            e.remove(k)
        }
        if (p.getBoolean("ds_by_us", false)) {
            SystemReader.read("cmd netpolicy set restrict-background false")
            e.remove("ds_by_us")
        }
        e.apply()
        return "Пинг: настройки возвращены"
    }

    fun pingTest(): String {
        val hosts = listOf("1.1.1.1", "8.8.8.8")
        val times = ArrayList<Long>()
        for (h in hosts) {
            for (i in 0 until 4) {
                val sock = java.net.Socket()
                try {
                    val t0 = System.nanoTime()
                    sock.connect(java.net.InetSocketAddress(h, 443), 1500)
                    times.add((System.nanoTime() - t0) / 1_000_000L)
                } catch (e: Throwable) {
                } finally {
                    try { sock.close() } catch (e: Throwable) {}
                }
            }
        }
        if (times.isEmpty()) return "Пинг: нет сети"
        val sorted = times.sorted()
        val med = sorted[sorted.size / 2]
        return "Пинг ~$med мс (мин ${sorted.first()}, макс ${sorted.last()})"
    }

    private fun even(v: Int): Int = if (v % 2 == 0) v else v - 1

    fun applyDisplay(gfxScalePct: Int, stretch: Int): String {
        if (gfxScalePct >= 100 && stretch == 0) return resetDisplay()
        val sizeOut = SystemReader.read("wm size")
        val m = Regex("Physical size:\\s*(\\d+)x(\\d+)").find(sizeOut) ?: return "Экран: не удалось узнать размер"
        val pw = m.groupValues[1].toInt()
        val ph = m.groupValues[2].toInt()
        val shortSide = Math.min(pw, ph)
        val longSide = Math.max(pw, ph)
        val scale = gfxScalePct.coerceIn(30, 100) / 100f
        val newShort = even(Math.max(360, (shortSide * scale).toInt()))
        val newLong = when (stretch) {
            1 -> even((newShort * 5f / 4f).toInt())
            2 -> even((newShort * 4f / 3f).toInt())
            else -> even(Math.max(newShort, (longSide * scale).toInt()))
        }
        val size = if (pw <= ph) "${newShort}x${newLong}" else "${newLong}x${newShort}"
        val densOut = SystemReader.read("wm density")
        val dens = Regex("Physical density:\\s*(\\d+)").find(densOut)?.groupValues?.get(1)?.toIntOrNull()
        val cmd = StringBuilder("wm size $size")
        if (dens != null) {
            val nd = Math.max(120, (dens * newShort.toFloat() / shortSide).toInt())
            cmd.append("; wm density $nd")
        }
        SystemReader.read(cmd.toString())
        val st = when (stretch) { 1 -> " (5:4)"; 2 -> " (4:3)"; else -> "" }
        AppPrefs(ctx).setDisplayTweaked(true)
        return "Экран: $size$st"
    }

    fun resetDisplay(): String {
        SystemReader.read("wm size reset; wm density reset")
        AppPrefs(ctx).setDisplayTweaked(false)
        return "Экран: сброшен"
    }

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

    fun unlockGameFps(pkg: String, hzRaw: Int): String {
        if (pkg.isBlank()) return "Пакет не указан"
        val hz = clampHz(hzRaw)
        val safeHz = when { hz >= 120 -> 120; hz >= 90 -> 90; else -> 60 }
        SystemReader.read("cmd game mode performance $pkg; cmd game set --fps $safeHz $pkg; device_config put game_overlay $pkg mode=2,fps=$safeHz")
        return "FPS cap: $safeHz"
    }

    fun resetGameFps(pkg: String): String { if (pkg.isBlank()) return "Пакет не указан"; SystemReader.read("cmd game mode standard $pkg; device_config delete game_overlay $pkg"); return "standard" }

    fun applySense(level: Int): String {
        val lv = level.coerceIn(0, 3)
        val tier = tier()
        val longPress: Int
        val multiPress: Int
        val pointer: Int
        val touchRate: Int
        when (lv) {
            0 -> { longPress = 350; multiPress = 280; pointer = if (tier == 0) 2 else 3; touchRate = 120 }
            1 -> { longPress = 300; multiPress = 250; pointer = if (tier == 0) 3 else 5; touchRate = if (tier == 0) 120 else 180 }
            2 -> { longPress = 250; multiPress = 220; pointer = if (tier == 0) 5 else 6; touchRate = if (tier == 0) 180 else 240 }
            else -> { longPress = 200; multiPress = 200; pointer = 7; touchRate = if (tier == 0) 240 else 300 }
        }
        val sb = StringBuilder()
        sb.append("settings put secure long_press_timeout $longPress; ")
        sb.append("settings put secure multi_press_timeout $multiPress; ")
        sb.append("settings put system pointer_speed $pointer; ")
        sb.append("settings put system touch_sampling_rate $touchRate; ")
        if (lv == 3) {
            sb.append("settings put global low_power 0; ")
            sb.append("settings put global automatic_power_save_mode 0; ")
            sb.append("settings put system show_touches 0; settings put system pointer_location 0; ")
            sb.append("cmd power set-fixed-performance-mode-enabled true 2>/dev/null; ")
        } else {
            sb.append("cmd power set-fixed-performance-mode-enabled false 2>/dev/null; ")
        }
        SystemReader.read(sb.toString())
        val name = arrayOf("Средний", "Сильный", "Эксп.", "Макс")[lv]
        return if (lv == 3) "Сенса: $name (${touchRate} Гц). В игре: Aim Smoothing = 0, ускорение = 0"
        else "Сенса: $name (${touchRate} Гц)"
    }

    fun resetSense(): String {
        SystemReader.read("settings delete secure long_press_timeout; settings delete secure multi_press_timeout; settings delete system pointer_speed; settings delete system touch_sampling_rate; cmd power set-fixed-performance-mode-enabled false 2>/dev/null")
        return "Сенса: сброшена"
    }

    fun applyCustomSense(pointerSpeed: Int, touchRate: Int, predictionOff: Boolean, blockingZero: Boolean, aggregationOff: Boolean): String {
        val ps = pointerSpeed.coerceIn(-5, 5)
        val sb = StringBuilder()
        sb.append("settings put system pointer_speed $ps; ")
        if (touchRate > 0) sb.append("settings put system touch_sampling_rate $touchRate; ")
        else sb.append("settings delete system touch_sampling_rate; ")
        if (predictionOff) sb.append("settings put secure touch_prediction_enabled 0; ")
        else sb.append("settings delete secure touch_prediction_enabled; ")
        if (blockingZero) sb.append("settings put secure touch_blocking_period 0; ")
        else sb.append("settings delete secure touch_blocking_period; ")
        if (aggregationOff) sb.append("settings put secure motion_event_observer_aggregation 0; ")
        else sb.append("settings delete secure motion_event_observer_aggregation; ")
        SystemReader.read(sb.toString())
        return "Сенса: кастом (PS $ps" + (if (touchRate > 0) ", $touchRate Гц" else "") +
            (if (predictionOff) ", без предикта" else "") + (if (blockingZero) ", мгновенный тач" else "") +
            (if (aggregationOff) ", без агрегации" else "") + ")"
    }

    fun resetCustomSense(): String {
        SystemReader.read("settings put system pointer_speed 0; settings delete system touch_sampling_rate; settings delete secure touch_prediction_enabled; settings delete secure touch_blocking_period; settings delete secure motion_event_observer_aggregation")
        return "Сенса: кастом сброшена"
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
        val pm = ctx.packageManager; val result = mutableListOf<Pair<String, String>>()
        for (a in pm.getInstalledApplications(PackageManager.GET_META_DATA)) { if (pm.getLaunchIntentForPackage(a.packageName) == null) continue; val label = pm.getApplicationLabel(a).toString(); if (GameBlocklist.isBlocked(a.packageName, label)) continue; result.add(a.packageName to label) }
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

    fun revertAll(pkg: String, prefs: AppPrefs): String {
        deactivateMaxPerformance()
        resetCustomSense()
        if (pkg.isNotBlank()) {
            resetGameFps(pkg)
            setDownscale(pkg, 0f)
            SystemReader.read("cmd deviceidle whitelist -$pkg")
        }
        restoreBackground()
        restoreGms()
        restorePowerKeeper()
        applySurfaceFlingerBoost(false)
        resetThreadPriorities()
        if (prefs.isDisplayTweaked()) { resetDisplay() }
        prefs.setActive(false)
        OpLog.log("Полный откат${if (pkg.isNotBlank()) " ($pkg)" else ""}")
        return "Всё возвращено к исходному"
    }
}
