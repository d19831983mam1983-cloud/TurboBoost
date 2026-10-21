package com.example.turboboost

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
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
    lateinit var btnDiagnostics: TextView
    lateinit var btnJoyose: TextView
    lateinit var btnThreads: TextView
    lateinit var btnMglru: TextView
    lateinit var btnGameSpace: TextView
    lateinit var btnModePerf: TextView
    lateinit var btnModeBal: TextView
    lateinit var btnGpuBoost: TextView
    lateinit var btnBgLimit: TextView
    lateinit var btnGames: TextView
    lateinit var btnProfiles: TextView
    lateinit var pkgInput: EditText
    lateinit var cardApplied: TextView
    lateinit var statusResult: TextView
    lateinit var senseRow: LinearLayout
    lateinit var thermalRow: LinearLayout
    lateinit var presetRow: LinearLayout
    lateinit var hzRow: LinearLayout

    var onToggle: (() -> Unit)? = null
    var onTurboToggle: (() -> Unit)? = null
    var onDiagnostics: (() -> Unit)? = null
    var onJoyoseToggle: (() -> Unit)? = null
    var onThreadsToggle: (() -> Unit)? = null
    var onMglruToggle: (() -> Unit)? = null
    var onGameSpace: (() -> Unit)? = null
    var onSenseSelected: ((Int) -> Unit)? = null
    var onThermalSelected: ((Int) -> Unit)? = null
    var onModeSelected: ((Int) -> Unit)? = null
    var onGpuBoostToggle: (() -> Unit)? = null
    var onBgLimitToggle: (() -> Unit)? = null
    var onShowGames: (() -> Unit)? = null
    var onShowProfiles: (() -> Unit)? = null
    var onPresetSelected: ((Int) -> Unit)? = null
    var onHzSelected: ((Int) -> Unit)? = null
    var onPackageChanged: ((String) -> Unit)? = null

    var currentSense = -1
    var currentThermal = -1
    var currentMode = 0
    var currentPreset = -1
    var currentHz = 120

    private val blueS by lazy { gb(intArrayOf(0xFF4F46E5.toInt(), 0xFF7BA7F5.toInt(), 0xFF4F46E5.toInt()), 0x99FFFFFF.toInt()).constantState }
    private val redS by lazy { gb(intArrayOf(0xFFDC2626.toInt(), 0xFFF87171.toInt(), 0xFFDC2626.toInt()), 0x99FFFFFF.toInt()).constantState }
    private val unselS by lazy { gb(intArrayOf(0x22FFFFFF, 0x11FFFFFF, 0x22FFFFFF), 0x33FFFFFF.toInt()).constantState }
    private val tBlueS by lazy { gb(intArrayOf(0xFF2563EB.toInt(), 0xFF60A5FA.toInt(), 0xFF2563EB.toInt()), 0x99FFFFFF.toInt()).constantState }
    private val tGreenS by lazy { gb(intArrayOf(0xFF16A34A.toInt(), 0xFF4ADE80.toInt(), 0xFF16A34A.toInt()), 0x99FFFFFF.toInt()).constantState }
    private val cardS by lazy { cb().constantState }

    private fun blue() = blueS!!.newDrawable().mutate() as GradientDrawable
    private fun red() = redS!!.newDrawable().mutate() as GradientDrawable
    private fun unsel() = unselS!!.newDrawable().mutate() as GradientDrawable
    private fun tBlue() = tBlueS!!.newDrawable().mutate() as GradientDrawable
    private fun tGreen() = tGreenS!!.newDrawable().mutate() as GradientDrawable
    private fun cardD() = cardS!!.newDrawable().mutate() as GradientDrawable

    fun build(): FrameLayout {
        root = FrameLayout(ctx)
        root.setBackgroundColor(Color.parseColor("#05070A"))
        page1 = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(24), dp(16), dp(24)) }
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
        h.addView(ic, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(12) })
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        h.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        col.addView(tv("TurboBoost", 26f, Color.WHITE).apply { letterSpacing = 0.05f })
        col.addView(tv("ZNAK • LIQUID", 11f, Color.parseColor("#88AACCFF")).apply { letterSpacing = 0.3f })
        page1.addView(View(ctx).apply { setBackgroundColor(Color.parseColor("#22FFFFFF")) }, LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply { topMargin = dp(16); bottomMargin = dp(10) })
    }

    private fun buildMain() {
        val mr = row(page1, 6)
        btnModePerf = glass("Производительность") { onModeSelected?.invoke(0); setMode(0) }
        mr.addView(btnModePerf, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(6) })
        btnModeBal = glass("Сбалансированный") { onModeSelected?.invoke(1); setMode(1) }
        mr.addView(btnModeBal, LinearLayout.LayoutParams(0, dp(38), 1f))

        page1.addView(lbl("Пресеты:"))
        presetRow = row(page1, 6)
        listOf("Стандарт", "Агрессивный", "Максимум").forEachIndexed { i, n ->
            presetRow.addView(glass(n) { currentPreset = i; onPresetSelected?.invoke(i); updPreset() }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(6) })
        }

        page1.addView(lbl("Частота экрана:"))
        hzRow = row(page1, 6)
        listOf(60, 90, 120, 144).forEach { hz ->
            hzRow.addView(glass(hz.toString()) { currentHz = hz; onHzSelected?.invoke(hz); updHz() }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(6) })
        }

        val er = row(page1, 6)
        btnGpuBoost = glass("GPU Boost") { onGpuBoostToggle?.invoke() }
        er.addView(btnGpuBoost, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(6) })
        btnBgLimit = glass("Лимит фона") { onBgLimitToggle?.invoke() }
        er.addView(btnBgLimit, LinearLayout.LayoutParams(0, dp(38), 1f))

        page1.addView(lbl("Сенса:"))
        senseRow = row(page1, 6)
        listOf("Средний", "Сильный", "Эксп.", "Макс").forEachIndexed { i, n ->
            senseRow.addView(glass(n) { currentSense = i; onSenseSelected?.invoke(i); updSense() }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(6) })
        }

        page1.addView(lbl("Троттлинг (°C):"))
        thermalRow = row(page1, 6)
        listOf("Авто", "50", "60", "70").forEachIndexed { i, n ->
            thermalRow.addView(glass(n) { currentThermal = i; onThermalSelected?.invoke(i); updThermal() }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(6) })
        }

        page1.addView(lbl("Пакет игры:"))
        pkgInput = EditText(ctx).apply {
            hint = "com.axlebolt.standoff2"
            setHintTextColor(Color.parseColor("#55FFFFFF"))
            setTextColor(Color.WHITE)
            textSize = 13f
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = unsel()
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            if (android.os.Build.VERSION.SDK_INT >= 31) setRenderEffect(RenderEffect.createBlurEffect(15f, 15f, Shader.TileMode.CLAMP))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) { onPackageChanged?.invoke(s?.toString()?.trim() ?: "") }
            })
        }
        page1.addView(pkgInput, LinearLayout.LayoutParams(MATCH_PARENT, dp(42)).apply { topMargin = dp(6) })
        
        val r1 = row(page1, 6)
        btnGames = glass("Игры") { onShowGames?.invoke() }
        r1.addView(btnGames, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) })
        btnProfiles = glass("Профили") { onShowProfiles?.invoke() }
        r1.addView(btnProfiles, LinearLayout.LayoutParams(0, dp(42), 1f))
        btnThreads = glass("Потоки CPU") { onThreadsToggle?.invoke() }
        r1.addView(btnThreads, LinearLayout.LayoutParams(0, dp(42), 1f))

        val r2 = row(page1, 6)
        btnMglru = glass("MGLRU") { onMglruToggle?.invoke() }
        r2.addView(btnMglru, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) })
        btnGameSpace = glass("Game Turbo") { onGameSpace?.invoke() }
        r2.addView(btnGameSpace, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) })
        btnJoyose = glass("Xiaomi Boost") { onJoyoseToggle?.invoke() }
        btnJoyose.visibility = View.GONE
        r2.addView(btnJoyose, LinearLayout.LayoutParams(0, dp(42), 1f))

        btnDiagnostics = glass("Диагностика") { onDiagnostics?.invoke() }
        page1.addView(btnDiagnostics, LinearLayout.LayoutParams(MATCH_PARENT, dp(42)).apply { topMargin = dp(6) })

        cardApplied = card("Применено: —"); addCard(page1, cardApplied)
        statusResult = card("Готов к работе"); addCard(page1, statusResult)

        page1.addView(View(ctx), LinearLayout.LayoutParams(MATCH_PARENT, dp(16)))

        val br = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        page1.addView(br, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        btnTurbo = bigBtn("TURBO", 14f, tBlue()) { onTurboToggle?.invoke() }
        br.addView(btnTurbo, LinearLayout.LayoutParams(0, dp(66), 0.32f).apply { marginEnd = dp(8) })
        btnOpt = bigBtn("ОПТИМИЗИРОВАТЬ", 16f, blue()) { onToggle?.invoke() }
        br.addView(btnOpt, LinearLayout.LayoutParams(0, dp(66), 0.68f))
    }

    private fun animateIn() {
        for (i in 0 until page1.childCount) {
            val v = page1.getChildAt(i)
            v.alpha = 0f; v.translationY = dp(24).toFloat()
            v.animate().alpha(1f).translationY(0f).setStartDelay((i * 25).toLong()).setDuration(320).setInterpolator(OvershootInterpolator(0.8f)).start()
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
    fun setThreadsState(a: Boolean) { fade(btnThreads, if (a) blue() else unsel()) }
    fun setMglruState(a: Boolean) { fade(btnMglru, if (a) blue() else unsel()) }

    fun setAppliedState(active: Boolean, gov: Boolean, dns: Boolean, bbr: Boolean) {
        if (!active) { cardApplied.text = "Применено: —"; return }
        var n = 0; if (gov) n++; if (dns) n++; if (bbr) n++
        cardApplied.text = "Применено: $n/3 | ${if (gov) "Gov ✓" else "Gov ✗"} | ${if (dns) "DNS ✓" else "DNS ✗"} | ${if (bbr) "BBR ✓" else "BBR ✗"}"
    }

    fun setBatteryLowBlock(b: Boolean) { btnOpt.alpha = if (b) 0.4f else 1f; btnTurbo.alpha = if (b) 0.4f else 1f }
    fun setOverheat(warn: Boolean, critical: Boolean) { if (critical) statusResult.text = "КРИТИЧЕСКИЙ ПЕРЕГРЕВ — выключено"; else if (warn) statusResult.text = "Перегрев — снизьте троттлинг" }

    private fun fade(v: View, bg: GradientDrawable) {
        val o = v.background
        if (o == null) { v.background = bg; return }
        val t = TransitionDrawable(arrayOf(o, bg)); t.isCrossFadeEnabled = true; v.background = t; t.startTransition(220)
    }

    private fun updPreset() { for (i in 0 until presetRow.childCount) (presetRow.getChildAt(i) as TextView).background = if (i == currentPreset) blue() else unsel() }
    private fun updHz() { val m = listOf(60, 90, 120, 144); for (i in 0 until hzRow.childCount) (hzRow.getChildAt(i) as TextView).background = if (m[i] == currentHz) blue() else unsel() }
    private fun updSense() { for (i in 0 until senseRow.childCount) (senseRow.getChildAt(i) as TextView).background = if (i == currentSense) blue() else unsel() }
    private fun updThermal() { for (i in 0 until thermalRow.childCount) (thermalRow.getChildAt(i) as TextView).background = if (i == currentThermal) blue() else unsel() }

    private fun touch(v: View, click: () -> Unit) {
        v.setOnTouchListener { view, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.92f).scaleY(0.92f).alpha(0.75f).setDuration(70).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(250).setInterpolator(OvershootInterpolator(2.5f)).start()
            }
            false
        }
        v.setOnClickListener { v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY); click() }
    }

    private fun glass(text: String, click: () -> Unit): TextView = TextView(ctx).apply {
        this.text = text; setTextColor(Color.WHITE); textSize = 11f; gravity = Gravity.CENTER; setPadding(dp(4), 0, dp(4), 0)
        background = unsel(); setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        if (android.os.Build.VERSION.SDK_INT >= 31) setRenderEffect(RenderEffect.createBlurEffect(15f, 15f, Shader.TileMode.CLAMP))
        touch(this, click)
    }

    private fun bigBtn(text: String, size: Float, bg: GradientDrawable, click: () -> Unit): TextView = TextView(ctx).apply {
        this.text = text; setTextColor(Color.WHITE); textSize = size; gravity = Gravity.CENTER; setShadowLayer(6f, 0f, 2f, Color.parseColor("#44000000"))
        background = bg; touch(this, click)
    }

    private fun card(text: String): TextView = TextView(ctx).apply {
        this.text = text; setTextColor(Color.parseColor("#E0E0E0")); textSize = 13f; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12)); background = cardD()
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        if (android.os.Build.VERSION.SDK_INT >= 31) setRenderEffect(RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP))
    }

    private fun addCard(p: LinearLayout, v: View) { p.addView(v, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }) }
    private fun tv(text: String, size: Float, color: Int): TextView = TextView(ctx).apply { this.text = text; setTextColor(color); textSize = size }
    private fun lbl(text: String): TextView = tv(text, 12f, Color.parseColor("#99FFFFFF")).apply { setPadding(dp(4), dp(8), 0, dp(2)) }
    private fun row(p: LinearLayout, margin: Int): LinearLayout = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; p.addView(this, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(margin) }) }
    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    private fun cb(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(20).toFloat(); colors = intArrayOf(0x33FFFFFF, 0x1AFFFFFF, 0x0DFFFFFF)
        gradientType = GradientDrawable.LINEAR_GRADIENT; orientation = GradientDrawable.Orientation.TL_BR; setStroke(dp(1), 0x44FFFFFF.toInt())
    }

    private fun gb(colors: IntArray, stroke: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(16).toFloat(); this.colors = colors
        gradientType = GradientDrawable.LINEAR_GRADIENT; orientation = GradientDrawable.Orientation.LEFT_RIGHT; setStroke(dp(1), stroke)
    }
}
