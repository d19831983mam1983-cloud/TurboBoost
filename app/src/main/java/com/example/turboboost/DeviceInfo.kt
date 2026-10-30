package com.example.turboboost

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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

    fun isRooted(): Boolean = SystemReader.rootMode

    fun isXiaomi(): Boolean {
        val m = (Build.MANUFACTURER ?: "").lowercase()
        val b = (Build.BRAND ?: "").lowercase()
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") ||
               b.contains("xiaomi") || b.contains("redmi") || b.contains("poco")
    }

    fun isRealme(): Boolean {
        val m = (Build.MANUFACTURER ?: "").lowercase()
        val b = (Build.BRAND ?: "").lowercase()
        return m.contains("realme") || m.contains("oppo") || m.contains("oneplus") ||
               b.contains("realme") || b.contains("oppo") || b.contains("oneplus")
    }

    fun isGpuBoostSupported(): Boolean =
        isRooted() && (SystemReader.test("/sys/class/kgsl/kgsl-3d0") || SystemReader.test("/sys/class/misc/mali0"))

    fun isThreadTuningSupported(): Boolean = isRooted()

    fun isMglruSupported(): Boolean = isRooted() && SystemReader.test(mglruPath)

    fun isJoyoseSupported(): Boolean = isXiaomi() && hasJoyose()

    fun isVendorGameCmdSupported(): Boolean = isXiaomi() || isRealme()

    fun isGtGpuTunerSupported(): Boolean = isXiaomi()

    fun isSenseTuningSupported(): Boolean = isXiaomi() || isRealme()

    fun isDownscaleSupported(): Boolean = isXiaomi() || isRealme()

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
        } catch (_: Throwable) {}
        if (rates.isEmpty()) {
            try {
                val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                rates.add(Math.round(wm.defaultDisplay.refreshRate))
            } catch (_: Throwable) {}
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

    private fun hasJoyoseInternal(): Boolean =
        SystemReader.read("pm list packages com.xiaomi.joyose").contains("com.xiaomi.joyose")

    fun hasJoyose(): Boolean = joyose()fun disableJoyose(): String {
    if (!isXiaomi()) return "Только Xiaomi"
    if (!isRooted()) return "Требуется root"
    if (!joyose()) return "Joyose не найден"
    SystemReader.read("pm disable-user --user 0 com.xiaomi.joyose; am force-stop com.xiaomi.joyose")
    return "Joyose отключён"
}

fun enableJoyose(): String {
    if (!isXiaomi()) return "Только Xiaomi"
    if (!isRooted()) return "Требуется root"
    if (!joyose()) return "Joyose не найден"
    SystemReader.read("pm enable --user 0 com.xiaomi.joyose")
    return "Joyose включён"
}

fun toggleMglru(enable: Boolean): String {
    if (!isRooted()) return "Требуется root"
    if (!isMglruSupported()) return "MGLRU не поддерживается ядром"
    val v = if (enable) "y" else "n"
    val res = SystemReader.read("echo $v > $mglruPath 2>&1")
    val check = SystemReader.read("cat $mglruPath 2>/dev/null")
    return if (check.contains(v)) "MGLRU: ${if (enable) "вкл" else "выкл"}"
    else "MGLRU: нет доступа на запись${if (res.isNotBlank()) " ($res)" else ""}"
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
    val minF = freqs.minOf { it.second }
    val maxF = freqs.maxOf { it.second }
    val mid = (minF + maxF) / 2
    for ((idx, f) in freqs) {
        val label = when {
            f <= mid -> "little"
            f >= maxF - (maxF - minF) / 4 -> "big"
            else -> "mid"
        }
        result.add(idx to label)
    }
    return result
}

fun gpuBoost(enable: Boolean): String {
    if (!isRooted()) return "Требуется root"
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
    val bgLimit = if (mode == 0) "96" else "64"
    val sb = StringBuilder()
    sb.append("settings put global activity_manager_constants max_cached_processes=$bgLimit; ")
    sb.append("settings put system peak_refresh_rate $hz; settings put system min_refresh_rate $hz; ")
    if (pkg.isNotBlank()) {
        sb.append("cmd appops set $pkg RUN_IN_BACKGROUND allow; cmd deviceidle whitelist +$pkg; ")
    }
    SystemReader.read(sb.toString())
    val gpuResult = if (gpuBoost && isGpuBoostSupported()) gpuBoost(true) else ""
    if (pkg.isNotBlank()) compilePackageSpeedProfile(pkg)
    applyMemoryTweaks(true)
    applyPowerTweaks(true)
    applyNotificationTweaks(true)
    val gpuLabel = when {
        !gpuBoost -> "stock"
        gpuResult.startsWith("GPU:") -> "boost"
        else -> gpuResult.ifBlank { "stock" }
    }
    return "Класс: ${tierName()} | RAM: настроена | Hz: $hz | AOT: да | GPU: $gpuLabel"
}

fun deactivateMaxPerformance(): String {
    SystemReader.read("settings delete global activity_manager_constants; settings delete system peak_refresh_rate; settings delete system min_refresh_rate")
    if (isGpuBoostSupported()) gpuBoost(false)
    resetSense()
    restoreAllSettings()
    applyMemoryTweaks(false)
    applyPowerTweaks(false)
    applyNotificationTweaks(false)
    return "Настройки возвращены"
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
    if (!isRooted()) return "Требуется root"
    if (pkg.isBlank()) return "Пакет не указан"
    val pidInt = firstPid(pkg) ?: return "Игра не запущена"
    val pid = pidInt.toString()
    val allCores = clusters().map { it.first }.sorted()
    if (allCores.isEmpty()) return "Ядра не определены"
    var mask = 0
    for (core in allCores) mask = mask or (1 shl core)
    val hexMask = Integer.toHexString(mask)
    var tids = SystemReader.read("ps -T -p $pid -o TID").lines().map { it.trim() }
        .filter { it.isNotEmpty() && it.toIntOrNull() != null && it.toInt() != pid.toInt() }
    if (tids.isEmpty()) {
        tids = SystemReader.read("top -H -b -n 1 -p $pid").lines().map { it.trim().split(Regex("\\s+")) }
            .filter { it.size > 1 && it[0].toIntOrNull() != null }.map { it[0] }.filter { it != pid }
    }
    if (tids.isEmpty()) return "Потоки не найдены"
    val sb = StringBuilder()
    for (tid in tids) sb.append("taskset -p $hexMask $tid 2>/dev/null; ")
    SystemReader.read(sb.toString())
    return "Игра $pkg: ${tids.size} потоков на ${allCores.size} ядрах"
}

fun pinGameToPrimeCore(pkg: String): String {
    if (!isRooted()) return "Требуется root"
    if (pkg.isBlank()) return "Пакет не указан"
    val pidInt = firstPid(pkg) ?: return "Игра не запущена"
    val pid = pidInt.toString()
    val big = clusters().filter { it.second == "big" }.map { it.first }.sorted()
    if (big.isEmpty()) return "Нет big-ядер"
    var mask = 0
    for (core in big) mask = mask or (1 shl core)
    val hexMask = Integer.toHexString(mask)
    val allTids = SystemReader.read("ps -T -p $pid -o TID,CMD").lines().map { it.trim() }
        .filter { it.isNotEmpty() }.mapNotNull { line ->
            val parts = line.split(Regex("\\s+"))
            if (parts.size >= 2) {
                val tid = parts[0].toIntOrNull()
                if (tid != null && tid != pid.toInt()) tid to parts[1] else null
            } else null
        }
    if (allTids.isEmpty()) return "Потоки не найдены"
    var target = allTids.firstOrNull { (_, name) ->
        name.contains("GameThread", true) || name.contains("UnityMain", true) || name == "main"
    }
    if (target == null) target = allTids.firstOrNull { (_, name) ->
        name.contains("RenderThread", true) || name.contains("UnityGfx", true)
    }
    if (target == null) target = allTids.firstOrNull()
    if (target == null) return "Главный поток не найден"
    SystemReader.read("taskset -p $hexMask ${target.first} 2>/dev/null; renice -n -15 -p ${target.first} 2>/dev/null")
    return "Отклик: ${target.second} -> big-ядра"
}

fun boostInputDispatcher(): String {
    if (!isRooted()) return "Требуется root"
    val sb = StringBuilder()
    val inputPid = SystemReader.read("pidof inputflinger").trim()
    if (inputPid.isNotBlank()) {
        sb.append("renice -n -15 -p $inputPid 2>/dev/null; ")
        val tids = SystemReader.read("ps -T -p $inputPid -o TID,CMD").lines().map { it.trim() }
            .filter { it.contains("InputDispatcher", true) }
            .mapNotNull { it.split(Regex("\\s+")).firstOrNull()?.toIntOrNull() }
        for (tid in tids) sb.append("renice -n -15 -p $tid 2>/dev/null; ")
    }
    SystemReader.read(sb.toString())
    return if (inputPid.isNotBlank()) "InputDispatcher: усилен" else "inputflinger не найден"
}

fun boostThreadPriorities(): String {
    if (!isRooted()) return "Требуется root"
    val sb = StringBuilder()
    val sfNice = when (tier()) { 0 -> -10; 2 -> -18; else -> -15 }
    val sysNice = when (tier()) { 0 -> -5; 2 -> -10; else -> -8 }
    val surface = SystemReader.read("pidof surfaceflinger")
    if (surface.isNotBlank()) sb.append("renice -n $sfNice -p $surface 2>/dev/null; ")
    val sys = SystemReader.read("pidof system_server")
    if (sys.isNotBlank()) sb.append("renice -n $sysNice -p $sys 2>/dev/null; ")
    SystemReader.read(sb.toString())
    return "Приоритеты: усилены"
}

fun resetThreadPriorities(): String {
    if (!isRooted()) return "Требуется root"
    val surface = SystemReader.read("pidof surfaceflinger")
    if (surface.isNotBlank()) SystemReader.read("renice -n 0 -p $surface 2>/dev/null")
    val sys = SystemReader.read("pidof system_server")
    if (sys.isNotBlank()) SystemReader.read("renice -n 0 -p $sys 2>/dev/null")
    return "Приоритеты: сброшены"
}fun restrictPowerKeeper(): String {
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
        if (SystemReader.read("pm list packages $pkg").contains(pkg)) {
            SystemReader.read("dumpsys deviceidle whitelist -$pkg")
            count++
        }
    }
    return if (count > 0) "Doze: очищен" else "Нечего чистить"
}

