package com.example.turboboost

object OptimizationModules {

    data class Module(
        val id: String,
        val title: String,
        val hint: String
    )

    val modules: List<Module> = listOf(
        Module("gpu",     "GPU Accelerator",   "Графика и рендер"),
        Module("launch",  "App Launch Boost",  "Быстрый запуск приложений"),
        Module("network", "Network Optimizer", "Сеть и пинг"),
        Module("ui",      "UI Responsiveness", "Отклик интерфейса"),
        Module("touch",   "Touch Enhancer",    "Сенсор и тач"),
        Module("fps",     "Frame Rate Boost",  "Стабильность FPS"),
        Module("ram",     "Memory Manager",    "Управление RAM")
    )

    fun apply(info: DeviceInfo, id: String, on: Boolean): String = when (id) {
        "gpu"     -> applyGpu(info, on)
        "launch"  -> applyLaunch(info, on)
        "network" -> applyNetwork(info, on)
        "ui"      -> applyUi(info, on)
        "touch"   -> applyTouch(info, on)
        "fps"     -> applyFps(info, on)
        "ram"     -> applyRam(info, on)
        else      -> "Неизвестный модуль"
    }

    private fun applyGpu(info: DeviceInfo, on: Boolean): String {
        if (!SystemReader.isReady()) return "Нужен Root/Shizuku"
        return if (on) {
            info.applyGlobalSetting("global", "force_hw_accel", "1")
            info.applyGlobalSetting("global", "enable_gpu_debug_layers", "0")
            info.applyGlobalSetting("global", "disable_window_blurs", "1")
            setProp("debug.hwui.renderer", "skiagl")
            setProp("debug.hwui.texture_cache_size", "72")
            setProp("debug.hwui.layer_cache_size", "48")
            setProp("debug.hwui.render_ahead", "20")
            setProp("debug.renderengine.multithread", "1")
            setProp("debug.hwc.force_gpu_vsync", "0")
            setProp("debug.sf.disable_backpressure", "1")
            "GPU Accelerator: включён"
        } else {
            info.revertSetting("global", "force_hw_accel")
            info.revertSetting("global", "enable_gpu_debug_layers")
            info.revertSetting("global", "disable_window_blurs")
            setProp("debug.hwui.renderer", "")
            setProp("debug.hwui.texture_cache_size", "")
            setProp("debug.hwui.layer_cache_size", "")
            setProp("debug.hwui.render_ahead", "")
            setProp("debug.renderengine.multithread", "")
            setProp("debug.hwc.force_gpu_vsync", "")
            setProp("debug.sf.disable_backpressure", "")
            "GPU Accelerator: выключен"
        }
    }

    private fun applyLaunch(info: DeviceInfo, on: Boolean): String {
        if (!SystemReader.isReady()) return "Нужен Root/Shizuku"
        return if (on) {
            info.applyGlobalSetting("global", "activity_manager_constants", "max_cached_processes=96")
            info.applyGlobalSetting("global", "settings_enable_monitor_phantom_procs", "false")
            info.applyDeviceConfig("activity_manager", "max_phantom_processes", "2147483647")
            info.applyGlobalSetting("global", "app_standby_enabled", "0")
            "App Launch Boost: включён"
        } else {
            info.revertSetting("global", "activity_manager_constants")
            info.revertSetting("global", "settings_enable_monitor_phantom_procs")
            info.revertDeviceConfig("activity_manager", "max_phantom_processes")
            info.revertSetting("global", "app_standby_enabled")
            "App Launch Boost: выключен"
        }
    }

    private fun applyNetwork(info: DeviceInfo, on: Boolean): String {
        if (!SystemReader.isReady()) return "Нужен Root/Shizuku"
        return if (on) {
            info.applyGlobalSetting("global", "wifi_scan_always_enabled", "0")
            info.applyGlobalSetting("global", "ble_scan_always_enabled", "0")
            info.applyGlobalSetting("global", "wifi_networks_available_notification_on", "0")
            info.applyGlobalSetting("global", "wifi_sleep_policy", "2")
            try { SystemReader.read("ndc resolver flushdefaultif") } catch (_: Throwable) {}
            "Network Optimizer: включён"
        } else {
            info.revertSetting("global", "wifi_scan_always_enabled")
            info.revertSetting("global", "ble_scan_always_enabled")
            info.revertSetting("global", "wifi_networks_available_notification_on")
            info.revertSetting("global", "wifi_sleep_policy")
            "Network Optimizer: выключен"
        }
    }

