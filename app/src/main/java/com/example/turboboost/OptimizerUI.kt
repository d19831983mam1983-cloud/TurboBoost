package com.example.turboboost

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.TransitionDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.AnticipateOvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class OptimizerUI(val ctx: Context) {

    lateinit var root: FrameLayout
    lateinit var topBar: FrameLayout
    lateinit var scroll1: ScrollView
    lateinit var page1: LinearLayout
    lateinit var bgView: LiquidBackgroundView
    lateinit var btnSettings: ImageView
    lateinit var btnOpt: TextView
    lateinit var btnDiagnostics: TextView
    lateinit var btnJoyose: ToggleRow
    lateinit var btnThreads: ToggleRow
    lateinit var btnMglru: ToggleRow
    lateinit var btnGameSpace: TextView
    lateinit var btnModePerf: TextView
    lateinit var btnModeBal: TextView
    lateinit var btnGpuBoost: ToggleRow
    lateinit var btnBgLimit: ToggleRow
    lateinit var btnGames: TextView
    lateinit var btnProfiles: TextView
    lateinit var btnLauncher: TextView
    lateinit var pkgInput: EditText
    lateinit var cardApplied: TextView
    lateinit var statusResult: TextView
    lateinit var btnLaunchStretch: TextView
    lateinit var etCustomW: EditText
    lateinit var etCustomH: EditText

    var senseRow: LinearLayout? = null
    var hzRow: LinearLayout? = null
    var hzSection: LinearLayout? = null
    var stretchRow: LinearLayout? = null

    val engineViews = LinkedHashMap<String, Triple<ToggleSwitch, TextView, LinearLayout>>()

    var onToggle: (() -> Unit)? = null
    var onDiagnostics: (() -> Unit)? = null
    var onJoyoseToggle: (() -> Unit)? = null
    var onThreadsToggle: (() -> Unit)? = null
    var onMglruToggle: (() -> Unit)? = null
    var onGameSpace: (() -> Unit)? = null
    var onSenseSelected: ((Int) -> Unit)? = null
    var onModeSelected: ((Int) -> Unit)? = null
    var onGpuBoostToggle: (() -> Unit)? = null
    var onBgLimitToggle: (() -> Unit)? = null
    var onShowGames: (() -> Unit)? = null
    var onShowProfiles: (() -> Unit)? = null
    var onHzSelected: ((Int) -> Unit)? = null
    var onPackageChanged: ((String) -> Unit)? = null
    var onSettingsClick: (() -> Unit)? = null
    var onShowLauncher: (() -> Unit)? = null
    var onShowProfile: (() -> Unit)? = null
    var onSenseFinder: (() -> Unit)? = null
    var onSenseCustom: (() -> Unit)? = null
    var onResetCustomResolution: (() -> Unit)? = null
    var onApplyCustomResolution: ((Int, Int) -> Unit)? = null
    var onEngineToggle: ((String) -> Unit)? = null
    var onStretchPreset: ((Int) -> Unit)? = null
    var onLaunchStretch: (() -> Unit)? = null
    var onPingTest: (() -> Unit)? = null

    var currentSense = -1
    var currentMode = 0
    var currentHz = 120
    var maxHz = 144
    var currentStretch = 0

    var isActive = false
    var themeIndex = 0
    var currentGpu = false
    var currentBg = false
    var currentThreads = false
    var currentMglru = false
    var currentJoyose = false
    var lastStatus = "Готов к работе"
    var heroBox: LinearLayout? = null
    var isCustomSense = false
    var prefsRef: AppPrefs? = null

    val senseLevels = listOf(1, 2, 3, -1)
    val senseNames = listOf("Сильный", "Эксп.", "Макс", "Кастом")
    val stretchNames = listOf("Стандарт", "4:3", "5:4", "27:9", "750p")

    val engineList = listOf(
        "fps" to "FPS", "gpu" to "GPU", "internet" to "Internet",
        "launch" to "Launch", "ram" to "RAM", "touch" to "Touch", "ui" to "UI"
    )

    fun theme(): ThemePalette.Theme = ThemePalette.get(themeIndex)

    fun isGrid(): Boolean = theme().isGrid

    fun prefs(): AppPrefs {
        val p = prefsRef ?: AppPrefs(ctx).also { prefsRef = it }
        return p
    }

    fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun setThemeIndexBeforeBuild(i: Int) { themeIndex = i.coerceIn(0, ThemePalette.themes.size - 1) }

    fun build(): FrameLayout {
        root = FrameLayout(ctx).apply { setBackgroundColor(if (isGrid()) Color.parseColor("#1A1A1A") else Color.parseColor("#05070A")) }
        bgView = LiquidBackgroundView(ctx)
        bgView.setTheme(themeIndex, isActive)
        root.addView(bgView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        val content = FrameLayout(ctx)
        root.addView(content, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        page1 = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(24), dp(16), dp(24)) }
        scroll1 = ScrollView(ctx).apply {
            isFillViewport = true
            setBackgroundColor(Color.TRANSPARENT)
            addView(page1, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            setPadding(0, dp(80), 0, 0)
        }
        content.addView(scroll1, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        topBar = buildTopBar()
        content.addView(topBar, FrameLayout.LayoutParams(MATCH_PARENT, dp(72)))
        buildMain()
        animateIn()
        return root
    }

    fun setThemeIndex(i: Int) {
        themeIndex = i.coerceIn(0, ThemePalette.themes.size - 1)
        if (!::root.isInitialized) return
        heroBox?.background = heroBgDrawable()
        if (::bgView.isInitialized) bgView.setTheme(themeIndex, isActive)
        if (::btnModePerf.isInitialized) setMode(currentMode)
        if (::btnGpuBoost.isInitialized) setGpuBoostState(currentGpu)
        if (::btnBgLimit.isInitialized) setBgLimitState(currentBg)
        if (::btnThreads.isInitialized) setThreadsState(currentThreads)
        if (::btnMglru.isInitialized) setMglruState(currentMglru)
        if (::btnJoyose.isInitialized) setJoyoseState(currentJoyose)
        if (hzRow != null) updHz()
        if (senseRow != null) updSense()
        if (stretchRow != null) updStretch()
        if (::btnOpt.isInitialized) fade(btnOpt, accentDrawable())
        if (::btnGames.isInitialized) refreshActionBackgrounds()
    }

    fun setMode(m: Int) {
        currentMode = m
        if (::btnModePerf.isInitialized) fade(btnModePerf, if (m == 0) accentDrawable() else unselDrawable())
        if (::btnModeBal.isInitialized) fade(btnModeBal, if (m == 1) accentDrawable() else unselDrawable())
    }

    fun setHz(hz: Int) { currentHz = hz; if (hzRow != null) updHz() }
    fun setPackageText(t: String) { if (t.isNotEmpty() && ::pkgInput.isInitialized) pkgInput.setText(t) }
    fun setSenseLevel(l: Int) { currentSense = l; isCustomSense = (l == -1); if (senseRow != null) updSense() }

    fun setActive(a: Boolean) {
        isActive = a
        heroBox?.background = heroBgDrawable()
        if (::btnOpt.isInitialized) { btnOpt.text = if (a) "ВЕРНУТЬ АВТО" else "ОПТИМИЗИРОВАТЬ"; fade(btnOpt, accentDrawable()) }
        if (::bgView.isInitialized) bgView.setActive(a)
        setMode(currentMode)
        if (::btnGpuBoost.isInitialized) setGpuBoostState(currentGpu)
        if (::btnBgLimit.isInitialized) setBgLimitState(currentBg)
        if (::btnThreads.isInitialized) setThreadsState(currentThreads)
        if (::btnMglru.isInitialized) setMglruState(currentMglru)
        if (::btnJoyose.isInitialized) setJoyoseState(currentJoyose)
        if (hzRow != null) updHz()
        if (senseRow != null) updSense()
        if (stretchRow != null) updStretch()
    }

    fun setGpuBoostState(a: Boolean) { currentGpu = a; if (::btnGpuBoost.isInitialized) { applyToggleGridStyle(btnGpuBoost, a); btnGpuBoost.setOn(a) } }
    fun setBgLimitState(a: Boolean) { currentBg = a; if (::btnBgLimit.isInitialized) { applyToggleGridStyle(btnBgLimit, a); btnBgLimit.setOn(a) } }
    fun setJoyoseState(a: Boolean) { currentJoyose = a; if (::btnJoyose.isInitialized) { applyToggleGridStyle(btnJoyose, a); btnJoyose.setOn(a) } }
    fun setJoyoseVisible(v: Boolean) { if (::btnJoyose.isInitialized) btnJoyose.visibility = if (v) View.VISIBLE else View.GONE }
    fun setGpuBoostVisible(v: Boolean) { if (::btnGpuBoost.isInitialized) btnGpuBoost.visibility = if (v) View.VISIBLE else View.GONE }
    fun setThreadsVisible(v: Boolean) { if (::btnThreads.isInitialized) btnThreads.visibility = if (v) View.VISIBLE else View.GONE }
    fun setMglruVisible(v: Boolean) { if (::btnMglru.isInitialized) btnMglru.visibility = if (v) View.VISIBLE else View.GONE }
    fun setThreadsState(a: Boolean) { currentThreads = a; if (::btnThreads.isInitialized) { applyToggleGridStyle(btnThreads, a); btnThreads.setOn(a) } }
    fun setMglruState(a: Boolean) { currentMglru = a; if (::btnMglru.isInitialized) { applyToggleGridStyle(btnMglru, a); btnMglru.setOn(a) } }

    fun setAppliedState(active: Boolean, gov: Boolean) {
        if (!::cardApplied.isInitialized) return
        if (!active) { cardApplied.text = "Применено: —"; return }
        cardApplied.text = "Применено: RAM очищена"
    }

    fun setBatteryLowBlock(b: Boolean) { if (::btnOpt.isInitialized) btnOpt.alpha = if (b) 0.4f else 1f }

    fun setOverheat(warn: Boolean, critical: Boolean) {
        if (!::statusResult.isInitialized) return
        if (critical) { statusResult.text = "КРИТИЧЕСКИЙ ПЕРЕГРЕВ — турбо выключено" }
        else if (warn) { statusResult.text = "⚠ Перегрев · $lastStatus" }
        else { statusResult.text = lastStatus }
    }

    fun setStatus(text: String) { lastStatus = text; if (::statusResult.isInitialized) statusResult.text = text }

    fun setEngineState(key: String, on: Boolean, score: String) {
        val entry = engineViews[key] ?: return
        val sw = entry.first
        val scoreTv = entry.second
        val cell = entry.third
        sw.setOn(on, true)
        scoreTv.text = if (score.isBlank()) { if (on) "…" else "—" } else score
        scoreTv.setTextColor(if (on) theme().primary else Color.parseColor("#88FFFFFF"))
        if (isGrid()) {
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                colors = if (on) intArrayOf(0xFF4A4A4A.toInt(), 0xFF3A3A3A.toInt()) else intArrayOf(0xFF3A3A3A.toInt(), 0xFF2A2A2A.toInt())
                gradientType = GradientDrawable.LINEAR_GRADIENT
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                setStroke(dp(2), if (on) theme().primary else 0x66FF8A00.toInt())
            }
            cell.background = bg
        } else {
            cell.background = glassBgDrawable(14)
        }
    }

    fun setEngineScore(key: String, score: String) { engineViews[key]?.second?.text = if (score.isBlank()) "—" else score }
    fun setEngineScoreLoading(key: String) { engineViews[key]?.second?.text = "…" }

    fun setBackgroundBitmap(bmp: Bitmap?) { if (::bgView.isInitialized) bgView.setBackgroundImage(bmp) }

    fun setStretchPreset(preset: Int) { currentStretch = preset; if (stretchRow != null) updStretch() }

    fun setCustomResolution(w: Int, h: Int) {
        if (::etCustomW.isInitialized && w > 0) etCustomW.setText(w.toString())
        if (::etCustomH.isInitialized && h > 0) etCustomH.setText(h.toString())
    }

    fun applyToggleGridStyle(row: ToggleRow, on: Boolean) {
        if (isGrid()) row.applyGridStyle(on, theme().primary) else row.clearGridStyle()
        row.onColor = theme().primary
    }
}fun OptimizerUI.buildTopBar(): FrameLayout {
    val bar = FrameLayout(ctx).apply { setBackgroundColor(if (isGrid()) Color.parseColor("#CC1A1A1A") else Color.parseColor("#CC05070A")) }
    val h = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12)) }
    bar.addView(h, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    val ic = ImageView(ctx).apply { setImageResource(R.drawable.ic_bolt); alpha = 0.9f }
    h.addView(ic, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(12) })
    val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    h.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
    col.addView(tv("TurboBoost", 22f, Color.WHITE).apply { letterSpacing = 0.05f; setShadowLayer(8f, 0f, 0f, theme().secondary) })
    col.addView(tv("TURBO · 2.2", 10f, Color.parseColor("#AACCE0FF")).apply { letterSpacing = 0.3f })
    btnSettings = ImageView(ctx).apply {
        setImageResource(android.R.drawable.ic_menu_preferences)
        setColorFilter(Color.WHITE)
        setPadding(dp(8), dp(8), dp(8), dp(8))
        setOnClickListener { onSettingsClick?.invoke() }
    }
    h.addView(btnSettings, LinearLayout.LayoutParams(dp(44), dp(44)))
    return bar
}

