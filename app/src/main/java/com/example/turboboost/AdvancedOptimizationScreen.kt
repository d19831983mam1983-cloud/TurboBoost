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
        val content = LinearLayout(ctx)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(16), dp(16), dp(16), dp(16))
        val scroll = ScrollView(ctx)
        scroll.isFillViewport = true
        scroll.addView(content, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        scroll.setPadding(0, dp(72), 0, 0)
        root.addView(scroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        header(content, "ПАМЯТЬ И ФОНОВЫЕ ПРОЦЕССЫ")
        gToggle(content, "Ограничить фантомные процессы", "max_phantom_processes", "adv_phantom_limit",
            { info.applyDeviceConfig("activity_manager", "max_phantom_processes", "2147483647") },
            { info.revertDeviceConfig("activity_manager", "max_phantom_processes") })
        gToggle(content, "Отключить монитор фантомов", "settings_enable_monitor_phantom_procs", "adv_phantom_monitor",
            { info.applyGlobalSetting("global", "settings_enable_monitor_phantom_procs", "false") },
            { info.revertSetting("global", "settings_enable_monitor_phantom_procs") })
        gToggle(content, "Лимит кэшированных процессов", "max_cached_processes=96", "adv_cached_procs",
            { info.applyGlobalSetting("global", "activity_manager_constants", "max_cached_processes=96") },
            { info.revertSetting("global", "activity_manager_constants") })
        pgToggle(content, "Doze whitelist", "adv_doze_wl",
            { p -> info.setDozeWhitelist(p, true) },
            { p -> info.setDozeWhitelist(p, false) })
        pgToggle(content, "RUN_IN_BACKGROUND allow", "adv_run_bg",
            { p -> info.setAppOp(p, "RUN_IN_BACKGROUND", "allow") },
            { p -> info.setAppOp(p, "RUN_IN_BACKGROUND", "default") })
        pgToggle(content, "RUN_ANY_IN_BACKGROUND allow", "adv_run_any_bg",
            { p -> info.setAppOp(p, "RUN_ANY_IN_BACKGROUND", "allow") },
            { p -> info.setAppOp(p, "RUN_ANY_IN_BACKGROUND", "default") })
        pgToggle(content, "Standby bucket active", "adv_bucket",
            { p -> info.setStandbyBucket(p, "active") },
            { p -> info.setStandbyBucket(p, "active") })
        runAction(content, "Очистить кэш", "pm trim-caches 1000M", "Очистить",
            { info.trimCaches() })
        pgAction(content, "AOT speed", "Полная компиляция", "Компилировать",
            { p -> info.compilePackage(p) })
        pgAction(content, "AOT speed-profile", "Профильная компиляция", "Компилировать",
            { p -> info.compilePackageSpeedProfile(p) })

        header(content, "ЧАСТОТА, FPS, РЕНДЕР")
        val targetHz = prefs.getTargetHz()
        val maxHz = info.supportedHz().last()
        gToggle(content, "peak_refresh_rate", targetHz.toString() + " Hz", "adv_peak_hz",
            { info.applyGlobalSetting("system", "peak_refresh_rate", targetHz.toString()) },
            { info.revertSetting("system", "peak_refresh_rate") })
        gToggle(content, "min_refresh_rate", targetHz.toString() + " Hz", "adv_min_hz",
            { info.applyGlobalSetting("system", "min_refresh_rate", targetHz.toString()) },
            { info.revertSetting("system", "min_refresh_rate") })
        pgToggle(content, "Game Mode performance", "adv_game_mode",
            { p -> info.setGameMode(p, "performance") },
            { p -> info.setGameMode(p, "standard") })
        pgToggle(content, "Game FPS cap", "adv_game_fps",
            { p -> info.setGameFps(p, maxHz) },
            { p -> info.resetGameFpsOnly(p) })
        pgToggle(content, "Game downscale 80%", "adv_game_downscale",
            { p -> info.setGameDownscale(p, 0.8f) },
            { p -> info.setGameDownscale(p, 0f) })

        header(content, "СЕТЬ И ФОНОВЫЕ СКАНЕРЫ")
        gToggle(content, "Wi-Fi scan always off", "wifi_scan_always_enabled=0", "adv_wifi_scan",
            { info.applyGlobalSetting("global", "wifi_scan_always_enabled", "0") },
            { info.revertSetting("global", "wifi_scan_always_enabled") })
        gToggle(content, "BLE scan always off", "ble_scan_always_enabled=0", "adv_ble_scan",
            { info.applyGlobalSetting("global", "ble_scan_always_enabled", "0") },
            { info.revertSetting("global", "ble_scan_always_enabled") })
        gToggle(content, "Wi-Fi notif off", "wifi_networks_available_notification_on=0", "adv_wifi_notif",
            { info.applyGlobalSetting("global", "wifi_networks_available_notification_on", "0") },
            { info.revertSetting("global", "wifi_networks_available_notification_on") })
        gToggle(content, "Wi-Fi sleep policy 2", "wifi_sleep_policy=2", "adv_wifi_sleep",
            { info.applyGlobalSetting("global", "wifi_sleep_policy", "2") },
            { info.revertSetting("global", "wifi_sleep_policy") })

        header(content, "ЭНЕРГОСБЕРЕЖЕНИЕ")
        gToggle(content, "low_power off", "low_power=0", "adv_low_power",
            { info.applyGlobalSetting("global", "low_power", "0") },
            { info.revertSetting("global", "low_power") })
        gToggle(content, "auto power save off", "automatic_power_save_mode=0", "adv_auto_power",
            { info.applyGlobalSetting("global", "automatic_power_save_mode", "0") },
            { info.revertSetting("global", "automatic_power_save_mode") })
        gToggle(content, "adaptive battery off", "adaptive_battery_management_enabled=0", "adv_adaptive_bat",
            { info.applyGlobalSetting("global", "adaptive_battery_management_enabled", "0") },
            { info.revertSetting("global", "adaptive_battery_management_enabled") })
        gToggle(content, "App Standby off", "app_standby_enabled=0", "adv_app_standby",
            { info.applyGlobalSetting("global", "app_standby_enabled", "0") },
            { info.revertSetting("global", "app_standby_enabled") })
        gToggle(content, "cached_apps_freezer off", "cached_apps_freezer=0", "adv_freezer",
            { info.applyGlobalSetting("global", "cached_apps_freezer", "0") },
            { info.revertSetting("global", "cached_apps_freezer") })

        header(content, "УВЕДОМЛЕНИЯ")
        gToggle(content, "heads_up off", "heads_up_notifications_enabled=0", "adv_heads_up",
            { info.applyGlobalSetting("global", "heads_up_notifications_enabled", "0") },
            { info.revertSetting("global", "heads_up_notifications_enabled") })

        content.addView(View(ctx), LinearLayout.LayoutParams(MATCH_PARENT, dp(24)))
        root.addView(topBar(), FrameLayout.LayoutParams(MATCH_PARENT, dp(72)))
        return root
    }

    private fun topBar(): FrameLayout {
        val bar = FrameLayout(ctx)
        bar.setBackgroundColor(Color.parseColor("#CC05070A"))
        val h = LinearLayout(ctx)
        h.orientation = LinearLayout.HORIZONTAL
        h.gravity = Gravity.CENTER_VERTICAL
        h.setPadding(dp(16), dp(12), dp(16), dp(12))
        bar.addView(h, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        val back = ImageView(ctx)
        back.setImageResource(android.R.drawable.ic_menu_revert)
        back.setColorFilter(Color.WHITE)
        back.setPadding(dp(8), dp(8), dp(8), dp(8))
        back.setOnClickListener { onBack?.invoke() }
        h.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) })
        val title = TextView(ctx)
        title.text = "Расширенная оптимизация"
        title.setTextColor(Color.WHITE)
        title.textSize = 20f
        title.gravity = Gravity.CENTER_VERTICAL
        title.typeface = Typeface.DEFAULT_BOLD
        h.addView(title, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        return bar
    }

    private fun header(parent: LinearLayout, text: String) {
        val tv = TextView(ctx)
        tv.text = text
        tv.setTextColor(Color.parseColor("#99FFFFFF"))
        tv.textSize = 11f
        tv.letterSpacing = 0.18f
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.setPadding(dp(4), dp(18), 0, dp(8))
        parent.addView(tv)
    }

    private fun gToggle(parent: LinearLayout, title: String, subtitle: String, key: String,
                        onOn: () -> String, onOff: () -> String) {
        val row = toggleRow(title, subtitle, key)
        val stateTv = row.tag as TextView
        row.setOnClickListener {
            val next = stateTv.text != "ВКЛ"
            val r = runSafe { if (next) onOn() else onOff() }
            prefs.setFeat(key, next)
            stateTv.text = if (next) "ВКЛ" else "ВЫКЛ"
            stateTv.setTextColor(if (next) Color.parseColor("#34D399") else Color.parseColor("#8899AA"))
            onStatus?.invoke(r)
        }
        parent.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(6) })
    }

    private fun pgToggle(parent: LinearLayout, title: String, key: String,
                         onOn: (String) -> String, onOff: (String) -> String) {
        val row = toggleRow(title, "Для текущей игры", key)
        val stateTv = row.tag as TextView
        row.setOnClickListener {
            val pkg = prefs.getLastPackage()
            if (pkg.isBlank()) {
                Toast.makeText(ctx, "Сначала выберите игру", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val next = stateTv.text != "ВКЛ"
            val r = runSafe { if (next) onOn(pkg) else onOff(pkg) }
            prefs.setFeat(key, next)
            stateTv.text = if (next) "ВКЛ" else "ВЫКЛ"
            stateTv.setTextColor(if (next) Color.parseColor("#34D399") else Color.parseColor("#8899AA"))
            onStatus?.invoke(r)
        }
        parent.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(6) })
    }

    private fun toggleRow(title: String, subtitle: String, key: String): LinearLayout {
        val state = prefs.isFeat(key)
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(16), dp(12), dp(16), dp(12))
        row.background = glassItem()
        row.isClickable = true

        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        val titleTv = TextView(ctx)
        titleTv.text = title
        titleTv.setTextColor(Color.WHITE)
        titleTv.textSize = 14f
        titleTv.typeface = Typeface.DEFAULT_BOLD
        col.addView(titleTv)
        val subTv = TextView(ctx)
        subTv.text = subtitle
        subTv.setTextColor(Color.parseColor("#88FFFFFF"))
        subTv.textSize = 11f
        col.addView(subTv)
        row.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        val stateTv = TextView(ctx)
        stateTv.text = if (state) "ВКЛ" else "ВЫКЛ"
        stateTv.setTextColor(if (state) Color.parseColor("#34D399") else Color.parseColor("#8899AA"))
        stateTv.textSize = 13f
        stateTv.typeface = Typeface.DEFAULT_BOLD
        row.addView(stateTv)
        row.tag = stateTv
        return row
    }

    private fun runAction(parent: LinearLayout, title: String, subtitle: String, buttonText: String,
                          action: () -> String) {
        val btn = actionBtn(buttonText) { action() }
        parent.addView(actionRow(title, subtitle, btn), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(6) })
    }

    private fun pgAction(parent: LinearLayout, title: String, subtitle: String, buttonText: String,
                         action: (String) -> String) {
        val btn = actionBtn(buttonText) {
            val pkg = prefs.getLastPackage()
            if (pkg.isBlank()) {
                Toast.makeText(ctx, "Сначала выберите игру", Toast.LENGTH_SHORT).show()
                ""
            } else {
                action(pkg)
            }
        }
        parent.addView(actionRow(title, subtitle, btn), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(6) })
    }

    private fun actionRow(title: String, subtitle: String, btn: TextView): LinearLayout {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(16), dp(12), dp(16), dp(12))
        row.background = glassItem()
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        val titleTv = TextView(ctx)
        titleTv.text = title
        titleTv.setTextColor(Color.WHITE)
        titleTv.textSize = 14f
        titleTv.typeface = Typeface.DEFAULT_BOLD
        col.addView(titleTv)
        val subTv = TextView(ctx)
        subTv.text = subtitle
        subTv.setTextColor(Color.parseColor("#88FFFFFF"))
        subTv.textSize = 11f
        col.addView(subTv)
        row.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row.addView(btn)
        return row
    }

    private fun actionBtn(label: String, action: () -> String): TextView {
        val btn = TextView(ctx)
        btn.text = label
        btn.setTextColor(Color.WHITE)
        btn.textSize = 12f
        btn.typeface = Typeface.DEFAULT_BOLD
        btn.gravity = Gravity.CENTER
        btn.setPadding(dp(14), dp(8), dp(14), dp(8))
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.RECTANGLE
        bg.cornerRadius = dp(10).toFloat()
        bg.setColor(Color.parseColor("#5B8DEF"))
        btn.background = bg
        btn.setOnClickListener {
            btn.isEnabled = false
            btn.text = "..."
            Thread {
                val r = runSafe { action() }
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    btn.isEnabled = true
                    btn.text = label
                    onStatus?.invoke(r)
                }
            }.start()
        }
        return btn
    }

    private fun runSafe(block: () -> String): String {
        return try { block() } catch (e: Throwable) { "Ошибка" }
    }

    private fun glassItem(): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = dp(14).toFloat()
        g.setColor(Color.parseColor("#14FFFFFF"))
        g.setStroke(dp(1), Color.parseColor("#22FFFFFF"))
        return g
    }
}
