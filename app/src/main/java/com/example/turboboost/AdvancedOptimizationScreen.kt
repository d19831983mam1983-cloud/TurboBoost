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

    private val theme = ThemePalette.get(prefs.getThemeIndex())
    private val accent: Int = theme.primary
    private val accentSoft: Int = (theme.primary and 0x00FFFFFF) or (0x44 shl 24)
    private val bgDark = Color.parseColor("#05070A")
    private val cardDefault = Color.parseColor("#14FFFFFF")

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    private data class Item(
        val title: String,
        val hint: String,
        val key: String,
        val perGame: Boolean = false,
        val isButton: Boolean = false,
        val isCycle: Boolean = false,
        val cycleLabel: ((String) -> String)? = null,
        val run: (isOn: Boolean, pkg: String) -> String
    )

    fun build(): FrameLayout {
        val root = FrameLayout(ctx)
        root.setBackgroundColor(bgDark)
        val content = LinearLayout(ctx)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(12), dp(12), dp(12), dp(12))
        val scroll = ScrollView(ctx)
        scroll.isFillViewport = true
        scroll.addView(content, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        scroll.setPadding(0, dp(72), 0, 0)
        root.addView(scroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        section(content, "ПАМЯТЬ И ФОНОВЫЕ ПРОЦЕССЫ", listOf(
            Item("Фантомные\nпроцессы", "Убрать лимит", "adv_phantom_limit",
                run = { on, _ ->
                    if (on) info.applyDeviceConfig("activity_manager", "max_phantom_processes", "2147483647")
                    else info.revertDeviceConfig("activity_manager", "max_phantom_processes")
                }),
            Item("Монитор\nфантомов", "Отключить убийцу", "adv_phantom_monitor",
                run = { on, _ ->
                    if (on) info.applyGlobalSetting("global", "settings_enable_monitor_phantom_procs", "false")
                    else info.revertSetting("global", "settings_enable_monitor_phantom_procs")
                }),
            Item("Лимит кэша", "Больше процессов", "adv_cached_procs",
                run = { on, _ ->
                    if (on) info.applyGlobalSetting("global", "activity_manager_constants", "max_cached_processes=96")
                    else info.revertSetting("global", "activity_manager_constants")
                }),
            Item("Doze\nwhitelist", "Убрать из энергосбера", "adv_doze_wl", perGame = true,
                run = { on, p -> info.setDozeWhitelist(p, on) }),
            Item("RUN_IN_BG", "Работа в фоне", "adv_run_bg", perGame = true,
                run = { on, p -> info.setAppOp(p, "RUN_IN_BACKGROUND", if (on) "allow" else "default") }),
            Item("RUN_ANY_IN_BG", "Любой фон", "adv_run_any_bg", perGame = true,
                run = { on, p -> info.setAppOp(p, "RUN_ANY_IN_BACKGROUND", if (on) "allow" else "default") }),
            Item("Standby\nactive", "Не в редком бакете", "adv_bucket", perGame = true,
                run = { _, p -> info.setStandbyBucket(p, "active") }),
            Item("Очистить\nкэш", "До 1 ГБ", "adv_trim", isButton = true,
                run = { _, _ -> info.trimCaches() })
        ))

        section(content, "ЧАСТОТА, FPS, РЕНДЕР", listOf(
            Item("Peak Hz", "Пиковая частота", "adv_peak_hz",
                run = { on, _ ->
                    if (on) info.applyGlobalSetting("system", "peak_refresh_rate", prefs.getTargetHz().toString())
                    else info.revertSetting("system", "peak_refresh_rate")
                }),
            Item("Min Hz", "Минимальная частота", "adv_min_hz",
                run = { on, _ ->
                    if (on) info.applyGlobalSetting("system", "min_refresh_rate", prefs.getTargetHz().toString())
                    else info.revertSetting("system", "min_refresh_rate")
                }),
            Item("Game Mode", "Performance-режим", "adv_game_mode", perGame = true,
                run = { on, p -> info.setGameMode(p, if (on) "performance" else "standard") }),
            Item("FPS cap", "Максимум экрана", "adv_game_fps", perGame = true,
                run = { on, p ->
                    if (on) info.setGameFps(p, info.supportedHz().last())
                    else info.resetGameFpsOnly(p)
                }),
            Item("Downscale\n80%", "Меньше рендер", "adv_game_downscale", perGame = true,
                run = { on, p -> info.setGameDownscale(p, if (on) 0.8f else 0f) }),
            Item("Авто AOT", "Компиляция\nпри турбо", "adv_auto_aot",
                run = { on, p ->
                    if (on && p.isBlank()) "Сначала выберите игру"
                    else if (on) "Включено — сработает при включении турбо"
                    else "Выключено"
                }),
            Item("Драйвер GPU", "Системный → Игровой → Prerelease", "adv_driver",
                perGame = true, isCycle = true,
                cycleLabel = { p -> info.driverModeName(p) },
                run = { _, p -> info.nextDriverMode(p) })
        ))

        section(content, "СЕТЬ И СКАНЕРЫ", listOf(
            Item("Wi-Fi scan", "Фоновое сканирование", "adv_wifi_scan",
                run = { on, _ ->
                    if (on) info.applyGlobalSetting("global", "wifi_scan_always_enabled", "0")
                    else info.revertSetting("global", "wifi_scan_always_enabled")
                }),
            Item("BLE scan", "Bluetooth-сканирование", "adv_ble_scan",
                run = { on, _ ->
                    if (on) info.applyGlobalSetting("global", "ble_scan_always_enabled", "0")
                    else info.revertSetting("global", "ble_scan_always_enabled")
                }),
            Item("Wi-Fi notif", "Уведомления о сетях", "adv_wifi_notif",
                run = { on, _ ->
                    if (on) info.applyGlobalSetting("global", "wifi_networks_available_notification_on", "0")
                    else info.revertSetting("global", "wifi_networks_available_notification_on")
                }),
            Item("Wi-Fi sleep", "Не отключать Wi-Fi", "adv_wifi_sleep",
                run = { on, _ ->
                    if (on) info.applyGlobalSetting("global", "wifi_sleep_policy", "2")
                    else info.revertSetting("global", "wifi_sleep_policy")
                })
        ))

        section(content, "ЭНЕРГОСБЕРЕЖЕНИЕ", listOf(
            Item("low_power", "Не экономить", "adv_low_power",
                run = { on, _ ->
                    if (on) info.applyGlobalSetting("global", "low_power", "0")
                    else info.revertSetting("global", "low_power")
                }),
            Item("Auto save", "Авто-энергосбережение", "adv_auto_power",
                run = { on, _ ->
                    if (on) info.applyGlobalSetting("global", "automatic_power_save_mode", "0")
                    else info.revertSetting("global", "automatic_power_save_mode")
                }),
            Item("Adaptive", "Адаптивная батарея", "adv_adaptive_bat",
                run = { on, _ ->
                    if (on) info.applyGlobalSetting("global", "adaptive_battery_management_enabled", "0")
                    else info.revertSetting("global", "adaptive_battery_management_enabled")
                }),
            Item("Standby", "App Standby", "adv_app_standby",
                run = { on, _ ->
                    if (on) info.applyGlobalSetting("global", "app_standby_enabled", "0")
                    else info.revertSetting("global", "app_standby_enabled")
                }),
            Item("Freezer", "Заморозка кэша", "adv_freezer",
                run = { on, _ ->
                    if (on) info.applyGlobalSetting("global", "cached_apps_freezer", "0")
                    else info.revertSetting("global", "cached_apps_freezer")
                })
        ))

        section(content, "УВЕДОМЛЕНИЯ", listOf(
            Item("Heads-up", "Всплывающие уведомления", "adv_heads_up",
                run = { on, _ ->
                    if (on) info.applyGlobalSetting("global", "heads_up_notifications_enabled", "0")
                    else info.revertSetting("global", "heads_up_notifications_enabled")
                })
        ))

        section(content, "СИСТЕМА", listOf(
            Item("Блокировка\nповорота", "Фиксирует ориентацию", "adv_rot_lock",
                run = { on, _ ->
                    if (on) info.applyGlobalSetting("system", "accelerometer_rotation", "0")
                    else info.revertSetting("system", "accelerometer_rotation")
                })
        ))

        content.addView(View(ctx), LinearLayout.LayoutParams(MATCH_PARENT, dp(24)))
        root.addView(topBar(), FrameLayout.LayoutParams(MATCH_PARENT, dp(72)))
        return root
    }    private fun section(parent: LinearLayout, title: String, items: List<Item>) {
        header(parent, title)
        items.chunked(2).forEach { pair ->
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(0, dp(4), 0, dp(4))
            pair.forEach { item ->
                row.addView(makeCard(item), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                    marginStart = dp(4)
                    marginEnd = dp(4)
                })
            }
            if (pair.size == 1) {
                val filler = LinearLayout(ctx)
                row.addView(filler, LinearLayout.LayoutParams(0, 1, 1f).apply {
                    marginStart = dp(4)
                    marginEnd = dp(4)
                })
            }
            parent.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
    }

    private fun makeCard(item: Item): LinearLayout {
        val state = if (item.isButton) false else prefs.isFeat(item.key)
        val pkg = prefs.getLastPackage()
        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(10), dp(10), dp(10), dp(10))
        card.setBackground(cardBg(if (state) accentSoft else cardDefault))
        card.isClickable = true

        val titleTv = TextView(ctx)
        titleTv.text = item.title
        titleTv.setTextColor(Color.WHITE)
        titleTv.textSize = 12f
        titleTv.typeface = Typeface.DEFAULT_BOLD
        titleTv.maxLines = 2
        card.addView(titleTv)

        val hintTv = TextView(ctx)
        hintTv.text = item.hint
        hintTv.setTextColor(Color.parseColor("#88FFFFFF"))
        hintTv.textSize = 9f
        hintTv.maxLines = 2
        hintTv.setPadding(0, dp(2), 0, dp(6))
        card.addView(hintTv)

        val stateTv = TextView(ctx)
        stateTv.setTextColor(if (state) accent else Color.parseColor("#8899AA"))
        stateTv.textSize = 10f
        stateTv.typeface = Typeface.DEFAULT_BOLD
        stateTv.gravity = Gravity.END
        if (item.isCycle) {
            stateTv.text = if (pkg.isBlank()) "-" else item.cycleLabel?.invoke(pkg) ?: "-"
        } else if (item.isButton) {
            stateTv.text = "→"
        } else {
            stateTv.text = if (state) "ВКЛ" else "ВЫКЛ"
        }
        card.addView(stateTv)

        card.setOnClickListener {
            val p = prefs.getLastPackage()
            if (item.perGame && p.isBlank()) {
                Toast.makeText(ctx, "Сначала выберите игру", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (item.isCycle) {
                val r = try { item.run(true, p) } catch (e: Throwable) { "Ошибка: " + (e.message ?: "?") }
                stateTv.text = item.cycleLabel?.invoke(p) ?: "-"
                stateTv.setTextColor(accent)
                onStatus?.invoke(r)
                return@setOnClickListener
            }
            val next = if (item.isButton) true else stateTv.text != "ВКЛ"
            val r = try { item.run(next, p) } catch (e: Throwable) { "Ошибка: " + (e.message ?: "?") }
            if (!item.isButton) {
                prefs.setFeat(item.key, next)
                stateTv.text = if (next) "ВКЛ" else "ВЫКЛ"
                stateTv.setTextColor(if (next) accent else Color.parseColor("#8899AA"))
                card.setBackground(cardBg(if (next) accentSoft else cardDefault))
            }
            onStatus?.invoke(r)
        }
        return card
    }

    private fun cardBg(color: Int): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = dp(14).toFloat()
        g.setColor(color)
        g.setStroke(dp(1), accentSoft)
        return g
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
        title.setTextColor(accent)
        title.textSize = 20f
        title.gravity = Gravity.CENTER_VERTICAL
        title.typeface = Typeface.DEFAULT_BOLD
        h.addView(title, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        return bar
    }

    private fun header(parent: LinearLayout, text: String) {
        val tv = TextView(ctx)
        tv.text = text
        tv.setTextColor(accent)
        tv.textSize = 11f
        tv.letterSpacing = 0.18f
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.setPadding(dp(4), dp(18), 0, dp(6))
        parent.addView(tv)
    }
}