fun OptimizerUI.lpf(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(6) }
fun OptimizerUI.lpl(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)

fun OptimizerUI.section(title: String): LinearLayout {
    val box = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(14))
        val tt = theme()
        background = if (isGrid()) {
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20).toFloat()
                colors = intArrayOf(0xFF2A2A2A.toInt(), 0xFF1F1F1F.toInt())
                gradientType = GradientDrawable.LINEAR_GRADIENT
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                setStroke(dp(1), (tt.primary and 0xFFFFFF) or 0x66000000.toInt())
            }
        } else glassBgDrawable(20)
    }
    box.addView(tv(title, 11f, Color.parseColor("#99FFFFFF")).apply {
        letterSpacing = 0.18f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setPadding(dp(2), 0, 0, dp(2))
    })
    page1.addView(box, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) })
    return box
}

fun OptimizerUI.toggleRow(parent: LinearLayout, title: String, onToggle: (() -> Unit)?): ToggleRow {
    val row = ToggleRow(ctx, title).apply {
        this.onToggle = { onToggle?.invoke() }
        onColor = theme().primary
        if (isGrid()) applyGridStyle(false, theme().primary) else clearGridStyle()
    }
    parent.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) })
    return row
}

fun OptimizerUI.buildMain() {
    val hero = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(18))
        background = heroBgDrawable()
    }
    heroBox = hero
    val heroTop = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    heroTop.addView(tv("СТАТУС", 11f, Color.parseColor("#B3FFFFFF")).apply {
        letterSpacing = 0.2f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
    heroTop.addView(tv("v${appVer()}", 11f, Color.parseColor("#80FFFFFF")))
    hero.addView(heroTop, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    statusResult = tv("Готов к работе", 15f, Color.WHITE).apply {
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setPadding(0, dp(8), 0, dp(2))
    }
    hero.addView(statusResult, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    cardApplied = tv("Применено: —", 12f, Color.parseColor("#B3FFFFFF"))
    hero.addView(cardApplied, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(14) })
    btnOpt = bigBtn("ОПТИМИЗИРОВАТЬ", 16f, accentDrawable()) { onToggle?.invoke() }
    hero.addView(btnOpt, LinearLayout.LayoutParams(MATCH_PARENT, dp(58)))
    page1.addView(hero, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

    val sEngines = section("МОДУЛИ ZNAK")
    buildEngineGrid(sEngines)

    val sGame = section("ИГРА")
    pkgInput = EditText(ctx).apply {
        hint = "com.axlebolt.standoff2"
        setHintTextColor(Color.parseColor("#88FFFFFF"))
        setTextColor(Color.WHITE)
        textSize = 13f
        setSingleLine()
        inputType = android.text.InputType.TYPE_CLASS_TEXT
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        setPadding(dp(16), dp(10), dp(16), dp(10))
        setBackgroundColor(Color.TRANSPARENT)
        addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { onPackageChanged?.invoke(s?.toString()?.trim() ?: "") }
        })
    }
    val pkgWrap = FrameLayout(ctx).apply {
        background = glassBgDrawable(14)
        addView(pkgInput, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }
    sGame.addView(pkgWrap, LinearLayout.LayoutParams(MATCH_PARENT, dp(44)).apply { topMargin = dp(4) })
    val r1 = row(sGame, 8)
    btnGames = glass("Игры") { onShowGames?.invoke() }
    r1.addView(btnGames, lpf())
    btnProfiles = glass("Профили") { onShowProfiles?.invoke() }
    r1.addView(btnProfiles, lpf())
    btnLauncher = glass("Лаунчер") { onShowLauncher?.invoke() }
    r1.addView(btnLauncher, lpl())

    val sMode = section("РЕЖИМ")
    val mr = row(sMode, 4)
    btnModePerf = glass("Производительность") { onModeSelected?.invoke(0); setMode(0) }
    mr.addView(btnModePerf, lpf())
    btnModeBal = glass("Сбалансированный") { onModeSelected?.invoke(1); setMode(1) }
    mr.addView(btnModeBal, lpl())

    val hs = LinearLayout(ctx)
    hs.orientation = LinearLayout.VERTICAL
    hzSection = hs
    hs.addView(lbl("Частота экрана, Гц"))
    val hr = row(hs, 2)
    hzRow = hr
    listOf(60, 90, 120, 144).forEach { hz ->
        hr.addView(glass(hz.toString()) { currentHz = hz; onHzSelected?.invoke(hz); updHz() }, lpf())
    }
    sMode.addView(hs, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    applyHzLimit()

    btnGpuBoost = toggleRow(sMode, "GPU Boost") { onGpuBoostToggle?.invoke() }
    btnBgLimit = toggleRow(sMode, "Лимит фона") { onBgLimitToggle?.invoke() }    val sSense = section("СЕНСА")
    val sr = row(sSense, 4)
    senseRow = sr
    senseNames.forEachIndexed { i, n ->
        val lvl = senseLevels[i]
        sr.addView(glass(n) {
            if (lvl == -1) { currentSense = -1; isCustomSense = true; onSenseCustom?.invoke() }
            else { currentSense = lvl; isCustomSense = false; onSenseSelected?.invoke(lvl) }
            updSense()
        }, lpf())
    }
    val senseFinderBtn = glass("Подбор сенсы Standoff 2") { onSenseFinder?.invoke() }
    sSense.addView(senseFinderBtn, LinearLayout.LayoutParams(MATCH_PARENT, dp(42)).apply { topMargin = dp(8) })

    val sStretch = section("РАСТЯГ (STRETCH)")
    val str = row(sStretch, 4)
    stretchRow = str
    stretchNames.forEachIndexed { i, name ->
        val btn = glass(name) {
            currentStretch = i
            onStretchPreset?.invoke(i)
            updStretch()
        }
        if (i < stretchNames.size - 1) str.addView(btn, lpf())
        else str.addView(btn, lpl())
    }

    btnLaunchStretch = bigBtn("ПРИМЕНИТЬ И ЗАПУСТИТЬ", 15f, accentDrawable()) { onLaunchStretch?.invoke() }
    sStretch.addView(btnLaunchStretch, LinearLayout.LayoutParams(MATCH_PARENT, dp(52)).apply { topMargin = dp(12) })

    sStretch.addView(glass("Сбросить разрешение") { onResetCustomResolution?.invoke() },
        LinearLayout.LayoutParams(MATCH_PARENT, dp(42)).apply { topMargin = dp(8) })

    val customRow = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    etCustomW = EditText(ctx).apply {
        hint = "Ширина"
        setHintTextColor(Color.parseColor("#88FFFFFF"))
        setTextColor(Color.WHITE)
        textSize = 13f
        inputType = android.text.InputType.TYPE_CLASS_NUMBER
        setSingleLine()
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = glassBgDrawable(12)
    }
    etCustomH = EditText(ctx).apply {
        hint = "Высота"
        setHintTextColor(Color.parseColor("#88FFFFFF"))
        setTextColor(Color.WHITE)
        textSize = 13f
        inputType = android.text.InputType.TYPE_CLASS_NUMBER
        setSingleLine()
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = glassBgDrawable(12)
    }
    customRow.addView(etCustomW, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(8) })
    customRow.addView(etCustomH, LinearLayout.LayoutParams(0, dp(44), 1f))
    sStretch.addView(customRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })

    sStretch.addView(glass("Применить кастомное разрешение") {
        val w = etCustomW.text.toString().toIntOrNull() ?: 0
        val h = etCustomH.text.toString().toIntOrNull() ?: 0
        if (w > 0 && h > 0) onApplyCustomResolution?.invoke(w, h)
    }, LinearLayout.LayoutParams(MATCH_PARENT, dp(42)).apply { topMargin = dp(8) })

    sStretch.addView(tv("Игра выбирается в Настройках. Разрешение применится, игра запустится, и через 7 сек (PUBG) / 10 сек (Oxide) / 25 сек (Standoff 2) вернётся исходное.", 11f, Color.parseColor("#99FFFFFF")).apply {
        setPadding(dp(4), dp(10), dp(4), dp(0))
    })

    val sSys = section("СИСТЕМА")
    btnThreads = toggleRow(sSys, "Потоки CPU") { onThreadsToggle?.invoke() }
    btnMglru = toggleRow(sSys, "MGLRU") { onMglruToggle?.invoke() }
    btnJoyose = toggleRow(sSys, "Xiaomi Boost") { onJoyoseToggle?.invoke() }
    btnJoyose.visibility = View.GONE
    val r4 = row(sSys, 8)
    val pingBtn = glass("Пинг-тест") { onPingTest?.invoke() }
    r4.addView(pingBtn, lpf())
    btnGameSpace = glass("Game Turbo") { onGameSpace?.invoke() }
    r4.addView(btnGameSpace, lpf())
    btnDiagnostics = glass("Диагностика") { onDiagnostics?.invoke() }
    r4.addView(btnDiagnostics, lpl())
    val btnProfile = glass("Мой аккаунт") { onShowProfile?.invoke() }
    sSys.addView(btnProfile, LinearLayout.LayoutParams(MATCH_PARENT, dp(42)).apply { topMargin = dp(8) })

    page1.addView(View(ctx), LinearLayout.LayoutParams(MATCH_PARENT, dp(24)))
}

fun OptimizerUI.buildEngineGrid(parent: LinearLayout) {
    engineViews.clear()
    var i = 0
    while (i < engineList.size) {
        val rowLayout = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val left = engineList[i]
        rowLayout.addView(buildEngineCell(left.first, left.second),
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        if (i + 1 < engineList.size) {
            val right = engineList[i + 1]
            rowLayout.addView(buildEngineCell(right.first, right.second),
                LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        } else {
            rowLayout.addView(View(ctx), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        parent.addView(rowLayout, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
        i += 2
    }
}

fun OptimizerUI.buildEngineCell(key: String, title: String): LinearLayout {
    val cell = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = glassBgDrawable(14)
    }
    val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    val titleTv = tv(title, 13f, Color.WHITE).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD }
    col.addView(titleTv)
    val scoreTv = tv(prefs().getEngineScore(key).ifBlank { "—" }, 10f, Color.parseColor("#88FFFFFF"))
    scoreTv.setPadding(0, dp(2), 0, 0)
    col.addView(scoreTv)
    cell.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
    val sw = ToggleSwitch(ctx).apply { onColor = theme().primary; doubleBorder = false }
    cell.addView(sw, LinearLayout.LayoutParams(dp(46), dp(28)).apply { marginStart = dp(8) })
    sw.onToggle = { on ->
        onEngineToggle?.invoke(key)
        if (on && scoreTv.text == "—") scoreTv.text = "…"
        if (!on) scoreTv.text = prefs().getEngineScore(key).ifBlank { "—" }
    }
    cell.setOnClickListener {
        cell.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        val next = !sw.on
        sw.setOn(next, true)
        onEngineToggle?.invoke(key)
        if (next && scoreTv.text == "—") scoreTv.text = "…"
    }
    engineViews[key] = Triple(sw, scoreTv, cell)
    return cell
}

fun OptimizerUI.updStretch() {
    val row = stretchRow ?: return
    for (i in 0 until row.childCount) {
        (row.getChildAt(i) as TextView).background = if (i == currentStretch) accentDrawable() else unselDrawable()
    }
}

fun OptimizerUI.updHz() {
    val row = hzRow ?: return
    val m = listOf(60, 90, 120, 144)
    for (i in 0 until row.childCount) {
        (row.getChildAt(i) as TextView).background = if (m[i] == currentHz) accentDrawable() else unselDrawable()
    }
}

fun OptimizerUI.updSense() {
    val row = senseRow ?: return
    val levels = listOf(1, 2, 3, -1)
    for (i in 0 until row.childCount) {
        val lvl = levels.getOrNull(i) ?: 0
        val bg = if (lvl == -1) {
            if (isCustomSense) accentDrawable() else unselDrawable()
        } else {
            if (lvl == currentSense && !isCustomSense) accentDrawable() else unselDrawable()
        }
        (row.getChildAt(i) as TextView).background = bg
    }
}

fun OptimizerUI.applyHzLimit() {
    val row = hzRow ?: return
    val m = listOf(60, 90, 120, 144)
    for (i in 0 until row.childCount) {
        row.getChildAt(i).visibility = if (m[i] <= maxHz || m[i] == 60) View.VISIBLE else View.GONE
    }
}

fun OptimizerUI.animateIn() {
    for (i in 0 until page1.childCount) {
        val v = page1.getChildAt(i)
        v.alpha = 0f
        v.translationY = dp(40).toFloat()
        v.scaleX = 0.9f
        v.scaleY = 0.9f
        v.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setStartDelay((i * 20).toLong()).setDuration(500).setInterpolator(AnticipateOvershootInterpolator(1.2f)).start()
    }
}

fun OptimizerUI.showGlassList(title: String, items: List<String>, onClick: (Int) -> Unit) {
    val dialog = android.app.Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
    val r = FrameLayout(ctx).apply { setBackgroundColor(if (isGrid()) Color.parseColor("#FF1A1A1A") else Color.parseColor("#FF000000")) }
    val scroll = ScrollView(ctx)
    val container = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(40), dp(24), dp(40)) }
    scroll.addView(container)
    r.addView(scroll, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { gravity = Gravity.CENTER })
    container.addView(TextView(ctx).apply { text = title; setTextColor(Color.WHITE); textSize = 20f; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(20)) })
    val dlgBg = if (isGrid()) gridButton(false) else GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(24).toFloat()
        colors = intArrayOf(0x33FFFFFF, 0x1AFFFFFF, 0x22FFFFFF)
        gradientType = GradientDrawable.LINEAR_GRADIENT
        orientation = GradientDrawable.Orientation.TL_BR
        setStroke(dp(1), 0xAAFFFFFF.toInt())
    }
    items.forEachIndexed { i, item ->
        val tvItem = TextView(ctx).apply {
            text = item
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = dlgBg
            setOnClickListener {
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                onClick(i)
                dialog.dismiss()
            }
        }
        container.addView(tvItem, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })
    }
    dialog.setContentView(r)
    dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
    dialog.window?.setLayout(MATCH_PARENT, WRAP_CONTENT)
    dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
    dialog.show()
}

