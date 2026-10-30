package com.example.turboboost

import android.animation.ValueAnimator
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import kotlin.math.sin

class GameOverlayService : Service() {

    private lateinit var wm: WindowManager
    private var hud: TextView? = null
    private var hudLp: WindowManager.LayoutParams? = null
    private var panel: View? = null
    private var panelLp: WindowManager.LayoutParams? = null
    private var statusTv: TextView? = null
    private var contentBox: LinearLayout? = null
    private var tabBar: LinearLayout? = null
    private var cross: CrosshairView? = null
    private var snow: SnowView? = null
    private var fxLayer: FxOverlayView? = null
    private var statsBar: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val statsExecutor = Executors.newSingleThreadExecutor()
    private var info: DeviceInfo? = null
    private var lastPing = ""
    private var sessionStart = 0L
    private var lastGameAt = 0L
    private var lastStatsAt = 0L
    private var pulseAnim: ValueAnimator? = null
    private val prefs by lazy { AppPrefs(this) }
    private var currentFg: String? = null
    private var currentTab = 0

    private val type: Int
        get() = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private val interactFlags: Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    private val passthroughFlags: Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        if (!::wm.isInitialized) wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (sessionStart == 0L) {
            sessionStart = prefs.getSessionStartMs().takeIf { it > 0 } ?: System.currentTimeMillis()
            prefs.setSessionStartMs(sessionStart)
        }
        loop()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        pulseAnim?.cancel()
        removeAll()
        try { statsExecutor.shutdownNow() } catch (_: Throwable) {}
        super.onDestroy()
    }

    private fun loop() {
        try { sync() } catch (_: Throwable) {}
        handler.postDelayed({ loop() }, 1500L)
    }

    private fun sync() {
        if (!prefs.isGameOverlay() || (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this))) {
            removeAll(); return
        }
        val fg = foregroundPkg()
        currentFg = fg
        if (!ForegroundDetector.available(this)) {
            lastGameAt = System.currentTimeMillis()
        } else if (fg == null) {
            if (System.currentTimeMillis() - lastGameAt > 5000L) removeAll()
            return
        } else if (fg == packageName) {
            return
        } else {
            val isGame = prefs.getSavedGames().contains(fg) || fg == prefs.getLastPackage()
            if (isGame) lastGameAt = System.currentTimeMillis()
            else if (System.currentTimeMillis() - lastGameAt > 5000L) { removeAll(); return }
        }
        showHud()
        applyPrefsToViews()
        refreshStatus()
        if (System.currentTimeMillis() - lastStatsAt > 2000L) {
            lastStatsAt = System.currentTimeMillis()
            refreshStatsBar()
        }
    }

    private fun foregroundPkg(): String? = ForegroundDetector.current(this)

    private fun postBg(b: () -> Unit) {
        try { statsExecutor.execute { try { b() } catch (_: Throwable) {} } } catch (_: RejectedExecutionException) {}
    }

    private fun noFocus(v: View): View {
        v.isFocusable = false
        v.isFocusableInTouchMode = false
        if (v is ViewGroup) {
            v.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            for (i in 0 until v.childCount) noFocus(v.getChildAt(i))
        }
        return v
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun themeAccent(): Int = when (prefs.getPanelTheme()) {
        1 -> 0xFF00C7FF.toInt()
        2 -> 0xFFFF2D95.toInt()
        3 -> 0xFF34C759.toInt()
        else -> 0xFFF2580A.toInt()
    }

    private fun themeBg(): Int = when (prefs.getPanelTheme()) {
        1 -> 0xF20A1628.toInt()
        2 -> 0xF21A0A14.toInt()
        3 -> 0xF20A1A0C.toInt()
        else -> 0xF20C0C10.toInt()
    }

    private fun glass(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(18).toFloat()
        setColor(themeBg())
        setStroke(dp(1), themeAccent() and 0x00FFFFFF or 0xCC000000.toInt())
    }

    private fun lbl(t: String, size: Float = 12f, color: Int = Color.WHITE) = TextView(this).apply {
        text = t; setTextColor(color); textSize = size
        isFocusable = false; isFocusableInTouchMode = false
    }

    private fun haptic(v: View) {
        try {
            v.performHapticFeedback(
                if (prefs.isHapticStrong()) android.view.HapticFeedbackConstants.LONG_PRESS
                else android.view.HapticFeedbackConstants.VIRTUAL_KEY
            )
        } catch (_: Throwable) {}
    }

    private fun netType(): String {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return "—"
            val n = cm.activeNetwork ?: return "нет"
            val c = cm.getNetworkCapabilities(n) ?: return "нет"
            when {
                c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "LTE"
                c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "Eth"
                else -> "—"
            }
        } catch (_: Throwable) { "—" }
    }

    private fun cpuLoad(): String {
        val la = try { java.io.File("/proc/loadavg").readText().split(" ").firstOrNull() ?: "0" } catch (_: Throwable) { "0" }
        return "CPU $la"
    }

    private fun showHud() {
        if (hud != null) {
            applyWatermarkStyle(hud!!)
            return
        }
        val t = TextView(this).apply {
            text = watermarkText()
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(7), dp(14), dp(7))
            isFocusable = false
            isFocusableInTouchMode = false
        }
        applyWatermarkStyle(t)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            type, interactFlags, PixelFormat.TRANSLUCENT
        )
        applyWmPosition(lp)
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        hud = t; hudLp = lp
        attachDrag(handle = t, windowView = t, lp = lp) {
            if (panel == null) openPanel() else closePanel()
        }
        try { wm.addView(t, lp) } catch (_: Throwable) { hud = null; return }
        startPulse(t)
    }

    private fun watermarkText(): String {
        val base = when (prefs.getWatermarkStyle()) {
            0 -> "TB"
            2 -> "⚡ TURBO"
            3 -> "◆ TurboBoost"
            4 -> "🔥 BOOST"
            else -> "⚡ TurboBoost"
        }
        val parts = mutableListOf(base)
        if (prefs.isShowGameName() && !currentFg.isNullOrBlank()) {
            parts.add(currentFg!!.substringAfterLast('.').take(10))
        }
        if (prefs.isFeat("wm_timer_on_wm") && sessionStart > 0) {
            val sec = ((System.currentTimeMillis() - sessionStart) / 1000L).coerceAtLeast(0)
            parts.add(String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60))
        }
        if (prefs.isFeat("wm_temp_on_wm")) {
            val t = try { info?.cpuTemp() ?: 0f } catch (_: Throwable) { 0f }
            if (t > 1f) parts.add(String.format(Locale.US, "%.0f°C", t))
        }
        return parts.joinToString(" · ")
    }

    private fun applyWatermarkStyle(t: TextView) {
        val op = prefs.getWatermarkOpacity().coerceIn(20, 100) / 100f
        val style = prefs.getWatermarkStyle()
        t.text = watermarkText()
        t.alpha = op
        val tiny = prefs.isFeat("wm_tiny")
        t.textSize = if (tiny) 8f else 11f
        t.rotation = if (prefs.isFeat("wm_vertical")) -90f else 0f
        if (prefs.isFeat("wm_shadow") || prefs.isFeat("wm_outline")) {
            t.setShadowLayer(6f, 0f, 0f, 0xFF000000.toInt())
        } else {
            t.setShadowLayer(0f, 0f, 0f, 0)
        }
        val bg = GradientDrawable()
        bg.cornerRadius = if (style == 0) dp(20).toFloat() else dp(16).toFloat()
        bg.orientation = GradientDrawable.Orientation.LEFT_RIGHT
        bg.colors = when (style) {
            0 -> intArrayOf(0xCC111118.toInt(), 0xCC1A1A22.toInt())
            2 -> intArrayOf(0xE6FF3B30.toInt(), 0xE6F2580A.toInt())
            3 -> intArrayOf(0xE60A0A0C.toInt(), 0xE61C1C28.toInt())
            4 -> intArrayOf(0xE6F2580A.toInt(), 0xE6FF6B00.toInt())
            else -> intArrayOf(0xE6F2580A.toInt(), 0xE6D94E08.toInt())
        }
        if (prefs.isNeonBorder() || prefs.isFeat("wm_badge")) bg.setStroke(dp(1), 0xFFF2580A.toInt())
        t.background = bg
        t.setTextColor(if (style == 0) 0xFFF2580A.toInt() else Color.WHITE)
    }

    private fun applyWmPosition(lp: WindowManager.LayoutParams) {
        when (prefs.getWatermarkPos()) {
            1 -> { lp.gravity = Gravity.TOP or Gravity.START; lp.x = dp(10); lp.y = dp(6) }
            2 -> { lp.gravity = Gravity.TOP or Gravity.END; lp.x = dp(10); lp.y = dp(6) }
            3 -> { lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; lp.x = 0; lp.y = dp(20) }
            4 -> { lp.gravity = Gravity.BOTTOM or Gravity.END; lp.x = dp(10); lp.y = dp(20) }
            else -> { lp.gravity = Gravity.TOP or Gravity.END; lp.x = dp(10); lp.y = dp(6) }
        }
    }

    private fun startPulse(t: TextView) {
        pulseAnim?.cancel()
        if (!prefs.isWmPulse()) { t.scaleX = 1f; t.scaleY = 1f; return }
        pulseAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2400L
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val v = it.animatedValue as Float
                val s = 1f + 0.025f * sin(v * Math.PI * 2).toFloat()
                t.scaleX = s; t.scaleY = s
            }
            start()
        }
    }

    private fun removeHud() {
        pulseAnim?.cancel(); pulseAnim = null
        try { hud?.let { wm.removeView(it) } } catch (_: Throwable) {}
        hud = null; hudLp = null
        closePanel()
    }private fun applyPrefsToViews() {
    if (prefs.isCrosshair()) showCross() else removeCross()
    if (prefs.isSnow()) showSnow() else removeSnow()
    val needFx = prefs.isGridOverlay() || prefs.isFovCircle() || prefs.isVignette() ||
        prefs.isScanlines() || prefs.isNightTint() || prefs.isRecoilHelper() ||
        prefs.isSafeZone() || prefs.isEdgeFlash() || prefs.isFogOverlay() ||
        prefs.isRadarFrame() || prefs.isChromatic() || prefs.isLowHpVignette() ||
        prefs.isFeat("fx_letterbox") || prefs.isFeat("fx_hud_dim") ||
        prefs.isFeat("fx_warm") || prefs.isFeat("fx_cold") ||
        prefs.isFeat("hud_minimap_frame")
    if (needFx) showFx() else removeFx()
    fxLayer?.applyPrefs(prefs)
    if (prefs.isStatsBar() || prefs.isNetHud() || prefs.isTempHud() ||
        prefs.isBatteryHud() || prefs.isRamHud() || prefs.isSessionTimer() || prefs.isStopwatch() ||
        prefs.isFeat("hud_clock") || prefs.isFeat("hud_date") ||
        prefs.isFeat("hud_net_type") || prefs.isFeat("hud_cpu_load")
    ) showStatsBar() else removeStatsBar()
}

