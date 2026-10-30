package com.example.turboboost

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.TransitionDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.Window
import android.view.WindowManager
import android.view.animation.AnticipateOvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class OptimizerUI(private val ctx: Context) {

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
    lateinit var senseRow: LinearLayout
    lateinit var hzRow: LinearLayout

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
    var onApplyCustomResolution: ((Int, Int) -> Unit)? = null
    var onResetCustomResolution: (() -> Unit)? = null
    var onShowAdvanced: (() -> Unit)? = null

    var currentSense = -1
    var currentMode = 0
    var currentHz = 120
    var maxHz = 144
    var onPingTest: (() -> Unit)? = null

    private var isActive = false
    private var themeIndex = 0
    private var currentGpu = false
    private var currentBg = false
    private var currentThreads = false
    private var currentMglru = false
    private var currentJoyose = false
    private var lastStatus = "Готов к работе"
    private var heroBox: LinearLayout? = null
    private var isCustomSense = false
    private var prefsRef: AppPrefs? = null

    private val senseLevels = listOf(1, 2, 3, -1)
    private val senseNames = listOf("Сильный", "Эксп.", "Макс", "Кастом")

    private fun theme(): ThemePalette.Theme = ThemePalette.get(themeIndex)
    private fun isGrid(): Boolean = theme().isGrid

    fun setThemeIndexBeforeBuild(i: Int) {
        themeIndex = i.coerceIn(0, ThemePalette.themes.size - 1)
    }

    private fun prefs(): AppPrefs {
        val p = prefsRef ?: AppPrefs(ctx).also { prefsRef = it }
        return p
    }

    private fun gridButton(active: Boolean): Drawable {
        val t = theme()
        val fill = if (active)
            intArrayOf(0xFF5A5A5A.toInt(), 0xFF454545.toInt())
        else
            intArrayOf(0xFF3A3A3A.toInt(), 0xFF2A2A2A.toInt())
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14).toFloat()
            colors = fill
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            setStroke(dp(2), if (active) t.primary else 0x99FF8A00.toInt())
        }
    }

    private fun accentDrawable(): Drawable {
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

    private fun unselDrawable(): Drawable {
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

    private fun heroBgDrawable(): Drawable {
        val t = theme()
        if (t.isGrid) {
            return GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(26).toFloat()
                colors = intArrayOf(0xFF454545.toInt(), 0xFF353535.toInt(), 0xFF2A2A2A.toInt())
                gradientType = GradientDrawable.LINEAR_GRADIENT
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                setStroke(dp(2), if (isActive) t.primary else 0x99FF8A00.toInt())
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
        if (::hzRow.isInitialized) updHz()
        if (::senseRow.isInitialized) updSense()
        if (::btnOpt.isInitialized) fade(btnOpt, accentDrawable())
        if (::btnGames.isInitialized) refreshActionBackgrounds()
    }

    private fun refreshActionBackgrounds() {
        val list = listOf(btnGames, btnProfiles, btnLauncher, btnDiagnostics, btnGameSpace)
        for (v in list) {
            try { v.background = unselDrawable() } catch (_: Throwable) {}
        }
    }

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

    private fun buildTopBar(): FrameLayout {
        val bar = FrameLayout(ctx).apply { setBackgroundColor(if (isGrid()) Color.parseColor("#CC1A1A1A") else Color.parseColor("#CC05070A")) }
        val h = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12)) }
        bar.addView(h, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        val ic = ImageView(ctx).apply { setImageResource(R.drawable.ic_bolt); alpha = 0.9f }
        h.addView(ic, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(12) })
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        h.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        col.addView(tv("TurboBoost", 22f, Color.WHITE).apply { letterSpacing = 0.05f; setShadowLayer(8f, 0f, 0f, theme().secondary) })
        col.addView(tv("TURBO · 2.1", 10f, Color.parseColor("#AACCE0FF")).apply { letterSpacing = 0.3f })
        btnSettings = ImageView(ctx).apply {
            setImageResource(android.R.drawable.ic_menu_preferences)
            setColorFilter(Color.WHITE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { onSettingsClick?.invoke() }
        }
        h.addView(btnSettings, LinearLayout.LayoutParams(dp(44), dp(44)))
        return bar
    }

    private fun lpf(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(6) }
    private fun lpl(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)

    private fun section(title: String): LinearLayout {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
            background = if (isGrid()) {
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(20).toFloat()
                    colors = intArrayOf(0xFF2A2A2A.toInt(), 0xFF1F1F1F.toInt())
                    gradientType = GradientDrawable.LINEAR_GRADIENT
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                    setStroke(dp(1), 0x66FF8A00.toInt())
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

    private fun toggleRow(parent: LinearLayout, title: String, onToggle: (() -> Unit)?): ToggleRow {
        val row = ToggleRow(ctx, title).apply {
            this.onToggle = { onToggle?.invoke() }
            onColor = theme().primary
            if (isGrid()) applyGridStyle(false, theme().primary) else clearGridStyle()
        }
        parent.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) })
        return row
    }private fun buildMain() {
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

    val sGame = section("ИГРА")
    pkgInput = EditText(ctx).apply {
        hint = "com.axlebolt.standoff2"
        setHintTextColor(Color.parseColor("#88FFFFFF"))
        setTextColor(Color.WHITE)
        textSize = 13f
        setSingleLine()
        inputType = InputType.TYPE_CLASS_TEXT
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        setPadding(dp(16), dp(10), dp(16), dp(10))
        setBackgroundColor(Color.TRANSPARENT)
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val t = s?.toString()?.trim() ?: ""
                if (GameBlocklist.isBlocked(t)) {
                    s?.clear()
                    android.widget.Toast.makeText(ctx, GameBlocklist.MESSAGE, android.widget.Toast.LENGTH_SHORT).show()
                    return
                }
                onPackageChanged?.invoke(t)
            }
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
    sMode.addView(lbl("Частота экрана, Гц"))
    hzRow = row(sMode, 2)
    listOf(60, 90, 120, 144).forEach { hz ->
        hzRow.addView(glass(hz.toString()) { currentHz = hz; onHzSelected?.invoke(hz); updHz() }, lpf())
    }
    applyHzLimit()
    btnGpuBoost = toggleRow(sMode, "GPU Boost") { onGpuBoostToggle?.invoke() }
    btnBgLimit = toggleRow(sMode, "Лимит фона") { onBgLimitToggle?.invoke() }

    val sSense = section("СЕНСА")
    senseRow = row(sSense, 4)
    senseNames.forEachIndexed { i, n ->
        val lvl = senseLevels[i]
        senseRow.addView(glass(n) {
            if (lvl == -1) {
                currentSense = -1
                isCustomSense = true
                onSenseCustom?.invoke()
            } else {
                currentSense = lvl
                isCustomSense = false
                onSenseSelected?.invoke(lvl)
            }
            updSense()
        }, lpf())
    }
    val senseFinderBtn = glass("Подбор сенсы Standoff 2") { onSenseFinder?.invoke() }
    sSense.addView(senseFinderBtn, LinearLayout.LayoutParams(MATCH_PARENT, dp(42)).apply { topMargin = dp(8) })

    val sGfx = section("РАЗРЕШЕНИЕ ЭКРАНА")
    sGfx.addView(lbl("Своё разрешение (высота × ширина)"))
    val resRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    val hInput = EditText(ctx).apply {
        hint = "Высота"
        setHintTextColor(Color.parseColor("#88FFFFFF"))
        setTextColor(Color.WHITE)
        textSize = 14f
        setSingleLine()
        inputType = InputType.TYPE_CLASS_NUMBER
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = glassBgDrawable(12)
        val v = prefs().getCustomH()
        if (v > 0) setText(v.toString())
    }
    val wInput = EditText(ctx).apply {
        hint = "Ширина"
        setHintTextColor(Color.parseColor("#88FFFFFF"))
        setTextColor(Color.WHITE)
        textSize = 14f
        setSingleLine()
        inputType = InputType.TYPE_CLASS_NUMBER
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = glassBgDrawable(12)
        val v = prefs().getCustomW()
        if (v > 0) setText(v.toString())
    }
    resRow.addView(hInput, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(8) })
    resRow.addView(wInput, LinearLayout.LayoutParams(0, dp(46), 1f))
    sGfx.addView(resRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(4) })

    sGfx.addView(glass("Применить и запустить Standoff 2") {
        val w = wInput.text.toString().trim().toIntOrNull() ?: 0
        val h = hInput.text.toString().trim().toIntOrNull() ?: 0
        if (w < 320 || h < 320) {
            android.widget.Toast.makeText(ctx, "Введи числа (мин. 320)", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            onApplyCustomResolution?.invoke(w, h)
        }
    }, LinearLayout.LayoutParams(MATCH_PARENT, dp(46)).apply { topMargin = dp(10) })

    sGfx.addView(glass("Сбросить разрешение") { onResetCustomResolution?.invoke() },
        LinearLayout.LayoutParams(MATCH_PARENT, dp(42)).apply { topMargin = dp(6) })

    sGfx.addView(tv("Разрешение применится, Standoff 2 запустится, через 25 секунд вернётся исходное.", 11f, Color.parseColor("#99FFFFFF")).apply {
        setPadding(dp(4), dp(8), dp(4), dp(0))
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
    val btnAdvanced = glass("Расширенная оптимизация") { onShowAdvanced?.invoke() }
    sSys.addView(btnAdvanced, LinearLayout.LayoutParams(MATCH_PARENT, dp(42)).apply { topMargin = dp(6) })

    page1.addView(View(ctx), LinearLayout.LayoutParams(MATCH_PARENT, dp(24)))
}

private fun animateIn() {
    for (i in 0 until page1.childCount) {
        val v = page1.getChildAt(i)
        v.alpha = 0f
        v.translationY = dp(40).toFloat()
        v.scaleX = 0.9f
        v.scaleY = 0.9f
        v.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setStartDelay((i * 20).toLong()).setDuration(500).setInterpolator(AnticipateOvershootInterpolator(1.2f)).start()
    }
}

fun setBackgroundBitmap(bmp: Bitmap?) {
    if (::bgView.isInitialized) bgView.setBackgroundImage(bmp)
}

fun showGlassList(title: String, items: List<String>, onClick: (Int) -> Unit) {
    val dialog = Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
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
        val tv = TextView(ctx).apply {
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
        container.addView(tv, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })
    }
    dialog.setContentView(r)
    dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
    dialog.window?.setLayout(MATCH_PARENT, WRAP_CONTENT)
    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    dialog.show()
}

fun setMode(m: Int) {
    currentMode = m
    if (::btnModePerf.isInitialized) fade(btnModePerf, if (m == 0) accentDrawable() else unselDrawable())
    if (::btnModeBal.isInitialized) fade(btnModeBal, if (m == 1) accentDrawable() else unselDrawable())
}

fun setHz(hz: Int) {
    currentHz = hz
    if (::hzRow.isInitialized) updHz()
}

fun setPackageText(t: String) {
    if (t.isNotEmpty() && ::pkgInput.isInitialized) pkgInput.setText(t)
}

fun setSenseLevel(l: Int) {
    currentSense = l
    isCustomSense = (l == -1)
    if (::senseRow.isInitialized) updSense()
}

fun setActive(a: Boolean) {
    isActive = a
    heroBox?.background = heroBgDrawable()
    if (::btnOpt.isInitialized) {
        btnOpt.text = if (a) "ВЕРНУТЬ АВТО" else "ОПТИМИЗИРОВАТЬ"
        fade(btnOpt, accentDrawable())
    }
    if (::bgView.isInitialized) bgView.setActive(a)
    setMode(currentMode)
    if (::btnGpuBoost.isInitialized) setGpuBoostState(currentGpu)
    if (::btnBgLimit.isInitialized) setBgLimitState(currentBg)
    if (::btnThreads.isInitialized) setThreadsState(currentThreads)
    if (::btnMglru.isInitialized) setMglruState(currentMglru)
    if (::btnJoyose.isInitialized) setJoyoseState(currentJoyose)
    if (::hzRow.isInitialized) updHz()
    if (::senseRow.isInitialized) updSense()
}

private fun applyToggleGridStyle(row: ToggleRow, on: Boolean) {
    if (isGrid()) row.applyGridStyle(on, theme().primary) else row.clearGridStyle()
    row.onColor = theme().primary
}

fun setGpuBoostState(a: Boolean) {
    currentGpu = a
    if (::btnGpuBoost.isInitialized) { applyToggleGridStyle(btnGpuBoost, a); btnGpuBoost.setOn(a) }
}

fun setBgLimitState(a: Boolean) {
    currentBg = a
    if (::btnBgLimit.isInitialized) { applyToggleGridStyle(btnBgLimit, a); btnBgLimit.setOn(a) }
}

fun setJoyoseState(a: Boolean) {
    currentJoyose = a
    if (::btnJoyose.isInitialized) { applyToggleGridStyle(btnJoyose, a); btnJoyose.setOn(a) }
}

fun setJoyoseVisible(v: Boolean) {
    if (::btnJoyose.isInitialized) btnJoyose.visibility = if (v) View.VISIBLE else View.GONE
}

fun setGpuBoostVisible(v: Boolean) {
    if (::btnGpuBoost.isInitialized) btnGpuBoost.visibility = if (v) View.VISIBLE else View.GONE
}

fun setThreadsVisible(v: Boolean) {
    if (::btnThreads.isInitialized) btnThreads.visibility = if (v) View.VISIBLE else View.GONE
}

fun setMglruVisible(v: Boolean) {
    if (::btnMglru.isInitialized) btnMglru.visibility = if (v) View.VISIBLE else View.GONE
}

fun setThreadsState(a: Boolean) {
    currentThreads = a
    if (::btnThreads.isInitialized) { applyToggleGridStyle(btnThreads, a); btnThreads.setOn(a) }
}

fun setMglruState(a: Boolean) {
    currentMglru = a
    if (::btnMglru.isInitialized) { applyToggleGridStyle(btnMglru, a); btnMglru.setOn(a) }
}

fun setAppliedState(active: Boolean, gov: Boolean) {
    if (!::cardApplied.isInitialized) return
    if (!active) { cardApplied.text = "Применено: —"; return }
    cardApplied.text = "Применено: RAM очищена"
}

fun setBatteryLowBlock(b: Boolean) {
    if (::btnOpt.isInitialized) btnOpt.alpha = if (b) 0.4f else 1f
}

fun setOverheat(warn: Boolean, critical: Boolean) {
    if (!::statusResult.isInitialized) return
    if (critical) { statusResult.text = "КРИТИЧЕСКИЙ ПЕРЕГРЕВ — турбо выключено" }
    else if (warn) { statusResult.text = "⚠ Перегрев · $lastStatus" }
    else { statusResult.text = lastStatus }
}

fun setStatus(text: String) {
    lastStatus = text
    if (::statusResult.isInitialized) statusResult.text = text
}

private fun fade(v: View, bg: Drawable) {
    val o = v.background
    if (o == null) { v.background = bg; return }
    val t = TransitionDrawable(arrayOf(o, bg))
    t.isCrossFadeEnabled = true
    v.background = t
    t.startTransition(500)
}

private fun applyHzLimit() {
    if (!::hzRow.isInitialized) return
    val m = listOf(60, 90, 120, 144)
    for (i in 0 until hzRow.childCount) {
        hzRow.getChildAt(i).visibility = if (m[i] <= maxHz || m[i] == 60) View.VISIBLE else View.GONE
    }
}

private fun updHz() {
    val m = listOf(60, 90, 120, 144)
    if (!::hzRow.isInitialized) return
    for (i in 0 until hzRow.childCount) {
        val tv = hzRow.getChildAt(i) as TextView
        tv.background = if (m[i] == currentHz) accentDrawable() else unselDrawable()
    }
}

private fun updSense() {
    if (!::senseRow.isInitialized) return
    val levels = listOf(1, 2, 3, -1)
    for (i in 0 until senseRow.childCount) {
        val tv = senseRow.getChildAt(i) as TextView
        val lvl = levels.getOrNull(i) ?: 0
        val selected = if (lvl == -1) isCustomSense else (lvl == currentSense && !isCustomSense)
        tv.background = if (selected) accentDrawable() else unselDrawable()
    }
}

private fun appVer(): String = try {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
} catch (_: Throwable) { "?" }

private fun touch(v: View, click: () -> Unit) {
    v.setOnTouchListener { view, ev ->
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.95f).scaleY(0.95f).alpha(0.8f).setDuration(50).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(300).setInterpolator(AnticipateOvershootInterpolator(2f)).start()
        }
        false
    }
    v.setOnClickListener {
        v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        click()
    }
}

