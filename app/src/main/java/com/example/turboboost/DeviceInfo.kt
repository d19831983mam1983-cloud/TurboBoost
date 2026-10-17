package com.example.turboboost

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build

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

    fun chipName(): String {
        return Build.SOC_MODEL.ifBlank { Build.HARDWARE }
    }

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

        val cores = 8
        for (i in 0 until cores) {
            SystemReader.read("echo performance > $cpuBase/cpu$i/cpufreq/scaling_governor")
            val max = SystemReader.read("cat $cpuBase/cpu$i/cpufreq/cpuinfo_max_freq").toLongOrNull() ?: 0L
            if (max > 0L) {
                SystemReader.read("echo $max > $cpuBase/cpu$i/cpufreq/scaling_min_freq")
                SystemReader.read("echo $max > $cpuBase/cpu$i/cpufreq/scaling_max_freq")
            }
        }

        SystemReader.read("setprop debug.hwui.renderer skiavk")
        
        return "Экран: $hz Hz · Анимации: выкл"
    }

    fun deactivateMaxPerformance(): String {
        SystemReader.read("settings delete system peak_refresh_rate")
        SystemReader.read("settings delete system min_refresh_rate")

        SystemReader.read("settings put global window_animation_scale 1.0")
        SystemReader.read("settings put global transition_animation_scale 1.0")
        SystemReader.read("settings put global animator_duration_scale 1.0")

        val cores = 8
        for (i in 0 until cores) {
            SystemReader.read("echo schedutil > $cpuBase/cpu$i/cpufreq/scaling_governor")
            val min = SystemReader.read("cat $cpuBase/cpu$i/cpufreq/cpuinfo_min_freq").toLongOrNull() ?: 0L
            if (min > 0L) {
                SystemReader.read("echo $min > $cpuBase/cpu$i/cpufreq/scaling_min_freq")
            }
        }

        SystemReader.read("setprop debug.hwui.renderer skiagl")

        return "Настройки возвращены в авто"
    }

    fun activateSense(): String {
        SystemReader.read("settings put secure touch_slop 10")
        SystemReader.read("settings put secure pointer_speed 7")
        SystemReader.read("settings put secure long_press_timeout 250")
        SystemReader.read("settings put secure multi_press_timeout 250")
        SystemReader.read("settings put system pointer_speed 7")
        SystemReader.read("settings put system touch.pressure.scale 0.001")
        SystemReader.read("settings put system touch.size.calibration diameter")
        SystemReader.read("settings put system touch.size.scale 1")
        SystemReader.read("settings put system touch.size.bias 0")
        SystemReader.read("settings put system low_power_trigger_level 0")
        SystemReader.read("settings put system hotword_detection_enabled 0")
        return "Сенса: макс"
    }

    fun deactivateSense(): String {
        SystemReader.read("settings delete secure touch_slop")
        SystemReader.read("settings delete secure pointer_speed")
        SystemReader.read("settings delete secure long_press_timeout")
        SystemReader.read("settings delete secure multi_press_timeout")
        SystemReader.read("settings delete system pointer_speed")
        SystemReader.read("settings delete system touch.pressure.scale")
        SystemReader.read("settings delete system touch.size.calibration")
        SystemReader.read("settings delete system touch.size.scale")
        SystemReader.read("settings delete system touch.size.bias")
        SystemReader.read("settings delete system low_power_trigger_level")
        SystemReader.read("settings delete system hotword_detection_enabled")
        return "Сенса: авто"
    }
}