private fun showCross() {
    if (cross == null) {
        val v = CrosshairView(this).also {
            it.isClickable = false; it.isFocusable = false; it.isFocusableInTouchMode = false
            it.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            type, passthroughFlags, PixelFormat.TRANSLUCENT
        )
        cross = v
        try { wm.addView(v, lp) } catch (_: Throwable) { cross = null; return }
    }
    cross?.applyFrom(prefs)
    cross?.alpha = prefs.getOverlayMasterOpacity() / 100f
}

private fun removeCross() {
    try { cross?.let { wm.removeView(it) } } catch (_: Throwable) {}
    cross = null
}

private fun showSnow() {
    if (snow == null) {
        val v = SnowView(this)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            type, passthroughFlags, PixelFormat.TRANSLUCENT
        )
        snow = v
        try { wm.addView(v, lp) } catch (_: Throwable) { snow = null; return }
    }
    snow?.applyFrom(prefs)
    snow?.alpha = (prefs.getOverlayMasterOpacity() / 100f) * 0.85f
}

private fun removeSnow() {
    try { snow?.let { wm.removeView(it) } } catch (_: Throwable) {}
    snow = null
}

private fun showFx() {
    if (fxLayer == null) {
        val v = FxOverlayView(this).also {
            it.isClickable = false; it.isFocusable = false
            it.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            type, passthroughFlags, PixelFormat.TRANSLUCENT
        )
        fxLayer = v
        try { wm.addView(v, lp) } catch (_: Throwable) { fxLayer = null; return }
    }
    fxLayer?.applyPrefs(prefs)
    fxLayer?.alpha = prefs.getOverlayMasterOpacity() / 100f
}

