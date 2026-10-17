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

    fun currentHz(): Float {
        val out = SystemReader.read("settings get system peak_refresh_rate")
        val valStr = out.trim().toFloatOrNull()
        if (valStr != null && valStr > 0f) return valStr
        val max = SystemReader.read("dumpsys display | grep -o 'fps=[0-9.]*' | sort -rn | head -1")
        val m = Regex("fps=([0-9.]+)").find(max)
        return m?.groupValues?.get(1)?.toFloatOrNull() ?: 60f
    }

    fun checkSystemState(): Boolean {
        if (!ShizukuHelper.hasPermission()) return false
        val gov = SystemReader.read("cat $cpuBase/cpu0/cpufreq/scaling_governor")
        return gov == "performance"
    }

    fun activateMaxPerformance(hz: Int): String {
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

        SystemReader.read("sysctl -w net.ipv4.tcp_congestion_control=bbr")
        SystemReader.read("sysctl -w net.core.default_qdisc=fq_codel")

        val gpuPath = "/sys/class/kgsl/kgsl-3d0"
        if (SystemReader.test(gpuPath)) {
            SystemReader.read("echo performance > $gpuPath/devfreq/governor")
            SystemReader.read("echo 0 > $gpuPath/min_pwrlevel")
            SystemReader.read("echo 0 > $gpuPath/max_pwrlevel")
            SystemReader.read("echo 1 > $gpuPath/force_clk_on")
            SystemReader.read("echo 1 > $gpuPath/force_bus_on")
            SystemReader.read("echo 1 > $gpuPath/force_rail_on")
        }

        SystemReader.read("settings put global private_dns_mode hostname")
        SystemReader.read("settings put global private_dns_specifier 1dot1dot1dot1.cloudflare-dns.com")

        SystemReader.read("settings put global low_power 0")
        SystemReader.read("dumpsys deviceidle disable")
        SystemReader.read("settings put global adaptive_battery_management_enabled 0")
        SystemReader.read("settings put global app_standby_enabled 0")
        SystemReader.read("settings put global cached_apps_freezer disabled")

        SystemReader.read("am kill-all")
        SystemReader.read("echo 1 > /proc/sys/vm/drop_caches")

        return "Сеть: BBR · GPU: фикс · RAM: очищено · Энергосбережение: выкл"
    }

    fun deactivateMaxPerformance(): String {
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

        SystemReader.read("sysctl -w net.ipv4.tcp_congestion_control=cubic")

        val gpuPath = "/sys/class/kgsl/kgsl-3d0"
        if (SystemReader.test(gpuPath)) {
            SystemReader.read("echo msm-adreno-tz > $gpuPath/devfreq/governor")
            SystemReader.read("echo 0 > $gpuPath/force_clk_on")
            SystemReader.read("echo 0 > $gpuPath/force_bus_on")
            SystemReader.read("echo 0 > $gpuPath/force_rail_on")
        }

        SystemReader.read("settings put global private_dns_mode off")
        SystemReader.read("settings delete global private_dns_specifier")

        SystemReader.read("settings delete global low_power")
        SystemReader.read("dumpsys deviceidle enable")
        SystemReader.read("settings delete global adaptive_battery_management_enabled")
        SystemReader.read("settings delete global app_standby_enabled")
        SystemReader.read("settings put global cached_apps_freezer enabled")

        return "Настройки возвращены в авто"
    }

    fun compilePackage(pkg: String): String {
        if (pkg.isBlank()) return "Введите пакет"
        val code = SystemReader.read("cmd package compile -m speed -f $pkg")
        return if (code.isBlank()) "Компиляция запущена: $pkg" else "Результат: $code"
    }

    fun tryKeepAlive(pkg: String): String {
        if (pkg.isBlank()) return "Введите пакет"
        SystemReader.read("appops set $pkg RUN_IN_BACKGROUND allow")
        SystemReader.read("appops set $pkg KEEP_ALIVE allow")
        SystemReader.read("cmd deviceidle whitelist +$pkg")
        return "Попытка удержания: $pkg"
    }

    fun openDeveloperOptions() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        } catch (e: Exception) {}
    }
}
