package com.example.turboboost

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.TransitionDrawable
import android.text.InputType
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

    private val blueS by lazy { btnBaseGlass(intArrayOf(0xFF5B8DEF.toInt(), 0xFF7BA7F5.toInt(), 0xFF5B8DEF.toInt()), 0xCCFFFFFF.toInt()).constantState }
    private val redS by lazy { btnBaseGlass(intArrayOf(0xFFEF4444.toInt(), 0xFFF87171.toInt(), 0xFFEF4444.toInt()), 0xCCFFFFFF.toInt()).constantState }
    private val unselS by lazy { btnBaseGlass(intArrayOf(0x33FFFFFF, 0x22FFFFFF, 0x33FFFFFF), 0x55FFFFFF.toInt()).constantState }
    private val tBlueS by lazy { btnBaseGlass(intArrayOf(0xFF3B82F6.toInt(), 0xFF60A5FA.toInt(), 0xFF3B82F6.toInt()), 0xCCFFFFFF.toInt()).constantState }
    private val tGreenS by lazy { btnBaseGlass(intArrayOf(0xFF22C55E.toInt(), 0xFF4ADE80.toInt(), 0xFF22C55E.toInt()), 0xCCFFFFFF.toInt()).constantState }
    private val cardS by lazy { cardGlass().constantState }

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
        content.setPadding(dp(18), dp(20), dp(18), dp(20))
        scroll.addView(content, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val header = LinearLayout(ctx)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        content.addView(header, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val icon = ImageView(ctx)
        icon.setImageResource(R.drawable.ic_bolt)
        header.addView(icon, LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(10) })

        val titleCol = LinearLayout(ctx)
        titleCol.orientation = LinearLayout.VERTICAL
        header.addView(titleCol, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        val t1 = TextView(ctx)
        t1.text = "TurboBoost"
        t1.setTextColor(Color.WHITE)
        t1.textSize = 24f
        titleCol.addView(t1)

        val t2 = TextView(ctx)
        t2.text = "ZNAK"
        t2.setTextColor(Color.parseColor("#66FFFFFF"))
        t2.textSize = 11f
        t2.letterSpacing = 0.3f
        titleCol.addView(t2)

        content.addView(divider(), LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply { topMargin = dp(14); bottomMargin = dp(6) })

        val modeRow = row()
        btnModePerf = btnGlass("Производительность") { onModeSelected?.invoke(0); setMode(0) }
        modeRow.addView(btnModePerf, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) })
        btnModeBal = btnGlass("Сбалансированный") { onModeSelected?.invoke(1); setMode(1) }
        modeRow.addView(btnModeBal, LinearLayout.LayoutParams(0, dp(42), 1f))

        content.addView(label("Пресет:", 13f).apply { setPadding(dp(8), dp(10), 0, dp(4)) })
        presetRow = row()
        val presets = listOf("Стандарт", "Агрессивный", "Максимум")
        presets.forEachIndexed { i, name ->
            val b = btnGlass(name) { currentPreset = i; onPresetSelected?.invoke(i); updatePreset() }
            presetRow.addView(b, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        }

        content.addView(label("Частота экрана (Гц):", 13f).apply { setPadding(dp(8), dp(10), 0, dp(4)) })
        hzRow = row()
        listOf(60, 90, 120, 144).forEach { hz ->
            val b = btnGlass(hz.toString()) { currentHz = hz; onHzSelected?.invoke(hz); updateHz() }
            hzRow.addView(b, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        }

        cardChip = card("Чип: —"); addCard(cardChip)
        cardGpu = card("GPU: —"); addCard(cardGpu)
        cardScreen = card("Частота: —"); addCard(cardScreen)
        cardBattery = card("Батарея: —"); addCard(cardBattery)
        cardTemp = card("Температура: —"); addCard(cardTemp)
        cardFps = card("FPS: —"); addCard(cardFps)
        cardPing = card("Пинг: —"); addCard(cardPing)
        cardApplied = card("Применено: —"); addCard(cardApplied)

        val extraRow = row()
        btnGpuBoost = btnGlass("GPU Boost") { onGpuBoostToggle?.invoke() }
        extraRow.addView(btnGpuBoost, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        btnBgLimit = btnGlass("Лимит фона") { onBgLimitToggle?.invoke() }
        extraRow.addView(btnBgLimit, LinearLayout.LayoutParams(0, dp(44), 1f))

        content.addView(label("Сенса:", 13f).apply { setPadding(dp(8), dp(10), 0, dp(4)) })
        senseRow = row()
        listOf("Средний", "Сильный", "Эксп.", "Макс").forEachIndexed { i, name ->
            val b = btnGlass(name) { currentSense = i; onSenseSelected?.invoke(i); updateSense() }
            senseRow.addView(b, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        }

        content.addView(label("Троттлинг (°C):", 13f).apply { setPadding(dp(8), dp(10), 0, dp(4)) })
        thermalRow = row()
        listOf("Авто", "50", "60", "70").forEachIndexed { i, name ->
            val b = btnGlass(name) { currentThermal = i; onThermalSelected?.invoke(i); updateThermal() }
            thermalRow.addView(b, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        }

        content.addView(label("Пакет игры:", 13f).apply { setPadding(dp(8), dp(10), 0, dp(4)) })
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

        val r1 = row()
        btnGames = btnGlass("Выбрать игру") { onShowGames?.invoke() }
        r1.addView(btnGames, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        btnProfiles = btnGlass("Профили") { onShowProfiles?.invoke() }
        r1.addView(btnProfiles, LinearLayout.LayoutParams(0, dp(48), 1f))

        val r2 = row()
        btnCompile = btnGlass("Ускорить запуск") { onCompile?.invoke(pkgInput.text.toString().trim()) }
        r2.addView(btnCompile, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        btnKeep = btnGlass("Держать в памяти") { onKeep?.invoke(pkgInput.text.toString().trim()) }
        r2.addView(btnKeep, LinearLayout.LayoutParams(0, dp(48), 1f))

        val r3 = row()
        btnCache = btnGlass("Очистить кэш") { onTrimCaches?.invoke() }
        r3.addView(btnCache, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        btnPing = btnGlass("Проверить пинг") { onPing?.invoke() }
        r3.addView(btnPing, LinearLayout.LayoutParams(0, dp(48), 1f))

        btnDevOptions = btnGlass("Настройки для разработчиков") { onDevOptions?.invoke() }
        content.addView(btnDevOptions, LinearLayout.LayoutParams(MATCH_PARENT, dp(48)).apply { topMargin = dp(8) })

        statusResult = card("Нажмите кнопку")
        addCard(statusResult)

        content.addView(View(ctx), LinearLayout.LayoutParams(MATCH_PARENT, dp(24)))

        val bottom = LinearLayout(ctx)
        bottom.orientation = LinearLayout.HORIZONTAL
        bottom.gravity = Gravity.CENTER_VERTICAL
        content.addView(bottom, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        btnTurbo = TextView(ctx).apply {
            text = "TURBO"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            background = tBlueBg()
            attachGlassTouch(this, { onTurboToggle?.invoke() })
        }
        bottom.addView(btnTurbo, LinearLayout.LayoutParams(0, dp(76), 0.3f).apply { marginEnd = dp(8) })

        btnOpt = TextView(ctx).apply {
            text = "ОПТИМИЗИРОВАТЬ"
            setTextColor(Color.WHITE)
            textSize = 17f
            gravity = Gravity.CENTER
            background = blueBg()
            attachGlassTouch(this, { onToggle?.invoke() })
        }
        bottom.addView(btnOpt, LinearLayout.LayoutParams(0, dp(76), 0.7f))

        animateIn()
        return root
    }

    private fun animateIn() {
        val kids = listOf<View>(content)
        for (i in 0 until content.childCount) {
            val v = content.getChildAt(i)
            v.alpha = 0f
            v.translationY = dp(20).toFloat()
            v.animate().alpha(1f).translationY(0f).setStartDelay((i * 25).toLong()).setDuration(280).start()
        }
    }

    fun setMode(mode: Int) {
        currentMode = mode
        crossfade(btnModePerf, if (mode == 0) blueBg() else unselBg())
        crossfade(btnModeBal, if (mode == 1) blueBg() else unselBg())
    }

    fun setPreset(p: Int) { currentPreset = p; updatePreset() }
    fun setHz(hz: Int) { currentHz = hz; updateHz() }
    fun setPackageText(t: String) { if (t.isNotEmpty()) pkgInput.setText(t) }
    fun setSenseLevel(l: Int) { currentSense = l; updateSense() }
    fun setThermalLevel(l: Int) { currentThermal = l; updateThermal() }

    fun setActive(a: Boolean) {
        btnOpt.text = if (a) "ВЕРНУТЬ АВТО" else "ОПТИМИЗИРОВАТЬ"
        crossfade(btnOpt, if (a) redBg() else blueBg())
    }

    fun setTurboState(a: Boolean) { crossfade(btnTurbo, if (a) tGreenBg() else tBlueBg()) }
    fun setGpuBoostState(a: Boolean) { crossfade(btnGpuBoost, if (a) blueBg() else unselBg()) }
    fun setBgLimitState(a: Boolean) { crossfade(btnBgLimit, if (a) blueBg() else unselBg()) }

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

    private fun crossfade(v: View, newBg: GradientDrawable) {
        val old = v.background
        if (old == null) { v.background = newBg; return }
        val tr = TransitionDrawable(arrayOf(old, newBg))
        tr.isCrossFadeEnabled = true
        v.background = tr
        tr.startTransition(180)
    }

    private fun updatePreset() {
        for (i in 0 until presetRow.childCount) {
            val v = presetRow.getChildAt(i) as TextView
            v.background = if (i == currentPreset) blueBg() else unselBg()
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

    private fun attachGlassTouch(v: View, click: () -> Unit) {
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

    private fun btnGlass(text: String, click: () -> Unit): TextView {
        val tv = TextView(ctx)
        tv.text = text
        tv.setTextColor(Color.WHITE)
        tv.textSize = 12f
        tv.gravity = Gravity.CENTER
        tv.background = unselBg()
        attachGlassTouch(tv, click)
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

    private fun addCard(v: View) {
        content.addView(v, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
    }

    private fun label(text: String, size: Float): TextView {
        val tv = TextView(ctx)
        tv.text = text
        tv.setTextColor(Color.WHITE)
        tv.textSize = size
        return tv
    }

    private fun divider(): View {
        val v = View(ctx)
        v.setBackgroundColor(Color.parseColor("#22FFFFFF"))
        return v
    }

    private fun row(): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        content.addView(r, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
        return r
    }

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    private fun cardGlass(): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = dp(22).toFloat()
        g.colors = intArrayOf(0x66FFFFFF, 0x33FFFFFF, 0x22FFFFFF)
        g.gradientType = GradientDrawable.LINEAR_GRADIENT
        g.orientation = GradientDrawable.Orientation.TL_BR
        g.setStroke(dp(1), 0x88FFFFFF.toInt())
        return g
    }

    private fun btnBaseGlass(colors: IntArray, stroke: Int): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = dp(18).toFloat()
        g.colors = colors
        g.gradientType = GradientDrawable.LINEAR_GRADIENT
        g.orientation = GradientDrawable.Orientation.LEFT_RIGHT
        g.setStroke(dp(1), stroke)
        return g
    }
}