private fun removeFx() {
    try { fxLayer?.let { wm.removeView(it) } } catch (_: Throwable) {}
    fxLayer = null
}

private fun showStatsBar() {
    if (statsBar == null) {
        val t = TextView(this).apply {
            textSize = 10f
            setTextColor(0xFFE8ECF0.toInt())
            typeface = Typeface.MONOSPACE
            setPadding(dp(10), dp(5), dp(10), dp(5))
            isFocusable = false; isFocusableInTouchMode = false
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(0xB3101016.toInt())
                setStroke(dp(1), 0x66F2580A.toInt())
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            type, passthroughFlags, PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = dp(8); lp.y = dp(44)
        statsBar = t
        try { wm.addView(t, lp) } catch (_: Throwable) { statsBar = null }
    }
}

private fun removeStatsBar() {
    try { statsBar?.let { wm.removeView(it) } } catch (_: Throwable) {}
    statsBar = null
}

private fun removeAll() {
    removeHud(); removeCross(); removeSnow(); removeFx(); removeStatsBar()
}

private var dragDX = 0f
private var dragDY = 0f
private var dragMoved = false

private fun attachDrag(
    handle: View,
    windowView: View,
    lp: WindowManager.LayoutParams,
    onTap: (() -> Unit)?
) {
    handle.isFocusable = false
    handle.isFocusableInTouchMode = false
    handle.setOnTouchListener { _, ev ->
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragDX = ev.rawX; dragDY = ev.rawY; dragMoved = false
                true
            }
            MotionEvent.ACTION_MOVE -> {
                if (prefs.isPanelLocked() && onTap == null) return@setOnTouchListener true
                val dx = ev.rawX - dragDX; val dy = ev.rawY - dragDY
                if (kotlin.math.abs(dx) > dp(6) || kotlin.math.abs(dy) > dp(6)) dragMoved = true
                if (dragMoved) {
                    if (lp.gravity != (Gravity.TOP or Gravity.START)) {
                        val loc = IntArray(2)
                        windowView.getLocationOnScreen(loc)
                        lp.gravity = Gravity.TOP or Gravity.START
                        lp.x = loc[0]
                        lp.y = loc[1]
                    }
                    lp.x += dx.toInt()
                    lp.y += dy.toInt()
                    dragDX = ev.rawX
                    dragDY = ev.rawY
                    try { wm.updateViewLayout(windowView, lp) } catch (_: Throwable) {}
                }
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragMoved) {
                    haptic(handle)
                    onTap?.invoke()
                }
                true
            }
            else -> false
        }
    }
}

private fun btn(t: String, onClick: (TextView) -> Unit): TextView = TextView(this).apply {
    text = t; setTextColor(Color.WHITE); textSize = 12f; gravity = Gravity.CENTER
    setPadding(dp(10), dp(9), dp(10), dp(9))
    isFocusable = false; isFocusableInTouchMode = false
    background = GradientDrawable().apply {
        cornerRadius = dp(11).toFloat(); setColor(0xFF1A1A22.toInt())
    }
    setOnClickListener { haptic(it); onClick(this) }
}

private fun toggle(title: String, get: () -> Boolean, set: (Boolean) -> Unit): LinearLayout {
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(10), dp(12), dp(10))
        isFocusable = false; isFocusableInTouchMode = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        background = GradientDrawable().apply {
            cornerRadius = dp(11).toFloat(); setColor(0xFF14141A.toInt())
        }
    }
    val name = lbl(title, 12f)
    val st = lbl(if (get()) "ВКЛ" else "ВЫКЛ", 11f, if (get()) 0xFF34D399.toInt() else 0xFF6B7280.toInt())
    row.addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    row.addView(st)
    row.setOnClickListener {
        haptic(it)
        val next = !get()
        set(next)
        st.text = if (next) "ВКЛ" else "ВЫКЛ"
        st.setTextColor(if (next) 0xFF34D399.toInt() else 0xFF6B7280.toInt())
    }
    return row
}

