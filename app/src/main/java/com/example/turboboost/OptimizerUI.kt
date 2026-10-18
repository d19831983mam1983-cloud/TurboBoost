package com.example.turboboost

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class OptimizerUI(private val ctx: Context) {

    lateinit var root: FrameLayout
    lateinit var scroll: ScrollView
    lateinit var content: LinearLayout
    lateinit var btnOpt: TextView
    lateinit var btnTurbo: TextView
    lateinit var btnDevOptions: TextView
    lateinit var btnCompile: TextView
    lateinit var btnKeep: TextView
    lateinit var btnModePerf: TextView
    lateinit var btnModeBal: TextView
    lateinit var btnGpuBoost: TextView
    lateinit var btnBgLimit: TextView
    lateinit var btnGames: TextView
    lateinit var btnProfiles: TextView
    lateinit var btnCache: TextView
    lateinit var btnPing: TextView
    lateinit var pkgInput: EditText
    lateinit var cardChip: TextView
    lateinit var cardGpu: TextView
    lateinit var cardScreen: TextView
    lateinit var cardBattery: TextView
    lateinit var cardTemp: TextView
    lateinit var cardFps: TextView
    lateinit var cardPing: TextView
    lateinit var cardApplied: TextView
    lateinit var statusResult: TextView
    lateinit var senseRow: LinearLayout
    lateinit var thermalRow: LinearLayout
    lateinit var presetRow: LinearLayout
    lateinit var hzRow: LinearLayout

    var onToggle: (() -> Unit)? = null
    var onTurboToggle: (() -> Unit)? = null
    var onDevOptions: (() -> Unit)? = null
    var onCompile: ((String) -> Unit)? = null
    var onKeep: ((String) -> Unit)? = null
    var onSenseSelected: ((Int) -> Unit)? = null
    var onThermalSelected: ((Int) -> Unit)? = null
    var onModeSelected: ((Int) -> Unit)? = null
    var onGpuBoostToggle: (() -> Unit)? = null
    var onBgLimitToggle: (() -> Unit)? = null
    var onShowGames: (() -> Unit)? = null
    var onShowProfiles: (() -> Unit)? = null
    var onTrimCaches: (() -> Unit)? = null
    var onPing: (() -> Unit)? = null
    var onPresetSelected: ((Int) -> Unit)? = null
    var onHzSelected: ((Int) -> Unit)? = null

    var currentSense = -1
    var currentThermal = -1
    var currentMode = 0
    var currentPreset = -1
    var currentHz = 120

    private val blueS by lazy { buttonBlue().constantState }
    private val redS by lazy { buttonRed().constantState }
    private val unselS by lazy { buttonUnselected().constantState }
    private val tBlueS by lazy { buttonTurboBlue().constantState }
    private val tGreenS by lazy { buttonTurboGreen().constantState }
    private val cardS by lazy { cardBg().constantState }

    private fun blueBg() = blueS!!.newDrawable().mutate() as GradientDrawable
    private fun redBg() = redS!!.newDrawable().mutate() as GradientDrawable
    private fun unselBg() = unselS!!.newDrawable().mutate() as GradientDrawable
    private fun tBlueBg() = tBlueS!!.newDrawable().mutate() as GradientDrawable
    private fun tGreenBg() = tGreenS!!.newDrawable().mutate() as GradientDrawable
    private fun cardCopy() = cardS!!.newDrawable().mutate() as GradientDrawable

    fun build(): FrameLayout {
        root = FrameLayout(ctx)
        root.setBackgroundColor(Color.parseColor("#0B0F1A"))
        scroll = ScrollView(ctx)
        scroll.isFillViewport = true
        root.addView(scroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        content = LinearLayout(ctx)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(18), dp(44), dp(18), dp(20))
        scroll.addView(content, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        content.addView(label("TurboBoost", 30f, Gravity.CENTER), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val sub = label("Оптимизация для игр", 13f, Gravity.CENTER)
        sub.setTextColor(Color.parseColor("#AAFFFFFF"))
        content.addView(sub, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(2) })

        val modeRow = row()
        btnModePerf = btn("Производительность") { onModeSelected?.invoke(0); setMode(0) }
        modeRow.addView(btnModePerf, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(6) })
        btnModeBal = btn("Сбалансированный") { onModeSelected?.invoke(1); setMode(1) }
        modeRow.addView(btnModeBal, LinearLayout.LayoutParams(0, dp(40), 1f))

        content.addView(label("Пресет:", 14f, Gravity.START).apply { setPadding(dp(8), dp(12), 0, dp(4)) })
        presetRow = row()
        val presets = listOf("Стандарт", "Агрессивный", "Максимум")
        presets.forEachIndexed { i, name ->
            val b = btn(name) { currentPreset = i; onPresetSelected?.invoke(i); updatePreset() }
            presetRow.addView(b, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        }

        content.addView(label("Частота экрана (Гц):", 14f, Gravity.START).apply { setPadding(dp(8), dp(12), 0, dp(4)) })
        hzRow = row()
        val hzs = listOf(60, 90, 120, 144)
        hzs.forEach { hz ->
            val b = btn(hz.toString()) { currentHz = hz; onHzSelected?.invoke(hz); updateHz() }
            hzRow.addView(b, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        }

        cardChip = card("Чип: —"); content.addView(cardChip, cardLp())
        cardGpu = card("GPU: —"); content.addView(cardGpu, cardLp())
        cardScreen = card("Частота: —"); content.addView(cardScreen, cardLp())
        cardBattery = card("Батарея: —"); content.addView(cardBattery, cardLp())
        cardTemp = card("Температура: —"); content.addView(cardTemp, cardLp())
        cardFps = card("FPS: —"); content.addView(cardFps, cardLp())
        cardPing = card("Пинг: —"); content.addView(cardPing, cardLp())
        cardApplied = card("Применено: —"); content.addView(cardApplied, cardLp())

        val extraRow = row()
        btnGpuBoost = btn("GPU Boost") { onGpuBoostToggle?.invoke() }
        extraRow.addView(btnGpuBoost, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        btnBgLimit = btn("Лимит фона") { onBgLimitToggle?.invoke() }
        extraRow.addView(btnBgLimit, LinearLayout.LayoutParams(0, dp(44), 1f))

        content.addView(label("Сенса:", 14f, Gravity.START).apply { setPadding(dp(8), dp(12), 0, dp(4)) })
        senseRow = row()
        val senses = listOf("Средний", "Сильный", "Эксп.", "Макс")
        senses.forEachIndexed { i, name ->
            val b = btn(name) { currentSense = i; onSenseSelected?.invoke(i); updateSense() }
            senseRow.addView(b, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        }

        content.addView(label("Троттлинг (°C):", 14f, Gravity.START).apply { setPadding(dp(8), dp(12), 0, dp(4)) })
        thermalRow = row()
        val thermals = listOf("Авто", "50", "60", "70")
        thermals.forEachIndexed { i, name ->
            val b = btn(name) { currentThermal = i; onThermalSelected?.invoke(i); updateThermal() }
            thermalRow.addView(b, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        }

        content.addView(label("Пакет игры:", 13f, Gravity.START).apply { setPadding(dp(8), dp(12), 0, dp(4)) })
        pkgInput = EditText(ctx)
        pkgInput.hint = "com.axlebolt.standoff2"
        pkgInput.setHintTextColor(Color.parseColor("#66FFFFFF"))
        pkgInput.setTextColor(Color.WHITE)
        pkgInput.textSize = 14f
        pkgInput.setSingleLine()
        pkgInput.inputType = InputType.TYPE_CLASS_TEXT
        pkgInput.setPadding(dp(14), dp(10), dp(14), dp(10))
        pkgInput.background = unselBg()
        content.addView(pkgInput, LinearLayout.LayoutParams(MATCH_PARENT, dp(48)).apply { topMargin = dp(4) })

        val row1 = row()
        btnGames = btn("Выбрать игру") { onShowGames?.invoke() }
        row1.addView(btnGames, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        btnProfiles = btn("Профили") { onShowProfiles?.invoke() }
        row1.addView(btnProfiles, LinearLayout.LayoutParams(0, dp(48), 1f))

        val row2 = row()
        btnCompile = btn("Ускорить запуск") { onCompile?.invoke(pkgInput.text.toString().trim()) }
        row2.addView(btnCompile, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        btnKeep = btn("Держать в памяти") { onKeep?.invoke(pkgInput.text.toString().trim()) }
        row2.addView(btnKeep, LinearLayout.LayoutParams(0, dp(48), 1f))

        val row3 = row()
        btnCache = btn("Очистить кэш") { onTrimCaches?.invoke() }
        row3.addView(btnCache, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        btnPing = btn("Проверить пинг") { onPing?.invoke() }
        row3.addView(btnPing, LinearLayout.LayoutParams(0, dp(48), 1f))

        btnDevOptions = btn("Настройки для разработчиков") { onDevOptions?.invoke() }
        content.addView(btnDevOptions, LinearLayout.LayoutParams(MATCH_PARENT, dp(48)).apply { topMargin = dp(8) })

        statusResult = card("Нажмите кнопку")
        content.addView(statusResult, cardLp())

        content.addView(View(ctx), LinearLayout.LayoutParams(MATCH_PARENT, dp(30)))

        val bottom = row()
        btnTurbo = TextView(ctx).apply {
            text = "TURBO"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            background = tBlueBg()
            setOnClickListener { animate(this); onTurboToggle?.invoke() }
        }
        bottom.addView(btnTurbo, LinearLayout.LayoutParams(0, dp(72), 0.3f).apply { marginEnd = dp(8) })

        btnOpt = TextView(ctx).apply {
            text = "ОПТИМИЗИРОВАТЬ"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            background = blueBg()
            setOnClickListener { animate(this); onToggle?.invoke() }
        }
        bottom.addView(btnOpt, LinearLayout.LayoutParams(0, dp(72), 0.7f))

        return root
    }

    fun setMode(mode: Int) {
        currentMode = mode
        btnModePerf.background = if (mode == 0) blueBg() else unselBg()
        btnModeBal.background = if (mode == 1) blueBg() else unselBg()
    }

    fun setPreset(p: Int) { currentPreset = p; updatePreset() }
    fun setHz(hz: Int) { currentHz = hz; updateHz() }
    fun setPackageText(t: String) { if (t.isNotEmpty()) pkgInput.setText(t) }
    fun setSenseLevel(l: Int) { currentSense = l; updateSense() }
    fun setThermalLevel(l: Int) { currentThermal = l; updateThermal() }

    fun setActive(a: Boolean) {
        btnOpt.text = if (a) "ВЕРНУТЬ АВТО" else "ОПТИМИЗИРОВАТЬ"
        btnOpt.background = if (a) redBg() else blueBg()
    }

    fun setTurboState(a: Boolean) { btnTurbo.background = if (a) tGreenBg() else tBlueBg() }
    fun setGpuBoostState(a: Boolean) { btnGpuBoost.background = if (a) blueBg() else unselBg() }
    fun setBgLimitState(a: Boolean) { btnBgLimit.background = if (a) blueBg() else unselBg() }

    fun setAppliedState(active: Boolean, gov: Boolean, dns: Boolean, bbr: Boolean) {
        if (!active) { cardApplied.text = "Применено: —"; return }
        var n = 0; if (gov) n++; if (dns) n++; if (bbr) n++
        val p = mutableListOf<String>()
        p.add(if (gov) "Governor ✓" else "Governor ✗")
        p.add(if (dns) "DNS ✓" else "DNS ✗")
        p.add(if (bbr) "BBR ✓" else "BBR ✗")
        cardApplied.text = "Применено: $n/3 | ${p.joinToString(" | ")}"
    }

    fun setBatteryLowBlock(b: Boolean) { btnOpt.alpha = if (b) 0.4f else 1f; btnTurbo.alpha = if (b) 0.4f else 1f }

    private fun updatePreset() {
        for (i in 0 until presetRow.childCount) {
            (presetRow.getChildAt(i) as TextView).background = if (i == currentPreset) blueBg() else unselBg()
        }
    }

    private fun updateHz() {
        val map = listOf(60, 90, 120, 144)
        for (i in 0 until hzRow.childCount) {
            (hzRow.getChildAt(i) as TextView).background = if (map[i] == currentHz) blueBg() else unselBg()
        }
    }

    private fun updateSense() {
        for (i in 0 until senseRow.childCount) {
            (senseRow.getChildAt(i) as TextView).background = if (i == currentSense) blueBg() else unselBg()
        }
    }

    private fun updateThermal() {
        for (i in 0 until thermalRow.childCount) {
            (thermalRow.getChildAt(i) as TextView).background = if (i == currentThermal) blueBg() else unselBg()
        }
    }

    private fun animate(v: View) {
        v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(60).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(OvershootInterpolator()).start()
        }.start()
    }

    private fun btn(text: String, click: () -> Unit): TextView {
        val tv = TextView(ctx)
        tv.text = text
        tv.setTextColor(Color.WHITE)
        tv.textSize = 12f
        tv.gravity = Gravity.CENTER
        tv.background = unselBg()
        tv.setOnClickListener { animate(tv); click() }
        return tv
    }

    private fun card(text: String): TextView {
        val tv = TextView(ctx)
        tv.text = text
        tv.setTextColor(Color.WHITE)
        tv.textSize = 14f
        tv.gravity = Gravity.CENTER_VERTICAL
        tv.setPadding(dp(18), dp(14), dp(18), dp(14))
        tv.background = cardCopy()
        return tv
    }

    private fun label(text: String, size: Float, grav: Int): TextView {
        val tv = TextView(ctx)
        tv.text = text
        tv.setTextColor(Color.WHITE)
        tv.textSize = size
        tv.gravity = grav
        return tv
    }

    private fun row(): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        content.addView(r, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
        return r
    }

    private fun cardLp(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
    }

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    private fun cardBg(): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = dp(22).toFloat()
        g.colors = intArrayOf(Color.parseColor("#66FFFFFF"), Color.parseColor("#33FFFFFF"), Color.parseColor("#22FFFFFF"))
        g.gradientType = GradientDrawable.LINEAR_GRADIENT
        g.orientation = GradientDrawable.Orientation.TL_BR
        g.setStroke(dp(1), Color.parseColor("#88FFFFFF"))
        return g
    }

    private fun buttonBlue() = btnBase(intArrayOf(Color.parseColor("#5B8DEF"), Color.parseColor("#7BA7F5"), Color.parseColor("#5B8DEF")), "#CCFFFFFF")
    private fun buttonRed() = btnBase(intArrayOf(Color.parseColor("#EF4444"), Color.parseColor("#F87171"), Color.parseColor("#EF4444")), "#CCFFFFFF")
    private fun buttonUnselected() = btnBase(intArrayOf(Color.parseColor("#33FFFFFF"), Color.parseColor("#22FFFFFF"), Color.parseColor("#33FFFFFF")), "#55FFFFFF")
    private fun buttonTurboBlue() = btnBase(intArrayOf(Color.parseColor("#3B82F6"), Color.parseColor("#60A5FA"), Color.parseColor("#3B82F6")), "#CCFFFFFF")
    private fun buttonTurboGreen() = btnBase(intArrayOf(Color.parseColor("#22C55E"), Color.parseColor("#4ADE80"), Color.parseColor("#22C55E")), "#CCFFFFFF")

    private fun btnBase(colors: IntArray, stroke: String): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = dp(18).toFloat()
        g.colors = colors
        g.gradientType = GradientDrawable.LINEAR_GRADIENT
        g.orientation = GradientDrawable.Orientation.LEFT_RIGHT
        g.setStroke(dp(1), Color.parseColor(stroke))
        return g
    }
}
