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

    private val cpuBase = "/sys/devices/system/cpu"
    private val chipCache: String by lazy { Build.SOC_MODEL.ifBlank { Build.HARDWARE } }
    private val gpuCache: String by lazy { detectGpu() }
    private val clustersCache: List<Pair<Int, String>> by lazy { detectClusters() }
    private val ioCache: String? by lazy { detectIo() }

    fun cpuTemp(): Float {
        val out = SystemReader.read(
            "for z in /sys/class/thermal/thermal_zone*; do t=\$(cat \$z/temp 2>/dev/null); if [ -n \"\$t\" ]; then echo \"\$t\"; fi; done"
        )
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

    fun chipName(): String = chipCache
    fun gpuName(): String = gpuCache
    fun cpuClusters(): List<Pair<Int, String>> = clustersCache

    fun checkSystemState(): Boolean {
        if (!ShizukuHelper.hasPermission()) return false
        val clusters = clustersCache
        if (clusters.isEmpty()) return false
        for ((idx, _) in clusters) {
            val gov = SystemReader.read("cat $cpuBase/cpu$idx/cpufreq/scaling_governor")
            if (gov != "performance") return false
        }
        return true
    }

    fun verifyDnsPublic(): Boolean = verifyDnsApplied()
    fun verifyBbrPublic(): Boolean = verifyBbrApplied()

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
            val f = SystemReader.read("cat $cpuBase/cpu$i/cpufreq/cpuinfo_max_freq 2>/dev/null").toLongOrNull() ?: 0L
            if (f > 0L) freqs.add(i to f)
        }
        if (freqs.isEmpty()) {
            for (i in 0 until 8) {
                if (SystemReader.test("$cpuBase/cpu$i")) {
                    result.add(i to if (i < 4) "little" else "big")
                }
            }
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

    private fun detectIo(): String? {
        val out = SystemReader.read("ls /sys/block/ 2>/dev/null")
        if (out.isBlank()) return null
        for (name in out.lines()) {
            val n = name.trim()
            if (n.isBlank()) continue
            if (n.startsWith("loop") || n.startsWith("ram") || n.startsWith("zram")) continue
            if (SystemReader.test("/sys/block/$n/queue/scheduler")) return n
        }
        return null
    }

    fun ioDevice(): String? = ioCache

    fun switchRenderer(vulkan: Boolean): String {
        return if (vulkan) {
            SystemReader.read("setprop debug.hwui.renderer skiavk; am crash com.android.systemui")
            "Рендерер: Vulkan"
        } else {
            SystemReader.read("setprop debug.hwui.renderer skiagl; am crash com.android.systemui")
            "Рендерер: OpenGL"
        }
    }

    fun activateMaxPerformance(hz: Int, mode: Int, gpuBoost: Boolean): String {
        val bgLimit = if (mode == 0) "64" else "32"
        val sched = if (mode == 0) "noop" else "deadline"
        val io = ioCache

        val sb = StringBuilder()
        sb.append("settings put global activity_manager_constants max_cached_processes=$bgLimit; ")
        if (io != null) sb.append("echo $sched > /sys/block/$io/queue/scheduler 2>/dev/null; ")
        sb.append("settings put system peak_refresh_rate $hz; ")
        sb.append("settings put system min_refresh_rate $hz; ")
        sb.append("settings put global window_animation_scale 0.0; ")
        sb.append("settings put global transition_animation_scale 0.0; ")
        sb.append("settings put global animator_duration_scale 0.5; ")

        val clusters = clustersCache
        val big = clusters.filter { it.second == "big" }.map { it.first }
        val little = clusters.filter { it.second == "little" }.map { it.first }
        val mid = clusters.filter { it.second == "mid" }.map { it.first }

        if (little.isNotEmpty()) sb.append("for i in ${little.joinToString(" ")}; do echo schedutil > $cpuBase/cpu\$i/cpufreq/scaling_governor 2>/dev/null; done; ")
        if (mid.isNotEmpty()) sb.append("for i in ${mid.joinToString(" ")}; do echo schedutil > $cpuBase/cpu\$i/cpufreq/scaling_governor 2>/dev/null; done; ")
        if (big.isNotEmpty()) sb.append("for i in ${big.joinToString(" ")}; do echo performance > $cpuBase/cpu\$i/cpufreq/scaling_governor 2>/dev/null; m=\$(cat $cpuBase/cpu\$i/cpufreq/cpuinfo_max_freq 2>/dev/null); if [ -n \"\$m\" ]; then echo \$m > $cpuBase/cpu\$i/cpufreq/scaling_max_freq 2>/dev/null; fi; done; ")

        if (gpuCache.contains("Adreno", true) && SystemReader.test("/sys/class/kgsl/kgsl-3d0")) {
            sb.append("echo msm-adreno-tz > /sys/class/kgsl/kgsl-3d0/devfreq/governor 2>/dev/null; ")
            sb.append("echo 0 > /sys/class/kgsl/kgsl-3d0/max_pwrlevel 2>/dev/null; ")
            sb.append("echo 3 > /sys/class/kgsl/kgsl-3d0/min_pwrlevel 2>/dev/null; ")
            if (gpuBoost) sb.append("echo 10000000 > /sys/class/kgsl/kgsl-3d0/idle_timer 2>/dev/null; ")
        } else if (gpuCache.contains("Mali", true)) {
            val maliPath = "/sys/class/misc/mali0/device/devfreq"
            if (SystemReader.testWrite("$maliPath/governor")) sb.append("echo performance > $maliPath/governor 2>/dev/null; ")
        }

        sb.append("settings put global low_power 0; ")
        sb.append("am kill-all; ")
        sb.append("echo 1 > /proc/sys/vm/drop_caches")

        SystemReader.read(sb.toString())

        val gov = if (checkGovernorApplied()) "Governor: ✓" else "Governor: ✗"
        return "Режим: ${if (mode == 0) "Производительность" else "Сбалансированный"} | $gov"
    }

    fun deactivateMaxPerformance(): String {
        val io = ioCache
        val sb = StringBuilder()
        sb.append("settings delete global activity_manager_constants; ")
        if (io != null) sb.append("echo deadline > /sys/block/$io/queue/scheduler 2>/dev/null; ")
        sb.append("settings delete system peak_refresh_rate; ")
        sb.append("settings delete system min_refresh_rate; ")
        sb.append("settings put global window_animation_scale 1.0; ")
        sb.append("settings put global transition_animation_scale 1.0; ")
        sb.append("settings put global animator_duration_scale 1.0; ")
        sb.append("for i in 0 1 2 3 4 5 6 7; do echo schedutil > $cpuBase/cpu\$i/cpufreq/scaling_governor 2>/dev/null; done; ")
        if (gpuCache.contains("Adreno", true) && SystemReader.test("/sys/class/kgsl/kgsl-3d0")) {
            sb.append("echo msm-adreno-tz > /sys/class/kgsl/kgsl-3d0/devfreq/governor 2>/dev/null; ")
            sb.append("echo 5000000 > /sys/class/kgsl/kgsl-3d0/idle_timer 2>/dev/null; ")
        }
        sb.append("settings delete global low_power; ")
        sb.append("settings put global private_dns_mode off; ")
        sb.append("settings delete global private_dns_specifier")
        SystemReader.read(sb.toString())
        return "Настройки возвращены"
    }

    fun toggleTurbo(enable: Boolean): String {
        return if (enable) {
            SystemReader.read("sysctl -w net.ipv4.tcp_congestion_control=bbr; sysctl -w net.core.default_qdisc=fq_codel; settings put global private_dns_mode hostname; settings put global private_dns_specifier 1dot1dot1dot1.cloudflare-dns.com")
            val bbr = verifyBbrApplied()
            val dns = verifyDnsApplied()
            "Turbo: BBR ${if (bbr) "✓" else "✗"} | DNS ${if (dns) "✓" else "✗"}"
        } else {
            SystemReader.read("sysctl -w net.ipv4.tcp_congestion_control=cubic; settings put global private_dns_mode off; settings delete global private_dns_specifier")
            "Turbo: ВЫКЛ"
        }
    }

    private fun checkGovernorApplied(): Boolean {
        for ((idx, label) in clustersCache) {
            if (label != "big") continue
            val g = SystemReader.read("cat $cpuBase/cpu$idx/cpufreq/scaling_governor")
            if (g != "performance") return false
        }
        return true
    }

    private fun verifyDnsApplied(): Boolean {
        val dns = SystemReader.read("settings get global private_dns_specifier")
        return dns.contains("cloudflare", true)
    }

    private fun verifyBbrApplied(): Boolean {
        val bbr = SystemReader.read("sysctl net.ipv4.tcp_congestion_control")
        return bbr.contains("bbr", true)
    }

    fun unlockGameFps(pkg: String, hz: Int): String {
        if (pkg.isBlank()) return "Пакет не указан"
        val safeHz = when { hz >= 144 -> 144; hz >= 120 -> 120; hz >= 90 -> 90; else -> 60 }
        SystemReader.read("cmd game mode performance $pkg; cmd game set --fps $safeHz $pkg")
        return "Игра $pkg: FPS $safeHz"
    }

    fun resetGameFps(pkg: String): String {
        if (pkg.isBlank()) return "Пакет не указан"
        SystemReader.read("cmd game mode standard $pkg")
        return "Игра $pkg: standard"
    }

    fun applySense(level: Int): String {
        val slop: Int; val longPress: Int; val multiPress: Int; val pointer: Int; val pressure: String
        when (level) {
            0 -> { slop = 8; longPress = 400; multiPress = 300; pointer = 1; pressure = "1" }
            1 -> { slop = 6; longPress = 300; multiPress = 250; pointer = 4; pressure = "0.1" }
            2 -> { slop = 4; longPress = 200; multiPress = 200; pointer = 7; pressure = "0.001" }
            else -> { slop = 2; longPress = 120; multiPress = 120; pointer = 10; pressure = "0.0001" }
        }
        SystemReader.read("settings put secure touch_slop $slop; settings put secure long_press_timeout $longPress; settings put secure multi_press_timeout $multiPress; settings put secure pointer_speed $pointer; settings put system pointer_speed $pointer; settings put system touch.pressure.scale $pressure")
        return "Сенса: ${arrayOf("Средний", "Сильный", "Эксп.", "Макс")[level]}"
    }

    fun setThermalLimit(temp: Int): String {
        if (temp <= 0) { SystemReader.read("dumpsys thermalservice override-status 0"); return "Троттлинг: выкл" }
        val milli = temp * 1000
        val zones = SystemReader.read("ls /sys/class/thermal/thermal_zone* 2>/dev/null").split("\n")
        var count = 0
        for (z in zones) {
            val zn = z.trim()
            if (zn.isBlank()) continue
            val type = SystemReader.read("cat $zn/type 2>/dev/null").trim().lowercase()
            val allowed = type.contains("cpu") || type.contains("soc") || type.contains("gpu") || type.contains("tsens") || type.contains("battery") || type.contains("skin")
            if (!allowed) continue
            if (type.contains("critical")) continue
            if (SystemReader.testWrite("$zn/trip_point_0_temp")) {
                SystemReader.read("echo $milli > $zn/trip_point_0_temp 2>/dev/null")
                count++
            }
        }
        return "Троттлинг: $temp°C (зон: $count)"
    }

    fun resetThermal(): String {
        SystemReader.read("dumpsys thermalservice override-status -1")
        return "Троттлинг: авто"
    }

    fun compilePackage(pkg: String): String {
        if (pkg.isBlank()) return "Введите пакет"
        val code = SystemReader.read("cmd package compile -m speed -f $pkg")
        return if (code.isBlank()) "Компиляция запущена" else "Результат: $code"
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

    fun ping(): String {
        val c = SystemReader.read("ping -c 3 -W 2 1.1.1.1")
        val g = SystemReader.read("ping -c 3 -W 2 8.8.8.8")
        val ct = parsePingAvg(c)
        val gt = parsePingAvg(g)
        return "Cloudflare: ${if (ct >= 0) "$ct мс" else "—"} | Google: ${if (gt >= 0) "$gt мс" else "—"}"
    }

    private fun parsePingAvg(out: String): Int {
        val line = out.lines().firstOrNull { it.contains("avg") } ?: return -1
        val parts = line.split("/")
        if (parts.size < 5) return -1
        return parts[4].toDoubleOrNull()?.toInt() ?: -1
    }

    fun currentFps(pkg: String): Int {
        if (pkg.isBlank()) return 0
        val out = SystemReader.read("dumpsys gfxinfo $pkg framestats 2>/dev/null")
        if (out.isBlank()) return 0
        val lines = out.lines()
        var lastFrame = 0L
        for (i in lines.indices.reversed()) {
            val l = lines[i].trim()
            if (l.startsWith("0,") && l.contains(",")) {
                val cols = l.split(",")
                if (cols.size > 1) {
                    val t = cols[1].toLongOrNull() ?: 0L
                    if (t > 0L) { lastFrame = t; break }
                }
            }
        }
        if (lastFrame == 0L) return 0
        return 0
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
        val pm = ctx.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val result = mutableListOf<Pair<String, String>>()
        for (a in apps) {
            if (a.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue
            if (a.category != ApplicationInfo.CATEGORY_GAME) continue
            result.add(a.packageName to pm.getApplicationLabel(a).toString())
        }
        return result.sortedBy { it.second.lowercase() }
    }

    fun openDeveloperOptions() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        } catch (e: Exception) {}
    }
}