    private fun applyUi(info: DeviceInfo, on: Boolean): String {
        if (!SystemReader.isReady()) return "Нужен Root/Shizuku"
        return if (on) {
            info.applyGlobalSetting("global", "window_animation_scale", "0.5")
            info.applyGlobalSetting("global", "transition_animation_scale", "0.5")
            info.applyGlobalSetting("global", "animator_duration_scale", "0.5")
            info.applyGlobalSetting("global", "disable_window_blurs", "1")
            "UI Responsiveness: включён"
        } else {
            info.revertSetting("global", "window_animation_scale")
            info.revertSetting("global", "transition_animation_scale")
            info.revertSetting("global", "animator_duration_scale")
            info.revertSetting("global", "disable_window_blurs")
            "UI Responsiveness: выключен"
        }
    }

    private fun applyTouch(info: DeviceInfo, on: Boolean): String {
        if (!SystemReader.isReady()) return "Нужен Root/Shizuku"
        return if (on) {
            info.applyGlobalSetting("system", "pointer_speed", "3")
            info.applyGlobalSetting("global", "text_classifier_constants", "enabled=false")
            info.applyGlobalSetting("secure", "long_press_timeout", "250")
            if (info.isSenseTuningSupported()) {
                info.applyGlobalSetting("system", "touch_sampling_rate", "180")
            }
            "Touch Enhancer: включён"
        } else {
            info.revertSetting("system", "pointer_speed")
            info.revertSetting("global", "text_classifier_constants")
            info.revertSetting("secure", "long_press_timeout")
            info.revertSetting("system", "touch_sampling_rate")
            "Touch Enhancer: выключен"
        }
    }

    private fun applyFps(info: DeviceInfo, on: Boolean): String {
        if (!SystemReader.isReady()) return "Нужен Root/Shizuku"
        return if (on) {
            val hz = info.supportedHz().last()
            info.applyGlobalSetting("system", "peak_refresh_rate", hz.toString())
            info.applyGlobalSetting("system", "min_refresh_rate", hz.toString())
            info.applyGlobalSetting("global", "refresh_rate_mode", "0")
            "Frame Rate Boost: $hz Гц"
        } else {
            info.revertSetting("system", "peak_refresh_rate")
            info.revertSetting("system", "min_refresh_rate")
            info.revertSetting("global", "refresh_rate_mode")
            "Frame Rate Boost: выключен"
        }
    }

    private fun applyRam(info: DeviceInfo, on: Boolean): String {
        if (!SystemReader.isReady()) return "Нужен Root/Shizuku"
        return if (on) {
            info.applyDeviceConfig("activity_manager", "max_cached_processes", "96")
            info.applyGlobalSetting("global", "cached_apps_freezer", "0")
            info.applyGlobalSetting("global", "app_standby_enabled", "0")
            info.applyGlobalSetting("global", "low_power", "0")
            info.applyGlobalSetting("global", "automatic_power_save_mode", "0")
            try { SystemReader.read("cmd package trim-caches 1000M") } catch (_: Throwable) {}
            "Memory Manager: включён"
        } else {
            info.revertDeviceConfig("activity_manager", "max_cached_processes")
            info.revertSetting("global", "cached_apps_freezer")
            info.revertSetting("global", "app_standby_enabled")
            info.revertSetting("global", "low_power")
            info.revertSetting("global", "automatic_power_save_mode")
            "Memory Manager: выключен"
        }
    }

    private fun setProp(key: String, value: String) {
        try {
            if (value.isEmpty()) SystemReader.read("setprop $key ''")
            else SystemReader.read("setprop $key $value")
        } catch (_: Throwable) {}
    }
}
