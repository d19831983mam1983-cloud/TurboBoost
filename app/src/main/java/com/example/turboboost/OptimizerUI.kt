package com.example.turboboost

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class OptimizerUI(private val ctx: Context) {

    lateinit var root: LinearLayout
    lateinit var btnOpt: TextView
    lateinit var btnTurbo: TextView
    lateinit var btnDevOptions: TextView
    lateinit var btnCompile: TextView
    lateinit var btnKeep: TextView
    lateinit var btnModePerf: TextView
    lateinit var btnModeBal: TextView
    lateinit var pkgInput: EditText
    lateinit var cardChip: TextView
    lateinit var cardScreen: TextView
    lateinit var cardBattery: TextView
    lateinit var cardTemp: TextView
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
    
    var currentSense: Int = -1
    var currentThermal: Int = -1
    var currentMode: Int = 0

    fun build(): LinearLayout {
        root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#0B0F1A"))
        root.setPadding(dp(18), dp(44), dp(18), dp(20))

        val title = TextView(ctx)
        title.text = "TurboBoost"
        title.setTextColor(Color.WHITE)
        title.textSize = 30f
        title.gravity = Gravity.CENTER
        root.addView(title, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val sub = TextView(ctx)
        sub.text = "Оптимизация для игр"
        sub.setTextColor(Color.parseColor("#AAFFFFFF"))
        sub.textSize = 13f
        sub.gravity = Gravity.CENTER
        val subLp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        subLp.topMargin = dp(2)
        root.addView(sub, subLp)

        val modeRow = LinearLayout(ctx)
        modeRow.orientation = LinearLayout.HORIZONTAL
        root.addView(modeRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) })

        btnModePerf = smallButton("Производительность")
        btnModePerf.setOnClickListener { onModeSelected?.invoke(0); setMode(0) }
        modeRow.addView(btnModePerf, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(6) })

        btnModeBal = smallButton("Сбалансированный")
        btnModeBal.setOnClickListener { onModeSelected?.invoke(1); setMode(1) }
        modeRow.addView(btnModeBal, LinearLayout.LayoutParams(0, dp(40), 1f))

        cardChip = card("Чип: —")
        root.addView(cardChip, cardLp())
        cardScreen = card("Экран: —")
        root.addView(cardScreen, cardLp())

        val senseLabel = TextView(ctx)
        senseLabel.text = "Сенса:"
        senseLabel.setTextColor(Color.WHITE)
        senseLabel.textSize = 14f
        senseLabel.setPadding(dp(8), dp(12), 0, dp(4))
        root.addView(senseLabel)

        senseRow = LinearLayout(ctx)
        senseRow.orientation = LinearLayout.HORIZONTAL
        senseRow.gravity = Gravity.CENTER_VERTICAL
        root.addView(senseRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val senses = listOf("Средний", "Сильный", "Эксп.", "Макс")
        for ((i, name) in senses.withIndex()) {
            val b = smallButton(name)
            b.setOnClickListener { currentSense = i; onSenseSelected?.invoke(i); updateSenseSelection() }
            val lp = LinearLayout.LayoutParams(0, dp(44), 1f); lp.marginEnd = dp(6); senseRow.addView(b, lp)
        }

        val thermalLabel = TextView(ctx)
        thermalLabel.text = "Троттлинг (°C):"
        thermalLabel.setTextColor(Color.WHITE)
        thermalLabel.textSize = 14f
        thermalLabel.setPadding(dp(8), dp(12), 0, dp(4))
        root.addView(thermalLabel)

        thermalRow = LinearLayout(ctx)
        thermalRow.orientation = LinearLayout.HORIZONTAL
        thermalRow.gravity = Gravity.CENTER_VERTICAL
        root.addView(thermalRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val thermals = listOf("Авто", "50", "60", "70")
        for ((i, name) in thermals.withIndex()) {
            val b = smallButton(name)
            b.setOnClickListener { currentThermal = i; onThermalSelected?.invoke(i); updateThermalSelection() }
            val lp = LinearLayout.LayoutParams(0, dp(44), 1f); lp.marginEnd = dp(6); thermalRow.addView(b, lp)
        }

        val pkgLabel = TextView(ctx)
        pkgLabel.text = "Пакет игры:"
        pkgLabel.setTextColor(Color.WHITE)
        pkgLabel.textSize = 13f
        pkgLabel.setPadding(dp(8), dp(12), 0, dp(4))
        root.addView(pkgLabel)

        pkgInput = EditText(ctx)
        pkgInput.hint = "com.axlebolt.standoff2"
        pkgInput.setHintTextColor(Color.parseColor("#66FFFFFF"))
        pkgInput.setTextColor(Color.WHITE)
        pkgInput.textSize = 14f
        pkgInput.setSingleLine()
        pkgInput.inputType = InputType.TYPE_CLASS_TEXT
        pkgInput.setPadding(dp(14), dp(10), dp(14), dp(10))
        pkgInput.background = buttonUnselected()
        root.addView(pkgInput, LinearLayout.LayoutParams(MATCH_PARENT, dp(48)).apply { topMargin = dp(4) })

        val actionRow = LinearLayout(ctx)
        actionRow.orientation = LinearLayout.HORIZONTAL
        root.addView(actionRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })

        btnCompile = smallButton("Ускорить запуск")
        btnCompile.setOnClickListener { onCompile?.invoke(pkgInput.text.toString().trim()) }
        actionRow.addView(btnCompile, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })

        btnKeep = smallButton("Держать в памяти")
        btnKeep.setOnClickListener { onKeep?.invoke(pkgInput.text.toString().trim()) }
        actionRow.addView(btnKeep, LinearLayout.LayoutParams(0, dp(48), 1f))

        btnDevOptions = smallButton("Настройки для разработчиков")
        btnDevOptions.setOnClickListener { onDevOptions?.invoke() }
        root.addView(btnDevOptions, LinearLayout.LayoutParams(MATCH_PARENT, dp(48)).apply { topMargin = dp(8) })

        cardBattery = card("Батарея: —")
        root.addView(cardBattery, cardLp())
        cardTemp = card("Температура: —")
        root.addView(cardTemp, cardLp())
        statusResult = card("Нажмите кнопку")
        root.addView(statusResult, cardLp())

        val spacer = View(ctx)
        root.addView(spacer, LinearLayout.LayoutParams(MATCH_PARENT, dp(30)))

        val bottomRow = LinearLayout(ctx)
        bottomRow.orientation = LinearLayout.HORIZONTAL
        bottomRow.gravity = Gravity.CENTER_VERTICAL
        root.addView(bottomRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

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
            btnModePerf.background = buttonBlue()
            btnModeBal.background = buttonUnselected()
        } else {
            btnModePerf.background = buttonUnselected()
            btnModeBal.background = buttonBlue()
        }
    }

    fun setPackageText(text: String) { if (text.isNotEmpty()) pkgInput.setText(text) }
    fun setSenseLevel(level: Int) { currentSense = level; updateSenseSelection() }
    fun setThermalLevel(level: Int) { currentThermal = level; updateThermalSelection() }

    fun setActive(active: Boolean) {
        btnOpt.text = if (active) "ВЕРНУТЬ АВТО" else "ОПТИМИЗИРОВАТЬ"
        btnOpt.background = if (active) buttonRed() else buttonBlue()
    }

    fun setTurboState(active: Boolean) {
        btnTurbo.background = if (active) buttonTurboGreen() else buttonTurboBlue()
    }

    fun setBatteryLowBlock(block: Boolean) { btnOpt.alpha = if (block) 0.4f else 1f; btnTurbo.alpha = if (block) 0.4f else 1f }

    private fun updateSenseSelection() {
        for (i in 0 until senseRow.childCount) {
            val v = senseRow.getChildAt(i) as TextView
            v.background = if (i == currentSense) buttonBlue() else buttonUnselected()
        }
    }

    private fun updateThermalSelection() {
        for (i in 0 until thermalRow.childCount) {
            val v = thermalRow.getChildAt(i) as TextView
            v.background = if (i == currentThermal) buttonBlue() else buttonUnselected()
        }
    }

    private fun animateButton(v: View) {
        v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(60).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(250).setInterpolator(OvershootInterpolator()).start()
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

    private fun cardBg(): LayerDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = dp(22).toFloat()
        g.colors = intArrayOf(Color.parseColor("#66FFFFFF"), Color.parseColor("#33FFFFFF"), Color.parseColor("#22FFFFFF"))
        g.gradientType = GradientDrawable.LINEAR_GRADIENT
        g.orientation = GradientDrawable.Orientation.TL_BR
        g.setStroke(dp(1), Color.parseColor("#88FFFFFF"))
        return LayerDrawable(arrayOf(g))
    }

    private fun buttonBlue(): LayerDrawable = btnBase(intArrayOf(Color.parseColor("#5B8DEF"), Color.parseColor("#7BA7F5"), Color.parseColor("#5B8DEF")), "#CCFFFFFF")
    private fun buttonRed(): LayerDrawable = btnBase(intArrayOf(Color.parseColor("#EF4444"), Color.parseColor("#F87171"), Color.parseColor("#EF4444")), "#CCFFFFFF")
    private fun buttonUnselected(): LayerDrawable = btnBase(intArrayOf(Color.parseColor("#33FFFFFF"), Color.parseColor("#22FFFFFF"), Color.parseColor("#33FFFFFF")), "#55FFFFFF")
    private fun buttonTurboBlue(): LayerDrawable = btnBase(intArrayOf(Color.parseColor("#3B82F6"), Color.parseColor("#60A5FA"), Color.parseColor("#3B82F6")), "#CCFFFFFF")
    private fun buttonTurboGreen(): LayerDrawable = btnBase(intArrayOf(Color.parseColor("#22C55E"), Color.parseColor("#4ADE80"), Color.parseColor("#22C55E")), "#CCFFFFFF")

    private fun btnBase(colors: IntArray, stroke: String): LayerDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = dp(18).toFloat()
        g.colors = colors
        g.gradientType = GradientDrawable.LINEAR_GRADIENT
        g.orientation = GradientDrawable.Orientation.LEFT_RIGHT
        g.setStroke(dp(1), Color.parseColor(stroke))
        return LayerDrawable(arrayOf(g))
    }
}