fun OptimizerUI.fade(v: View, bg: Drawable) {
    val o = v.background
    if (o == null) {
        v.background = bg
        return
    }
    val t = TransitionDrawable(arrayOf(o, bg))
    t.isCrossFadeEnabled = true
    v.background = t
    t.startTransition(500)
}

fun OptimizerUI.gridButton(active: Boolean): Drawable {
    val t = theme()
    val fill = if (active) intArrayOf(0xFF5A5A5A.toInt(), 0xFF454545.toInt())
               else intArrayOf(0xFF3A3A3A.toInt(), 0xFF2A2A2A.toInt())
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(14).toFloat()
        colors = fill
        gradientType = GradientDrawable.LINEAR_GRADIENT
        orientation = GradientDrawable.Orientation.TOP_BOTTOM
        setStroke(dp(2), if (active) t.primary else (t.primary and 0xFFFFFF) or 0x99000000.toInt())
    }
}

fun OptimizerUI.accentDrawable(): Drawable {
    if (isGrid()) return gridButton(true)
    val t = theme()
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(14).toFloat()
        colors = intArrayOf(t.primary, t.secondary)
        gradientType = GradientDrawable.LINEAR_GRADIENT
        orientation = GradientDrawable.Orientation.LEFT_RIGHT
        setStroke(dp(1), 0x33FFFFFF)
    }
}

