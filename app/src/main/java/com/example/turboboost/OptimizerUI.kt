package com.example.turboboost

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.TransitionDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.Window
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class OptimizerUI(private val ctx: Context) {

    lateinit var root: FrameLayout
    lateinit var scroll1: ScrollView
    lateinit var page1: LinearLayout
    lateinit var snowView: SnowView
    lateinit var fxView: FXView
    lateinit var btnOpt: TextView
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

    private val blueS by lazy { gb(intArrayOf(0xFF4F46E5.toInt(), 0xFF7BA7F5.toInt(), 0xFF4F46E5.toInt()), 0xCCFFFFFF.toInt()).constantState }
    private val redS by lazy { gb(intArrayOf(0xFFDC2626.toInt(), 0xFFF87171.toInt(), 0xFFDC2626.toInt()), 0xCCFFFFFF.toInt()).constantState }
    private val unselS by lazy { gb(intArrayOf(0x22FFFFFF, 0x11FFFFFF, 0x22FFFFFF), 0x55FFFFFF.toInt()).constantState }

    private fun blue() = blueS!!.newDrawable().mutate() as GradientDrawable
    private fun red() = redS!!.newDrawable().mutate() as GradientDrawable
    private fun unsel() = unselS!!.newDrawable().mutate() as GradientDrawable

    fun build(): FrameLayout {
        root = FrameLayout(ctx)
        root.setBackgroundColor(Color.parseColor("#05070A"))
        
        snowView = SnowView(ctx)
        snowView.onShake = { shakeUI() }
        root.addView(snowView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        
        fxView = FXView(ctx)
        root.addView(fxView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        page1 = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(24), dp(16), dp(24)) }
        scroll1 = ScrollView(ctx).apply { 
            isFillViewport = true
            setBackgroundColor(Color.TRANSPARENT)
            addView(page1, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                val dir = if (scrollY > oldScrollY) 1f else -1f
                fxView.spawnScrollDust(scroll1.width / 2f, dir)
            }
        }
        root.addView(scroll1, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        
        header()
        buildMain()
        animateIn()
        return root
    }

    private fun header() {
        val h = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        page1.addView(h, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val ic = ImageView(ctx).apply { setImageResource(R.drawable.ic_bolt); alpha = 0.9f }
        h.addView(ic, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(12) })
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        h.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        col.addView(tv("TurboBoost", 26f, Color.WHITE).apply { letterSpacing = 0.05f; setShadowLayer(10f, 0f, 0f, Color.parseColor("#887BA7F5")) })
        col.addView(tv("ZNAK • LIQUID", 11f, Color.parseColor("#AACCE0FF")).apply { letterSpacing = 0.3f })
        page1.addView(View(ctx).apply { setBackgroundColor(Color.parseColor("#33FFFFFF")) }, LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply { topMargin = dp(16); bottomMargin = dp(10) })
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
            presetRow.addView(glass(n) { currentPreset = i; onPresetSelected?.invoke(i); updPreset() }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { if (i < 2) marginEnd = dp(6) })
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
            setHintTextColor(Color.parseColor("#88FFFFFF"))
            setTextColor(Color.WHITE)
            textSize = 13f
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setBackgroundColor(Color.TRANSPARENT)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) { onPackageChanged?.invoke(s?.toString()?.trim() ?: "") }
            })
        }
        val pkgWrap = FrameLayout(ctx).apply {
            background = glassBgDrawable(14)
            if (android.os.Build.VERSION.SDK_INT >= 31) setRenderEffect(RenderEffect.createBlurEffect(30f, 30f, Shader.TileMode.CLAMP))
            addView(pkgInput, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
        page1.addView(pkgWrap, LinearLayout.LayoutParams(MATCH_PARENT, dp(44)).apply { topMargin = dp(8) })

        val r1 = row(page1, 6)
        btnGames = glass("Игры") { onShowGames?.invoke() }
        r1.addView(btnGames, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) })
        btnProfiles = glass("Профили") { onShowProfiles?.invoke() }
        r1.addView(btnProfiles, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) })
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
        btnOpt = bigBtn("ОПТИМИЗИРОВАТЬ", 16f, blue()) { onToggle?.invoke() }
        br.addView(btnOpt, LinearLayout.LayoutParams(MATCH_PARENT, dp(68)))
    }

    private fun animateIn() {
        for (i in 0 until page1.childCount) {
            val v = page1.getChildAt(i)
            v.alpha = 0f
            v.translationY = dp(30).toFloat()
            v.scaleX = 0.97f
            v.scaleY = 0.97f
            v.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setStartDelay((i * 15).toLong())
                .setDuration(300)
                .setInterpolator(OvershootInterpolator(1.0f))
                .start()
        }
    }    fun showGlassList(title: String, items: List<String>, onClick: (Int) -> Unit) {
        val dialog = Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        
        val root = FrameLayout(ctx)
        root.setBackgroundColor(Color.parseColor("#CC05070A"))
        
        val scroll = ScrollView(ctx)
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(40), dp(24), dp(40))
        }
        scroll.addView(container)
        root.addView(scroll, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { gravity = Gravity.CENTER })
        
        container.addView(TextView(ctx).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(20))
        })

        val dlgBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(24).toFloat()
            colors = intArrayOf(0x44FFFFFF, 0x11FFFFFF, 0x33FFFFFF)
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.TL_BR
            setStroke(dp(1), 0x88FFFFFF.toInt())
        }

        items.forEachIndexed { i, item ->
            val tv = TextView(ctx).apply {
                text = item
                setTextColor(Color.WHITE)
                textSize = 15f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = dlgBg
                setOnClickListener {
                    performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    onClick(i)
                    dialog.dismiss()
                }
            }
            container.addView(tv, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })
        }

        if (android.os.Build.VERSION.SDK_INT >= 31) {
            root.setRenderEffect(RenderEffect.createBlurEffect(25f, 25f, Shader.TileMode.CLAMP))
        }
        
        dialog.setContentView(root)
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        dialog.window?.setLayout(MATCH_PARENT, WRAP_CONTENT)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun shakeUI() {
        if (!::scroll1.isInitialized) return
        val animator = android.animation.ObjectAnimator.ofFloat(scroll1, "translationX", 0f, -20f, 20f, -15f, 15f, -10f, 10f, -5f, 5f, 0f)
        animator.duration = 700
        animator.interpolator = android.view.animation.DecelerateInterpolator()
        animator.start()
        
        val animatorY = android.animation.ObjectAnimator.ofFloat(scroll1, "translationY", 0f, -12f, 12f, -8f, 8f, -4f, 4f, 0f)
        animatorY.duration = 700
        animatorY.interpolator = android.view.animation.DecelerateInterpolator()
        animatorY.start()
        
        if (::fxView.isInitialized) {
            fxView.spawnBurst(root.width / 2f, root.height / 2f)
        }
        if (::pkgInput.isInitialized) {
            pkgInput.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        }
        if (::btnOpt.isInitialized) {
            btnOpt.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
                btnOpt.animate().scaleX(1f).scaleY(1f).setDuration(250).setInterpolator(OvershootInterpolator(2.5f)).start()
            }.start()
        }
    }

    fun setMode(m: Int) { currentMode = m; fade(btnModePerf, if (m == 0) blue() else unsel()); fade(btnModeBal, if (m == 1) blue() else unsel()) }
    fun setPreset(p: Int) { currentPreset = p; updPreset() }
    fun setHz(hz: Int) { currentHz = hz; updHz() }
    fun setPackageText(t: String) { if (t.isNotEmpty()) pkgInput.setText(t) }
    fun setSenseLevel(l: Int) { currentSense = l; updSense() }
    fun setThermalLevel(l: Int) { currentThermal = l; updThermal() }
    
    fun setActive(a: Boolean) { 
        btnOpt.text = if (a) "ВЕРНУТЬ АВТО" else "ОПТИМИЗИРОВАТЬ"
        fade(btnOpt, if (a) red() else blue())
        if (a) pulse(btnOpt) else stopPulse(btnOpt)
    }
    
    fun setGpuBoostState(a: Boolean) { fade(btnGpuBoost, if (a) blue() else unsel()) }
    fun setBgLimitState(a: Boolean) { fade(btnBgLimit, if (a) blue() else unsel()) }
    fun setJoyoseState(a: Boolean) { fade(btnJoyose, if (a) blue() else unsel()) }
    fun setJoyoseVisible(v: Boolean) { btnJoyose.visibility = if (v) View.VISIBLE else View.GONE }
    fun setThreadsState(a: Boolean) { fade(btnThreads, if (a) blue() else unsel()) }
    fun setMglruState(a: Boolean) { fade(btnMglru, if (a) blue() else unsel()) }

    fun setAppliedState(active: Boolean, gov: Boolean) {
        if (!active) { cardApplied.text = "Применено: —"; return }
        cardApplied.text = "Применено: Governor ${if (gov) "✓" else "✗"}"
    }

    fun setBatteryLowBlock(b: Boolean) { btnOpt.alpha = if (b) 0.4f else 1f }
    fun setOverheat(warn: Boolean, critical: Boolean) { if (critical) statusResult.text = "КРИТИЧЕСКИЙ ПЕРЕГРЕВ — выключено"; else if (warn) statusResult.text = "Перегрев — снизьте троттлинг" }
    
    fun setStatus(text: String) { statusResult.text = text }

    private fun pulse(v: View) {
        v.animate().cancel()
        v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(800).setInterpolator(android.view.animation.AccelerateDecelerateInterpolator()).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(800).setInterpolator(android.view.animation.AccelerateDecelerateInterpolator()).withEndAction {
                if (v.tag == "pulse") pulse(v)
            }
        }.start()
        v.tag = "pulse"
    }
    private fun stopPulse(v: View) { v.tag = null; v.animate().cancel(); v.animate().scaleX(1f).scaleY(1f).setDuration(200).start() }

    private fun fade(v: View, bg: GradientDrawable) {
        val o = v.background
        if (o == null) { v.background = bg; return }
        val t = TransitionDrawable(arrayOf(o, bg)); t.isCrossFadeEnabled = true; v.background = t; t.startTransition(250)
    }

    private fun updPreset() { for (i in 0 until presetRow.childCount) (presetRow.getChildAt(i) as TextView).background = if (i == currentPreset) blue() else unsel() }
    private fun updHz() { val m = listOf(60, 90, 120, 144); for (i in 0 until hzRow.childCount) (hzRow.getChildAt(i) as TextView).background = if (m[i] == currentHz) blue() else unsel() }
    private fun updSense() { for (i in 0 until senseRow.childCount) (senseRow.getChildAt(i) as TextView).background = if (i == currentSense) blue() else unsel() }
    private fun updThermal() { for (i in 0 until thermalRow.childCount) (thermalRow.getChildAt(i) as TextView).background = if (i == currentThermal) blue() else unsel() }

    private fun touch(v: View, click: () -> Unit) {
        v.setOnTouchListener { view, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.95f).scaleY(0.95f).alpha(0.8f).setDuration(50).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(250).setInterpolator(OvershootInterpolator(2.5f)).start()
            }
            false
        }
        v.setOnClickListener { 
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            if (::fxView.isInitialized) {
                val loc = IntArray(2)
                v.getLocationInWindow(loc)
                fxView.spawnBurst(loc[0] + v.width / 2f, loc[1] + v.height / 2f)
            }
            click() 
        }
    }

    private fun glass(text: String, click: () -> Unit): TextView = TextView(ctx).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 11f
        gravity = Gravity.CENTER
        setPadding(dp(4), 0, dp(4), 0)
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            setRenderEffect(RenderEffect.createBlurEffect(15f, 15f, Shader.TileMode.CLAMP))
        }
        background = glassBgDrawable(12)
        touch(this, click)
    }

    private fun bigBtn(text: String, size: Float, bg: GradientDrawable, click: () -> Unit): TextView = TextView(ctx).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = size
        gravity = Gravity.CENTER
        setShadowLayer(6f, 0f, 2f, Color.parseColor("#55000000"))
        background = bg
        touch(this, click)
    }

    private fun card(text: String): TextView = TextView(ctx).apply {
        this.text = text
        setTextColor(Color.parseColor("#EEEEEE"))
        textSize = 13f
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            setRenderEffect(RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP))
        }
        background = glassBgDrawable(16)
    }

    private fun glassBgDrawable(radius: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radius).toFloat()
        colors = intArrayOf(0x55FFFFFF, 0x1AFFFFFF, 0x33FFFFFF)
        gradientType = GradientDrawable.LINEAR_GRADIENT
        orientation = GradientDrawable.Orientation.TL_BR
        setStroke(dp(1), 0xAAFFFFFF.toInt())
    }

    private fun addCard(p: LinearLayout, v: View) { p.addView(v, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }) }
    private fun tv(text: String, size: Float, color: Int): TextView = TextView(ctx).apply { this.text = text; setTextColor(color); textSize = size }
    private fun lbl(text: String): TextView = tv(text, 12f, Color.parseColor("#99FFFFFF")).apply { setPadding(dp(4), dp(10), 0, dp(2)) }
    private fun row(p: LinearLayout, margin: Int): LinearLayout = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; p.addView(this, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(margin) }) }
    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    private fun gb(colors: IntArray, stroke: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(12).toFloat()
        this.colors = colors
        gradientType = GradientDrawable.LINEAR_GRADIENT
        orientation = GradientDrawable.Orientation.LEFT_RIGHT
        setStroke(dp(1), stroke)
    }

    class FXView(context: Context) : View(context) {
        private data class Spark(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Int, val size: Float)
        private val sparks = mutableListOf<Spark>()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rand = Random(System.currentTimeMillis())

        fun spawnBurst(cx: Float, cy: Float) {
            if (sparks.size > 10) return
            for (i in 0 until 6) {
                val angle = rand.nextFloat() * Math.PI * 2
                val speed = rand.nextFloat() * 10f + 3f
                sparks.add(Spark(cx, cy, cos(angle).toFloat() * speed, sin(angle).toFloat() * speed, 1f, Color.WHITE, rand.nextFloat() * 3f + 1.5f))
            }
            invalidate()
        }

        fun spawnScrollDust(cx: Float, dir: Float) {
            if (sparks.size > 10) return
            val x = cx + (rand.nextFloat() - 0.5f) * width
            sparks.add(Spark(x, if (dir > 0) -10f else height + 10f, 0f, dir * 8f, 1f, Color.WHITE, rand.nextFloat() * 2f + 1f))
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (sparks.isEmpty()) return

            val iter = sparks.iterator()
            while (iter.hasNext()) {
                val s = iter.next()
                s.x += s.vx
                s.y += s.vy
                s.vy += 0.4f
                s.life -= 0.03f
                if (s.life <= 0) {
                    iter.remove()
                } else {
                    paint.color = s.color
                    paint.alpha = (s.life * 255).toInt().coerceIn(0, 255)
                    canvas.drawCircle(s.x, s.y, s.size * s.life, paint)
                }
            }
            invalidate()
        }
    }

    class SnowView(context: Context) : View(context), SensorEventListener {
        private data class Flake(var x: Float, var y: Float, val radius: Float, val speed: Float, val drift: Float, var alpha: Int)
        private val flakes = mutableListOf<Flake>()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val auroraPaint1 = Paint(Paint.ANTI_ALIAS_FLAG)
        private val auroraPaint2 = Paint(Paint.ANTI_ALIAS_FLAG)
        private val matrix1 = Matrix()
        private val matrix2 = Matrix()
        private val rand = Random(System.currentTimeMillis())
        private var time = 0f
        private var active = true
        
        private var sensorManager: SensorManager? = null
        private var accelerometer: Sensor? = null
        private var lastShakeTime = 0L
        private var lastX = 0f
        private var lastY = 0f
        private var lastZ = 0f

        var onShake: (() -> Unit)? = null

        init {
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            sensorManager?.unregisterListener(this)
        }

        override fun onVisibilityChanged(changedView: View, visibility: Int) {
            super.onVisibilityChanged(changedView, visibility)
            active = visibility == VISIBLE
            if (active) invalidate()
        }

        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val deltaX = abs(x - lastX)
                val deltaY = abs(y - lastY)
                val deltaZ = abs(z - lastZ)
                val speed = deltaX + deltaY + deltaZ

                if (speed > 12f) {
                    val now = System.currentTimeMillis()
                    if (now - lastShakeTime > 800) {
                        lastShakeTime = now
                        triggerShake()
                    }
                }
                lastX = x; lastY = y; lastZ = z
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        private fun triggerShake() {
            val cx = width / 2f
            val cy = height / 2f
            for (f in flakes) {
                val dx = f.x - cx
                val dy = f.y - cy
                val dist = hypot(dx, dy).coerceAtLeast(1f)
                val force = (rand.nextFloat() * 20f + 15f)
                f.x += (dx / dist) * force
                f.y += (dy / dist) * force
            }
            post { onShake?.invoke() }
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            flakes.clear()
            for (i in 0 until 30) {
                flakes.add(Flake(rand.nextFloat() * w, rand.nextFloat() * h, rand.nextFloat() * 2.5f + 1f, rand.nextFloat() * 1.5f + 0.5f, rand.nextFloat() * 0.6f - 0.3f, 100 + rand.nextInt(155)))
            }
            bgPaint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0xFF05070A.toInt(), 0xFF101A3A.toInt(), 0xFF2A1A4A.toInt(), 0xFF05070A.toInt()), floatArrayOf(0f, 0.3f, 0.7f, 1f), Shader.TileMode.CLAMP)
            auroraPaint1.shader = RadialGradient(w / 2f, h * 0.3f, w * 0.9f, intArrayOf(0x44A855F7, 0x00000000), null, Shader.TileMode.CLAMP)
            auroraPaint2.shader = RadialGradient(w / 2f, h * 0.7f, w * 0.8f, intArrayOf(0x4450C0FF, 0x00000000), null, Shader.TileMode.CLAMP)
        }

        override fun onDraw(canvas: Canvas) {
            if (!active) return
            time += 0.015f
            canvas.drawPaint(bgPaint)
            
            matrix1.setTranslate(sin(time * 0.7f) * width * 0.3f, cos(time * 0.9f) * height * 0.2f)
            auroraPaint1.shader?.setLocalMatrix(matrix1)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), auroraPaint1)

            matrix2.setTranslate(cos(time * 0.5f) * width * 0.4f, sin(time * 0.8f) * height * 0.2f)
            auroraPaint2.shader?.setLocalMatrix(matrix2)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), auroraPaint2)

            for (f in flakes) {
                f.y += f.speed
                f.x += f.drift + sin(time + f.y * 0.01f) * 0.3f
                if (f.y > height + 10) { f.y = -10f; f.x = rand.nextFloat() * width }
                if (f.x > width + 10) f.x = -10f
                if (f.x < -10) f.x = width + 10f

                paint.color = Color.argb(f.alpha, 255, 255, 255)
                canvas.drawCircle(f.x, f.y, f.radius, paint)
            }
            invalidate()
        }
    }
}
