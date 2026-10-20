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
    lateinit var btnDiagnostics: TextView
    lateinit var btnJoyose: TextView
    lateinit var btnDownscale: TextView
    lateinit var btnThreads: TextView
    lateinit var btnMglru: TextView
    lateinit var btnGameSpace: TextView
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
    lateinit var cardApplied: TextView
    lateinit var statusResult: TextView
    lateinit var senseRow: LinearLayout
    lateinit var thermalRow: LinearLayout
    lateinit var presetRow: LinearLayout
    lateinit var hzRow: LinearLayout

    var onToggle: (() -> Unit)? = null
    var onTurboToggle: (() -> Unit)? = null
    var onDevOptions: (() -> Unit)? = null
    var onDiagnostics: (() -> Unit)? = null
    var onJoyoseToggle: (() -> Unit)? = null
    var onDownscaleToggle: (() -> Unit)? = null
    var onThreadsToggle: (() -> Unit)? = null
    var onMglruToggle: (() -> Unit)? = null
    var onGameSpace: (() -> Unit)? = null
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
        page1 = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(16), dp(14), dp(16)) }
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
        h.addView(ic, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(8) })
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        h.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        col.addView(tv("TurboBoost", 22f, Color.WHITE))
        col.addView(tv("ZNAK", 10f, Color.parseColor("#66FFFFFF")).apply { letterSpacing = 0.3f })
        page1.addView(View(ctx).apply { setBackgroundColor(Color.parseColor("#22FFFFFF")) }, LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply { topMargin = dp(8); bottomMargin = dp(4) })
    }

    private fun buildMain() {
        val mr = row(page1, 4)
        btnModePerf = glass("Производительность") { onModeSelected?.invoke(0); setMode(0) }
        mr.addView(btnModePerf, LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(4) })
        btnModeBal = glass("Сбалансированный") { onModeSelected?.invoke(1); setMode(1) }
        mr.addView(btnModeBal, LinearLayout.LayoutParams(0, dp(36), 1f))

        page1.addView(lbl("Пресет:"))
        presetRow = row(page1, 4)
        listOf("Стандарт", "Агрессивный", "Максимум").forEachIndexed { i, n ->
            presetRow.addView(glass(n) { currentPreset = i; onPresetSelected?.invoke(i); updPreset() }, LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(4) })
        }

        page1.addView(lbl("Частота:"))
        hzRow = row(page1, 4)
        listOf(60, 90, 120, 144).forEach { hz ->
            hzRow.addView(glass(hz.toString()) { currentHz = hz; onHzSelected?.invoke(hz); updHz() }, LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(4) })
        }

        val er = row(page1, 4)
        btnGpuBoost = glass("GPU Boost") { onGpuBoostToggle?.invoke() }
        er.addView(btnGpuBoost, LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(4) })
        btnBgLimit = glass("Лимит фона") { onBgLimitToggle?.invoke() }
        er.addView(btnBgLimit, LinearLayout.LayoutParams(0, dp(36), 1f))

        page1.addView(lbl("Сенса:"))
        senseRow = row(page1, 4)
        listOf("Средний", "Сильный", "Эксп.", "Макс").forEachIndexed { i, n ->
            senseRow.addView(glass(n) { currentSense = i; onSenseSelected?.invoke(i); updSense() }, LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(4) })
        }

        page1.addView(lbl("Троттлинг (°C):"))
        thermalRow = row(page1, 4)
        listOf("Авто", "50", "60", "70").forEachIndexed { i, n ->
            thermalRow.addView(glass(n) { currentThermal = i; onThermalSelected?.invoke(i); updThermal() }, LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(4) })
        }

        page1.addView(lbl("Пакет игры:"))
        pkgInput = EditText(ctx).apply {
            hint = "com.axlebolt.standoff2"
            setHintTextColor(Color.parseColor("#66FFFFFF"))
            setTextColor(Color.WHITE)
            textSize = 13f
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = unsel()
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) { onPackageChanged?.invoke(s?.toString()?.trim() ?: "") }
            })
        }
        page1.addView(pkgInput, LinearLayout.LayoutParams(MATCH_PARENT, dp(40)).apply { topMargin = dp(4) })
        val r1 = row(page1, 4)
        btnGames = glass("Игры") { onShowGames?.invoke() }
        r1.addView(btnGames, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(4) })
        btnProfiles = glass("Профили") { onShowProfiles?.invoke() }
        r1.addView(btnProfiles, LinearLayout.LayoutParams(0, dp(40), 1f))
        btnCompile = glass("Компиляция") { onCompile?.invoke(pkgInput.text.toString().trim()) }
        r1.addView(btnCompile, LinearLayout.LayoutParams(0, dp(40), 1f))

        val r2 = row(page1, 4)
        btnKeep = glass("В памяти") { onKeep?.invoke(pkgInput.text.toString().trim()) }
        r2.addView(btnKeep, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(4) })
        btnCache = glass("Кэш") { onTrimCaches?.invoke() }
        r2.addView(btnCache, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(4) })
        btnPing = glass("Пинг") { onPing?.invoke() }
        r2.addView(btnPing, LinearLayout.LayoutParams(0, dp(40), 1f))

        val r3 = row(page1, 4)
        btnDownscale = glass("720p") { onDownscaleToggle?.invoke() }
        r3.addView(btnDownscale, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(4) })
        btnThreads = glass("Потоки") { onThreadsToggle?.invoke() }
        r3.addView(btnThreads, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(4) })
        btnMglru = glass("MGLRU") { onMglruToggle?.invoke() }
        r3.addView(btnMglru, LinearLayout.LayoutParams(0, dp(40), 1f))

        val r4 = row(page1, 4)
        btnGameSpace = glass("Game Turbo") { onGameSpace?.invoke() }
        r4.addView(btnGameSpace, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(4) })
        btnJoyose = glass("Xiaomi") { onJoyoseToggle?.invoke() }
        btnJoyose.visibility = View.GONE
        r4.addView(btnJoyose, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(4) })
        btnDiagnostics = glass("Диагностика") { onDiagnostics?.invoke() }
        r4.addView(btnDiagnostics, LinearLayout.LayoutParams(0, dp(40), 1f))

        btnDevOptions = glass("Для разработчиков") { onDevOptions?.invoke() }
        page1.addView(btnDevOptions, LinearLayout.LayoutParams(MATCH_PARENT, dp(40)).apply { topMargin = dp(4) })

        cardApplied = card("Применено: —"); addCard(page1, cardApplied)
        statusResult = card("Готово"); addCard(page1, statusResult)

        page1.addView(View(ctx), LinearLayout.LayoutParams(MATCH_PARENT, dp(12)))

        val br = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        page1.addView(br, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        btnTurbo = bigBtn("TURBO", 13f, tBlue()) { onTurboToggle?.invoke() }
        br.addView(btnTurbo, LinearLayout.LayoutParams(0, dp(60), 0.3f).apply { marginEnd = dp(6) })
        btnOpt = bigBtn("ОПТИМИЗИРОВАТЬ", 15f, blue()) { onToggle?.invoke() }
        br.addView(btnOpt, LinearLayout.LayoutParams(0, dp(60), 0.7f))
    }

    private fun animateIn() {
        for (i in 0 until page1.childCount) {
            val v = page1.getChildAt(i)
            v.alpha = 0f; v.translationY = dp(15).toFloat()
            v.animate().alpha(1f).translationY(0f).setStartDelay((i * 20).toLong()).setDuration(250).start()
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
    fun setJoyoseState(a: Boolean) { fade(btnJoyose, if (a) blue() else unsel()) }
    fun setJoyoseVisible(v: Boolean) { btnJoyose.visibility = if (v) View.VISIBLE else View.GONE }
    fun setDownscaleState(a: Boolean) { fade(btnDownscale, if (a) blue() else unsel()) }
    fun setThreadsState(a: Boolean) { fade(btnThreads, if (a) blue() else unsel()) }
    fun setMglruState(a: Boolean) { fade(btnMglru, if (a) blue() else unsel()) }

    fun setAppliedState(active: Boolean, gov: Boolean, dns: Boolean, bbr: Boolean) {
        if (!active) { cardApplied.text = "Применено: —"; return }
        var n = 0; if (gov) n++; if (dns) n++; if (bbr) n++
        cardApplied.text = "Применено: $n/3 | ${if (gov) "Gov ✓" else "Gov ✗"} | ${if (dns) "DNS ✓" else "DNS ✗"} | ${if (bbr) "BBR ✓" else "BBR ✗"}"
    }

    fun setBatteryLowBlock(b: Boolean) { btnOpt.alpha = if (b) 0.4f else 1f; btnTurbo.alpha = if (b) 0.4f else 1f }

    fun setOverheat(warn: Boolean, critical: Boolean) {
        if (critical) statusResult.text = "КРИТИЧЕСКИЙ ПЕРЕГРЕВ — оптимизация выключена"
        else if (warn) statusResult.text = "Перегрев — рекомендуется снять троттлинг"
    }

    private fun fade(v: View, bg: GradientDrawable) {
        val o = v.background
        if (o == null) { v.background = bg; return }
        val t = TransitionDrawable(arrayOf(o, bg))
        t.isCrossFadeEnabled = true
        v.background = t
        t.startTransition(150)
    }

    private fun updPreset() { for (i in 0 until presetRow.childCount) (presetRow.getChildAt(i) as TextView).background = if (i == currentPreset) blue() else unsel() }
    private fun updHz() { val m = listOf(60, 90, 120, 144); for (i in 0 until hzRow.childCount) (hzRow.getChildAt(i) as TextView).background = if (m[i] == currentHz) blue() else unsel() }
    private fun updSense() { for (i in 0 until senseRow.childCount) (senseRow.getChildAt(i) as TextView).background = if (i == currentSense) blue() else unsel() }
    private fun updThermal() { for (i in 0 until thermalRow.childCount) (thermalRow.getChildAt(i) as TextView).background = if (i == currentThermal) blue() else unsel() }

    private fun touch(v: View, click: () -> Unit) {
        v.setOnTouchListener { view, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.95f).setDuration(60).start()
                    ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.95f).setDuration(60).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    ObjectAnimator.ofFloat(view, "scaleX", 0.95f, 1f).setDuration(200).apply { interpolator = OvershootInterpolator() }.start()
                    ObjectAnimator.ofFloat(view, "scaleY", 0.95f, 1f).setDuration(200).apply { interpolator = OvershootInterpolator() }.start()
                }
            }
            false
        }
        v.setOnClickListener { click() }
    }

    private fun glass(text: String, click: () -> Unit): TextView = TextView(ctx).apply {
        this.text = text; setTextColor(Color.WHITE); textSize = 11f; gravity = Gravity.CENTER
        background = unsel(); touch(this, click)
    }

    private fun bigBtn(text: String, size: Float, bg: GradientDrawable, click: () -> Unit): TextView = TextView(ctx).apply {
        this.text = text; setTextColor(Color.WHITE); textSize = size; gravity = Gravity.CENTER
        background = bg; touch(this, click)
    }

    private fun card(text: String): TextView = TextView(ctx).apply {
        this.text = text; setTextColor(Color.WHITE); textSize = 13f; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(10), dp(14), dp(10)); background = cardD()
    }

    private fun addCard(p: LinearLayout, v: View) { p.addView(v, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) }) }

    private fun tv(text: String, size: Float, color: Int): TextView = TextView(ctx).apply {
        this.text = text; setTextColor(color); textSize = size
    }

    private fun lbl(text: String): TextView = tv(text, 12f, Color.WHITE).apply { setPadding(dp(6), dp(6), 0, dp(2)) }

    private fun row(p: LinearLayout, margin: Int): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        p.addView(this, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(margin) })
    }

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    private fun cb(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(18).toFloat()
        colors = intArrayOf(0x66FFFFFF, 0x33FFFFFF, 0x22FFFFFF)
        gradientType = GradientDrawable.LINEAR_GRADIENT; orientation = GradientDrawable.Orientation.TL_BR
        setStroke(dp(1), 0x88FFFFFF.toInt())
    }

    private fun gb(colors: IntArray, stroke: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(14).toFloat()
        this.colors = colors; gradientType = GradientDrawable.LINEAR_GRADIENT
        orientation = GradientDrawable.Orientation.LEFT_RIGHT; setStroke(dp(1), stroke)
    }
}   