fun OptimizerUI.unselDrawable(): Drawable {
    if (isGrid()) return gridButton(false)
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(14).toFloat()
        colors = intArrayOf(0x1AFFFFFF, 0x10FFFFFF)
        gradientType = GradientDrawable.LINEAR_GRADIENT
        orientation = GradientDrawable.Orientation.TL_BR
        setStroke(dp(1), 0x22FFFFFF)
    }
}

fun OptimizerUI.heroBgDrawable(): Drawable {
    val t = theme()
    if (t.isGrid) {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(26).toFloat()
            colors = intArrayOf(0xFF454545.toInt(), 0xFF353535.toInt(), 0xFF2A2A2A.toInt())
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            setStroke(dp(2), if (isActive) t.primary else (t.primary and 0xFFFFFF) or 0x99000000.toInt())
        }
    }
    val a1 = if (isActive) 0x70 else 0x38
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(26).toFloat()
        colors = intArrayOf((a1 shl 24) or (t.primary and 0xFFFFFF), (0x24 shl 24) or (t.secondary and 0xFFFFFF), 0x12FFFFFF)
        orientation = GradientDrawable.Orientation.TL_BR
        setStroke(dp(1), if (isActive) t.secondary else 0x33FFFFFF)
    }
}

fun OptimizerUI.refreshActionBackgrounds() {
    val list = listOf(btnGames, btnProfiles, btnLauncher, btnDiagnostics, btnGameSpace)
    for (v in list) {
        try { v.background = unselDrawable() } catch (_: Throwable) {}
    }
}

