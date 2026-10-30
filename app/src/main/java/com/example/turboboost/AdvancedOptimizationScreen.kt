package com.example.turboboost

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class AdvancedOptimizationScreen(
    private val ctx: Context,
    private val prefs: AppPrefs,
    private val info: DeviceInfo
) {
    var onBack: (() -> Unit)? = null
    var onStatus: ((String) -> Unit)? = null

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun build(): FrameLayout {
        val root = FrameLayout(ctx)
        root.setBackgroundColor(Color.parseColor("#05070A"))

        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val scroll = ScrollView(ctx).apply {
            isFillViewport = true
            addView(content, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            setPadding(0, dp(72), 0, 0)
        }
        root.addView(scroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        content.addView(sectionHeader("ПАМЯТЬ И ФОНОВЫЕ ПРОЦЕССЫ"))

        addToggle(content, "Ограничить фантомные процессы",
            "device_config max_phantom_processes = Int.MAX_VALUE. Android 11+",
            "adv_phantom_limit",
            onOn = { info.applyDeviceConfig("activity_manager", "max_phantom_processes", "2147483647") },
            onOff = { info.revertDeviceConfig("activity_manager", "max_phantom_processes") })

        addToggle(content, "Отключить монитор фантомных процессов",
            "settings_enable_monitor_phantom_procs = false",
            "adv_phantom_monitor",
            onOn = { info.applyGlobalSetting("global", "settings_enable_monitor_phantom_procs", "false") },
            onOff = { info.revertSetting("global", "settings_enable_monitor_phantom_procs") })

        addToggle(content, "Увеличить лимит кэшированных процессов",
            "activity_manager_constants max_cached_processes=96",
            "adv_cached_procs",
            onOn = { info.applyGlobalSetting("global", "activity_manager_constants", "max_cached_processes=96") },
            onOff = { info.revertSetting("global", "activity_manager_constants") })

        addPerGameToggle(content, "Doze whitelist для игры",
            "Убирает игру из энергосбережения",
            "adv_doze_wl",
            onOn = { pkg -> info.setDozeWhitelist(pkg, true) },
            onOff = { pkg -> info.setDozeWhitelist(pkg, false) })

        addPerGameToggle(content, "RUN_IN_BACKGROUND allow",
            "Разрешает игре работать в фоне",
            "adv_run_bg",
            onOn = { pkg -> info.setAppOp(pkg, "RUN_IN_BACKGROUND", "allow") },
            onOff = { pkg -> info.setAppOp(pkg, "RUN_IN_BACKGROUND", "default") })

        addPerGameToggle(content, "RUN_ANY_IN_BACKGROUND allow",
            "Шире: любой фон разрешён",
            "adv_run_any_bg",
            onOn = { pkg -> info.setAppOp(pkg, "RUN_ANY_IN_BACKGROUND", "allow") },
            onOff = { pkg -> info.setAppOp(pkg, "RUN_ANY_IN_BACKGROUND", "default") })

        addPerGameToggle(content, "Standby bucket = active",
            "Игра не уходит в редкий бакет",
            "adv_bucket",
            onOn = { pkg -> info.setStandbyBucket(pkg, "active") },
            onOff = { pkg -> info.setStandbyBucket(pkg, "active") })

        addAction(content, "Очистить кэш",
            "pm trim-caches 1000M — освобождает до 1 ГБ кэша",
            "Очистить") { info.trimCaches() }

        addPerGameAction(content, "AOT-компиляция игры (speed)",
            "Полная компиляция, ~30-60 сек на игру",
            "Компилировать speed") { pkg -> info.compilePackage(pkg) }

        addPerGameAction(content, "AOT-компиляция игры (speed-profile)",
            "Профильная, компактнее и быстрее",
            "Компилировать speed-profile") { pkg -> info.compilePackageSpeedProfile(pkg) }

        content.addView(sectionHeader("ЧАСТОТА, FPS, РЕНДЕР"))

        addToggle(content, "Фиксировать peak_refresh_rate",
            "peak_refresh_rate = ${prefs.getTargetHz()} Hz",
            "adv_peak_hz",
            onOn = { info.applyGlobalSetting("system", "peak_refresh_rate", prefs.getTargetHz().toString()) },
            onOff = { info.revertSetting("system", "peak_refresh_rate") })

        addToggle(content, "Фиксировать min_refresh_rate",
            "min_refresh_rate = ${prefs.getTargetHz()} Hz",
            "adv_min_hz",
            onOn = { info.applyGlobalSetting("system", "min_refresh_rate", prefs.getTargetHz().toString()) },
            onOff = { info.revertSetting("system", "min_refresh_rate") })

        addPerGameToggle(content, "Game Mode: performance",
            "Переключает игру в performance-режим",
            "adv_game_mode",
            onOn = { pkg -> info.setGameMode(pkg, "performance") },
            onOff = { pkg -> info.setGameMode(pkg, "standard") })

        addPerGameToggle(content, "Game FPS cap = макс. экрана",
            "supportedHz().last() = ${info.supportedHz().last()} Hz",
            "adv_game_fps",
            onOn = { pkg -> info.setGameFps(pkg, info.supportedHz().last()) },
            onOff = { pkg -> info.resetGameFpsOnly(pkg) })

        addPerGameToggle(content, "Game downscale 80%",
            "Рендер-размер 80% от экрана",
            "adv_game_downscale",
            onOn = { pkg -> info.setGameDownscale(pkg, 0.8f) },
            onOff = { pkg -> info.setGameDownscale(pkg, 0f) })

        content.addView(sectionHeader("СЕТЬ И ФОНОВЫЕ СКАНЕРЫ"))

        addToggle(content, "Отключить Wi-Fi scan always",
            "wifi_scan_always_enabled = 0",
            "adv_wifi_scan",
            onOn = { info.applyGlobalSetting("global", "wifi_scan_always_enabled", "0") },
            onOff = { info.revertSetting("global", "wifi_scan_always_enabled") })

        addToggle(content, "Отключить BLE scan always",
            "ble_scan_always_enabled = 0",
            "adv_ble_scan",
            onOn = { info.applyGlobalSetting("global", "ble_scan_always_enabled", "0") },
            onOff = { info.revertSetting("global", "ble_scan_always_enabled") })

        addToggle(content, "Отключить уведомления о доступных сетях",
            "wifi_networks_available_notification_on = 0",
            "adv_wifi_notif",
            onOn = { info.applyGlobalSetting("global", "wifi_networks_available_notification_on", "0") },
            onOff = { info.revertSetting("global", "wifi_networks_available_notification_on") })

        addToggle(content, "Wi-Fi не отключать в спящем режиме",
            "wifi_sleep_policy = 2 (не экономия, стабильность)",
            "adv_wifi_sleep",
            onOn = { info.applyGlobalSetting("global", "wifi_sleep_policy", "2") },
            onOff = { info.revertSetting("global", "wifi_sleep_policy") })

        content.addView(sectionHeader("ЭНЕРГОСБЕРЕЖЕНИЕ"))

        addToggle(content, "Отключить low_power",
            "low_power = 0",
            "adv_low_power",
            onOn = { info.applyGlobalSetting("global", "low_power", "0") },
            onOff = { info.revertSetting("global", "low_power") })

        addToggle(content, "Отключить auto power save",
            "automatic_power_save_mode = 0",
            "adv_auto_power",
            onOn = { info.applyGlobalSetting("global", "automatic_power_save_mode", "0") },
            onOff = { info.revertSetting("global", "automatic_power_save_mode") })

        addToggle(content, "Отключить adaptive battery",
            "adaptive_battery_management_enabled = 0",
            "adv_adaptive_bat",
            onOn = { info.applyGlobalSetting("global", "adaptive_battery_management_enabled", "0") },
            onOff = { info.revertSetting("global", "adaptive_battery_management_enabled") })

        addToggle(content, "Отключить App Standby",
            "app_standby_enabled = 0 — приложения не уходят в редкий бакет",
            "adv_app_standby",
            onOn = { info.applyGlobalSetting("global", "app_standby_enabled", "0") },
            onOff = { info.revertSetting("global", "app_standby_enabled") })

        addToggle(content, "Отключить cached_apps_freezer",
            "cached_apps_freezer = 0 — быстрее переключение, выше расход RAM",
            "adv_freezer",
            onOn = { info.applyGlobalSetting("global", "cached_apps_freezer", "0") },
            onOff = { info.revertSetting("global", "cached_apps_freezer") })

        content.addView(sectionHeader("УВЕДОМЛЕНИЯ"))

        addToggle(content, "Отключить heads-up уведомления",
            "heads_up_notifications_enabled = 0. SMS/звонки продолжат работать",
            "adv_heads_up",
            onOn = { info.applyGlobalSetting("global", "heads_up_notifications_enabled", "0") },
            onOff = { info.revertSetting("global", "heads_up_notifications_enabled") })

        content.addView(View(ctx), LinearLayout.LayoutParams(MATCH_PARENT, dp(24)))

        root.addView(buildTopBar(), FrameLayout.LayoutParams(MATCH_PARENT, dp(72)))
        return root
    }

    private fun buildTopBar(): FrameLayout {
        val bar = FrameLayout(ctx).apply { setBackgroundColor(Color.parseColor("#CC05070A")) }
        val h = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        bar.addView(h, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        val back = ImageView(ctx).apply {
            setImageResource(android.R.drawable.ic_menu_revert)
            setColorFilter(Color.WHITE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { onBack?.invoke() }
        }
        h.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) })
        val title = TextView(ctx).apply {
            text = "Расширенная оптимизация"
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER_VERTICAL
            typeface = Typeface.DEFAULT_BOLD
        }
        h.addView(title, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        return bar
    }

    private fun sectionHeader(text: String): TextView = TextView(ctx).apply {
        this.text = text
        setTextColor(Color.parseColor("#99FFFFFF"))
        textSize = 11f
        letterSpacing = 0.18f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(4), dp(18), 0, dp(8))
    }

    private fun addToggle(
        parent: LinearLayout,
        title: String,
        subtitle: String,
        prefKey: String,
        onOn: () -> String,
        onOff: () -> String
    ) {
        val state = prefs.isFeat(prefKey)
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = glassItem()
            isClickable = true
        }
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(ctx).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        })
        col.addView(TextView(ctx).apply {
            text = subtitle
            setTextColor(Color.parseColor("#88FFFFFF"))
            textSize = 11f
            setPadding(0, dp(2), 0, 0)
        })
        row.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val stateTv = TextView(ctx).apply {
            text = if (state) "ВКЛ" else "ВЫКЛ"
            setTextColor(if (state) Color.parseColor("#34D399") else Color.parseColor("#8899AA"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }
        row.addView(stateTv)
        row.setOnClickListener {
            val next = stateTv.text != "ВКЛ"
            val result = try {
                if (next) onOn() else onOff()
            } catch (e: Throwable) {
                "Ошибка: ${e.message ?: "?"}"
            }
            prefs.setFeat(prefKey, next)
            stateTv.text = if (next) "ВКЛ" else "ВЫКЛ"
            stateTv.setTextColor(if (next) Color.parseColor("#34D399") else Color.parseColor("#8899AA"))
            onStatus?.invoke(result)
        }
        parent.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(6) })
    }

    private fun addPerGameToggle(
        parent: LinearLayout,
        title: String,
        subtitle: String,
        prefKey: String,
        onOn: (String) -> String,
        onOff: (String) -> String
    ) {
        val state = prefs.isFeat(prefKey)
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = glassItem()
            isClickable = true
        }
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(ctx).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        })
        col.addView(TextView(ctx).apply {
            text = subtitle
            setTextColor(Color.parseColor("#88FFFFFF"))
            textSize = 11f
            setPadding(0, dp(2), 0, 0)
        })
        row.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val stateTv = TextView(ctx).apply {
            text = if (state) "ВКЛ" else "ВЫКЛ"
            setTextColor(if (state) Color.parseColor("#34D399") else Color.parseColor("#8899AA"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }
        row.addView(stateTv)
        row.setOnClickListener {
            val pkg = prefs.getLastPackage()
            if (pkg.isBlank()) {
                Toast.makeText(ctx, "Сначала выберите игру в главном экране", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val next = stateTv.text != "ВКЛ"
            val result = try {
                if (next) onOn(pkg) else onOff(pkg)
            } catch (e: Throwable) {
                "Ошибка: ${e.message ?: "?"}"
            }
            prefs.setFeat(prefKey, next)
            stateTv.text = if (next) "ВКЛ" else "ВЫКЛ"
            stateTv.setTextColor(if (next) Color.parseColor("#34D399") else Color.parseColor("#8899AA"))
            onStatus?.invoke(result)
        }
        parent.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(6) })
    }

    private fun addAction(
        parent: LinearLayout,
        title: String,
        subtitle: String,
        buttonText: String,
        action: () -> String
    ) {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = glassItem()
        }
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(ctx).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        })
        col.addView(TextView(ctx).apply {
            text = subtitle
            setTextColor(Color.parseColor("#88FFFFFF"))
            textSize = 11f
            setPadding(0, dp(2), 0, 0)
        })
        row.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val btn = TextView(ctx).apply {
            text = buttonText
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor("#5B8DEF"))
            }
            setOnClickListener {
                isEnabled = false
                text = "…"
                Thread {
                    val r = try { action() } catch (e: Throwable) { "Ошибка: ${e.message ?: "?"}" }
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        isEnabled = true
                        text = buttonText
                        onStatus?.invoke(r)
                    }
                }.start()
            }
        }
        row.addView(btn)
        parent.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(6) })
    }

    private fun addPerGameAction(
        parent: LinearLayout,
        title: String,
        subtitle: String,
        buttonText: String,
        action: (String) -> String
    ) {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = glassItem()
        }
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(ctx).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        })
        col.addView(TextView(ctx).apply {
            text = subtitle
            setTextColor(Color.parseColor("#88FFFFFF"))
            textSize = 11f
            setPadding(0, dp(2), 0, 0)
        })
        row.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val btn = TextView(ctx).apply {
            text = buttonText
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor("#5B8DEF"))
            }
            setOnClickListener {
                val pkg = prefs.getLastPackage()
                if (pkg.isBlank()) {
                    Toast.makeText(ctx, "Сначала выберите игру в главном экране", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                isEnabled = false
                text = "…"
                Thread {
                    val r = try { action(pkg) } catch (e: Throwable) { "Ошибка: ${e.message ?: "?"}" }
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        isEnabled = true
                        text = buttonText
                        onStatus?.invoke(r)
                    }
                }.start()
            }
        }
        row.addView(btn)
        parent.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(6) })
    }

    private fun glassItem() = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(14).toFloat()
        setColor(Color.parseColor("#14FFFFFF"))
        setStroke(dp(1), Color.parseColor("#22FFFFFF"))
    }
}