fun restrictGms(): String {
    if (!SystemReader.read("pm list packages com.google.android.gms").contains("com.google.android.gms")) return "GMS не найден"
    SystemReader.read("am set-standby-bucket com.google.android.gms rare")
    return "GMS: rare"
}

fun restoreGms(): String {
    SystemReader.read("am set-standby-bucket com.google.android.gms active")
    return "GMS: active"
}

private fun pingPrefs() = ctx.getSharedPreferences("turbo_ping", Context.MODE_PRIVATE)

private fun devPrefs() = ctx.getSharedPreferences("turbo_devcfg", Context.MODE_PRIVATE)

private fun saveAndSet(ns: String, key: String, value: String) {
    val p = pingPrefs()
    val pk = "prev|$ns|$key"
    if (!p.contains(pk)) {
        val prev = SystemReader.read("settings get $ns $key").trim()
        p.edit().putString(pk, if (prev.isEmpty() || prev == "null") "null" else prev).apply()
    }
    SystemReader.read("settings put $ns $key $value")
}

private fun saveAndSetDeviceConfig(name: String, key: String, value: String) {
    val p = devPrefs()
    val pk = "prev|$name|$key"
    if (!p.contains(pk)) {
        val prev = SystemReader.read("device_config get $name $key").trim()
        p.edit().putString(pk, if (prev.isBlank() || prev == "null") "null" else prev).apply()
    }
    SystemReader.read("device_config put $name $key $value")
}

