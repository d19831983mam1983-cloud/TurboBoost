package com.example.turboboost

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.TransitionDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class OptimizerUI(private val ctx: Context) {

    lateinit var root: FrameLayout
    lateinit var scroll1: ScrollView
    lateinit var page1: LinearLayout
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
    var onPackageChanged: ((String) -> Unit)? = null

    var currentSense = -1
    var currentThermal = -1
    var currentMode = 0
    var currentPreset = -1
    var currentHz = 120

    private val blueS by lazy { gb(intArrayOf(0xFF5B8DEF.toInt(), 0xFF7BA7F5.toInt(), 0xFF5B8DEF.toInt()), 0xCCFFFFFF.toInt()).constantState }
    private val redS by lazy { gb(intArrayOf(0xFFEF4444.toInt(), 0xFFF87171.toInt(), 0xFFEF4444.toInt()), 0xCCFFFFFF.toInt()).constantState }
    private val unselS by lazy { gb(intArrayOf(0x33FFFFFF, 0x22FFFFFF, 0x33FFFFFF), 0x55FFFFFF.toInt()).constantState }
    private val tBlueS by lazy { gb(intArrayOf(0xFF3B82F6.toInt(), 0xFF60A5FA.toInt(), 0xFF3B82F6.toInt()), 0xCCFFFFFF.toInt()).constantState }
    private val tGreenS by lazy { gb(intArrayOf(0xFF22C55E.toInt(), 0xFF4ADE80.toInt(), 0xFF22C55E.toInt()), 0xCCFFFFFF.toInt()).constantState }
    private val cardS by lazy { cb().constantState }

    private fun blue() = blueS!!.newDrawable().mutate() as GradientDrawable
    private fun red() = redS!!.newDrawable().mutate() as GradientDrawable
    private fun unsel() = unselS!!.newDrawable().mutate() as GradientDrawable
    private fun tBlue() = tBlueS!!.newDrawable().mutate() as GradientDrawable
    private fun tGreen() = tGreenS!!.newDrawable().mutate() as GradientDrawable
    private fun cardD() = cardS!!.newDrawable().mutate() as GradientDrawable

    fun build(): FrameLayout {
        root = FrameLayout(ctx)
        root.setBackgroundColor(Color.parseColor("#0B0F1A"))
        page1 = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(20), dp(18), dp(20)) }
        scroll1 = ScrollView(ctx).apply { isFillViewport = true; addView(page1, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)) }
        root.addView(scroll1, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        header()
        buildMain()
        animateIn()
        return root
    }

    private fun header() {
        val h = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        page1.addView(h, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val ic = ImageView(ctx).apply { setImageResource(R.drawable.ic_bolt) }
        h.addView(ic, LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(10) })
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        h.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        col.addView(tv("TurboBoost", 24f, Color.WHITE))
        col.addView(tv("ZNAK", 11f, Color.parseColor("#66FFFFFF")).apply { letterSpacing = 0.3f })
        page1.addView(View(ctx).apply { setBackgroundColor(Color.parseColor("#22FFFFFF")) }, LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply { topMargin = dp(10); bottomMargin = dp(6) })
    }

    private fun buildMain() {
        val mr = row(page1)
        btnModePerf = glass("Производительность") { onModeSelected?.invoke(0); setMode(0) }
        mr.addView(btnModePerf, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) })
        btnModeBal = glass("Сбалансированный") { onModeSelected?.invoke(1); setMode(1) }
        mr.addView(btnModeBal, LinearLayout.LayoutParams(0, dp(42), 1f))

        page1.addView(lbl("Пресет:"))
        presetRow = row(page1)
        listOf("Стандарт", "Агрессивный", "Максимум").forEachIndexed { i, n ->
            presetRow.addView(glass(n) { currentPreset = i; onPresetSelected?.invoke(i); updPreset() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        }

        page1.addView(lbl("Частота экрана (Гц):"))
        hzRow = row(page1)
        listOf(60, 90, 120, 144).forEach { hz ->
            hzRow.addView(glass(hz.toString()) { currentHz = hz; onHzSelected?.invoke(hz); updHz() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        }

        val er = row(page1)
        btnGpuBoost = glass("GPU Boost") { onGpuBoostToggle?.invoke() }
        er.addView(btnGpuBoost, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        btnBgLimit = glass("Лимит фона") { onBgLimitToggle?.invoke() }
        er.addView(btnBgLimit, LinearLayout.LayoutParams(0, dp(44), 1f))

        page1.addView(lbl("Сенса:"))
        senseRow = row(page1)
        listOf("Средний", "Сильный", "Эксп.", "Макс").forEachIndexed { i, n ->
            senseRow.addView(glass(n) { currentSense = i; onSenseSelected?.invoke(i); updSense() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        }

        page1.addView(lbl("Троттлинг (°C):"))
        thermalRow = row(page1)
        listOf("Авто", "50", "60", "70").forEachIndexed { i, n ->
            thermalRow.addView(glass(n) { currentThermal = i; onThermalSelected?.invoke(i); updThermal() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        }

        page1.addView(lbl("Пакет игры:"))
        pkgInput = EditText(ctx).apply {
            hint = "com.axlebolt.standoff2"
            setHintTextColor(Color.parseColor("#66FFFFFF"))
            setTextColor(Color.WHITE)
            textSize = 14f
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = unsel()
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    onPackageChanged?.invoke(s?.toString()?.trim() ?: "")
                }
            })
        }
        page1.addView(pkgInput, LinearLayout.LayoutParams(MATCH_PARENT, dp(48)).apply { topMargin = dp(4) })

        val r1 = row(page1)
        btnGames = glass("Выбрать игру") { onShowGames?.invoke() }
        r1.addView(btnGames, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        btnProfiles = glass("Профили") { onShowProfiles?.invoke() }
        r1.addView(btnProfiles, LinearLayout.LayoutParams(0, dp(48), 1f))

        val r2 = row(page1)
        btnCompile = glass("Ускорить запуск") { onCompile?.invoke(pkgInput.text.toString().trim()) }
        r2.addView(btnCompile, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        btnKeep = glass("Держать в памяти") { onKeep?.invoke(pkgInput.text.toString().trim()) }
        r2.addView(btnKeep, LinearLayout.LayoutParams(0, dp(48), 1f))

        val r3 = row(page1)
        btnCache = glass("Очистить кэш") { onTrimCaches?.invoke() }
        r3.addView(btnCache, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        btnPing = glass("Проверить пинг") { onPing?.invoke() }
        r3.addView(btnPing, LinearLayout.LayoutParams(0, dp(48), 1f))

        btnDevOptions = glass("Настройки для разработчиков") { onDevOptions?.invoke() }
        page1.addView(btnDevOptions, LinearLayout.LayoutParams(MATCH_PARENT, dp(48)).apply { topMargin = dp(8) })

        cardChip = card("Чип: —"); addCard(page1, cardChip)
        cardGpu = card("GPU: —"); addCard(page1, cardGpu)
        cardScreen = card("Частота: —"); addCard(page1, cardScreen)
        cardBattery = card("Батарея: —"); addCard(page1, cardBattery)
        cardTemp = card("Температура: —"); addCard(page1, cardTemp)
        cardFps = card("FPS: —"); addCard(page1, cardFps)
        cardPing = card("Пинг: —"); addCard(page1, cardPing)
        cardApplied = card("Применено: —"); addCard(page1, cardApplied)
        statusResult = card("Нажмите кнопку"); addCard(page1, statusResult)

        page1.addView(View(ctx), LinearLayout.LayoutParams(MATCH_PARENT, dp(24)))

        val br = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        page1.addView(br, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        btnTurbo = bigBtn("TURBO", 14f, tBlue()) { onTurboToggle?.invoke() }
        br.addView(btnTurbo, LinearLayout.LayoutParams(0, dp(76), 0.3f).apply { marginEnd = dp(8) })
        btnOpt = bigBtn("ОПТИМИЗИРОВАТЬ", 17f, blue()) { onToggle?.invoke() }
        br.addView(btnOpt, LinearLayout.LayoutParams(0, dp(76), 0.7f))
    }

    private fun animateIn() {
        for (i in 0 until page1.childCount) {
            val v = page1.getChildAt(i)
            v.alpha = 0f; v.translationY = dp(20).toFloat()
            v.animate().alpha(1f).translationY(0f).setStartDelay((i * 25).toLong()).setDuration(280).start()
        }
    }

    fun setMode(m: Int) { currentMode = m; fade(btnModePerf, if (m == 0) blue() else unsel()); fade(btnModeBal, if (m == 1) blue() else unsel()) }
    fun setPreset(p: Int) { currentPreset = p; updPreset() }
    fun setHz(hz: Int) { currentHz = hz; updHz() }
    fun setPackageText(t: String) { if (t.isNotEmpty()) pkgInput.setText(t) }
    fun setSenseLevel(l: Int) { currentSense = l; updSense() }
    fun setThermalLevel(l: Int) { currentThermal = l; updThermal() }
    fun setActive(a: Boolean) { btnOpt.text = if (a) "ВЕРНУТЬ АВТО" else "ОПТИМИЗИРОВАТЬ"; fade(btnOpt, if (a) red() else blue()) }
    fun setTurboState(a: Boolean) { fade(btnTurbo, if (a) tGreen() else tBlue()) }
    fun setGpuBoostState(a: Boolean) { fade(btnGpuBoost, if (a) blue() else unsel()) }
    fun setBgLimitState(a: Boolean) { fade(btnBgLimit, if (a) blue() else unsel()) }

    fun setAppliedState(active: Boolean, gov: Boolean, dns: Boolean, bbr: Boolean) {
        if (!active) { cardApplied.text = "Применено: —"; return }
        var n = 0; if (gov) n++; if (dns) n++; if (bbr) n++
        cardApplied.text = "Применено: $n/3 | ${if (gov) "Governor ✓" else "Governor ✗"} | ${if (dns) "DNS ✓" else "DNS ✗"} | ${if (bbr) "BBR ✓" else "BBR ✗"}"
    }

    fun setBatteryLowBlock(b: Boolean) { btnOpt.alpha = if (b) 0.4f else 1f; btnTurbo.alpha = if (b) 0.4f else 1f }

    private fun fade(v: View, bg: GradientDrawable) {
        val o = v.background
        if (o == null) { v.background = bg; return }
        val t = TransitionDrawable(arrayOf(o, bg))
        t.isCrossFadeEnabled = true
        v.background = t
        t.startTransition(180)
    }

    private fun updPreset() { for (i in 0 until presetRow.childCount) (presetRow.getChildAt(i) as TextView).background = if (i == currentPreset) blue() else unsel() }
    private fun updHz() { val m = listOf(60, 90, 120, 144); for (i in 0 until hzRow.childCount) (hzRow.getChildAt(i) as TextView).background = if (m[i] == currentHz) blue() else unsel() }
    private fun updSense() { for (i in 0 until senseRow.childCount) (senseRow.getChildAt(i) as TextView).background = if (i == currentSense) blue() else unsel() }
    private fun updThermal() { for (i in 0 until thermalRow.childCount) (thermalRow.getChildAt(i) as TextView).background = if (i == currentThermal) blue() else unsel() }

    private fun touch(v: View, click: () -> Unit) {
        v.setOnTouchListener { view, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.94f).setDuration(80).start()
                    ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.94f).setDuration(80).start()
                    ObjectAnimator.ofFloat(view, "alpha", 1f, 0.85f).setDuration(80).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    ObjectAnimator.ofFloat(view, "scaleX", 0.94f, 1f).setDuration(240).apply { interpolator = OvershootInterpolator() }.start()
                    ObjectAnimator.ofFloat(view, "scaleY", 0.94f, 1f).setDuration(240).apply { interpolator = OvershootInterpolator() }.start()
                    ObjectAnimator.ofFloat(view, "alpha", 0.85f, 1f).setDuration(180).start()
                }
            }
            false
        }
        v.setOnClickListener { click() }
    }

    private fun glass(text: String, click: () -> Unit): TextView = TextView(ctx).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 12f
        gravity = Gravity.CENTER
        background = unsel()
        touch(this, click)
    }

    private fun bigBtn(text: String, size: Float, bg: GradientDrawable, click: () -> Unit): TextView = TextView(ctx).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = size
        gravity = Gravity.CENTER
        background = bg
        touch(this, click)
    }

    private fun card(text: String): TextView = TextView(ctx).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 14f
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(18), dp(14), dp(18), dp(14))
        background = cardD()
    }

    private fun addCard(p: LinearLayout, v: View) { p.addView(v, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }) }

    private fun tv(text: String, size: Float, color: Int): TextView = TextView(ctx).apply {
        this.text = text
        setTextColor(color)
        textSize = size
    }

    private fun lbl(text: String): TextView = tv(text, 13f, Color.WHITE).apply { setPadding(dp(8), dp(10), 0, dp(4)) }

    private fun row(p: LinearLayout): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        p.addView(this, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
    }

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    private fun cb(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(22).toFloat()
        colors = intArrayOf(0x66FFFFFF, 0x33FFFFFF, 0x22FFFFFF)
        gradientType = GradientDrawable.LINEAR_GRADIENT
        orientation = GradientDrawable.Orientation.TL_BR
        setStroke(dp(1), 0x88FFFFFF.toInt())
    }

    private fun gb(colors: IntArray, stroke: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(18).toFloat()
        this.colors = colors
        gradientType = GradientDrawable.LINEAR_GRADIENT
        orientation = GradientDrawable.Orientation.LEFT_RIGHT
        setStroke(dp(1), stroke)
    }
}
