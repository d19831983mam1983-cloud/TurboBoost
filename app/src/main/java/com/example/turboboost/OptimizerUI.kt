package com.example.turboboost

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
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
import android.view.animation.AnticipateOvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class OptimizerUI(private val ctx: Context) {

    lateinit var root: FrameLayout
    lateinit var topBar: FrameLayout
    lateinit var scroll1: ScrollView
    lateinit var page1: LinearLayout
    lateinit var bgView: LiquidBackgroundView
    lateinit var btnSettings: ImageView
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
    lateinit var btnLauncher: TextView
    lateinit var pkgInput: EditText
    lateinit var cardApplied: TextView
    lateinit var statusResult: TextView
    lateinit var senseRow: LinearLayout
    lateinit var hzRow: LinearLayout

    var onToggle: (() -> Unit)? = null
    var onDiagnostics: (() -> Unit)? = null
    var onJoyoseToggle: (() -> Unit)? = null
    var onThreadsToggle: (() -> Unit)? = null
    var onMglruToggle: (() -> Unit)? = null
    var onGameSpace: (() -> Unit)? = null
    var onSenseSelected: ((Int) -> Unit)? = null
    var onModeSelected: ((Int) -> Unit)? = null
    var onGpuBoostToggle: (() -> Unit)? = null
    var onBgLimitToggle: (() -> Unit)? = null
    var onShowGames: (() -> Unit)? = null
    var onShowProfiles: (() -> Unit)? = null
    var onHzSelected: ((Int) -> Unit)? = null
    var onPackageChanged: ((String) -> Unit)? = null
    var onSettingsClick: (() -> Unit)? = null
    var onShowLauncher: (() -> Unit)? = null

    var currentSense = -1
    var currentMode = 0
    var currentHz = 120
    private var isActive = false
    private var themeIndex = 0
    private var currentGpu = false
    private var currentBg = false
    private var currentThreads = false
    private var currentMglru = false
    private var currentJoyose = false
    private var supportedHz: List<Int> = listOf(60, 90, 120, 144)

    fun setSupportedHz(list: List<Int>) { supportedHz = list }

    private fun theme(): ThemePalette.Theme = ThemePalette.get(themeIndex)

    private fun accentDrawable(): GradientDrawable {
        val t = theme()
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            colors = intArrayOf(t.primary, t.secondary, t.primary)
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.LEFT_RIGHT
            setStroke(dp(1), 0xCCFFFFFF.toInt())
        }
    }

    private fun unselDrawable(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(12).toFloat()
        colors = intArrayOf(0x44FFFFFF, 0x22FFFFFF, 0x33FFFFFF)
        gradientType = GradientDrawable.LINEAR_GRADIENT
        orientation = GradientDrawable.Orientation.LEFT_RIGHT
        setStroke(dp(1), 0x55FFFFFF.toInt())
    }

    fun setThemeIndex(i: Int) {
        themeIndex = i.coerceIn(0, ThemePalette.themes.size - 1)
        if (!::root.isInitialized) return
        if (::bgView.isInitialized) bgView.setTheme(themeIndex, isActive)
        applyThemeToAll()
    }

    private fun applyThemeToAll() {
        if (!::root.isInitialized) return
        setMode(currentMode)
        setGpuBoostState(currentGpu)
        setBgLimitState(currentBg)
        setThreadsState(currentThreads)
        setMglruState(currentMglru)
        setJoyoseState(currentJoyose)
        updHz(); updSense()
        if (::btnOpt.isInitialized) fade(btnOpt, accentDrawable())
    }

    fun build(): FrameLayout {
        root = FrameLayout(ctx)
        root.setBackgroundColor(Color.parseColor("#05070A"))
        
        bgView = LiquidBackgroundView(ctx)
        root.addView(bgView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val content = FrameLayout(ctx)
        root.addView(content, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        page1 = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(24), dp(16), dp(24)) }
        scroll1 = ScrollView(ctx).apply { 
            isFillViewport = true
            setBackgroundColor(Color.TRANSPARENT)
            addView(page1, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            setPadding(0, dp(80), 0, 0)
        }
        content.addView(scroll1, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        topBar = buildTopBar()
        content.addView(topBar, FrameLayout.LayoutParams(MATCH_PARENT, dp(72)))

        buildMain()
        animateIn()
        return root
    }

    private fun buildTopBar(): FrameLayout {
        val bar = FrameLayout(ctx)
        bar.setBackgroundColor(Color.parseColor("#CC05070A"))
        
        val h = LinearLayout(ctx).apply { 
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        bar.addView(h, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        
        val ic = ImageView(ctx).apply { setImageResource(R.drawable.ic_bolt); alpha = 0.9f }
        h.addView(ic, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(12) })
        
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        h.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        col.addView(tv("TurboBoost", 22f, Color.WHITE).apply { letterSpacing = 0.05f; setShadowLayer(8f, 0f, 0f, theme().secondary) })
        col.addView(tv("LIQUID GLASS", 10f, Color.parseColor("#AACCE0FF")).apply { letterSpacing = 0.3f })
        
        btnSettings = ImageView(ctx).apply {
            setImageResource(android.R.drawable.ic_menu_preferences)
            setColorFilter(Color.WHITE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { onSettingsClick?.invoke() }
        }
        h.addView(btnSettings, LinearLayout.LayoutParams(dp(44), dp(44)))
        
        return bar
    }private fun buildMain() {
    val mr = row(page1, 6)
    btnModePerf = glass("Производительность") { onModeSelected?.invoke(0); setMode(0) }
    mr.addView(btnModePerf, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(6) })
    btnModeBal = glass("Сбалансированный") { onModeSelected?.invoke(1); setMode(1) }
    mr.addView(btnModeBal, LinearLayout.LayoutParams(0, dp(38), 1f))

    page1.addView(lbl("Частота экрана:"))
    hzRow = row(page1, 6)
    supportedHz.forEach { hz ->
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

    btnLauncher = glass("Мои игры (Лаунчер)") { onShowLauncher?.invoke() }
    page1.addView(btnLauncher, LinearLayout.LayoutParams(MATCH_PARENT, dp(42)).apply { topMargin = dp(6) })

    btnDiagnostics = glass("Диагностика") { onDiagnostics?.invoke() }
    page1.addView(btnDiagnostics, LinearLayout.LayoutParams(MATCH_PARENT, dp(42)).apply { topMargin = dp(6) })

    cardApplied = card("Применено: —"); addCard(page1, cardApplied)
    statusResult = card("Готов к работе"); addCard(page1, statusResult)

    page1.addView(View(ctx), LinearLayout.LayoutParams(MATCH_PARENT, dp(16)))

    val br = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    page1.addView(br, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    btnOpt = bigBtn("ОПТИМИЗИРОВАТЬ", 16f, accentDrawable()) { onToggle?.invoke() }
    br.addView(btnOpt, LinearLayout.LayoutParams(MATCH_PARENT, dp(68)))
}

private fun animateIn() {
    for (i in 0 until page1.childCount) {
        val v = page1.getChildAt(i)
        v.alpha = 0f
        v.translationY = dp(40).toFloat()
        v.scaleX = 0.9f
        v.scaleY = 0.9f
        v.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay((i * 20).toLong())
            .setDuration(500)
            .setInterpolator(AnticipateOvershootInterpolator(1.2f))
            .start()
    }
}

fun setBackgroundBitmap(bmp: Bitmap?) {
    if (::bgView.isInitialized) bgView.setBackgroundImage(bmp)
}

fun showGlassList(title: String, items: List<String>, onClick: (Int) -> Unit) {
    val dialog = Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    
    val root = FrameLayout(ctx)
    root.setBackgroundColor(Color.parseColor("#FF000000"))
    
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
        colors = intArrayOf(0x33FFFFFF, 0x1AFFFFFF, 0x22FFFFFF)
        gradientType = GradientDrawable.LINEAR_GRADIENT
        orientation = GradientDrawable.Orientation.TL_BR
        setStroke(dp(1), 0xAAFFFFFF.toInt())
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
    
    dialog.setContentView(root)
    dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
    dialog.window?.setLayout(MATCH_PARENT, WRAP_CONTENT)
    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    dialog.show()
}

fun setMode(m: Int) { 
    currentMode = m
    fade(btnModePerf, if (m == 0) accentDrawable() else unselDrawable())
    fade(btnModeBal, if (m == 1) accentDrawable() else unselDrawable())
}
fun setHz(hz: Int) { currentHz = hz; updHz() }
fun setPackageText(t: String) { if (t.isNotEmpty()) pkgInput.setText(t) }
fun setSenseLevel(l: Int) { currentSense = l; updSense() }

fun setActive(a: Boolean) { 
    isActive = a
    btnOpt.text = if (a) "ВЕРНУТЬ АВТО" else "ОПТИМИЗИРОВАТЬ"
    fade(btnOpt, accentDrawable())
    if (::bgView.isInitialized) bgView.setActive(a)
    setMode(currentMode)
    setGpuBoostState(currentGpu)
    setBgLimitState(currentBg)
    setThreadsState(currentThreads)
    setMglruState(currentMglru)
    setJoyoseState(currentJoyose)
    updHz(); updSense()
}

fun setGpuBoostState(a: Boolean) { currentGpu = a; fade(btnGpuBoost, if (a) accentDrawable() else unselDrawable()) }
fun setBgLimitState(a: Boolean) { currentBg = a; fade(btnBgLimit, if (a) accentDrawable() else unselDrawable()) }
fun setJoyoseState(a: Boolean) { currentJoyose = a; fade(btnJoyose, if (a) accentDrawable() else unselDrawable()) }
fun setJoyoseVisible(v: Boolean) { btnJoyose.visibility = if (v) View.VISIBLE else View.GONE }
fun setThreadsState(a: Boolean) { currentThreads = a; fade(btnThreads, if (a) accentDrawable() else unselDrawable()) }
fun setMglruState(a: Boolean) { currentMglru = a; fade(btnMglru, if (a) accentDrawable() else unselDrawable()) }

fun setAppliedState(active: Boolean, gov: Boolean) {
    if (!active) { cardApplied.text = "Применено: —"; return }
    cardApplied.text = "Применено: RAM очищена"
}

fun setBatteryLowBlock(b: Boolean) { btnOpt.alpha = if (b) 0.4f else 1f }
fun setOverheat(warn: Boolean, critical: Boolean) { 
    if (critical) statusResult.text = "КРИТИЧЕСКИЙ ПЕРЕГРЕВ — выключено"; 
    else if (warn) statusResult.text = "Перегрев" 
}

fun setStatus(text: String) { statusResult.text = text }    private fun fade(v: View, bg: GradientDrawable) {
        val o = v.background
        if (o == null) { v.background = bg; return }
        val t = TransitionDrawable(arrayOf(o, bg)); t.isCrossFadeEnabled = true; v.background = t; t.startTransition(500)
    }

    private fun updHz() { 
        val m = supportedHz
        for (i in 0 until hzRow.childCount) {
            val tv = hzRow.getChildAt(i) as TextView
            tv.background = if (m[i] == currentHz) accentDrawable() else unselDrawable()
        }
    }
    private fun updSense() { 
        for (i in 0 until senseRow.childCount) {
            val tv = senseRow.getChildAt(i) as TextView
            tv.background = if (i == currentSense) accentDrawable() else unselDrawable()
        }
    }

    private fun touch(v: View, click: () -> Unit) {
        v.setOnTouchListener { view, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.95f).scaleY(0.95f).alpha(0.8f).setDuration(50).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(300).setInterpolator(AnticipateOvershootInterpolator(2f)).start()
            }
            false
        }
        v.setOnClickListener { 
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            click() 
        }
    }

    private fun glass(text: String, click: () -> Unit): TextView = TextView(ctx).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 11f
        gravity = Gravity.CENTER
        setPadding(dp(4), 0, dp(4), 0)
        background = unselDrawable()
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
        background = glassBgDrawable(16)
    }

    private fun glassBgDrawable(radius: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radius).toFloat()
        colors = intArrayOf(0x55FFFFFF.toInt(), 0x22FFFFFF, 0x44FFFFFF)
        gradientType = GradientDrawable.LINEAR_GRADIENT
        orientation = GradientDrawable.Orientation.TL_BR
        setStroke(dp(1), 0xAAFFFFFF.toInt())
    }

    private fun addCard(p: LinearLayout, v: View) { p.addView(v, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }) }
    private fun tv(text: String, size: Float, color: Int): TextView = TextView(ctx).apply { this.text = text; setTextColor(color); textSize = size }
    private fun lbl(text: String): TextView = tv(text, 12f, Color.parseColor("#99FFFFFF")).apply { setPadding(dp(4), dp(10), 0, dp(2)) }
    private fun row(p: LinearLayout, margin: Int): LinearLayout = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; p.addView(this, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(margin) }) }
    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    class LiquidBackgroundView(context: Context) : View(context), SensorEventListener {
        private data class Flake(
            var x: Float, var y: Float, val radius: Float,
            val speed: Float, val drift: Float, var alpha: Int, var rotation: Float
        )
        private val flakes = mutableListOf<Flake>()
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val blobPaint1 = Paint(Paint.ANTI_ALIAS_FLAG)
        private val blobPaint2 = Paint(Paint.ANTI_ALIAS_FLAG)
        private val blobPaint3 = Paint(Paint.ANTI_ALIAS_FLAG)
        private val flakePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val matrix1 = Matrix()
        private val matrix2 = Matrix()
        private val matrix3 = Matrix()
        private val rand = Random(System.currentTimeMillis())
        private var time = 0f
        private var active = true
        private var isActiveMode = false
        private var themeIndex = 0
        private var bgBitmap: Bitmap? = null
        
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

        fun setBackgroundImage(bmp: Bitmap?) {
            bgBitmap = bmp
            invalidate()
        }

        fun setActive(a: Boolean) {
            isActiveMode = a
            updateGradients()
            invalidate()
        }

        fun setTheme(index: Int, activeMode: Boolean) {
            themeIndex = index
            isActiveMode = activeMode
            updateGradients()
            invalidate()
        }

        private fun currentPrimary(): Int {
            val t = ThemePalette.get(themeIndex)
            return if (isActiveMode) t.secondary else t.primary
        }

        private fun currentSecondary(): Int {
            val t = ThemePalette.get(themeIndex)
            return if (isActiveMode) t.primary else t.secondary
        }

        private fun updateGradients() {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0 || h <= 0) return

            val t = ThemePalette.get(themeIndex)
            val top = if (isActiveMode) darken(t.secondary) else darken(t.primary)
            val mid = if (isActiveMode) t.secondary else t.primary
            val bot = darken(if (isActiveMode) t.primary else t.secondary)

            bgPaint.shader = LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(0xFF05070A.toInt(), top, mid, bot, 0xFF05070A.toInt()),
                floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
                Shader.TileMode.CLAMP
            )

            val b1 = if (isActiveMode) t.blob2 else t.blob1
            val b2 = if (isActiveMode) t.blob3 else t.blob2
            val b3 = if (isActiveMode) t.blob1 else t.blob3

            blobPaint1.shader = RadialGradient(w * 0.3f, h * 0.3f, w * 0.7f, intArrayOf(b1, 0x00000000), null, Shader.TileMode.CLAMP)
            blobPaint2.shader = RadialGradient(w * 0.7f, h * 0.6f, w * 0.8f, intArrayOf(b2, 0x00000000), null, Shader.TileMode.CLAMP)
            blobPaint3.shader = RadialGradient(w * 0.5f, h * 0.9f, w * 0.6f, intArrayOf(b3, 0x00000000), null, Shader.TileMode.CLAMP)
        }

        private fun darken(c: Int): Int {
            val r = (Color.red(c) * 0.4f).toInt().coerceIn(0, 255)
            val g = (Color.green(c) * 0.4f).toInt().coerceIn(0, 255)
            val b = (Color.blue(c) * 0.4f).toInt().coerceIn(0, 255)
            return Color.rgb(r, g, b)
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
                val delta = abs(x - lastX) + abs(y - lastY) + abs(z - lastZ)
                if (delta > 12f) {
                    val now = System.currentTimeMillis()
                    if (now - lastShakeTime > 800) {
                        lastShakeTime = now
                        val cx = width / 2f
                        val cy = height / 2f
                        for (f in flakes) {
                            val dx = f.x - cx
                            val dy = f.y - cy
                            val dist = hypot(dx, dy).coerceAtLeast(1f)
                            val force = rand.nextFloat() * 18f + 12f
                            f.x += (dx / dist) * force
                            f.y += (dy / dist) * force
                        }
                        post { onShake?.invoke() }
                    }
                }
                lastX = x; lastY = y; lastZ = z
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            flakes.clear()
            for (i in 0 until 30) {
                flakes.add(
                    Flake(
                        rand.nextFloat() * w,
                        rand.nextFloat() * h,
                        rand.nextFloat() * 2.5f + 1f,
                        rand.nextFloat() * 1.5f + 0.5f,
                        rand.nextFloat() * 0.6f - 0.3f,
                        100 + rand.nextInt(155),
                        rand.nextFloat() * 360f
                    )
                )
            }
            updateGradients()
        }

        override fun onDraw(canvas: Canvas) {
            if (!active) return
            time += 0.01f

            val bmp = bgBitmap
            if (bmp != null) {
                val scale = maxOf(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
                val dx = (width - bmp.width * scale) / 2f
                val dy = (height - bmp.height * scale) / 2f
                canvas.save()
                canvas.translate(dx, dy)
                canvas.scale(scale, scale)
                canvas.drawBitmap(bmp, 0f, 0f, null)
                canvas.restore()
            } else {
                canvas.drawPaint(bgPaint)
            }

            matrix1.setTranslate(sin(time * 0.5f) * width * 0.3f, kotlin.math.cos(time * 0.7f) * height * 0.2f)
            blobPaint1.shader?.setLocalMatrix(matrix1)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), blobPaint1)

            matrix2.setTranslate(kotlin.math.cos(time * 0.4f) * width * 0.4f, sin(time * 0.6f) * height * 0.3f)
            blobPaint2.shader?.setLocalMatrix(matrix2)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), blobPaint2)

            matrix3.setTranslate(sin(time * 0.3f) * width * 0.2f, kotlin.math.cos(time * 0.5f) * height * 0.4f)
            blobPaint3.shader?.setLocalMatrix(matrix3)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), blobPaint3)

            val flakeColor = if (isActiveMode) currentSecondary() else currentPrimary()

            for (f in flakes) {
                f.y += f.speed
                f.x += f.drift + sin(time + f.y * 0.01f) * 0.3f
                f.rotation += 2f
                if (f.y > height + 10) { f.y = -10f; f.x = rand.nextFloat() * width }
                if (f.x > width + 10) f.x = -10f
                if (f.x < -10) f.x = width + 10f

                if (isActiveMode) {
                    flakePaint.color = Color.argb(f.alpha, Color.red(flakeColor), Color.green(flakeColor), Color.blue(flakeColor))
                    flakePaint.setShadowLayer(f.radius * 2, 0f, 0f, flakeColor)
                    canvas.save()
                    canvas.rotate(f.rotation, f.x, f.y)
                    canvas.drawOval(f.x - f.radius, f.y - f.radius * 0.5f, f.x + f.radius, f.y + f.radius * 0.5f, flakePaint)
                    canvas.restore()
                } else {
                    flakePaint.color = Color.argb(f.alpha, Color.red(flakeColor), Color.green(flakeColor), Color.blue(flakeColor))
                    flakePaint.setShadowLayer(f.radius * 3, 0f, 0f, flakeColor)
                    canvas.drawCircle(f.x, f.y, f.radius, flakePaint)
                }
            }
            invalidate()
        }
    }
}
