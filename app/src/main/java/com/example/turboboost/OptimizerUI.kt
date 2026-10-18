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
    lateinit var content: LinearLayout
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

    private val blueBg by lazy { buttonBlue() }
    private val redBg by lazy { buttonRed() }
    private val unselBg by lazy { buttonUnselected() }
    private val turboBlueBg by lazy { buttonTurboBlue() }
    private val turboGreenBg by lazy { buttonTurboGreen() }
    private val cardGrad by lazy { cardBg() }

    fun build(): FrameLayout {
        root = FrameLayout(ctx)
        root.setBackgroundColor(Color.parseColor("#0B0F1A"))

        val scroll = ScrollView(ctx)
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
        cardScreen = card("Экран: —")
        content.addView(cardScreen, cardLp())

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
        pkgLabel