private fun stepper(title: String, value: () -> String, onDelta: (Int) -> Unit): LinearLayout {
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(4), dp(8), dp(4))
        isFocusable = false; isFocusableInTouchMode = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }
    val valTv = lbl(value(), 12f, themeAccent()).apply { setPadding(dp(8), 0, dp(8), 0) }
    row.addView(lbl(title, 12f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    row.addView(btn("−") { onDelta(-1); valTv.text = value() })
    row.addView(valTv)
    row.addView(btn("+") { onDelta(1); valTv.text = value() })
    return row
}

private fun cycle(title: String, label: () -> String, onCycle: () -> Unit): LinearLayout {
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(10), dp(12), dp(10))
        isFocusable = false; isFocusableInTouchMode = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        background = GradientDrawable().apply {
            cornerRadius = dp(11).toFloat(); setColor(0xFF14141A.toInt())
        }
    }
    val valTv = lbl(label(), 12f, themeAccent())
    row.addView(lbl(title, 12f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    row.addView(valTv)
    row.setOnClickListener {
        haptic(it)
        onCycle()
        valTv.text = label()
    }
    return row
}

private fun matchM(top: Int = 3) = LinearLayout.LayoutParams(
    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
).apply { topMargin = dp(top) }

private fun header(text: String) = lbl(text, 10f, themeAccent()).apply {
    typeface = Typeface.DEFAULT_BOLD
    setPadding(dp(2), dp(10), dp(2), dp(4))
    letterSpacing = 0.06f
}

private val tabNames = arrayOf("Прицел", "Эффекты", "HUD", "Знак", "Турбо")

private fun openPanel() {
    if (panel != null) return

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(10), dp(12), dp(12))
        background = glass()
        isFocusable = false
        isFocusableInTouchMode = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }

    val title = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }
    title.addView(lbl("⚡ Меню", 14f, themeAccent()).apply {
        typeface = Typeface.DEFAULT_BOLD
    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    title.addView(btn("✕") { closePanel() }.apply { setTextColor(0xFFFF6B5E.toInt()) })
    root.addView(title)

    val st = lbl("игра не на паузе · тап снаружи = в игру", 9f, 0xFF7A8494.toInt()).apply {
        setPadding(0, dp(2), 0, dp(6))
    }
    statusTv = st
    root.addView(st)

    val tabsScroll = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        isFocusable = false
        isFocusableInTouchMode = false
    }
    val tabs = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        isFocusable = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }
    tabBar = tabs
    tabNames.forEachIndexed { idx, name ->
        tabs.addView(makeTabChip(name, idx == currentTab) { selectTab(idx) })
    }
    tabsScroll.addView(tabs)
    root.addView(tabsScroll, matchM(2))

    val scroll = ScrollView(this).apply {
        isVerticalScrollBarEnabled = true
        isFocusable = false
        isFocusableInTouchMode = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
    }
    val box = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        isFocusable = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }
    contentBox = box
    scroll.addView(box)
    root.addView(scroll, LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
    ).apply { topMargin = dp(6) })

    noFocus(root)

    val lp = WindowManager.LayoutParams(
        dp(300), dp(420),
        type, interactFlags, PixelFormat.TRANSLUCENT
    )
    lp.gravity = Gravity.TOP or Gravity.END
    lp.x = dp(8)
    lp.y = dp(52)
    if (Build.VERSION.SDK_INT >= 28) {
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    panel = root
    panelLp = lp
    attachDrag(handle = title, windowView = root, lp = lp, onTap = null)
    try {
        wm.addView(root, lp)
    } catch (_: Throwable) {
        panel = null; panelLp = null; return
    }
    selectTab(currentTab)
    refreshStatus()
}

private fun makeTabChip(name: String, selected: Boolean, onClick: () -> Unit): TextView {
    return TextView(this).apply {
        text = name
        textSize = 11f
        setTextColor(if (selected) Color.WHITE else 0xFF9AA3B2.toInt())
        setPadding(dp(12), dp(7), dp(12), dp(7))
        isFocusable = false; isFocusableInTouchMode = false
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(if (selected) themeAccent() and 0x00FFFFFF or 0xDD000000.toInt() else 0xFF1A1A22.toInt())
            if (selected) setStroke(dp(1), themeAccent())
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.marginEnd = dp(6)
        layoutParams = lp
        setOnClickListener { haptic(it); onClick() }
    }
}