fun OptimizerUI.appVer(): String = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?" } catch (_: Throwable) { "?" }

fun OptimizerUI.touch(v: View, click: () -> Unit) {
    v.setOnTouchListener { view, ev ->
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.95f).scaleY(0.95f).alpha(0.8f).setDuration(50).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(300).setInterpolator(AnticipateOvershootInterpolator(2f)).start()
        }
        false
    }
    v.setOnClickListener { v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY); click() }
}

fun OptimizerUI.glass(text: String, click: () -> Unit): TextView = TextView(ctx).apply {
    this.text = text
    setTextColor(Color.WHITE)
    textSize = 12f
    typeface = android.graphics.Typeface.DEFAULT_BOLD
    gravity = Gravity.CENTER
    maxLines = 1
    setPadding(dp(4), 0, dp(4), 0)
    background = unselDrawable()
    touch(this, click)
}

fun OptimizerUI.bigBtn(text: String, size: Float, bg: Drawable, click: () -> Unit): TextView = TextView(ctx).apply {
    this.text = text
    setTextColor(Color.WHITE)
    textSize = size
    gravity = Gravity.CENTER
    setShadowLayer(6f, 0f, 2f, Color.parseColor("#55000000"))
    background = bg
    touch(this, click)
}

fun OptimizerUI.glassBgDrawable(radius: Int): Drawable {
    if (isGrid()) {
        val tt = theme()
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radius).toFloat()
            colors = intArrayOf(0xFF3A3A3A.toInt(), 0xFF2A2A2A.toInt())
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            setStroke(dp(1), (tt.primary and 0xFFFFFF) or 0x66000000.toInt())
        }
    }
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radius).toFloat()
        colors = intArrayOf(0x1CFFFFFF, 0x0DFFFFFF)
        gradientType = GradientDrawable.LINEAR_GRADIENT
        orientation = GradientDrawable.Orientation.TL_BR
        setStroke(dp(1), 0x1FFFFFFF)
    }
}

fun OptimizerUI.tv(text: String, size: Float, color: Int): TextView = TextView(ctx).apply {
    this.text = text
    setTextColor(color)
    textSize = size
}

fun OptimizerUI.lbl(text: String): TextView = tv(text, 12f, Color.parseColor("#99FFFFFF")).apply {
    setPadding(dp(4), dp(10), dp(0), dp(4))
}

fun OptimizerUI.row(p: LinearLayout, margin: Int): LinearLayout = LinearLayout(ctx).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    p.addView(this, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(margin) })
}