fun applyGlobalSetting(ns: String, key: String, value: String): String {
    saveAndSet(ns, key, value)
    return "Применено"
}

fun revertSetting(ns: String, key: String): String {
    val p = pingPrefs()
    val pk = "prev|$ns|$key"
    val prev = p.getString(pk, null) ?: return "Нечего откатывать"
    if (prev == "null") SystemReader.read("settings delete $ns $key")
    else SystemReader.read("settings put $ns $key $prev")
    p.edit().remove(pk).apply()
    return "Возвращено"
}

fun applyDeviceConfig(name: String, key: String, value: String): String {
    saveAndSetDeviceConfig(name, key, value)
    return "Применено"
}

fun revertDeviceConfig(name: String, key: String): String {
    val p = devPrefs()
    val pk = "prev|$name|$key"
    val prev = p.getString(pk, null) ?: return "Нечего откатывать"
    if (prev == "null") SystemReader.read("device_config put $name $key null")
    else SystemReader.read("device_config put $name $key $prev")
    p.edit().remove(pk).apply()
    return "Возвращено"
}

fun applyMemoryTweaks(enable: Boolean): String {
    return if (enable) {
        saveAndSetDeviceConfig("activity_manager", "max_phantom_processes", "2147483647")
        saveAndSet("global", "settings_enable_monitor_phantom_procs", "false")
        SystemReader.read("cmd package trim-caches 1000M")
        "Память: твики применены"
    } else {
        "Память: твики возвращены"
    }
}

