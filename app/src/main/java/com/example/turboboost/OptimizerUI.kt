package com.example.turboboost

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
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
        private set
    var themeIndex = 0
        private set
    var currentGpu = false
    var currentBg = false
    var currentThreads = false
    var currentMglru = false
    var currentJoyose = false
    var lastStatus = "Готов к работе"
    var heroBox: LinearLayout? = null
    var isCustomSense = false
    private var prefsRef: AppPrefs? = null

    val senseLevels = listOf(1, 2, 3, -1)
    val senseNames = listOf("Сильный", "Эксп.", "Макс", "Кастом")
    val stretchNames = listOf("Стандарт", "4:3", "5:4", "27:9", "750p")
    val engineList = listOf(
        "fps" to "FPS", "gpu" to "GPU", "internet" to "Internet",
        "launch" to "Launch", "ram" to "RAM", "touch" to "Touch", "ui" to "UI"
    )

    fun theme() = ThemePalette.get(themeIndex)
    fun isGrid() = theme().isGrid
    fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
    fun prefs(): AppPrefs = prefsRef ?: AppPrefs(ctx).also { prefsRef = it }
    fun setThemeIndexBeforeBuild(i: Int) { themeIndex = i.coerceIn(0, ThemePalette.themes.size - 1) }

    fun build(): FrameLayout {
        root = FrameLayout(ctx)
        root.setBackgroundColor(if (isGrid()) Color.parseColor("#1A1A1A") else Color.parseColor("#05070A"))
        bgView = LiquidBackgroundView(ctx)
        bgView.setTheme(themeIndex, isActive)
        root.addView(bgView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        val content = FrameLayout(ctx)
        root.addView(content, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        page1 = LinearLayout(ctx)
        page1.orientation = LinearLayout.VERTICAL
        page1.setPadding(dp(16), dp(24), dp(16), dp(24))
        scroll1 = ScrollView(ctx)
        scroll1.isFillViewport = true
        scroll1.addView(page1, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        scroll1.setPadding(0, dp(80), 0, 0)
        content.addView(scroll1, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        topBar = buildTopBar()
        content.addView(topBar, FrameLayout.LayoutParams(MATCH_PARENT, dp(72)))
        buildMain()
        animateIn()
        return root
    }

    @JvmName("applyThemeIndex")
    fun setThemeIndex(i: Int) {
        themeIndex = i.coerceIn(0, ThemePalette.themes.size - 1)
        if (!::root.isInitialized) return
        heroBox?.background = heroBg()
        if (::bgView.isInitialized) bgView.setTheme(themeIndex, isActive)
        if (::btnModePerf.isInitialized) setMode(currentMode)
        if (::btnOpt.isInitialized) btnOpt.background = accent()
        if (::btnGpuBoost.isInitialized) {
            applyGrid(btnGpuBoost, currentGpu)
            btnGpuBoost.setOn(currentGpu)
        }
        if (::btnBgLimit.isInitialized) {
            applyGrid(btnBgLimit, currentBg)
            btnBgLimit.setOn(currentBg)
        }
        if (::btnThreads.isInitialized) {
            applyGrid(btnThreads, currentThreads)
            btnThreads.setOn(currentThreads)
        }
        if (::btnMglru.isInitialized) {
            applyGrid(btnMglru, currentMglru)
            btnMglru.setOn(currentMglru)
        }
        if (::btnJoyose.isInitialized) {
            applyGrid(btnJoyose, currentJoyose)
            btnJoyose.setOn(currentJoyose)
        }
        if (hzRow != null) updHz()
        if (senseRow != null) updSense()
        if (stretchRow != null) updStretch()
        if (::btnGames.isInitialized) refreshActionBg()
    }

    fun setMode(m: Int) {
        currentMode = m
        if (::btnModePerf.isInitialized) btnModePerf.background = if (m == 0) accent() else unsel()
        if (::btnModeBal.isInitialized) btnModeBal.background = if (m == 1) accent() else unsel()
    }

    fun setHz(hz: Int) {
        currentHz = hz
        if (hzRow != null) updHz()
    }

    fun setPackageText(t: String) {
        if (t.isNotEmpty() && ::pkgInput.isInitialized) pkgInput.setText(t)
    }

    fun setSenseLevel(l: Int) {
        currentSense = l
        isCustomSense = (l == -1)
        if (senseRow != null) updSense()
    }

    @JvmName("applyActive")
    fun setActive(a: Boolean) {
        isActive = a
        heroBox?.background = heroBg()
        if (::btnOpt.isInitialized) {
            btnOpt.text = if (a) "ВЕРНУТЬ АВТО" else "ОПТИМИЗИРОВАТЬ"
            btnOpt.background = accent()
        }
        if (::bgView.isInitialized) bgView.setActive(a)
        setMode(currentMode)
        if (::btnGpuBoost.isInitialized) {
            applyGrid(btnGpuBoost, currentGpu)
            btnGpuBoost.setOn(currentGpu)
        }
        if (::btnBgLimit.isInitialized) {
            applyGrid(btnBgLimit, currentBg)
            btnBgLimit.setOn(currentBg)
        }
        if (::btnThreads.isInitialized) {
            applyGrid(btnThreads, currentThreads)
            btnThreads.setOn(currentThreads)
        }
        if (::btnMglru.isInitialized) {
            applyGrid(btnMglru, currentMglru)
            btnMglru.setOn(currentMglru)
        }
        if (::btnJoyose.isInitialized) {
            applyGrid(btnJoyose, currentJoyose)
            btnJoyose.setOn(currentJoyose)
        }
        if (hzRow != null) updHz()
        if (senseRow != null) updSense()
        if (stretchRow != null) updStretch()
    }

    fun setGpuBoostState(a: Boolean) {
        currentGpu = a
        if (::btnGpuBoost.isInitialized) {
            applyGrid(btnGpuBoost, a)
            btnGpuBoost.setOn(a)
        }
    }

    fun setBgLimitState(a: Boolean) {
        currentBg = a
        if (::btnBgLimit.isInitialized) {
            applyGrid(btnBgLimit, a)
            btnBgLimit.setOn(a)
        }
    }

    fun setThreadsState(a: Boolean) {
        currentThreads = a
        if (::btnThreads.isInitialized) {
            applyGrid(btnThreads, a)
            btnThreads.setOn(a)
        }
    }

    fun setMglruState(a: Boolean) {
        currentMglru = a
        if (::btnMglru.isInitialized) {
            applyGrid(btnMglru, a)
            btnMglru.setOn(a)
        }
    }

    fun setJoyoseState(a: Boolean) {
        currentJoyose = a
        if (::btnJoyose.isInitialized) {
            applyGrid(btnJoyose, a)
            btnJoyose.setOn(a)
        }
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

    fun setAppliedState(active: Boolean, gov: Boolean) {
        if (!::cardApplied.isInitialized) return
        cardApplied.text = if (!active) "Применено: —" else "Применено: RAM очищена"
    }

    fun setBatteryLowBlock(b: Boolean) {
        if (::btnOpt.isInitialized) btnOpt.alpha = if (b) 0.4f else 1f
    }

    fun setStatus(text: String) {
        lastStatus = text
        if (::statusResult.isInitialized) statusResult.text = text
    }

    fun setOverheat(warn: Boolean, critical: Boolean) {
        if (!::statusResult.isInitialized) return
        statusResult.text = when {
            critical -> "КРИТИЧЕСКИЙ ПЕРЕГРЕВ — турбо выключено"
            warn -> "⚠ Перегрев · $lastStatus"
            else -> lastStatus
        }
    }

    fun setEngineState(key: String, on: Boolean, score: String) {
        val e = engineViews[key] ?: return
        e.first.setOn(on, true)
        e.second.text = if (score.isBlank()) {
            if (on) "…" else "—"
        } else score
        e.second.setTextColor(if (on) theme().primary else Color.parseColor("#88FFFFFF"))
        if (isGrid()) {
            val g = GradientDrawable()
            g.shape = GradientDrawable.RECTANGLE
            g.cornerRadius = dp(14).toFloat()
            g.colors = if (on) intArrayOf(0xFF4A4A4A.toInt(), 0xFF3A3A3A.toInt()) else intArrayOf(0xFF3A3A3A.toInt(), 0xFF2A2A2A.toInt())
            g.orientation = GradientDrawable.Orientation.TOP_BOTTOM
            g.setStroke(dp(2), if (on) theme().primary else 0x66FF8A00.toInt())
            e.third.background = g
        } else {
            e.third.background = glassBg(14)
        }
    }

    fun setEngineScore(key: String, score: String) {
        engineViews[key]?.second?.text = if (score.isBlank()) "—" else score
    }

    fun setEngineScoreLoading(key: String) {
        engineViews[key]?.second?.text = "…"
    }

    fun setBackgroundBitmap(bmp: Bitmap?) {
        if (::bgView.isInitialized) bgView.setBackgroundImage(bmp)
    }

    fun setStretchPreset(preset: Int) {
        currentStretch = preset
        if (stretchRow != null) updStretch()
    }

    fun setCustomResolution(w: Int, h: Int) {
        if (::etCustomW.isInitialized && w > 0) etCustomW.setText(w.toString())
        if (::etCustomH.isInitialized && h > 0) etCustomH.setText(h.toString())
    }

    fun applyGrid(row: ToggleRow, on: Boolean) {
        if (isGrid()) row.applyGridStyle(on, theme().primary) else row.clearGridStyle()
        row.onColor = theme().primary
    }
}fun OptimizerUI.dr(r: Int, c1: Int, c2: Int, o: GradientDrawable.Orientation = GradientDrawable.Orientation.TL_BR, stroke: Int = 0): GradientDrawable {
    val g = GradientDrawable()
    g.shape = GradientDrawable.RECTANGLE
    g.cornerRadius = dp(r).toFloat()
    g.colors = intArrayOf(c1, c2)
    g.gradientType = GradientDrawable.LINEAR_GRADIENT
    g.orientation = o
    if (stroke != 0) g.setStroke(dp(1), stroke)
    return g
}

fun OptimizerUI.accent(): Drawable {
    if (isGrid()) {
        return dr(14, 0xFF5A5A5A.toInt(), 0xFF454545.toInt(), GradientDrawable.Orientation.TOP_BOTTOM, theme().primary)
    }
    return dr(14, theme().primary, theme().secondary, GradientDrawable.Orientation.LEFT_RIGHT, 0x33FFFFFF)
}

fun OptimizerUI.unsel(): Drawable {
    if (isGrid()) {
        return dr(14, 0xFF3A3A3A.toInt(), 0xFF2A2A2A.toInt(), GradientDrawable.Orientation.TOP_BOTTOM, theme().primary or 0x99000000.toInt())
    }
    return dr(14, 0x1AFFFFFF, 0x10FFFFFF, stroke = 0x22FFFFFF)
}

fun OptimizerUI.glassBg(r: Int): Drawable {
    if (isGrid()) {
        return dr(r, 0xFF3A3A3A.toInt(), 0xFF2A2A2A.toInt(), GradientDrawable.Orientation.TOP_BOTTOM, theme().primary or 0x66000000.toInt())
    }
    return dr(r, 0x1CFFFFFF, 0x0DFFFFFF, stroke = 0x1FFFFFFF)
}

fun OptimizerUI.heroBg(): Drawable {
    val t = theme()
    if (t.isGrid) {
        val g = dr(26, 0xFF454545.toInt(), 0xFF2A2A2A.toInt(), GradientDrawable.Orientation.TOP_BOTTOM, if (isActive) t.primary else t.primary or 0x99000000.toInt())
        g.colors = intArrayOf(0xFF454545.toInt(), 0xFF353535.toInt(), 0xFF2A2A2A.toInt())
        return g
    }
    val a1 = if (isActive) 0x70 else 0x38
    val g = GradientDrawable()
    g.shape = GradientDrawable.RECTANGLE
    g.cornerRadius = dp(26).toFloat()
    g.colors = intArrayOf((a1 shl 24) or (t.primary and 0xFFFFFF), (0x24 shl 24) or (t.secondary and 0xFFFFFF), 0x12FFFFFF)
    g.orientation = GradientDrawable.Orientation.TL_BR
    g.setStroke(dp(1), if (isActive) t.secondary else 0x33FFFFFF)
    return g
}

fun OptimizerUI.buildTopBar(): FrameLayout {
    val bar = FrameLayout(ctx)
    bar.setBackgroundColor(if (isGrid()) Color.parseColor("#CC1A1A1A") else Color.parseColor("#CC05070A"))
    val h = LinearLayout(ctx)
    h.orientation = LinearLayout.HORIZONTAL
    h.gravity = android.view.Gravity.CENTER_VERTICAL
    h.setPadding(dp(16), dp(12), dp(16), dp(12))
    bar.addView(h, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    val ic = ImageView(ctx)
    ic.setImageResource(R.drawable.ic_bolt)
    ic.alpha = 0.9f
    val icLp = LinearLayout.LayoutParams(dp(34), dp(34))
    icLp.marginEnd = dp(12)
    h.addView(ic, icLp)
    val col = LinearLayout(ctx)
    col.orientation = LinearLayout.VERTICAL
    h.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
    val title = tv("TurboBoost", 22f, Color.WHITE)
    title.letterSpacing = 0.05f
    title.setShadowLayer(8f, 0f, 0f, theme().secondary)
    col.addView(title)
    val sub = tv("TURBO · 2.2", 10f, Color.parseColor("#AACCE0FF"))
    sub.letterSpacing = 0.3f
    col.addView(sub)
    btnSettings = ImageView(ctx)
    btnSettings.setImageResource(android.R.drawable.ic_menu_preferences)
    btnSettings.setColorFilter(Color.WHITE)
    btnSettings.setPadding(dp(8), dp(8), dp(8), dp(8))
    btnSettings.setOnClickListener { onSettingsClick?.invoke() }
    h.addView(btnSettings, LinearLayout.LayoutParams(dp(44), dp(44)))
    return bar
}

fun OptimizerUI.lpf(): LinearLayout.LayoutParams {
    val lp = LinearLayout.LayoutParams(0, dp(40), 1f)
    lp.marginEnd = dp(6)
    return lp
}

fun OptimizerUI.lpl(): LinearLayout.LayoutParams {
    return LinearLayout.LayoutParams(0, dp(40), 1f)
}

fun OptimizerUI.section(title: String): LinearLayout {
    val box = LinearLayout(ctx)
    box.orientation = LinearLayout.VERTICAL
    box.setPadding(dp(14), dp(12), dp(14), dp(14))
    if (isGrid()) {
        box.background = dr(20, 0xFF2A2A2A.toInt(), 0xFF1F1F1F.toInt(), GradientDrawable.Orientation.TOP_BOTTOM, theme().primary or 0x66000000.toInt())
    } else {
        box.background = glassBg(20)
    }
    val hdr = tv(title, 11f, Color.parseColor("#99FFFFFF"))
    hdr.letterSpacing = 0.18f
    hdr.typeface = android.graphics.Typeface.DEFAULT_BOLD
    hdr.setPadding(dp(2), 0, 0, dp(2))
    box.addView(hdr)
    val boxLp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    boxLp.topMargin = dp(12)
    page1.addView(box, boxLp)
    return box
}

fun OptimizerUI.tr(parent: LinearLayout, title: String, cb: (() -> Unit)?): ToggleRow {
    val row = ToggleRow(ctx, title)
    row.onToggle = { cb?.invoke() }
    row.onColor = theme().primary
    if (isGrid()) {
        row.applyGridStyle(false, theme().primary)
    } else {
        row.clearGridStyle()
    }
    val rLp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    rLp.topMargin = dp(6)
    parent.addView(row, rLp)
    return row
}

fun OptimizerUI.buildEngineGrid(parent: LinearLayout) {
    engineViews.clear()
    var i = 0
    while (i < engineList.size) {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        val left = engineList[i]
        val leftLp = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        leftLp.marginEnd = dp(8)
        row.addView(buildEngineCell(left.first, left.second), leftLp)
        if (i + 1 < engineList.size) {
            val right = engineList[i + 1]
            row.addView(buildEngineCell(right.first, right.second), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        } else {
            row.addView(View(ctx), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        val rowLp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        rowLp.topMargin = dp(8)
        parent.addView(row, rowLp)
        i += 2
    }
}

fun OptimizerUI.buildEngineCell(key: String, title: String): LinearLayout {
    val cell = LinearLayout(ctx)
    cell.orientation = LinearLayout.HORIZONTAL
    cell.gravity = android.view.Gravity.CENTER_VERTICAL
    cell.setPadding(dp(12), dp(10), dp(12), dp(10))
    cell.background = glassBg(14)
    val col = LinearLayout(ctx)
    col.orientation = LinearLayout.VERTICAL
    val titleTv = tv(title, 13f, Color.WHITE)
    titleTv.typeface = android.graphics.Typeface.DEFAULT_BOLD
    col.addView(titleTv)
    val scoreTv = tv(prefs().getEngineScore(key).ifBlank { "—" }, 10f, Color.parseColor("#88FFFFFF"))
    scoreTv.setPadding(0, dp(2), 0, 0)
    col.addView(scoreTv)
    cell.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
    val sw = ToggleSwitch(ctx)
    sw.onColor = theme().primary
    sw.doubleBorder = false
    val swLp = LinearLayout.LayoutParams(dp(46), dp(28))
    swLp.marginStart = dp(8)
    cell.addView(sw, swLp)
    sw.onToggle = { on ->
        onEngineToggle?.invoke(key)
        if (on && scoreTv.text == "—") scoreTv.text = "…"
        if (!on) scoreTv.text = prefs().getEngineScore(key).ifBlank { "—" }
    }
    cell.setOnClickListener {
        it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
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
        val v = row.getChildAt(i) as TextView
        v.background = if (i == currentStretch) accent() else unsel()
    }
}

fun OptimizerUI.updHz() {
    val row = hzRow ?: return
    val m = listOf(60, 90, 120, 144)
    for (i in 0 until row.childCount) {
        val v = row.getChildAt(i) as TextView
        v.background = if (m[i] == currentHz) accent() else unsel()
    }
}

fun OptimizerUI.updSense() {
    val row = senseRow ?: return
    val levels = listOf(1, 2, 3, -1)
    for (i in 0 until row.childCount) {
        val lvl = levels.getOrNull(i) ?: 0
        val bg = if (lvl == -1) {
            if (isCustomSense) accent() else unsel()
        } else {
            if (lvl == currentSense && !isCustomSense) accent() else unsel()
        }
        val v = row.getChildAt(i) as TextView
        v.background = bg
    }
}

fun OptimizerUI.applyHzLimit() {
    val row = hzRow ?: return
    val m = listOf(60, 90, 120, 144)
    for (i in 0 until row.childCount) {
        val v = row.getChildAt(i)
        v.visibility = if (m[i] <= maxHz || m[i] == 60) View.VISIBLE else View.GONE
    }
}fun OptimizerUI.buildMain() {
    val hero = LinearLayout(ctx)
    hero.orientation = LinearLayout.VERTICAL
    hero.setPadding(dp(18), dp(16), dp(18), dp(18))
    hero.background = heroBg()
    heroBox = hero

    val heroTop = LinearLayout(ctx)
    heroTop.orientation = LinearLayout.HORIZONTAL
    heroTop.gravity = android.view.Gravity.CENTER_VERTICAL
    val stLbl = tv("СТАТУС", 11f, Color.parseColor("#B3FFFFFF"))
    stLbl.letterSpacing = 0.2f
    stLbl.typeface = android.graphics.Typeface.DEFAULT_BOLD
    heroTop.addView(stLbl, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
    heroTop.addView(tv("v" + appVer(), 11f, Color.parseColor("#80FFFFFF")))
    hero.addView(heroTop, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

    statusResult = tv("Готов к работе", 15f, Color.WHITE)
    statusResult.typeface = android.graphics.Typeface.DEFAULT_BOLD
    statusResult.setPadding(0, dp(8), 0, dp(2))
    hero.addView(statusResult, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

    cardApplied = tv("Применено: —", 12f, Color.parseColor("#B3FFFFFF"))
    val cardLp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    cardLp.bottomMargin = dp(14)
    hero.addView(cardApplied, cardLp)

    btnOpt = bigBtn("ОПТИМИЗИРОВАТЬ", 16f, accent()) { onToggle?.invoke() }
    hero.addView(btnOpt, LinearLayout.LayoutParams(MATCH_PARENT, dp(58)))
    page1.addView(hero, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

    buildEngineGrid(section("МОДУЛИ ZNAK"))

    val sGame = section("ИГРА")
    pkgInput = EditText(ctx)
    pkgInput.hint = "com.axlebolt.standoff2"
    pkgInput.setHintTextColor(Color.parseColor("#88FFFFFF"))
    pkgInput.setTextColor(Color.WHITE)
    pkgInput.textSize = 13f
    pkgInput.setSingleLine()
    pkgInput.inputType = android.text.InputType.TYPE_CLASS_TEXT
    pkgInput.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    pkgInput.setPadding(dp(16), dp(10), dp(16), dp(10))
    pkgInput.setBackgroundColor(Color.TRANSPARENT)
    pkgInput.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) {
            onPackageChanged?.invoke(s?.toString()?.trim() ?: "")
        }
    })
    val pkgWrap = FrameLayout(ctx)
    pkgWrap.background = glassBg(14)
    pkgWrap.addView(pkgInput, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    val pkgLp = LinearLayout.LayoutParams(MATCH_PARENT, dp(44))
    pkgLp.topMargin = dp(4)
    sGame.addView(pkgWrap, pkgLp)

    val r1 = row(sGame, 8)
    btnGames = glass("Игры") { onShowGames?.invoke() }
    r1.addView(btnGames, lpf())
    btnProfiles = glass("Профили") { onShowProfiles?.invoke() }
    r1.addView(btnProfiles, lpf())
    btnLauncher = glass("Лаунчер") { onShowLauncher?.invoke() }
    r1.addView(btnLauncher, lpl())

    val sMode = section("РЕЖИМ")
    val mr = row(sMode, 4)
    btnModePerf = glass("Производительность") {
        onModeSelected?.invoke(0)
        setMode(0)
    }
    mr.addView(btnModePerf, lpf())
    btnModeBal = glass("Сбалансированный") {
        onModeSelected?.invoke(1)
        setMode(1)
    }
    mr.addView(btnModeBal, lpl())

    val hs = LinearLayout(ctx)
    hs.orientation = LinearLayout.VERTICAL
    hs.addView(lbl("Частота экрана, Гц"))
    hzSection = hs
    val hr = row(hs, 2)
    hzRow = hr
    listOf(60, 90, 120, 144).forEach { hz ->
        val btn = glass(hz.toString()) {
            currentHz = hz
            onHzSelected?.invoke(hz)
            updHz()
        }
        hr.addView(btn, lpf())
    }
    sMode.addView(hs, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    applyHzLimit()

    btnGpuBoost = tr(sMode, "GPU Boost") { onGpuBoostToggle?.invoke() }
    btnBgLimit = tr(sMode, "Лимит фона") { onBgLimitToggle?.invoke() }

    val sSense = section("СЕНСА")
    val sr = row(sSense, 4)
    senseRow = sr
    senseNames.forEachIndexed { i, n ->
        val lvl = senseLevels[i]
        val btn = glass(n) {
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
        }
        sr.addView(btn, lpf())
    }
    val finderBtn = glass("Подбор сенсы Standoff 2") { onSenseFinder?.invoke() }
    val finderLp = LinearLayout.LayoutParams(MATCH_PARENT, dp(42))
    finderLp.topMargin = dp(8)
    sSense.addView(finderBtn, finderLp)

    val sStretch = section("РАСТЯГ (STRETCH)")
    val str = row(sStretch, 4)
    stretchRow = str
    stretchNames.forEachIndexed { i, name ->
        val btn = glass(name) {
            currentStretch = i
            onStretchPreset?.invoke(i)
            updStretch()
        }
        if (i < stretchNames.size - 1) {
            str.addView(btn, lpf())
        } else {
            str.addView(btn, lpl())
        }
    }

    btnLaunchStretch = bigBtn("ПРИМЕНИТЬ И ЗАПУСТИТЬ", 15f, accent()) { onLaunchStretch?.invoke() }
    val launchLp = LinearLayout.LayoutParams(MATCH_PARENT, dp(52))
    launchLp.topMargin = dp(12)
    sStretch.addView(btnLaunchStretch, launchLp)

    val resetBtn = glass("Сбросить разрешение") { onResetCustomResolution?.invoke() }
    val resetLp = LinearLayout.LayoutParams(MATCH_PARENT, dp(42))
    resetLp.topMargin = dp(8)
    sStretch.addView(resetBtn, resetLp)

    val customRow = LinearLayout(ctx)
    customRow.orientation = LinearLayout.HORIZONTAL
    customRow.gravity = android.view.Gravity.CENTER_VERTICAL
    etCustomW = EditText(ctx)
    etCustomW.hint = "Ширина"
    etCustomW.setHintTextColor(Color.parseColor("#88FFFFFF"))
    etCustomW.setTextColor(Color.WHITE)
    etCustomW.textSize = 13f
    etCustomW.inputType = android.text.InputType.TYPE_CLASS_NUMBER
    etCustomW.setSingleLine()
    etCustomW.setPadding(dp(12), dp(10), dp(12), dp(10))
    etCustomW.background = glassBg(12)
    etCustomH = EditText(ctx)
    etCustomH.hint = "Высота"
    etCustomH.setHintTextColor(Color.parseColor("#88FFFFFF"))
    etCustomH.setTextColor(Color.WHITE)
    etCustomH.textSize = 13f
    etCustomH.inputType = android.text.InputType.TYPE_CLASS_NUMBER
    etCustomH.setSingleLine()
    etCustomH.setPadding(dp(12), dp(10), dp(12), dp(10))
    etCustomH.background = glassBg(12)
    val wLp = LinearLayout.LayoutParams(0, dp(44), 1f)
    wLp.marginEnd = dp(8)
    customRow.addView(etCustomW, wLp)
    customRow.addView(etCustomH, LinearLayout.LayoutParams(0, dp(44), 1f))
    val rowLp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    rowLp.topMargin = dp(8)
    sStretch.addView(customRow, rowLp)

    val applyBtn = glass("Применить кастомное разрешение") {
        val w = etCustomW.text.toString().toIntOrNull() ?: 0
        val h = etCustomH.text.toString().toIntOrNull() ?: 0
        if (w > 0 && h > 0) onApplyCustomResolution?.invoke(w, h)
    }
    val applyLp = LinearLayout.LayoutParams(MATCH_PARENT, dp(42))
    applyLp.topMargin = dp(8)
    sStretch.addView(applyBtn, applyLp)

    val hint = tv("Игра выбирается в Настройках. Разрешение применится, игра запустится, и через 7 сек (PUBG) / 10 сек (Oxide) / 25 сек (Standoff 2) вернётся исходное.", 11f, Color.parseColor("#99FFFFFF"))
    hint.setPadding(dp(4), dp(10), dp(4), dp(0))
    sStretch.addView(hint)

    val sSys = section("СИСТЕМА")
    btnThreads = tr(sSys, "Потоки CPU") { onThreadsToggle?.invoke() }
    btnMglru = tr(sSys, "MGLRU") { onMglruToggle?.invoke() }
    btnJoyose = tr(sSys, "Xiaomi Boost") { onJoyoseToggle?.invoke() }
    btnJoyose.visibility = View.GONE
    val r4 = row(sSys, 8)
    val pingBtn = glass("Пинг-тест") { onPingTest?.invoke() }
    r4.addView(pingBtn, lpf())
    btnGameSpace = glass("Game Turbo") { onGameSpace?.invoke() }
    r4.addView(btnGameSpace, lpf())
    btnDiagnostics = glass("Диагностика") { onDiagnostics?.invoke() }
    r4.addView(btnDiagnostics, lpl())
    val profileBtn = glass("Мой аккаунт") { onShowProfile?.invoke() }
    val profileLp = LinearLayout.LayoutParams(MATCH_PARENT, dp(42))
    profileLp.topMargin = dp(8)
    sSys.addView(profileBtn, profileLp)

    page1.addView(View(ctx), LinearLayout.LayoutParams(MATCH_PARENT, dp(24)))
}

fun OptimizerUI.animateIn() {
    for (i in 0 until page1.childCount) {
        val v = page1.getChildAt(i)
        v.alpha = 0f
        v.translationY = dp(40).toFloat()
        v.scaleX = 0.9f
        v.scaleY = 0.9f
        v.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay((i * 20).toLong())
            .setDuration(500)
            .setInterpolator(android.view.animation.AnticipateOvershootInterpolator(1.2f))
            .start()
    }
}

fun OptimizerUI.refreshActionBg() {
    val list = listOf(btnGames, btnProfiles, btnLauncher, btnDiagnostics, btnGameSpace)
    for (v in list) {
        try {
            v.background = unsel()
        } catch (_: Throwable) {
        }
    }
}

fun OptimizerUI.showGlassList(title: String, items: List<String>, onClick: (Int) -> Unit) {
    val dialog = android.app.Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
    val r = FrameLayout(ctx)
    r.setBackgroundColor(if (isGrid()) Color.parseColor("#FF1A1A1A") else Color.parseColor("#FF000000"))
    val scroll = ScrollView(ctx)
    val container = LinearLayout(ctx)
    container.orientation = LinearLayout.VERTICAL
    container.setPadding(dp(24), dp(40), dp(24), dp(40))
    scroll.addView(container)
    val scrollLp = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    scrollLp.gravity = android.view.Gravity.CENTER
    r.addView(scroll, scrollLp)
    val titleTv = TextView(ctx)
    titleTv.text = title
    titleTv.setTextColor(Color.WHITE)
    titleTv.textSize = 20f
    titleTv.gravity = android.view.Gravity.CENTER
    titleTv.setPadding(0, 0, 0, dp(20))
    container.addView(titleTv)
    val dlgBg = if (isGrid()) {
        dr(24, 0xFF3A3A3A.toInt(), 0xFF2A2A2A.toInt(), GradientDrawable.Orientation.TOP_BOTTOM, theme().primary)
    } else {
        dr(24, 0x33FFFFFF, 0x22FFFFFF, stroke = 0xAAFFFFFF.toInt())
    }
    items.forEachIndexed { i, item ->
        val itemTv = TextView(ctx)
        itemTv.text = item
        itemTv.setTextColor(Color.WHITE)
        itemTv.textSize = 15f
        itemTv.gravity = android.view.Gravity.CENTER_VERTICAL
        itemTv.setPadding(dp(16), dp(14), dp(16), dp(14))
        itemTv.background = dlgBg
        itemTv.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            onClick(i)
            dialog.dismiss()
        }
        val itemLp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        itemLp.bottomMargin = dp(8)
        container.addView(itemTv, itemLp)
    }
    dialog.setContentView(r)
    dialog.window?.setLayout(MATCH_PARENT, WRAP_CONTENT)
    dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
    dialog.show()
}

fun OptimizerUI.tv(text: String, size: Float, color: Int): TextView {
    val v = TextView(ctx)
    v.text = text
    v.setTextColor(color)
    v.textSize = size
    return v
}

fun OptimizerUI.lbl(text: String): TextView {
    val v = tv(text, 12f, Color.parseColor("#99FFFFFF"))
    v.setPadding(dp(4), dp(10), dp(0), dp(4))
    return v
}

fun OptimizerUI.row(p: LinearLayout, margin: Int): LinearLayout {
    val r = LinearLayout(ctx)
    r.orientation = LinearLayout.HORIZONTAL
    r.gravity = android.view.Gravity.CENTER_VERTICAL
    val lp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    lp.topMargin = dp(margin)
    p.addView(r, lp)
    return r
}

fun OptimizerUI.glass(text: String, click: () -> Unit): TextView {
    val v = tv(text, 12f, Color.WHITE)
    v.typeface = android.graphics.Typeface.DEFAULT_BOLD
    v.gravity = android.view.Gravity.CENTER
    v.maxLines = 1
    v.setPadding(dp(4), 0, dp(4), 0)
    v.background = unsel()
    v.setOnTouchListener { view, ev ->
        when (ev.action) {
            android.view.MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.95f).scaleY(0.95f).alpha(0.8f).setDuration(50).start()
            android.view.MotionEvent.ACTION_UP -> view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(300).setInterpolator(android.view.animation.AnticipateOvershootInterpolator(2f)).start()
            android.view.MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(300).setInterpolator(android.view.animation.AnticipateOvershootInterpolator(2f)).start()
        }
        false
    }
    v.setOnClickListener {
        it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        click()
    }
    return v
}

fun OptimizerUI.bigBtn(text: String, size: Float, bg: Drawable, click: () -> Unit): TextView {
    val v = tv(text, size, Color.WHITE)
    v.gravity = android.view.Gravity.CENTER
    v.setShadowLayer(6f, 0f, 2f, Color.parseColor("#55000000"))
    v.background = bg
    v.setOnTouchListener { view, ev ->
        when (ev.action) {
            android.view.MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.95f).scaleY(0.95f).alpha(0.8f).setDuration(50).start()
            android.view.MotionEvent.ACTION_UP -> view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(300).setInterpolator(android.view.animation.AnticipateOvershootInterpolator(2f)).start()
            android.view.MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(300).setInterpolator(android.view.animation.AnticipateOvershootInterpolator(2f)).start()
        }
        false
    }
    v.setOnClickListener {
        it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        click()
    }
    return v
}

fun OptimizerUI.appVer(): String {
    return try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (_: Throwable) {
        "?"
    }
}
