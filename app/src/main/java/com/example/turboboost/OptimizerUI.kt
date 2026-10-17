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
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class OptimizerUI(private val ctx: Context) {

    lateinit var root: FrameLayout
    lateinit var mainScroll: ScrollView
    lateinit var secretScreen: ScrollView
    lateinit var content: LinearLayout
    lateinit var btnToggle: TextView
    lateinit var btnDevOptions: TextView
    lateinit var btnCompile: TextView
    lateinit var btnKeep: TextView
    lateinit var pkgInput: EditText
    lateinit var cardChip: TextView
    lateinit var cardScreen: TextView
    lateinit var cardBattery: TextView
    lateinit var cardTemp: TextView
    lateinit var statusResult: TextView
    lateinit var hzRow: LinearLayout
    lateinit var senseRow: LinearLayout
    lateinit var thermalRow: LinearLayout
    lateinit var secretLabel: TextView

    var onToggle: (() -> Unit)? = null
    var onDevOptions: (() -> Unit)? = null
    var onCompile: ((String) -> Unit)? = null
    var onKeep: ((String) -> Unit)? = null
    var onHzSelected: ((Int) -> Unit)? = null
    var onSenseSelected: ((Int) -> Unit)? = null
    var onThermalSelected: ((Int) -> Unit)? = null
    var onUnlock90: (() -> Unit)? = null
    var currentHz: Int = 120
    var currentSense: Int = -1
    var currentThermal: Int = -1

    private var tapCount = 0
    private var lastTapTime = 0L

    fun build(): FrameLayout {
        root = FrameLayout(ctx)
        root.setBackgroundColor(Color.parseColor("#0B0F1A"))

        mainScroll = ScrollView(ctx)
        mainScroll.isFillViewport = true
        root.addView(mainScroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val mainContent = LinearLayout(ctx)
        mainContent.orientation = LinearLayout.VERTICAL
        mainContent.setPadding(dp(18), dp(20), dp(18), dp(20))
        mainScroll.addView(mainContent, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        secretLabel = TextView(ctx)
        secretLabel.text = "t.me/FIXSENSZNAK"
        secretLabel.setTextColor(Color.parseColor("#33FFFFFF"))
        secretLabel.textSize = 10f
        secretLabel.gravity = Gravity.CENTER
        secretLabel.setPadding(0, dp(10), 0, dp(10))
        secretLabel.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastTapTime > 500) tapCount = 0
            lastTapTime = now
            tapCount++
            if (tapCount >= 5) {
                tapCount = 0
                showSecretScreen()
            }
        }
        mainContent.addView(secretLabel, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val title = TextView(ctx)
        title.text = "TurboBoost"
        title.setTextColor(Color.WHITE)
        title.textSize = 30f
        title.gravity = Gravity.CENTER
        mainContent.addView(title, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val sub = TextView(ctx)
        sub.text = "Максимальный отклик"
        sub.setTextColor(Color.parseColor("#AAFFFFFF"))
        sub.textSize = 13f
        sub.gravity = Gravity.CENTER
        val subLp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        subLp.topMargin = dp(2)
        mainContent.addView(sub, subLp)

        cardChip = card("Чип: —")
        mainContent.addView(cardChip, cardLp())
        cardScreen = card("Экран: —")
        mainContent.addView(cardScreen, cardLp())

        val hzLabel = TextView(ctx)
        hzLabel.text = "Частота экрана (Гц):"
        hzLabel.setTextColor(Color.WHITE)
        hzLabel.textSize = 14f
        hzLabel.setPadding(dp(8), dp(12), 0, dp(4))
        mainContent.addView(hzLabel)

        hzRow = LinearLayout(ctx)
        hzRow.orientation = LinearLayout.HORIZONTAL
        hzRow.gravity = Gravity.CENTER_VERTICAL
        mainContent.addView(hzRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val rates = listOf(60, 90, 120, 144)
        for (r in rates) {
            val b = hzButton("$r")
            b.setOnClickListener {
                currentHz = r
                onHzSelected?.invoke(r)
                updateHzSelection()
            }
            val lp = LinearLayout.LayoutParams(0, dp(44), 1f)
            lp.marginEnd = dp(6)
            hzRow.addView(b, lp)
        }

        val senseLabel = TextView(ctx)
        senseLabel.text = "Сенса (чувствительность):"
        senseLabel.setTextColor(Color.WHITE)
        senseLabel.textSize = 14f
        senseLabel.setPadding(dp(8), dp(12), 0, dp(4))
        mainContent.addView(senseLabel)

        senseRow = LinearLayout(ctx)
        senseRow.orientation = LinearLayout.HORIZONTAL
        senseRow.gravity = Gravity.CENTER_VERTICAL
        mainContent.addView(senseRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val senses = listOf("Средний", "Сильный", "Эксп.", "Макс")
        for ((i, name) in senses.withIndex()) {
            val b = smallButton(name)
            b.setOnClickListener {
                currentSense = i
                onSenseSelected?.invoke(i)
                updateSenseSelection()
            }
            val lp = LinearLayout.LayoutParams(0, dp(44), 1f)
            lp.marginEnd = dp(6)
            senseRow.addView(b, lp)
        }

        val thermalLabel = TextView(ctx)
        thermalLabel.text = "Троттлинг (порог °C):"
        thermalLabel.setTextColor(Color.WHITE)
        thermalLabel.textSize = 14f
        thermalLabel.setPadding(dp(8), dp(12), 0, dp(4))
        mainContent.addView(thermalLabel)

        thermalRow = LinearLayout(ctx)
        thermalRow.orientation = LinearLayout.HORIZONTAL
        thermalRow.gravity = Gravity.CENTER_VERTICAL
        mainContent.addView(thermalRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val thermals = listOf("Авто", "50", "60", "70")
        for ((i, name) in thermals.withIndex()) {
            val b = smallButton(name)
            b.setOnClickListener {
                currentThermal = i
                onThermalSelected?.invoke(i)
                updateThermalSelection()
            }
            val lp = LinearLayout.LayoutParams(0, dp(44), 1f)
            lp.marginEnd = dp(6)
            thermalRow.addView(b, lp)
        }

        val pkgLabel = TextView(ctx)
        pkgLabel.text = "Пакет игры:"
        pkgLabel.setTextColor(Color.WHITE)
        pkgLabel.textSize = 13f
        pkgLabel.setPadding(dp(8), dp(12), 0, dp(4))
        mainContent.addView(pkgLabel)

        pkgInput = EditText(ctx)
        pkgInput.hint = "com.example.game"
        pkgInput.setHintTextColor(Color.parseColor("#66FFFFFF"))
        pkgInput.setTextColor(Color.WHITE)
        pkgInput.textSize = 14f
        pkgInput.setSingleLine()
        pkgInput.inputType = InputType.TYPE_CLASS_TEXT
        pkgInput.setPadding(dp(14), dp(10), dp(14), dp(10))
        pkgInput.background = buttonUnselected()
        mainContent.addView(pkgInput, LinearLayout.LayoutParams(MATCH_PARENT, dp(48)).apply {
            topMargin = dp(4)
        })

        val actionRow = LinearLayout(ctx)
        actionRow.orientation = LinearLayout.HORIZONTAL
        mainContent.addView(actionRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            topMargin = dp(8)
        })

        btnCompile = smallButton("Ускорить запуск")
        btnCompile.setOnClickListener { onCompile?.invoke(pkgInput.text.toString().trim()) }
        actionRow.addView(btnCompile, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })

        btnKeep = smallButton("Держать в памяти")
        btnKeep.setOnClickListener { onKeep?.invoke(pkgInput.text.toString().trim()) }
        actionRow.addView(btnKeep, LinearLayout.LayoutParams(0, dp(48), 1f))

        btnDevOptions = smallButton("Настройки для разработчиков")
        btnDevOptions.setOnClickListener { onDevOptions?.invoke() }
        mainContent.addView(btnDevOptions, LinearLayout.LayoutParams(MATCH_PARENT, dp(48)).apply { topMargin = dp(8) })

        cardBattery = card("Батарея: —")
        mainContent.addView(cardBattery, cardLp())
        cardTemp = card("Температура: —")
        mainContent.addView(cardTemp, cardLp())
        statusResult = card("Нажмите кнопку")
        mainContent.addView(statusResult, cardLp())

        val spacer = View(ctx)
        mainContent.addView(spacer, LinearLayout.LayoutParams(MATCH_PARENT, dp(30)))

        btnToggle = TextView(ctx)
        btnToggle.text = "ОПТИМИЗИРОВАТЬ"
        btnToggle.setTextColor(Color.WHITE)
        btnToggle.textSize = 20f
        btnToggle.gravity = Gravity.CENTER
        btnToggle.background = buttonBlue()
        btnToggle.elevation = dp(12).toFloat()
        btnToggle.setOnClickListener {
            animateButton(btnToggle)
            onToggle?.invoke()
        }
        mainContent.addView(btnToggle, LinearLayout.LayoutParams(MATCH_PARENT, dp(72)))

        buildSecretScreen()
        return root
    }

    private fun buildSecretScreen() {
        secretScreen = ScrollView(ctx)
        secretScreen.isFillViewport = true
        secretScreen.setBackgroundColor(Color.parseColor("#0B0F1A"))
        secretScreen.visibility = View.GONE
        root.addView(secretScreen, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val content = LinearLayout(ctx)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(18), dp(60), dp(18), dp(20))
        secretScreen.addView(content, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val header = TextView(ctx)
        header.text = "Секретное меню"
        header.setTextColor(Color.WHITE)
        header.textSize = 26f
        header.gravity = Gravity.CENTER
        content.addView(header, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val desc = TextView(ctx)
        desc.text = "Разблокировка 90 Гц для Realme\n\n1. Нажмите кнопку ниже.\n2. Перейдите в Настройки телефона.\n3. Экран и яркость.\n4. Выберите 90 Гц."
        desc.setTextColor(Color.parseColor("#AAFFFFFF"))
        desc.textSize = 15f
        desc.gravity = Gravity.CENTER
        desc.setPadding(dp(20), dp(30), dp(20), dp(30))
        content.addView(desc, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val btnUnlock = TextView(ctx)
        btnUnlock.text = "АКТИВИРОВАТЬ 90 ГЦ"
        btnUnlock.setTextColor(Color.WHITE)
        btnUnlock.textSize = 18f
        btnUnlock.gravity = Gravity.CENTER
        btnUnlock.background = buttonBlue()
        btnUnlock.elevation = dp(10).toFloat()
        btnUnlock.setOnClickListener {
            animateButton(btnUnlock)
            onUnlock90?.invoke()
        }
        content.addView(btnUnlock, LinearLayout.LayoutParams(MATCH_PARENT, dp(64)).apply {
            topMargin = dp(20)
        })

        val btnBack = TextView(ctx)
        btnBack.text = "НАЗАД"
        btnBack.setTextColor(Color.WHITE)
        btnBack.textSize = 16f
        btnBack.gravity = Gravity.CENTER
        btnBack.background = buttonUnselected()
        btnBack.setOnClickListener { hideSecretScreen() }
        content.addView(btnBack, LinearLayout.LayoutParams(MATCH_PARENT, dp(56)).apply {
            topMargin = dp(30)
        })
    }

    private fun showSecretScreen() {
        secretScreen.visibility = View.VISIBLE
        mainScroll.visibility = View.GONE
        secretScreen.alpha = 0f
        secretScreen.animate().alpha(1f).setDuration(300).start()
    }

    private fun hideSecretScreen() {
        secretScreen.animate().alpha(0f).setDuration(200).withEndAction {
            secretScreen.visibility = View.GONE
            mainScroll.visibility = View.VISIBLE
        }.start()
    }

    fun setPackageText(text: String) {
        if (text.isNotEmpty()) pkgInput.setText(text)
    }

    fun setSenseLevel(level: Int) {
        currentSense = level
        updateSenseSelection()
    }

    fun setThermalLevel(level: Int) {
        currentThermal = level
        updateThermalSelection()
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
        tv.setOnClickListener { animateButton(tv) }
        return tv
    }

    private fun hzButton(text: String): TextView {
        val tv = TextView(ctx)
        tv.text = text
        tv.setTextColor(Color.WHITE)
        tv.textSize = 14f
        tv.gravity = Gravity.CENTER
        tv.background = buttonUnselected()
        return tv
    }

    fun updateHzSelection() {
        for (i in 0 until hzRow.childCount) {
            val v = hzRow.getChildAt(i) as TextView
            if (v.text.toString() == currentHz.toString()) v.background = buttonBlue()
            else v.background = buttonUnselected()
        }
    }

    fun updateSenseSelection() {
        for (i in 0 until senseRow.childCount) {
            val v = senseRow.getChildAt(i) as TextView
            if (i == currentSense) v.background = buttonBlue()
            else v.background = buttonUnselected()
        }
    }

    fun updateThermalSelection() {
        for (i in 0 until thermalRow.childCount) {
            val v = thermalRow.getChildAt(i) as TextView
            if (i == currentThermal) v.background = buttonBlue()
            else v.background = buttonUnselected()
        }
    }

    fun setActive(active: Boolean) {
        if (active) {
            btnToggle.text = "ВЕРНУТЬ АВТО"
            btnToggle.background = buttonRed()
        } else {
            btnToggle.text = "ОПТИМИЗИРОВАТЬ"
            btnToggle.background = buttonBlue()
        }
    }

    fun setBatteryLowBlock(block: Boolean) {
        btnToggle.alpha = if (block) 0.4f else 1f
    }

    private fun card(text: String): TextView {
        val tv = TextView(ctx)
        tv.text = text
        tv.setTextColor(Color.WHITE)
        tv.textSize = 14f
        tv.gravity = Gravity.CENTER_VERTICAL
        tv.setPadding(dp(18), dp(14), dp(18), dp(14))
        tv.background = cardBg()
        tv.elevation = dp(6).toFloat()
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

        val edge = object : GradientDrawable() {
            override fun draw(canvas: Canvas) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                val w = bounds.width().toFloat()
                val h = bounds.height().toFloat()
                val shader = LinearGradient(0f, 0f, w, 0f,
                    intArrayOf(Color.parseColor("#55FFFFFF"), Color.parseColor("#00FFFFFF"), Color.parseColor("#00FFFFFF"), Color.parseColor("#55FFFFFF")),
                    floatArrayOf(0f, 0.15f, 0.85f, 1f), Shader.TileMode.CLAMP)
                p.shader = shader
                canvas.drawRoundRect(0f, 0f, w, h, dp(22).toFloat(), dp(22).toFloat(), p)
            }
        }
        edge.shape = GradientDrawable.RECTANGLE
        return LayerDrawable(arrayOf(g, edge))
    }

    private fun buttonBlue(): LayerDrawable = btnBase(intArrayOf(Color.parseColor("#5B8DEF"), Color.parseColor("#7BA7F5"), Color.parseColor("#5B8DEF")), "#CCFFFFFF")
    private fun buttonRed(): LayerDrawable = btnBase(intArrayOf(Color.parseColor("#EF4444"), Color.parseColor("#F87171"), Color.parseColor("#EF4444")), "#CCFFFFFF")
    private fun buttonUnselected(): LayerDrawable = btnBase(intArrayOf(Color.parseColor("#33FFFFFF"), Color.parseColor("#22FFFFFF"), Color.parseColor("#33FFFFFF")), "#55FFFFFF")

    private fun btnBase(colors: IntArray, stroke: String): LayerDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = dp(12).toFloat()
        g.colors = colors
        g.gradientType = GradientDrawable.LINEAR_GRADIENT
        g.orientation = GradientDrawable.Orientation.LEFT_RIGHT
        g.setStroke(dp(1), Color.parseColor(stroke))
        return LayerDrawable(arrayOf(g))
    }
}
