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
    lateinit var statusResult: TextView
    lateinit var senseRow: LinearLayout
    lateinit var thermalRow: LinearLayout

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

    var currentSense: Int = -1
    var currentThermal: Int = -1
    var currentMode: Int = 0

    private val blueBg by lazy { buttonBlue() }
    private val redBg by lazy { buttonRed() }
    private val unselBg by lazy { buttonUnselected() }
    private val turboBlueBg by lazy { buttonTurboBlue() }
    private val turboGreenBg by lazy { buttonTurboGreen() }
    private val cardGrad by lazy { cardBg() }

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

        val title = TextView(ctx)
        title.text = "TurboBoost"
        title.setTextColor(Color.WHITE)
        title.textSize = 30f
        title.gravity = Gravity.CENTER
        content.addView(title, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val sub = TextView(ctx)
        sub.text = "Оптимизация для игр"
        sub.setTextColor(Color.parseColor("#AAFFFFFF"))
        sub.textSize = 13f
        sub.gravity = Gravity.CENTER
        val subLp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        subLp.topMargin = dp(2)
        content.addView(sub, subLp)

        val modeRow = LinearLayout(ctx)
        modeRow.orientation = LinearLayout.HORIZONTAL
        content.addView(modeRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) })

        btnModePerf = smallButton("Производительность")
        btnModePerf.setOnClickListener { animateButton(btnModePerf); onModeSelected?.invoke(0); setMode(0) }
        modeRow.addView(btnModePerf, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(6) })

        btnModeBal = smallButton("Сбалансированный")
        btnModeBal.setOnClickListener { animateButton(btnModeBal); onModeSelected?.invoke(1); setMode(1) }
        modeRow.addView(btnModeBal, LinearLayout.LayoutParams(0, dp(40), 1f))

        cardChip = card("Чип: —")
        content.addView(cardChip, cardLp())
        cardGpu = card("GPU: —")
        content.addView(cardGpu, cardLp())
        cardScreen = card("Частота: —")
        content.addView(cardScreen, cardLp())
        cardBattery = card("Батарея: —")
        content.addView(cardBattery, cardLp())
        cardTemp = card("Температура: —")
        content.addView(cardTemp, cardLp())
        cardFps = card("FPS: —")
        content.addView(cardFps, cardLp())
        cardPing = card("Пинг: —")
        content.addView(cardPing, cardLp())

        val extraRow = LinearLayout(ctx)
        extraRow.orientation = LinearLayout.HORIZONTAL
        content.addView(extraRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })

        btnGpuBoost = smallButton("GPU Boost")
        btnGpuBoost.setOnClickListener { animateButton(btnGpuBoost); onGpuBoostToggle?.invoke() }
        extraRow.addView(btnGpuBoost, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })

        btnBgLimit = smallButton("Лимит фона")
        btnBgLimit.setOnClickListener { animateButton(btnBgLimit); onBgLimitToggle?.invoke() }
        extraRow.addView(btnBgLimit, LinearLayout.LayoutParams(0, dp(44), 1f))

        val senseLabel = TextView(ctx)
        senseLabel.text = "Сенса:"
        senseLabel.setTextColor(Color.WHITE)
        senseLabel.textSize = 14f
        senseLabel.setPadding(dp(8), dp(12), 0, dp(4))
        content.addView(senseLabel)

        senseRow = LinearLayout(ctx)
        senseRow.orientation = LinearLayout.HORIZONTAL
        senseRow.gravity = Gravity.CENTER_VERTICAL
        content.addView(senseRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val senses = listOf("Средний", "Сильный", "Эксп.", "Макс")
        for ((i, name) in senses.withIndex()) {
            val b = smallButton(name)
            b.setOnClickListener { animateButton(b); currentSense = i; onSenseSelected?.invoke(i); updateSenseSelection() }
            val lp = LinearLayout.LayoutParams(0, dp(44), 1f); lp.marginEnd = dp(6); senseRow.addView(b, lp)
        }

        val thermalLabel = TextView(ctx)
        thermalLabel.text = "Троттлинг (°C):"
        thermalLabel.setTextColor(Color.WHITE)
        thermalLabel.textSize = 14f
        thermalLabel.setPadding(dp(8), dp(12), 0, dp(4))
        content.addView(thermalLabel)

        thermalRow = LinearLayout(ctx)
        thermalRow.orientation = LinearLayout.HORIZONTAL
        thermalRow.gravity = Gravity.CENTER_VERTICAL
        content.addView(thermalRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val thermals = listOf("Авто", "50", "60", "70")
        for ((i, name) in thermals.withIndex()) {
            val b = smallButton(name)
            b.setOnClickListener { animateButton(b); currentThermal = i; onThermalSelected?.invoke(i); updateThermalSelection() }
            val lp = LinearLayout.LayoutParams(0, dp(44), 1f); lp.marginEnd = dp(6); thermalRow.addView(b, lp)
        }

        val pkgLabel = TextView(ctx)
        pkgLabel.text = "Пакет игры:"
        pkgLabel.setTextColor(Color.WHITE)
        pkgLabel.textSize = 13f
        pkgLabel.setPadding(dp(8), dp(12), 0, dp(4))
        content.addView(pkgLabel)

        pkgInput = EditText(ctx)
        pkgInput.hint = "com.axlebolt.standoff2"
        pkgInput.setHintTextColor(Color.parseColor("#66FFFFFF"))
        pkgInput.setTextColor(Color.WHITE)
        pkgInput.textSize = 14f
        pkgInput.setSingleLine()
        pkgInput.inputType = InputType.TYPE_CLASS_TEXT
        pkgInput.setPadding(dp(14), dp(10), dp(14), dp(10))
        pkgInput.background = buttonUnselected()
        content.addView(pkgInput, LinearLayout.LayoutParams(MATCH_PARENT, dp(48)).apply { topMargin = dp(4) })

        val actionRow1 = LinearLayout(ctx)
        actionRow1.orientation = LinearLayout.HORIZONTAL
        content.addView(actionRow1, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })

        btnGames = smallButton("Выбрать игру")
        btnGames.setOnClickListener { animateButton(btnGames); onShowGames?.invoke() }
        actionRow1.addView(btnGames, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })

        btnProfiles = smallButton("Профили")
        btnProfiles.setOnClickListener { animateButton(btnProfiles); onShowProfiles?.invoke() }
        actionRow1.addView(btnProfiles, LinearLayout.LayoutParams(0, dp(48), 1f))

        val actionRow2 = LinearLayout(ctx)
        actionRow2.orientation = LinearLayout.HORIZONTAL
        content.addView(actionRow2, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })

        btnCompile = smallButton("Ускорить запуск")
        btnCompile.setOnClickListener { animateButton(btnCompile); onCompile?.invoke(pkgInput.text.toString().trim()) }
        actionRow2.addView(btnCompile, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })

        btnKeep = smallButton("Держать в памяти")
        btnKeep.setOnClickListener { animateButton(btnKeep); onKeep?.invoke(pkgInput.text.toString().trim()) }
        actionRow2.addView(btnKeep, LinearLayout.LayoutParams(0, dp(48), 1f))

        val actionRow3 = LinearLayout(ctx)
        actionRow3.orientation = LinearLayout.HORIZONTAL
        content.addView(actionRow3, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })

        btnCache = smallButton("Очистить кэш")
        btnCache.setOnClickListener { animateButton(btnCache); onTrimCaches?.invoke() }
        actionRow3.addView(btnCache, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })

        btnPing = smallButton("Проверить пинг")
        btnPing.setOnClickListener { animateButton(btnPing); onPing?.invoke() }
        actionRow3.addView(btnPing, LinearLayout.LayoutParams(0, dp(48), 1f))

        btnDevOptions = smallButton("Настройки для разработчиков")
        btnDevOptions.setOnClickListener { animateButton(btnDevOptions); onDevOptions?.invoke() }
        content.addView(btnDevOptions, LinearLayout.LayoutParams(MATCH_PARENT, dp(48)).apply { topMargin = dp(8) })

        statusResult = card("Нажмите кнопку")
        content.addView(statusResult, cardLp())

        val spacer = View(ctx)
        content.addView(spacer, LinearLayout.LayoutParams(MATCH_PARENT, dp(30)))

        val bottomRow = LinearLayout(ctx)
        bottomRow.orientation = LinearLayout.HORIZONTAL
        bottomRow.gravity = Gravity.CENTER_VERTICAL
        content.addView(bottomRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        btnTurbo = TextView(ctx)
        btnTurbo.text = "TURBO"
        btnTurbo.setTextColor(Color.WHITE)
        btnTurbo.textSize = 14f
        btnTurbo.gravity = Gravity.CENTER
        btnTurbo.background = buttonTurboBlue()
        btnTurbo.setOnClickListener { animateButton(btnTurbo); onTurboToggle?.invoke() }
        bottomRow.addView(btnTurbo, LinearLayout.LayoutParams(0, dp(72), 0.3f).apply { marginEnd = dp(8) })

        btnOpt = TextView(ctx)
        btnOpt.text = "ОПТИМИЗИРОВАТЬ"
        btnOpt.setTextColor(Color.WHITE)
        btnOpt.textSize = 18f
        btnOpt.gravity = Gravity.CENTER
        btnOpt.background = buttonBlue()
        btnOpt.setOnClickListener { animateButton(btnOpt); onToggle?.invoke() }
        bottomRow.addView(btnOpt, LinearLayout.LayoutParams(0, dp(72), 0.7f))

        return root
    }

    fun setMode(mode: Int) {
        currentMode = mode
        if (mode == 0) {
            btnModePerf.background = blueBg
            btnModeBal.background = unselBg
        } else {
            btnModePerf.background = unselBg
            btnModeBal.background = blueBg
        }
    }

    fun setPackageText(text: String) { if (text.isNotEmpty()) pkgInput.setText(text) }
    fun setSenseLevel(level: Int) { currentSense = level; updateSenseSelection() }
    fun setThermalLevel(level: Int) { currentThermal = level; updateThermalSelection() }

    fun setActive(active: Boolean) {
        btnOpt.text = if (active) "ВЕРНУТЬ АВТО" else "ОПТИМИЗИРОВАТЬ"
        btnOpt.background = if (active) redBg else blueBg
    }

    fun setTurboState(active: Boolean) {
        btnTurbo.background = if (active) turboGreenBg else turboBlueBg
    }

    fun setGpuBoostState(active: Boolean) {
        btnGpuBoost.background = if (active) blueBg else unselBg
    }

    fun setBgLimitState(active: Boolean) {
        btnBgLimit.background = if (active) blueBg else unselBg
    }

    fun setBatteryLowBlock(block: Boolean) { btnOpt.alpha = if (block) 0.4f else 1f; btnTurbo.alpha = if (block) 0.4f else 1f }

    private fun updateSenseSelection() {
        for (i in 0 until senseRow.childCount) {
            val v = senseRow.getChildAt(i) as TextView
            v.background = if (i == currentSense) blueBg else unselBg
        }
    }

    private fun updateThermalSelection() {
        for (i in 0 until thermalRow.childCount) {
            val v = thermalRow.getChildAt(i) as TextView
            v.background = if (i == currentThermal) blueBg else unselBg
        }
    }

    private fun animateButton(v: View) {
        v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(60).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(OvershootInterpolator()).start()
        }.start()
    }

    private fun smallButton(text: String): TextView {
        val tv = TextView(ctx)
        tv.text = text
        tv.setTextColor(Color.WHITE)
        tv.textSize = 12f
        tv.gravity = Gravity.CENTER
        tv.background = buttonUnselected()
        return tv
    }

    private fun card(text: String): TextView {
        val tv = TextView(ctx)
        tv.text = text
        tv.setTextColor(Color.WHITE)
        tv.textSize = 14f
        tv.gravity = Gravity.CENTER_VERTICAL
        tv.setPadding(dp(18), dp(14), dp(18), dp(14))
        tv.background = cardBg()
        return tv
    }

    private fun cardLp(): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        lp.topMargin = dp(8)
        return lp
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

    private fun buttonBlue(): GradientDrawable = btnBase(intArrayOf(Color.parseColor("#5B8DEF"), Color.parseColor("#7BA7F5"), Color.parseColor("#5B8DEF")), "#CCFFFFFF")
    private fun buttonRed(): GradientDrawable = btnBase(intArrayOf(Color.parseColor("#EF4444"), Color.parseColor("#F87171"), Color.parseColor("#EF4444")), "#CCFFFFFF")
    private fun buttonUnselected(): GradientDrawable = btnBase(intArrayOf(Color.parseColor("#33FFFFFF"), Color.parseColor("#22FFFFFF"), Color.parseColor("#33FFFFFF")), "#55FFFFFF")
    private fun buttonTurboBlue(): GradientDrawable = btnBase(intArrayOf(Color.parseColor("#3B82F6"), Color.parseColor("#60A5FA"), Color.parseColor("#3B82F6")), "#CCFFFFFF")
    private fun buttonTurboGreen(): GradientDrawable = btnBase(intArrayOf(Color.parseColor("#22C55E"), Color.parseColor("#4ADE80"), Color.parseColor("#22C55E")), "#CCFFFFFF")

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