private fun selectTab(idx: Int) {
    currentTab = idx
    tabBar?.let { bar ->
        bar.removeAllViews()
        tabNames.forEachIndexed { i, name ->
            bar.addView(makeTabChip(name, i == idx) { selectTab(i) })
        }
    }
    val box = contentBox ?: return
    box.removeAllViews()
    when (idx) {
        0 -> fillTabCrosshair(box)
        1 -> fillTabEffects(box)
        2 -> fillTabHud(box)
        3 -> fillTabWatermark(box)
        else -> fillTabTurbo(box)
    }
    noFocus(box)
}private fun fillTabCrosshair(box: LinearLayout) {
    box.addView(header("ПРИЦЕЛ"))
    box.addView(toggle("Прицел", { prefs.isCrosshair() }) {
        prefs.setCrosshair(it); applyPrefsToViews()
    }, matchM())
    box.addView(cycle("Стиль", {
        CrosshairView.STYLE_NAMES.getOrElse(prefs.getCrosshairStyle()) { "?" }
    }) {
        prefs.setCrosshairStyle((prefs.getCrosshairStyle() + 1) % CrosshairView.STYLE_NAMES.size)
        cross?.applyFrom(prefs)
    }, matchM())
    box.addView(cycle("Цвет", { "● цвет ${prefs.getCrosshairColor() + 1}" }) {
        prefs.setCrosshairColor((prefs.getCrosshairColor() + 1) % CrosshairView.PALETTE.size)
        cross?.applyFrom(prefs)
    }, matchM())
    box.addView(stepper("Размер", { "${prefs.getCrosshairSize()}/5" }) {
        prefs.setCrosshairSize(prefs.getCrosshairSize() + it); cross?.applyFrom(prefs)
    })
    box.addView(stepper("Прозрачность", { "${prefs.getCrosshairOpacity()}%" }) {
        prefs.setCrosshairOpacity(prefs.getCrosshairOpacity() + it * 10); cross?.applyFrom(prefs)
    })
    box.addView(stepper("Зазор", { "${prefs.getCrosshairGap()}" }) {
        prefs.setCrosshairGap(prefs.getCrosshairGap() + it); cross?.applyFrom(prefs)
    })
    box.addView(toggle("Обводка", { prefs.isCrosshairOutline() }) {
        prefs.setCrosshairOutline(it); cross?.applyFrom(prefs)
    }, matchM())

    box.addView(header("ПОЗИЦИЯ (от центра)"))
    box.addView(stepper("Влево / вправо (X)", { "${prefs.getCrosshairOffsetX()} dp" }) { d ->
        prefs.setCrosshairOffsetX(prefs.getCrosshairOffsetX() + d * 2)
        cross?.applyFrom(prefs)
    })
    box.addView(stepper("Вверх / вниз (Y)", { "${prefs.getCrosshairOffsetY()} dp" }) { d ->
        prefs.setCrosshairOffsetY(prefs.getCrosshairOffsetY() + d * 2)
        cross?.applyFrom(prefs)
    })

    val pad = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        isFocusable = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        setPadding(0, dp(4), 0, dp(4))
    }
    fun move(dx: Int, dy: Int) {
        if (dx != 0) prefs.setCrosshairOffsetX(prefs.getCrosshairOffsetX() + dx)
        if (dy != 0) prefs.setCrosshairOffsetY(prefs.getCrosshairOffsetY() + dy)
        cross?.applyFrom(prefs)
    }
    val step = 4
    pad.addView(btn("↑ вверх") { move(0, -step) }, matchM(2))
    val mid = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        isFocusable = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }
    mid.addView(btn("←") { move(-step, 0) })
    mid.addView(btn("● центр") {
        prefs.setCrosshairOffsetX(0)
        prefs.setCrosshairOffsetY(0)
        cross?.applyFrom(prefs)
    }.apply { setPadding(dp(12), dp(9), dp(12), dp(9)) })
    mid.addView(btn("→") { move(step, 0) })
    pad.addView(mid, matchM(2))
    pad.addView(btn("↓ вниз") { move(0, step) }, matchM(2))
    box.addView(pad, matchM(4))

    box.addView(lbl("X: − влево / + вправо · Y: − вверх / + вниз", 9f, 0xFF7A8494.toInt()).apply {
        gravity = Gravity.CENTER
        setPadding(0, dp(2), 0, dp(6))
    })

    box.addView(header("ДОПОЛНИТЕЛЬНО"))
    listOf(
        "cx_center_dot" to "Центральная точка",
        "cx_mirror" to "Зеркальный прицел",
        "cx_thick" to "Толстые линии",
        "cx_pixel" to "Пиксельный стиль",
        "cx_rainbow" to "Радужный прицел"
    ).forEach { (k, title) ->
        box.addView(toggle(title, { prefs.isFeat(k) }) {
            prefs.setFeat(k, it); cross?.applyFrom(prefs)
        }, matchM())
    }
}

private fun fillTabEffects(box: LinearLayout) {
    box.addView(header("ЧАСТИЦЫ (не мешают прицелу)"))
    box.addView(toggle("Частицы", { prefs.isSnow() }) {
        prefs.setSnow(it); applyPrefsToViews()
    }, matchM())
    box.addView(cycle("Режим", {
        arrayOf("Снег", "Дождь", "Искры", "Листья")[prefs.getParticleMode()]
    }) {
        prefs.setParticleMode((prefs.getParticleMode() + 1) % 4)
        snow?.applyFrom(prefs)
    }, matchM())
    box.addView(stepper("Плотность", { "${prefs.getSnowDensity()}" }) {
        prefs.setSnowDensity(prefs.getSnowDensity() + it * 5); snow?.applyFrom(prefs)
    })
    box.addView(stepper("Скорость", { "${prefs.getSnowSpeed()}/5" }) {
        prefs.setSnowSpeed(prefs.getSnowSpeed() + it); snow?.applyFrom(prefs)
    })
    box.addView(stepper("Размер", { "${prefs.getSnowSize()}/5" }) {
        prefs.setSnowSize(prefs.getSnowSize() + it); snow?.applyFrom(prefs)
    })
    box.addView(stepper("Чистый центр %", { "${prefs.getParticleClearCenter()}" }) {
        prefs.setParticleClearCenter(prefs.getParticleClearCenter() + it)
        snow?.applyFrom(prefs)
    })
    box.addView(header("ВИЗУАЛ"))
    fun fx(title: String, get: () -> Boolean, set: (Boolean) -> Unit) {
        box.addView(toggle(title, get) { set(it); applyPrefsToViews() }, matchM())
    }
    fx("Сетка", { prefs.isGridOverlay() }, { prefs.setGridOverlay(it) })
    fx("Круг FOV", { prefs.isFovCircle() }, { prefs.setFovCircle(it) })
    fx("Виньетка", { prefs.isVignette() }, { prefs.setVignette(it) })
    fx("Scanlines CRT", { prefs.isScanlines() }, { prefs.setScanlines(it) })
    fx("Ночной фильтр", { prefs.isNightTint() }, { prefs.setNightTint(it) })
    fx("Туман по краям", { prefs.isFogOverlay() }, { prefs.setFogOverlay(it) })
    fx("Хроматика", { prefs.isChromatic() }, { prefs.setChromatic(it) })
    fx("Линия отдачи", { prefs.isRecoilHelper() }, { prefs.setRecoilHelper(it) })
    fx("Safe-zone", { prefs.isSafeZone() }, { prefs.setSafeZone(it) })
    fx("Вспышка краёв", { prefs.isEdgeFlash() }, { prefs.setEdgeFlash(it) })
    fx("Рамка радара", { prefs.isRadarFrame() }, { prefs.setRadarFrame(it) })
    fx("Low-HP виньетка", { prefs.isLowHpVignette() }, { prefs.setLowHpVignette(it) })
    listOf(
        "fx_letterbox" to "Letterbox 21:9",
        "fx_hud_dim" to "Затемнение UI игры",
        "fx_warm" to "Тёплый тон",
        "fx_cold" to "Холодный тон"
    ).forEach { (k, t) ->
        box.addView(toggle(t, { prefs.isFeat(k) }) {
            prefs.setFeat(k, it); applyPrefsToViews()
        }, matchM())
    }
}