fun applyPowerTweaks(enable: Boolean): String {
    return if (enable) {
        saveAndSet("global", "low_power", "0")
        saveAndSet("global", "automatic_power_save_mode", "0")
        saveAndSet("global", "adaptive_battery_management_enabled", "0")
        saveAndSet("global", "app_standby_enabled", "0")
        saveAndSet("global", "cached_apps_freezer", "0")
        "Батарея: твики применены"
    } else {
        "Батарея: твики возвращены"
    }
}

fun applyNotificationTweaks(enable: Boolean): String {
    return if (enable) {
        saveAndSet("global", "heads_up_notifications_enabled", "0")
        "Уведомления: heads-up выключены"
    } else {
        "Уведомления: heads-up возвращены"
    }
}

fun optimizePing(): String {
    saveAndSet("global", "wifi_scan_always_enabled", "0")
    saveAndSet("global", "ble_scan_always_enabled", "0")
    saveAndSet("global", "wifi_networks_available_notification_on", "0")
    saveAndSet("global", "wifi_sleep_policy", "2")
    val p = pingPrefs()
    if (!p.getBoolean("ds_by_us", false)) {
        val st = SystemReader.read("cmd netpolicy get restrict-background")
        if (st.contains("disabled", ignoreCase = true)) {
            SystemReader.read("cmd netpolicy set restrict-background true")
            p.edit().putBoolean("ds_by_us", true).apply()
        }
    }
    val pkg = AppPrefs(ctx).getLastPackage()
    if (pkg.isNotBlank()) {
        SystemReader.read("cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow")
        SystemReader.read("am set-standby-bucket $pkg active")
    }
    return "Пинг: оптимизирован"
}

