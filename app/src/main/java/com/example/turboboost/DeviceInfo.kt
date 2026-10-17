package com.example.turboboost

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build

class DeviceInfo(private val ctx: Context) {

    private val cpuBase = "/sys/devices/system/cpu"

    fun coreCount(): Int {
        for (i in 0 until 16) {
            if (!SystemReader.test("$cpuBase/cpu$i")) return i
        }
        return 8
    }

    fun cpuCurFreq(core: Int): Long {
        return SystemReader.read("cat $cpuBase/cpu$core/cpufreq/scaling_cur_freq").toLongOrNull() ?: 0L
    }

    fun cpuMaxFreq(core: Int): Long {
        return SystemReader.read("cat $cpuBase/cpu$core/cpufreq/cpuinfo_max_freq").toLongOrNull() ?: 0L
    }

    fun cpuGovernor(core: Int): String {
        return SystemReader.read("cat $cpuBase/cpu$core/cpufreq/scaling_governor")
    }

    fun gpuPath(): String {
        val list = listOf(
            "/sys/class/kgsl/kgsl-3d0",
            "/sys/class/devfreq/5000000.qcom,kgsl-3d0",
            "/sys/class/misc/mali0/device",
            "/sys/devices/platform/13000000.mali"
        )
        for (p in list) if (SystemReader.test(p)) return p
        return ""
    }

    fun gpuMaxFreq(path: String): Long {
        if (path.isEmpty()) return 0L
        val files = listOf("$path/max_gpuclk", "$path/max_freq", "$path/available_frequencies")
        for (f in files) {
            val out = SystemReader.read("cat $f")
            if (out.isNotBlank()) {
                if (f.endsWith("available_frequencies")) {
                    return out.split(" ").mapNotNull { it.trim().toLongOrNull() }.maxOrNull() ?: 0L
                }
                return out.toLongOrNull() ?: 0L
            }
        }
        return 0L
    }

    fun refreshRate(): Float {
        val out = SystemReader.read("dumpsys display | grep -o 'fps=[0-9.]*' | head -1")
        val m = Regex("fps=([0-9.]+)").find(out)
        return m?.groupValues?.get(1)?.toFloatOrNull() ?: 60f
    }

    fun maxRefreshRate(): Float {
        val out = SystemReader.read("dumpsys display | grep -o 'fps=[0-9.]*' | sort -rn | head -1")
        val m = Regex("fps=([0-9.]+)").find(out)
        return m?.groupValues?.get(1)?.toFloatOrNull() ?: 60f
    }

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

    fun thermalThreshold(): Float {
        val hw = (Build.HARDWARE + " " + Build.BOARD + " " + Build.SOC_MODEL).lowercase()
        return when {
            hw.contains("sm8650") -> 75f
            hw.contains("sm8550") -> 73f
            hw.contains("sm8450") -> 72f
            hw.contains("mt6") -> 68f
            hw.contains("mt5") -> 65f
            hw.contains("exynos") -> 70f
            hw.contains("kirin") -> 70f
            hw.contains("tensor") -> 68f
            else -> 70f
        }
    }
}