private fun fillTabHud(box: LinearLayout) {
    box.addView(header("СТАТИСТИКА"))
    fun h(title: String, get: () -> Boolean, set: (Boolean) -> Unit) {
        box.addView(toggle(title, get) { set(it); applyPrefsToViews() }, matchM())
    }
    h("Панель статистики", { prefs.isStatsBar() }, { prefs.setStatsBar(it) })
    h("Сеть / пинг", { prefs.isNetHud() }, { prefs.setNetHud(it) })
    h("Температура", { prefs.isTempHud() }, { prefs.setTempHud(it) })
    h("Батарея", { prefs.isBatteryHud() }, { prefs.setBatteryHud(it) })
    h("RAM", { prefs.isRamHud() }, { prefs.setRamHud(it) })
    h("Таймер сессии", { prefs.isSessionTimer() }, { prefs.setSessionTimer(it) })
    h("Секундомер", { prefs.isStopwatch() }, { prefs.setStopwatch(it) })
    listOf(
        "hud_clock" to "Часы",
        "hud_date" to "Дата",
        "hud_net_type" to "Тип сети (WiFi/LTE)",
        "hud_cpu_load" to "Загрузка CPU",
        "hud_minimap_frame" to "Рамка миникарты"
    ).forEach { (k, t) ->
        box.addView(toggle(t, { prefs.isFeat(k) }) {
            prefs.setFeat(k, it); applyPrefsToViews()
        }, matchM())
    }
    box.addView(btn("Проверить пинг") {
        it.text = "…"
        postBg {
            val r = try { DeviceInfo(this).pingTest() } catch (_: Throwable) { "н/д" }
            lastPing = r
            handler.post { try { it.text = "Пинг: $r"; refreshStatus() } catch (_: Throwable) {} }
        }
    }, matchM(8))
    box.addView(btn("Сбросить таймер") {
        sessionStart = System.currentTimeMillis()
        prefs.setSessionStartMs(sessionStart)
        refreshStatsBar()
    }, matchM(4))
}

private fun fillTabWatermark(box: LinearLayout) {
    box.addView(header("ВОДЯНОЙ ЗНАК"))
    box.addView(cycle("Стиль", {
        arrayOf("Мини", "Классика", "Яркий", "Диам.", "Огонь")[prefs.getWatermarkStyle()]
    }) {
        prefs.setWatermarkStyle((prefs.getWatermarkStyle() + 1) % 5)
        hud?.let { applyWatermarkStyle(it) }
    }, matchM())
    box.addView(cycle("Позиция", {
        arrayOf("Верх↔", "Верх←", "Верх→", "Низ↔", "Низ→")[prefs.getWatermarkPos()]
    }) {
        prefs.setWatermarkPos((prefs.getWatermarkPos() + 1) % 5)
        hudLp?.let { lp ->
            applyWmPosition(lp)
            try { hud?.let { wm.updateViewLayout(it, lp) } } catch (_: Throwable) {}
        }
    }, matchM())
    box.addView(stepper("Прозрачность", { "${prefs.getWatermarkOpacity()}%" }) {
        prefs.setWatermarkOpacity(prefs.getWatermarkOpacity() + it * 5)
        hud?.let { applyWatermarkStyle(it) }
    })
    box.addView(toggle("Пульс", { prefs.isWmPulse() }) {
        prefs.setWmPulse(it); hud?.let { h -> startPulse(h) }
    }, matchM())
    box.addView(toggle("Неон", { prefs.isNeonBorder() }) {
        prefs.setNeonBorder(it); hud?.let { applyWatermarkStyle(it) }
    }, matchM())
    box.addView(toggle("Имя игры", { prefs.isShowGameName() }) {
        prefs.setShowGameName(it); hud?.let { applyWatermarkStyle(it) }
    }, matchM())
    listOf(
        "wm_tiny" to "Крошечный режим",
        "wm_vertical" to "Вертикальный текст",
        "wm_shadow" to "Тень",
        "wm_outline" to "Обводка текста",
        "wm_badge" to "Бейдж",
        "wm_timer_on_wm" to "Таймер на знаке",
        "wm_temp_on_wm" to "Темп. на знаке"
    ).forEach { (k, t) ->
        box.addView(toggle(t, { prefs.isFeat(k) }) {
            prefs.setFeat(k, it); hud?.let { h -> applyWatermarkStyle(h) }
        }, matchM())
    }
}