fun restoreAllSettings(): String {
    val ps = pingPrefs()
    val ep = ps.edit()
    for ((k, v) in ps.all) {
        if (!k.startsWith("prev|")) continue
        val parts = k.split("|")
        if (parts.size != 3) continue
        val value = v as? String ?: continue
        if (value == "null") SystemReader.read("settings delete ${parts[1]} ${parts[2]}")
        else SystemReader.read("settings put ${parts[1]} ${parts[2]} $value")
        ep.remove(k)
    }
    if (ps.getBoolean("ds_by_us", false)) {
        SystemReader.read("cmd netpolicy set restrict-background false")
        ep.remove("ds_by_us")
    }
    ep.apply()

    val pd = devPrefs()
    val ed = pd.edit()
    for ((k, v) in pd.all) {
        if (!k.startsWith("prev|")) continue
        val parts = k.split("|")
        if (parts.size != 3) continue
        val value = v as? String ?: continue
        if (value == "null") SystemReader.read("device_config put ${parts[1]} ${parts[2]} null")
        else SystemReader.read("device_config put ${parts[1]} ${parts[2]} $value")
        ed.remove(k)
    }
    ed.apply()

    val pkg = AppPrefs(ctx).getLastPackage()
    if (pkg.isNotBlank()) {
        SystemReader.read("cmd appops set $pkg RUN_ANY_IN_BACKGROUND default")
        SystemReader.read("am set-standby-bucket $pkg active")
    }
    return "Настройки возвращены"
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
            } catch (_: Throwable) {
            } finally {
                try { sock.close() } catch (_: Throwable) {}
            }
        }
    }
    if (times.isEmpty()) return "Пинг: нет сети"
    val sorted = times.sorted()
    val med = sorted[sorted.size / 2]
    return "Пинг ~$med мс (мин ${sorted.first()}, макс ${sorted.last()})"
}    private fun even(v: Int): Int = if (v % 2 == 0) v else v - 1

    private fun resPrefs() = ctx.getSharedPreferences("turbo_res", Context.MODE_PRIVATE)

    fun saveOriginalResolution(): Boolean {
        val p = resPrefs()
        if (p.getBoolean("saved", false)) return true
        val out = SystemReader.read("wm size")
        val m = Regex("Physical size:\\s*(\\d+)x(\\d+)").find(out) ?: return false
        val w = m.groupValues[1].toIntOrNull() ?: return false
        val h = m.groupValues[2].toIntOrNull() ?: return false
        val densOut = SystemReader.read("wm density")
        val d = Regex("Physical density:\\s*(\\d+)").find(densOut)?.groupValues?.get(1)?.toIntOrNull() ?: -1
        p.edit().putInt("ow", w).putInt("oh", h).putInt("od", d).putBoolean("saved", true).apply()
        return true
    }

    fun applyCustomResolution(width: Int, height: Int): String {
        if (width < 320 || height < 320) return "Слишком маленькое разрешение"
        if (width > 4320 || height > 4320) return "Слишком большое разрешение"
        if (!saveOriginalResolution()) return "Не удалось сохранить исходное"
        val p = resPrefs()
        val ow = p.getInt("ow", 0)
        val oh = p.getInt("oh", 0)
        val od = p.getInt("od", -1)
        if (ow <= 0 || oh <= 0) return "Неизвестно исходное разрешение"
        val w = even(width)
        val h = even(height)
        val oldShort = Math.min(ow, oh)
        val newShort = Math.min(w, h)
        val cmd = StringBuilder("wm size ${w}x${h}")
        if (od > 0) {
            val nd = Math.max(120, (od * newShort.toFloat() / oldShort).toInt())
            cmd.append("; wm density $nd")
        }
        SystemReader.read(cmd.toString())
        AppPrefs(ctx).setDisplayTweaked(true)
        p.edit().putLong("revert_at", System.currentTimeMillis() + 25_000L).apply()
        return "Экран: ${w}x${h}"
    }

    fun revertResolution(): String {
        val p = resPrefs()
        val ow = p.getInt("ow", 0)
        val oh = p.getInt("oh", 0)
        val od = p.getInt("od", -1)
        val cmd = StringBuilder()
        if (ow > 0 && oh > 0) cmd.append("wm size ${ow}x${oh}; ") else cmd.append("wm size reset; ")
        if (od > 0) cmd.append("wm density $od") else cmd.append("wm density reset")
        SystemReader.read(cmd.toString())
        p.edit().clear().apply()
        AppPrefs(ctx).setDisplayTweaked(false)
        return "Экран: возвращён"
    }

    fun getResRevertAt(): Long = resPrefs().getLong("revert_at", 0L)

    fun setDownscale(pkg: String, factor: Float): String {
        if (!isDownscaleSupported()) return "Не поддерживается на этом устройстве"
        if (pkg.isBlank()) return "Пакет не указан"
        val safe = when {
            factor <= 0f -> "disable"
            factor < 0.3f -> "0.3"
            factor > 0.9f -> "0.9"
            else -> String.format(java.util.Locale.US, "%.2f", factor)
        }
        return try {
            SystemReader.read("cmd game set --downscale $safe $pkg")
            if (safe == "disable") "Downscale: выкл" else "Downscale: $safe"
        } catch (_: Throwable) {
            "Downscale: ошибка"
        }
    }

    fun compilePackageSpeedProfile(pkg: String): String {
        if (pkg.isBlank()) return "Введите пакет"
        return if (SystemReader.read("cmd package compile -m speed-profile -f $pkg").isBlank()) "Компиляция запущена" else "Готово"
    }

    fun runFstrim(): String {
        SystemReader.read("sm fstrim")
        return "Fstrim: выполнен"
    }

    fun unlockGameFps(pkg: String, hzRaw: Int): String {
        if (!isVendorGameCmdSupported()) return "Не поддерживается на этом устройстве"
        if (pkg.isBlank()) return "Пакет не указан"
        val safeHz = supportedHz().last()
        SystemReader.read("cmd game mode performance $pkg; cmd game set --fps $safeHz $pkg; device_config put game_overlay $pkg mode=2,fps=$safeHz")
        return "FPS cap: $safeHz"
    }

    fun resetGameFps(pkg: String): String {
        if (!isVendorGameCmdSupported()) return "Не поддерживается на этом устройстве"
        if (pkg.isBlank()) return "Пакет не указан"
        SystemReader.read("cmd game mode standard $pkg; device_config delete game_overlay $pkg")
        return "standard"
    }

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
        val senseTuning = isSenseTuningSupported()
        if (senseTuning) sb.append("settings put system touch_sampling_rate $touchRate; ")
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
        val rateSuffix = if (senseTuning) " (${touchRate} Гц)" else ""
        return if (lv == 3) "Сенса: $name$rateSuffix. В игре: Aim Smoothing = 0, ускорение = 0"
        else "Сенса: $name$rateSuffix"
    }

    fun resetSense(): String {
        SystemReader.read("settings delete secure long_press_timeout; settings delete secure multi_press_timeout; settings delete system pointer_speed; settings delete system touch_sampling_rate; cmd power set-fixed-performance-mode-enabled false 2>/dev/null")
        return "Сенса: сброшена"
    }

    fun applyCustomSense(pointerSpeed: Int, touchRate: Int, predictionOff: Boolean, blockingZero: Boolean, aggregationOff: Boolean): String {
        val ps = pointerSpeed.coerceIn(-5, 5)
        val senseTuning = isSenseTuningSupported()
        val sb = StringBuilder()
        sb.append("settings put system pointer_speed $ps; ")
        if (senseTuning && touchRate > 0) sb.append("settings put system touch_sampling_rate $touchRate; ")
        else sb.append("settings delete system touch_sampling_rate; ")
        if (senseTuning && predictionOff) sb.append("settings put secure touch_prediction_enabled 0; ")
        else sb.append("settings delete secure touch_prediction_enabled; ")
        if (senseTuning && blockingZero) sb.append("settings put secure touch_blocking_period 0; ")
        else sb.append("settings delete secure touch_blocking_period; ")
        if (senseTuning && aggregationOff) sb.append("settings put secure motion_event_observer_aggregation 0; ")
        else sb.append("settings delete secure motion_event_observer_aggregation; ")
        SystemReader.read(sb.toString())
        val warn = if (!senseTuning) " (часть твиков недоступна)" else ""
        return "Сенса: кастом (PS $ps" + (if (senseTuning && touchRate > 0) ", $touchRate Гц" else "") +
            (if (senseTuning && predictionOff) ", без предикта" else "") +
            (if (senseTuning && blockingZero) ", мгновенный тач" else "") +
            (if (senseTuning && aggregationOff) ", без агрегации" else "") + ")$warn"
    }

    fun resetCustomSense(): String {
        SystemReader.read("settings put system pointer_speed 0; settings delete system touch_sampling_rate; settings delete secure touch_prediction_enabled; settings delete secure touch_blocking_period; settings delete secure motion_event_observer_aggregation")
        return "Сенса: кастом сброшена"
    }

    fun applyAnimationScale(scalePct: Int): String {
        val s = (scalePct.coerceIn(50, 100) / 100f).toString()
        SystemReader.read("settings put global window_animation_scale $s; settings put global transition_animation_scale $s; settings put global animator_duration_scale $s")
        return "Анимации: ${scalePct}%"
    }

    fun resetAnimationScale(): String {
        SystemReader.read("settings put global window_animation_scale 1.0; settings put global transition_animation_scale 1.0; settings put global animator_duration_scale 1.0")
        return "Анимации: 100%"
    }

    fun applyLongPress(ms: Int): String {
        SystemReader.read("settings put secure long_press_timeout ${ms.coerceIn(200, 700)}")
        return "Удержание: $ms мс"
    }

    fun applyGtGpuTuner(enable: Boolean): String {
        if (!isGtGpuTunerSupported()) return "Только Xiaomi"
        return if (enable) {
            SystemReader.read("settings put global GPUTUNER_SWITCH true")
            "Game Turbo: GPU Tuner включён (перезапусти Game Turbo)"
        } else {
            SystemReader.read("settings put global GPUTUNER_SWITCH false")
            "Game Turbo: GPU Tuner выключен"
        }
    }

    fun resetGameTurbo(): String {
        if (!isGtGpuTunerSupported()) return "Только Xiaomi"
        SystemReader.read("settings delete global GPUTUNER_SWITCH")
        return "Game Turbo: GPU Tuner сброшен"
    }

    fun compilePackage(pkg: String): String {
        if (pkg.isBlank()) return "Введите пакет"
        return if (SystemReader.read("cmd package compile -m speed -f $pkg").isBlank()) "Запущена" else "Готово"
    }

    fun trimCaches(): String {
        SystemReader.read("cmd package trim-caches 1000M")
        return "Кэш очищен"
    }

    fun listGames(): List<Pair<String, String>> {
        val pm = ctx.packageManager
        val result = mutableListOf<Pair<String, String>>()
        for (a in pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
            if (pm.getLaunchIntentForPackage(a.packageName) == null) continue
            val label = pm.getApplicationLabel(a).toString()
            if (GameBlocklist.isBlocked(a.packageName, label)) continue
            result.add(a.packageName to label)
        }
        return result.sortedBy { it.second.lowercase() }
    }

    fun openGameSpace(): String {
        for (pkg in listOf("com.miui.securitycenter", "com.coloros.gamespace", "com.coloros.gamespaceui", "com.transsion.game", "com.transsion.gamemode")) {
            try {
                val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
                if (i != null) {
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(i)
                    return "Открыт"
                }
            } catch (_: Throwable) {}
        }
        return "Не найден"
    }

    fun openDeveloperOptions() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun setDozeWhitelist(pkg: String, add: Boolean): String {
        if (pkg.isBlank()) return "Пакет не указан"
        SystemReader.read("cmd deviceidle whitelist ${if (add) "+" else "-"}$pkg")
        return if (add) "Doze whitelist: +$pkg" else "Doze whitelist: -$pkg"
    }

    fun setAppOp(pkg: String, op: String, mode: String): String {
        if (pkg.isBlank()) return "Пакет не указан"
        SystemReader.read("cmd appops set $pkg $op $mode")
        return "AppOps $op = $mode"
    }

    fun setStandbyBucket(pkg: String, bucket: String): String {
        if (pkg.isBlank()) return "Пакет не указан"
        SystemReader.read("am set-standby-bucket $pkg $bucket")
        return "Standby: $bucket"
    }

    fun setGameMode(pkg: String, mode: String): String {
        if (!isVendorGameCmdSupported()) return "Не поддерживается на этом устройстве"
        if (pkg.isBlank()) return "Пакет не указан"
        SystemReader.read("cmd game mode $mode $pkg")
        return "Game mode: $mode"
    }

    fun setGameFps(pkg: String, hz: Int): String {
        if (!isVendorGameCmdSupported()) return "Не поддерживается на этом устройстве"
        if (pkg.isBlank()) return "Пакет не указан"
        SystemReader.read("cmd game set --fps $hz $pkg")
        return "FPS cap: $hz"
    }

    fun resetGameFpsOnly(pkg: String): String {
        if (!isVendorGameCmdSupported()) return "Не поддерживается на этом устройстве"
        if (pkg.isBlank()) return "Пакет не указан"
        SystemReader.read("cmd game set --fps 0 $pkg")
        return "FPS cap: снят"
    }

    fun setGameDownscale(pkg: String, factor: Float): String {
        if (!isDownscaleSupported()) return "Не поддерживается на этом устройстве"
        if (pkg.isBlank()) return "Пакет не указан"
        val safe = when {
            factor <= 0f -> "disable"
            factor < 0.3f -> "0.3"
            factor > 0.9f -> "0.9"
            else -> String.format(java.util.Locale.US, "%.2f", factor)
        }
        SystemReader.read("cmd game set --downscale $safe $pkg")
        return if (safe == "disable") "Downscale: выкл" else "Downscale: $safe"
    }

    fun revertAll(pkg: String, prefs: AppPrefs): String {
        deactivateMaxPerformance()
        resetCustomSense()
        resetAnimationScale()
        applyLongPress(400)
        if (isGtGpuTunerSupported()) resetGameTurbo()
        if (pkg.isNotBlank()) {
            if (isVendorGameCmdSupported()) resetGameFps(pkg)
            if (isDownscaleSupported()) setDownscale(pkg, 0f)
            SystemReader.read("cmd deviceidle whitelist -$pkg")
            SystemReader.read("cmd appops set $pkg RUN_ANY_IN_BACKGROUND default")
            SystemReader.read("am set-standby-bucket $pkg active")
        }
        restoreGms()
        restorePowerKeeper()
        if (isRooted()) resetThreadPriorities()
        if (prefs.isDisplayTweaked()) revertResolution()
        prefs.setActive(false)
        OpLog.log("Полный откат${if (pkg.isNotBlank()) " ($pkg)" else ""}")
        return "Всё возвращено к исходному"
    }
}