private fun glass(text: String, click: () -> Unit): TextView = TextView(ctx).apply {
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

private fun bigBtn(text: String, size: Float, bg: Drawable, click: () -> Unit): TextView = TextView(ctx).apply {
    this.text = text
    setTextColor(Color.WHITE)
    textSize = size
    gravity = Gravity.CENTER
    setShadowLayer(6f, 0f, 2f, Color.parseColor("#55000000"))
    background = bg
    touch(this, click)
}

private fun glassBgDrawable(radius: Int): Drawable {
    if (isGrid()) {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radius).toFloat()
            colors = intArrayOf(0xFF3A3A3A.toInt(), 0xFF2A2A2A.toInt())
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            setStroke(dp(1), 0x66FF8A00.toInt())
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

private fun tv(text: String, size: Float, color: Int): TextView = TextView(ctx).apply {
    this.text = text
    setTextColor(color)
    textSize = size
}

private fun lbl(text: String): TextView = tv(text, 12f, Color.parseColor("#99FFFFFF")).apply {
    setPadding(dp(4), dp(10), 0, dp(4))
}

private fun row(p: LinearLayout, margin: Int): LinearLayout = LinearLayout(ctx).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    p.addView(this, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(margin) })
}

private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()    class LiquidBackgroundView(context: Context) : View(context), SensorEventListener {
        private data class Flake(
            var x: Float,
            var y: Float,
            val radius: Float,
            val speed: Float,
            val drift: Float,
            var alpha: Int,
            var rotation: Float
        )

        private val flakes = mutableListOf<Flake>()
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val blobPaint1 = Paint(Paint.ANTI_ALIAS_FLAG)
        private val blobPaint2 = Paint(Paint.ANTI_ALIAS_FLAG)
        private val blobPaint3 = Paint(Paint.ANTI_ALIAS_FLAG)
        private val flakePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val matrix1 = Matrix()
        private val matrix2 = Matrix()
        private val matrix3 = Matrix()
        private val rand = Random(System.currentTimeMillis())
        private var time = 0f
        private var active = true
        private var isActiveMode = false
        private var themeIndex = 0
        private var bgBitmap: Bitmap? = null
        private var sensorManager: SensorManager? = null
        private var accelerometer: Sensor? = null
        private var lastShakeTime = 0L
        private var lastX = 0f
        private var lastY = 0f
        private var lastZ = 0f

        var onShake: (() -> Unit)? = null

        init {
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            gridPaint.style = Paint.Style.STROKE
            gridPaint.strokeWidth = resources.displayMetrics.density * 1.2f
        }

        fun setBackgroundImage(bmp: Bitmap?) {
            bgBitmap = bmp
            invalidate()
        }

        fun setActive(a: Boolean) {
            isActiveMode = a
            updateGradients()
            invalidate()
        }

        fun setTheme(index: Int, activeMode: Boolean) {
            themeIndex = index
            isActiveMode = activeMode
            updateGradients()
            invalidate()
        }

        private fun currentPrimary(): Int {
            val t = ThemePalette.get(themeIndex)
            return if (isActiveMode) t.secondary else t.primary
        }

        private fun currentSecondary(): Int {
            val t = ThemePalette.get(themeIndex)
            return if (isActiveMode) t.primary else t.secondary
        }

        private fun updateGradients() {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0 || h <= 0) return
            val t = ThemePalette.get(themeIndex)
            if (t.isGrid) {
                val top = if (isActiveMode) 0xFF3A3A3A.toInt() else 0xFF2A2A2A.toInt()
                val mid = if (isActiveMode) 0xFF2A2A2A.toInt() else 0xFF1F1F1F.toInt()
                val bot = if (isActiveMode) 0xFF252525.toInt() else 0xFF1A1A1A.toInt()
                bgPaint.shader = LinearGradient(0f, 0f, 0f, h,
                    intArrayOf(top, mid, 0xFF1A1A1A.toInt(), bot, top),
                    floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1f), Shader.TileMode.CLAMP)
                blobPaint1.shader = null
                blobPaint2.shader = null
                blobPaint3.shader = null
                return
            }
            val top = if (isActiveMode) darken(t.secondary) else darken(t.primary)
            val mid = if (isActiveMode) t.secondary else t.primary
            val bot = darken(if (isActiveMode) t.primary else t.secondary)
            bgPaint.shader = LinearGradient(0f, 0f, 0f, h,
                intArrayOf(0xFF05070A.toInt(), top, mid, bot, 0xFF05070A.toInt()),
                floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f), Shader.TileMode.CLAMP)
            val b1 = if (isActiveMode) t.blob2 else t.blob1
            val b2 = if (isActiveMode) t.blob3 else t.blob2
            val b3 = if (isActiveMode) t.blob1 else t.blob3
            blobPaint1.shader = RadialGradient(w * 0.3f, h * 0.3f, w * 0.7f, intArrayOf(b1, 0x00000000), null, Shader.TileMode.CLAMP)
            blobPaint2.shader = RadialGradient(w * 0.7f, h * 0.6f, w * 0.8f, intArrayOf(b2, 0x00000000), null, Shader.TileMode.CLAMP)
            blobPaint3.shader = RadialGradient(w * 0.5f, h * 0.9f, w * 0.6f, intArrayOf(b3, 0x00000000), null, Shader.TileMode.CLAMP)
        }

        private fun darken(c: Int): Int {
            val r = (Color.red(c) * 0.4f).toInt().coerceIn(0, 255)
            val g = (Color.green(c) * 0.4f).toInt().coerceIn(0, 255)
            val b = (Color.blue(c) * 0.4f).toInt().coerceIn(0, 255)
            return Color.rgb(r, g, b)
        }

        private fun drawGrid(canvas: Canvas) {
            val t = ThemePalette.get(themeIndex)
            val density = resources.displayMetrics.density
            val step = 56f * density
            gridPaint.color = if (isActiveMode) t.secondary else t.primary
            gridPaint.alpha = if (isActiveMode) 90 else 55
            gridPaint.strokeWidth = 1.2f * density
            var x = 0f
            while (x < width) {
                canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
                x += step
            }
            var y = 0f
            while (y < height) {
                canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
                y += step
            }
            gridPaint.strokeWidth = 2f * density
            gridPaint.alpha = if (isActiveMode) 130 else 80
            val step2 = step * 4f
            var x2 = 0f
            while (x2 < width) {
                canvas.drawLine(x2, 0f, x2, height.toFloat(), gridPaint)
                x2 += step2
            }
            var y2 = 0f
            while (y2 < height) {
                canvas.drawLine(0f, y2, width.toFloat(), y2, gridPaint)
                y2 += step2
            }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            try { if (accelerometer != null) sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI) } catch (_: Throwable) {}
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            try { sensorManager?.unregisterListener(this) } catch (_: Throwable) {}
        }

        override fun onVisibilityChanged(changedView: View, visibility: Int) {
            super.onVisibilityChanged(changedView, visibility)
            active = visibility == VISIBLE
            if (active) invalidate()
        }

        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val delta = abs(x - lastX) + abs(y - lastY) + abs(z - lastZ)
                if (delta > 12f) {
                    val now = System.currentTimeMillis()
                    if (now - lastShakeTime > 800) {
                        lastShakeTime = now
                        val cx = width / 2f
                        val cy = height / 2f
                        for (f in flakes) {
                            val dx = f.x - cx
                            val dy = f.y - cy
                            val dist = hypot(dx, dy).coerceAtLeast(1f)
                            val force = rand.nextFloat() * 18f + 12f
                            f.x += (dx / dist) * force
                            f.y += (dy / dist) * force
                        }
                        post { onShake?.invoke() }
                    }
                }
                lastX = x
                lastY = y
                lastZ = z
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            flakes.clear()
            for (i in 0 until 30) {
                flakes.add(Flake(
                    rand.nextFloat() * w,
                    rand.nextFloat() * h,
                    rand.nextFloat() * 2.5f + 1f,
                    rand.nextFloat() * 1.5f + 0.5f,
                    rand.nextFloat() * 0.6f - 0.3f,
                    100 + rand.nextInt(155),
                    rand.nextFloat() * 360f
                ))
            }
            updateGradients()
        }

        override fun onDraw(canvas: Canvas) {
            if (!active) return
            time += 0.01f
            val t = ThemePalette.get(themeIndex)
            val bmp = bgBitmap
            if (bmp != null) {
                val scale = maxOf(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
                val dx = (width - bmp.width * scale) / 2f
                val dy = (height - bmp.height * scale) / 2f
                canvas.save()
                canvas.translate(dx, dy)
                canvas.scale(scale, scale)
                canvas.drawBitmap(bmp, 0f, 0f, null)
                canvas.restore()
            } else {
                canvas.drawPaint(bgPaint)
            }
            if (t.isGrid) {
                drawGrid(canvas)
            } else {
                matrix1.setTranslate(sin(time * 0.5f) * width * 0.3f, kotlin.math.cos(time * 0.7f) * height * 0.2f)
                blobPaint1.shader?.setLocalMatrix(matrix1)
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), blobPaint1)
                matrix2.setTranslate(kotlin.math.cos(time * 0.4f) * width * 0.4f, sin(time * 0.6f) * height * 0.3f)
                blobPaint2.shader?.setLocalMatrix(matrix2)
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), blobPaint2)
                matrix3.setTranslate(sin(time * 0.3f) * width * 0.2f, kotlin.math.cos(time * 0.5f) * height * 0.4f)
                blobPaint3.shader?.setLocalMatrix(matrix3)
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), blobPaint3)
            }
            val flakeColor = if (isActiveMode) currentSecondary() else currentPrimary()
            for (f in flakes) {
                f.y += f.speed
                f.x += f.drift + sin(time + f.y * 0.01f) * 0.3f
                f.rotation += 2f
                if (f.y > height + 10) {
                    f.y = -10f
                    f.x = rand.nextFloat() * width
                }
                if (f.x > width + 10) f.x = -10f
                if (f.x < -10) f.x = width + 10f
                if (isActiveMode) {
                    flakePaint.color = Color.argb(f.alpha, Color.red(flakeColor), Color.green(flakeColor), Color.blue(flakeColor))
                    flakePaint.setShadowLayer(f.radius * 2, 0f, 0f, flakeColor)
                    canvas.save()
                    canvas.rotate(f.rotation, f.x, f.y)
                    canvas.drawOval(f.x - f.radius, f.y - f.radius * 0.5f, f.x + f.radius, f.y + f.radius * 0.5f, flakePaint)
                    canvas.restore()
                } else {
                    flakePaint.color = Color.argb(f.alpha, Color.red(flakeColor), Color.green(flakeColor), Color.blue(flakeColor))
                    flakePaint.setShadowLayer(f.radius * 3, 0f, 0f, flakeColor)
                    canvas.drawCircle(f.x, f.y, f.radius, flakePaint)
                }
            }
            if (active) postInvalidateOnAnimation()
        }
    }
}