private fun fillTabTurbo(box: LinearLayout) {
    box.addView(header("ТУРБО И ПРОИЗВОДИТЕЛЬНОСТЬ"))
    box.addView(btn(if (prefs.isActive()) "● Турбо ВКЛ — выключить" else "○ Турбо ВЫКЛ — включить") {
        val next = !prefs.isActive()
        prefs.setActive(next)
        it.text = if (next) "● Турбо ВКЛ — выключить" else "○ Турбо ВЫКЛ — включить"
        postBg {
            try {
                val di = DeviceInfo(this)
                if (next) di.activateMaxPerformance(
                    prefs.getTargetHz(), prefs.getPerfMode(),
                    prefs.isGpuBoost() && di.isGpuBoostSupported(),
                    prefs.getLastPackage()
                ) else di.deactivateMaxPerformance()
            } catch (_: Throwable) {}
        }
    }, matchM())
    box.addView(stepper("Целевой Hz", { "${prefs.getTargetHz()}" }) {
        val cur = prefs.getTargetHz()
        val opts = intArrayOf(60, 90, 120, 144, 165)
        val idx = opts.indexOfFirst { it >= cur }.let { if (it < 0) 0 else it }
        val next = opts[((idx + it).coerceIn(0, opts.lastIndex))]
        prefs.setTargetHz(next)
    })
    box.addView(toggle("GPU Boost", { prefs.isGpuBoost() }) {
        val di = info ?: DeviceInfo(this).also { info = it }
        if (!di.isGpuBoostSupported()) {
            android.widget.Toast.makeText(this, "Требуется root", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            prefs.setGpuBoost(it)
        }
    }, matchM())
    box.addView(toggle("Лимит фона", { prefs.isBackgroundLimit() }) {
        prefs.setBackgroundLimit(it)
    }, matchM())
    box.addView(toggle("Threads boost", { prefs.isThreadsBoost() }) {
        val di = info ?: DeviceInfo(this).also { info = it }
        if (!di.isThreadTuningSupported()) {
            android.widget.Toast.makeText(this, "Требуется root", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            prefs.setThreadsBoost(it)
        }
    }, matchM())
    box.addView(header("БЫСТРЫЕ ДЕЙСТВИЯ"))
    box.addView(btn("Скрыть оверлей на 15 сек") {
        removeAll()
        handler.postDelayed({ try { sync() } catch (_: Throwable) {} }, 15_000L)
    }, matchM(6))
    box.addView(btn("Скрыть оверлей на 30 сек") {
        removeAll()
        handler.postDelayed({ try { sync() } catch (_: Throwable) {} }, 30_000L)
    }, matchM(4))
    box.addView(btn("Только прицел (выкл. всё лишнее)") {
        prefs.setSnow(false)
        prefs.setGridOverlay(false); prefs.setFovCircle(false)
        prefs.setVignette(false); prefs.setScanlines(false)
        prefs.setNightTint(false); prefs.setFogOverlay(false)
        prefs.setStatsBar(false)
        prefs.setCrosshair(true)
        applyPrefsToViews()
        closePanel()
    }, matchM(4))
}

private fun closePanel() {
    try { panel?.let { wm.removeView(it) } } catch (_: Throwable) {}
    panel = null; panelLp = null; statusTv = null
    contentBox = null; tabBar = null
}

private fun refreshStatus() {
    val tv = statusTv ?: return
    if (System.currentTimeMillis() - lastStatsAt < 800L) return
    postBg {
        if (info == null) info = DeviceInfo(this)
        val i = info ?: return@postBg
        val temp = try { i.cpuTemp() } catch (_: Throwable) { 0f }
        val bat = try { i.batteryLevel() } catch (_: Throwable) { -1 }
        val active = prefs.isActive()
        handler.post {
            try {
                val sb = StringBuilder()
                sb.append(String.format(Locale.US, "%.0f°C", temp))
                if (bat >= 0) sb.append(" · ").append(bat).append("%")
                sb.append(if (active) " · ТУРБО" else " · idle")
                if (lastPing.isNotBlank()) sb.append(" · ").append(lastPing)
                tv.text = sb.toString()
            } catch (_: Throwable) {}
        }
    }
}

private fun refreshStatsBar() {
    val tv = statsBar ?: return
    postBg {
        if (info == null) info = DeviceInfo(this)
        val i = info
        val parts = mutableListOf<String>()
        if (prefs.isTempHud() && i != null) {
            val t = try { i.cpuTemp() } catch (_: Throwable) { 0f }
            parts.add(String.format(Locale.US, "%.0f°C", t))
        }
        if (prefs.isBatteryHud() && i != null) {
            val b = try { i.batteryLevel() } catch (_: Throwable) { -1 }
            if (b >= 0) parts.add("$b%")
        }
        if (prefs.isRamHud()) {
            val rt = Runtime.getRuntime()
            val used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
            parts.add("${used}MB")
        }
        if (prefs.isNetHud() && lastPing.isNotBlank()) parts.add(lastPing)
        if (prefs.isSessionTimer() || prefs.isStopwatch()) {
            val sec = ((System.currentTimeMillis() - sessionStart) / 1000L).coerceAtLeast(0)
            parts.add(String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60))
        }
        if (prefs.isFeat("hud_clock")) parts.add(SimpleDateFormat("HH:mm", Locale.US).format(Date()))
        if (prefs.isFeat("hud_date")) parts.add(SimpleDateFormat("dd.MM", Locale.US).format(Date()))
        if (prefs.isFeat("hud_net_type")) parts.add(netType())
        if (prefs.isFeat("hud_cpu_load")) parts.add(cpuLoad())
        if (prefs.isActive()) parts.add("TURBO")
        val text = if (parts.isEmpty()) "TB" else parts.joinToString(" · ")
        handler.post { try { tv.text = text } catch (_: Throwable) {} }
    }
}    private class FxOverlayView(context: Context) : View(context) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private var grid = false
        private var fov = false
        private var vignette = false
        private var scan = false
        private var night = false
        private var fog = false
        private var recoil = false
        private var safe = false
        private var edge = false
        private var radar = false
        private var lowHp = false
        private var chroma = false
        private var letterbox = false
        private var hudDim = false
        private var warm = false
        private var cold = false
        private var minimap = false
        private var t = 0f

        init {
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        fun applyPrefs(prefs: AppPrefs) {
            grid = prefs.isGridOverlay()
            fov = prefs.isFovCircle()
            vignette = prefs.isVignette()
            scan = prefs.isScanlines()
            night = prefs.isNightTint()
            fog = prefs.isFogOverlay()
            recoil = prefs.isRecoilHelper()
            safe = prefs.isSafeZone()
            edge = prefs.isEdgeFlash()
            radar = prefs.isRadarFrame()
            lowHp = prefs.isLowHpVignette()
            chroma = prefs.isChromatic()
            letterbox = prefs.isFeat("fx_letterbox")
            hudDim = prefs.isFeat("fx_hud_dim")
            warm = prefs.isFeat("fx_warm")
            cold = prefs.isFeat("fx_cold")
            minimap = prefs.isFeat("hud_minimap_frame")
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2f
            val cy = h / 2f
            t += 0.016f

            if (hudDim) {
                p.style = Paint.Style.FILL
                p.color = 0x33000000.toInt()
                canvas.drawRect(0f, 0f, w, h, p)
            }
            if (warm) {
                p.style = Paint.Style.FILL
                p.color = 0x22FF8800.toInt()
                canvas.drawRect(0f, 0f, w, h, p)
            }
            if (cold) {
                p.style = Paint.Style.FILL
                p.color = 0x2200AAFF.toInt()
                canvas.drawRect(0f, 0f, w, h, p)
            }
            if (night) {
                p.style = Paint.Style.FILL
                p.color = 0x33001A33.toInt()
                canvas.drawRect(0f, 0f, w, h, p)
            }
            if (vignette || lowHp) {
                val radial = android.graphics.RadialGradient(
                    cx, cy, minOf(w, h) * 0.72f,
                    intArrayOf(0x00000000, if (lowHp) 0x55FF2020.toInt() else 0x66000000),
                    floatArrayOf(0.55f, 1f),
                    android.graphics.Shader.TileMode.CLAMP
                )
                p.shader = radial
                p.style = Paint.Style.FILL
                canvas.drawRect(0f, 0f, w, h, p)
                p.shader = null
            }
            if (fog) {
                p.style = Paint.Style.FILL
                p.color = 0x22FFFFFF
                canvas.drawRect(0f, 0f, w, h * 0.12f, p)
                canvas.drawRect(0f, h * 0.88f, w, h, p)
            }
            if (scan) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1f
                p.color = 0x18FFFFFF
                var y = 0f
                while (y < h) {
                    canvas.drawLine(0f, y, w, y, p)
                    y += 3f
                }
            }
            if (grid) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1f
                p.color = 0x33FFFFFF
                canvas.drawLine(w / 3f, 0f, w / 3f, h, p)
                canvas.drawLine(2 * w / 3f, 0f, 2 * w / 3f, h, p)
                canvas.drawLine(0f, h / 3f, w, h / 3f, p)
                canvas.drawLine(0f, 2 * h / 3f, w, 2 * h / 3f, p)
            }
            if (fov) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 2f
                p.color = 0x55F2580A.toInt()
                canvas.drawCircle(cx, cy, minOf(w, h) * 0.22f, p)
            }
            if (recoil) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 2f
                p.color = 0x88FF3B30.toInt()
                canvas.drawLine(cx, cy, cx, cy - h * 0.18f, p)
            }
            if (safe) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 2f
                p.color = 0x44FFFFFF
                val m = w * 0.06f
                canvas.drawRect(m, m, w - m, h - m, p)
            }
            if (edge) {
                val a = ((sin(t * 3.0) * 0.5 + 0.5) * 80).toInt().coerceIn(20, 90)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 8f
                p.color = Color.argb(a, 255, 60, 40)
                canvas.drawRect(4f, 4f, w - 4f, h - 4f, p)
            }
            if (radar) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 2f
                p.color = 0x55FFFFFF
                val rw = w * 0.18f
                canvas.drawRect(16f, 16f, 16f + rw, 16f + rw, p)
            }
            if (chroma) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1f
                p.color = 0x22FF0000
                canvas.drawCircle(cx - 2f, cy, minOf(w, h) * 0.2f, p)
                p.color = 0x2200FFFF
                canvas.drawCircle(cx + 2f, cy, minOf(w, h) * 0.2f, p)
            }
            if (letterbox) {
                val bar = (h - w * 9f / 21f) / 2f
                if (bar > 0f) {
                    p.style = Paint.Style.FILL
                    p.color = 0xFF000000.toInt()
                    canvas.drawRect(0f, 0f, w, bar, p)
                    canvas.drawRect(0f, h - bar, w, h, p)
                }
            }
            if (minimap) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 3f
                p.color = 0xAAFFFFFF.toInt()
                val m = w * 0.12f
                canvas.drawRect(w - m - 16f, 16f, w - 16f, m + 16f, p)
            }
            if (edge || lowHp) postInvalidateOnAnimation()
        }
    }
}
