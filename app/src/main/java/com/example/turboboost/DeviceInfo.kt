package com.example.turboboost

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings

class DeviceInfo(private val ctx: Context) {

    private val cpuBase = "/sys/devices/system/cpu"

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

    fun chipName(): String = Build.SOC_MODEL.ifBlank { Build.HARDWARE }

    fun checkSystemState(): Boolean {
        if (!ShizukuHelper.hasPermission()) return false
        val gov = SystemReader.read("cat $cpuBase/cpu0/cpufreq/scaling_governor")
        return gov == "performance"
    }

    fun isVulkanSupported(): Boolean = SystemReader.read("dumpsys SurfaceFlinger | grep -i vulkan").isNotBlank()
    fun isOpenGlSupported(): Boolean = SystemReader.read("dumpsys SurfaceFlinger | grep -i opengl").isNotBlank()

    fun switchRenderer(vulkan: Boolean): String {
        return if (vulkan) {
            SystemReader.read("setprop debug.hwui.renderer skiavk")
            SystemReader.read("am crash com.android.systemui")
            "Рендерер: Vulkan"
        } else {
            SystemReader.read("setprop debug.hwui.renderer skiagl")
            SystemReader.read("am crash com.android.systemui")
            "Рендерер: OpenGL"
        }
    }

    fun activateMaxPerformance(hz: Int, mode: Int): String {
        val bgLimit = if (mode == 0) "64" else "32"
        SystemReader.read("settings put global activity_manager_constants max_cached_processes=$bgLimit")

        val sched = if (mode == 0) "noop" else "deadline"
        SystemReader.read("echo $sched > /sys/block/sda/queue/scheduler")
        SystemReader.read("echo $sched > /sys/block/sdb/queue/scheduler")

        SystemReader.read("settings put system peak_refresh_rate $hz")
        SystemReader.read("settings put system min_refresh_rate $hz")
        SystemReader.read("settings put global window_animation_scale 0.0")
        SystemReader.read("settings put global transition_animation_scale 0.0")
        SystemReader.read("settings put global animator_duration_scale 0.0")

        for (i in 0 until 8) {
            SystemReader.read("echo performance > $cpuBase/cpu$i/cpufreq/scaling_governor")
            val max = SystemReader.read("cat $cpuBase/cpu$i/cpufreq/cpuinfo_max_freq").toLongOrNull() ?: 0L
            if (max > 0L) {
                SystemReader.read("echo $max > $cpuBase/cpu$i/cpufreq/scaling_min_freq")
                SystemReader.read("echo $max > $cpuBase/cpu$i/cpufreq/scaling_max_freq")
            }
        }

        val gpuPath = "/sys/class/kgsl/kgsl-3d0"
        if (SystemReader.test(gpuPath)) {
            SystemReader.read("echo performance > $gpuPath/devfreq/governor")
            SystemReader.read("echo 0 > $gpuPath/min_pwrlevel")
            SystemReader.read("echo 0 > $gpuPath/max_pwrlevel")
        }

        SystemReader.read("settings put global low_power 0")
        SystemReader.read("dumpsys deviceidle disable")
        
        SystemReader.read("am kill-all")
        SystemReader.read("echo 1 > /proc/sys/vm/drop_caches")

        return "Режим: ${if (mode == 0) "Производительность" else "Сбалансированный"}"
    }

    fun deactivateMaxPerformance(): String {
        SystemReader.read("settings delete global activity_manager_constants")
        SystemReader.read("echo deadline > /sys/block/sda/queue/scheduler")
        SystemReader.read("echo deadline > /sys/block/sdb/queue/scheduler")

        SystemReader.read("settings delete system peak_refresh_rate")
        SystemReader.read("settings delete system min_refresh_rate")
        SystemReader.read("settings put global window_animation_scale 1.0")
        SystemReader.read("settings put global transition_animation_scale 1.0")
        SystemReader.read("settings put global animator_duration_scale 1.0")

        for (i in 0 until 8) {
            SystemReader.read("echo schedutil > $cpuBase/cpu$i/cpufreq/scaling_governor")
            val min = SystemReader.read("cat $cpuBase/cpu$i/cpufreq/cpuinfo_min_freq").toLongOrNull() ?: 0L
            if (min > 0L) SystemReader.read("echo $min > $cpuBase/cpu$i/cpufreq/scaling_min_freq")
        }

        val gpuPath = "/sys/class/kgsl/kgsl-3d0"
        if (SystemReader.test(gpuPath)) SystemReader.read("echo msm-adreno-tz > $gpuPath/devfreq/governor")

        SystemReader.read("settings delete global low_power")
        SystemReader.read("dumpsys deviceidle enable")
        SystemReader.read("settings put global private_dns_mode off")
        SystemReader.read("settings delete global private_dns_specifier")
        return "Настройки возвращены"
    }

    fun toggleTurbo(enable: Boolean): String {
        if (enable) {
            SystemReader.read("sysctl -w net.ipv4.tcp_congestion_control=bbr")
            SystemReader.read("sysctl -w net.core.default_qdisc=fq_codel")
            SystemReader.read("settings put global private_dns_mode hostname")
            SystemReader.read("settings put global private_dns_specifier 1dot1dot1dot1.cloudflare-dns.com")
            return "Сетевой Turbo: ВКЛ"
        } else {
            SystemReader.read("sysctl -w net.ipv4.tcp_congestion_control=cubic")
            SystemReader.read("settings put global private_dns_mode off")
            SystemReader.read("settings delete global private_dns_specifier")
            return "Сетевой Turbo: ВЫКЛ"
        }
    }

    fun unlockGameFps(pkg: String, hz: Int): String {
        if (pkg.isBlank()) return "Пакет не указан"
        val safeHz = when { hz >= 144 -> 144; hz >= 120 -> 120; hz >= 90 -> 90; else -> 60 }
        SystemReader.read("cmd game mode performance $pkg")
        SystemReader.read("cmd game set --fps $safeHz $pkg")
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
        SystemReader.read("settings put secure touch_slop $slop")
        SystemReader.read("settings put secure long_press_timeout $longPress")
        SystemReader.read("settings put secure multi_press_timeout $multiPress")
        SystemReader.read("settings put secure pointer_speed $pointer")
        SystemReader.read("settings put system pointer_speed $pointer")
        SystemReader.read("settings put system touch.pressure.scale $pressure")
        return "Сенса: ${arrayOf("Средний", "Сильный", "Эксп.", "Макс")[level]}"
    }

    fun setThermalLimit(temp: Int): String {
        if (temp <= 0) { SystemReader.read("dumpsys thermalservice override-status 0"); return "Троттлинг: выкл" }
        val milli = temp * 1000
        val zones = SystemReader.read("ls /sys/class/thermal/thermal_zone*").split("\n")
        var count = 0
        for (z in zones) {
            if (z.isBlank()) continue
            if (SystemReader.read("test -w $z/trip_point_0_temp && echo ok").contains("ok")) {
                SystemReader.read("echo $milli > $z/trip_point_0_temp"); count++
            }
        }
        return "Троттлинг: $temp°C"
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
        SystemReader.read("appops set $pkg RUN_IN_BACKGROUND allow")
        SystemReader.read("appops set $pkg KEEP_ALIVE allow")
        SystemReader.read("cmd deviceidle whitelist +$pkg")
        return "Удержание: $pkg"
    }

    fun openDeveloperOptions() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        } catch (e: Exception) {}
    }
}
