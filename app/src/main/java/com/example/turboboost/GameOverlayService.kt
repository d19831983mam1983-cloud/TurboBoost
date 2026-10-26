package com.example.turboboost

import android.animation.ValueAnimator
import android.app.Service
import android.app.usage.UsageStatsManager
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
import java.util.Locale
import kotlin.math.sin

/**
 * Игровой оверлей 3.1
 * — НЕ забирает фокус у игры (игра не встаёт на паузу при тапах по меню)
 * — частицы не лезут в центр / не перехватывают тачи
 * — меню по вкладкам: Прицел · Эффекты · HUD · Знак · Игра · Ещё
 */
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
    private var info: DeviceInfo? = null
    private var lastPing = ""
    private var lastFps = "—"
    private var sessionStart = 0L
    private var pulseAnim: ValueAnimator? = null
    private val prefs by lazy { AppPrefs(this) }
    private var currentFg: String? = null
    private var currentTab = 0

    private val type: Int
        get() = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    /**
     * Интерактивные окна: можно тапать, но фокус у игры НЕ забираем.
     * FLAG_NOT_FOCUSABLE — игра не получает onPause / onWindowFocusChanged(false).
     * FLAG_NOT_TOUCH_MODAL — тапы мимо окна идут в игру.
     */
    private val interactFlags: Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    /** Полностью прозрачные для тачей слои (прицел, снег, fx). */
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
        // если приложение на экране определить нельзя вообще (нет Shizuku и доступа к статистике), показываем всегда
        val isGame = if (!ForegroundDetector.available(this)) true
        else if (fg == null) return
        else (fg != packageName && (prefs.getSavedGames().contains(fg) || fg == prefs.getLastPackage()))
        if (isGame) {
            showHud()
            applyPrefsToViews()
            refreshStatus()
            refreshStatsBar()
        } else {
            removeAll()
        }
    }

    private fun foregroundPkg(): String? = ForegroundDetector.current(this)

    private fun hasUsageAccess(): Boolean {
        return try {
            val appOps = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= 29)
                appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
            else
                @Suppress("DEPRECATION") appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (_: Throwable) { false }
    }

    // ---------- helpers: no focus ----------

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

    // ---------- watermark ----------

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
        // критично: окно не должно получать фокус
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
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
        if (prefs.isShowGameName() && !currentFg.isNullOrBlank()) {
            val short = currentFg!!.substringAfterLast('.').take(10)
            return "$base · $short"
        }
        return base
    }

    private fun applyWatermarkStyle(t: TextView) {
        val op = prefs.getWatermarkOpacity().coerceIn(20, 100) / 100f
        val style = prefs.getWatermarkStyle()
        t.text = watermarkText()
        t.alpha = op
        t.background = GradientDrawable().apply {
            cornerRadius = if (style == 0) dp(20).toFloat() else dp(16).toFloat()
            orientation = GradientDrawable.Orientation.LEFT_RIGHT
            colors = when (style) {
                0 -> intArrayOf(0xCC111118.toInt(), 0xCC1A1A22.toInt())
                2 -> intArrayOf(0xE6FF3B30.toInt(), 0xE6F2580A.toInt())
                3 -> intArrayOf(0xE60A0A0C.toInt(), 0xE61C1C28.toInt())
                4 -> intArrayOf(0xE6F2580A.toInt(), 0xE6FF6B00.toInt())
                else -> intArrayOf(0xE6F2580A.toInt(), 0xE6D94E08.toInt())
            }
            if (prefs.isNeonBorder()) setStroke(dp(1), 0xFFF2580A.toInt())
        }
        t.setTextColor(if (style == 0) 0xFFF2580A.toInt() else Color.WHITE)
    }

    private fun applyWmPosition(lp: WindowManager.LayoutParams) {
        when (prefs.getWatermarkPos()) {
            1 -> { lp.gravity = Gravity.TOP or Gravity.START; lp.x = dp(10); lp.y = dp(6) }
            2 -> { lp.gravity = Gravity.TOP or Gravity.END; lp.x = dp(10); lp.y = dp(6) }
            3 -> { lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; lp.x = 0; lp.y = dp(20) }
            4 -> { lp.gravity = Gravity.BOTTOM or Gravity.END; lp.x = dp(10); lp.y = dp(20) }
            else -> { lp.gravity = Gravity.TOP or Gravity.END; lp.x = dp(10); lp.y = dp(6) } // default: угол, не центр
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
    }

    // ---------- layers (passthrough) ----------

    private fun applyPrefsToViews() {
        if (prefs.isCrosshair()) showCross() else removeCross()
        // частицы только если явно включены
        if (prefs.isSnow()) showSnow() else removeSnow()
        val needFx = prefs.isGridOverlay() || prefs.isFovCircle() || prefs.isVignette() ||
            prefs.isScanlines() || prefs.isNightTint() || prefs.isRecoilHelper() ||
            prefs.isSafeZone() || prefs.isEdgeFlash() || prefs.isFogOverlay() ||
            prefs.isRadarFrame() || prefs.isChromatic() || prefs.isLowHpVignette()
        if (needFx) showFx() else removeFx()
        fxLayer?.applyPrefs(prefs)
        if (prefs.isStatsBar() || prefs.isFpsHud() || prefs.isNetHud() || prefs.isTempHud() ||
            prefs.isBatteryHud() || prefs.isRamHud() || prefs.isSessionTimer() || prefs.isStopwatch()
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
        refreshStatsBar()
    }

    private fun removeStatsBar() {
        try { statsBar?.let { wm.removeView(it) } } catch (_: Throwable) {}
        statsBar = null
    }

    private fun removeAll() {
        removeHud(); removeCross(); removeSnow(); removeFx(); removeStatsBar()
    }

    // ---------- drag (без фокуса) ----------
    // Важно: updateViewLayout вызывается только на окне WindowManager (windowView),
    // а не на дочернем View — иначе LayoutParams станет WM.LayoutParams и LinearLayout упадёт с ClassCastException.

    private var dragDX = 0f; private var dragDY = 0f; private var dragMoved = false

    /**
     * @param handle View, за который тянем (может быть дочерним)
     * @param windowView View, который реально добавлен в WindowManager
     * @param lp параметры окна windowView
     */
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

    // ---------- UI building blocks ----------

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

    // ---------- TAB MENU ----------

    private val tabNames = arrayOf("Прицел", "Эффекты", "HUD", "Знак", "Игра", "Ещё")

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

        // title row
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

        // tabs
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

        // content
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

        // панель сбоку, компактная — не перекрывает центр игры
        val lp = WindowManager.LayoutParams(
            dp(300), dp(420),
            type, interactFlags, PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.END
        lp.x = dp(8)
        lp.y = dp(52)
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        panel = root
        panelLp = lp
        // тянем за заголовок, но updateViewLayout — только на root (окно WM)
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
        // refresh tab chips
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
            4 -> fillTabGame(box)
            else -> fillTabMore(box)
        }
        noFocus(box)
    }

    // ===== TAB: ПРИЦЕЛ =====
    private fun fillTabCrosshair(box: LinearLayout) {
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

        // ----- позиция прицела -----
        box.addView(header("ПОЗИЦИЯ (от центра)"))
        box.addView(stepper("Влево / вправо (X)", { "${prefs.getCrosshairOffsetX()} dp" }) { d ->
            prefs.setCrosshairOffsetX(prefs.getCrosshairOffsetX() + d * 2)
            cross?.applyFrom(prefs)
        })
        box.addView(stepper("Вверх / вниз (Y)", { "${prefs.getCrosshairOffsetY()} dp" }) { d ->
            // + на степпере = вниз (как ось Y экрана)
            prefs.setCrosshairOffsetY(prefs.getCrosshairOffsetY() + d * 2)
            cross?.applyFrom(prefs)
        })

        // D-pad: ↑ ← ● → ↓
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
        val step = 4 // dp за одно нажатие
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
        }.apply {
            setPadding(dp(12), dp(9), dp(12), dp(9))
        })
        mid.addView(btn("→") { move(step, 0) })
        pad.addView(mid, matchM(2))
        pad.addView(btn("↓ вниз") { move(0, step) }, matchM(2))
        box.addView(pad, matchM(4))

        box.addView(lbl("X: − влево / + вправо · Y: − вверх / + вниз", 9f, 0xFF7A8494.toInt()).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(6))
        })

        // extra crosshair-related feats
        listOf(
            "cx_center_dot" to "Центральная точка",
            "cx_dynamic" to "Динамический размер",
            "cx_hit_flash" to "Вспышка попадания",
            "cx_spread" to "Разброс (визуал)",
            "cx_mirror" to "Зеркальный прицел",
            "cx_thick" to "Толстые линии",
            "cx_pixel" to "Пиксельный стиль",
            "cx_rainbow" to "Радужный прицел",
            "cx_hide_ads" to "Скрывать при ADS (mock)",
            "cx_only_hip" to "Только от бедра (mock)"
        ).forEach { (k, title) ->
            box.addView(toggle(title, { prefs.isFeat(k) }) { prefs.setFeat(k, it) }, matchM())
        }
    }

    // ===== TAB: ЭФФЕКТЫ =====
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
            "fx_bloom" to "Bloom (mock)",
            "fx_motion" to "Motion blur (mock)",
            "fx_sharpen" to "Резкость (mock)",
            "fx_warm" to "Тёплый тон",
            "fx_cold" to "Холодный тон",
            "fx_bw" to "Ч/Б фильтр",
            "fx_sepia" to "Сепия",
            "fx_noir" to "Нуар",
            "fx_retro" to "Ретро",
            "fx_neon_edges" to "Неоновые края",
            "fx_letterbox" to "Letterbox 21:9",
            "fx_hud_dim" to "Затемнение UI игры"
        ).forEach { (k, t) ->
            box.addView(toggle(t, { prefs.isFeat(k) }) { prefs.setFeat(k, it); applyPrefsToViews() }, matchM())
        }
    }

    // ===== TAB: HUD =====
    private fun fillTabHud(box: LinearLayout) {
        box.addView(header("СТАТИСТИКА"))
        fun h(title: String, get: () -> Boolean, set: (Boolean) -> Unit) {
            box.addView(toggle(title, get) { set(it); applyPrefsToViews() }, matchM())
        }
        h("Панель статистики", { prefs.isStatsBar() }, { prefs.setStatsBar(it) })
        h("FPS", { prefs.isFpsHud() }, { prefs.setFpsHud(it) })
        h("Сеть / пинг", { prefs.isNetHud() }, { prefs.setNetHud(it) })
        h("Температура", { prefs.isTempHud() }, { prefs.setTempHud(it) })
        h("Батарея", { prefs.isBatteryHud() }, { prefs.setBatteryHud(it) })
        h("RAM", { prefs.isRamHud() }, { prefs.setRamHud(it) })
        h("Таймер сессии", { prefs.isSessionTimer() }, { prefs.setSessionTimer(it) })
        h("Секундомер", { prefs.isStopwatch() }, { prefs.setStopwatch(it) })
        listOf(
            "hud_ping_graph" to "График пинга",
            "hud_fps_graph" to "График FPS",
            "hud_cpu_load" to "Загрузка CPU",
            "hud_gpu_load" to "Загрузка GPU",
            "hud_net_type" to "Тип сети (WiFi/LTE)",
            "hud_clock" to "Часы",
            "hud_date" to "Дата",
            "hud_session_kills" to "Счётчик (mock)",
            "hud_kd" to "K/D (mock)",
            "hud_damage" to "Урон (mock)",
            "hud_accuracy" to "Точность (mock)",
            "hud_compass" to "Компас",
            "hud_altitude" to "Высота (mock)",
            "hud_speed" to "Скорость (mock)",
            "hud_ammo" to "Патроны (mock)",
            "hud_armor" to "Броня (mock)",
            "hud_minimap_frame" to "Рамка миникарты",
            "hud_killfeed" to "Killfeed (mock)",
            "hud_quest" to "Квест-трекер (mock)",
            "hud_party" to "Пати-статус (mock)"
        ).forEach { (k, t) ->
            box.addView(toggle(t, { prefs.isFeat(k) }) { prefs.setFeat(k, it); applyPrefsToViews() }, matchM())
        }
        box.addView(btn("Проверить пинг") {
            it.text = "…"
            Thread {
                val r = try { DeviceInfo(this).pingTest() } catch (_: Throwable) { "н/д" }
                lastPing = r
                handler.post { it.text = "Пинг: $r"; refreshStatus() }
            }.start()
        }, matchM(8))
        box.addView(btn("Сбросить таймер") {
            sessionStart = System.currentTimeMillis()
            prefs.setSessionStartMs(sessionStart)
            refreshStatsBar()
        }, matchM(4))
    }

    // ===== TAB: ЗНАК =====
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
            "wm_hide_combat" to "Скрывать в бою (mock)",
            "wm_tiny" to "Крошечный режим",
            "wm_vertical" to "Вертикальный текст",
            "wm_shadow" to "Тень",
            "wm_outline" to "Обводка текста",
            "wm_badge" to "Бейдж",
            "wm_timer_on_wm" to "Таймер на знаке",
            "wm_temp_on_wm" to "Темп. на знаке"
        ).forEach { (k, t) ->
            box.addView(toggle(t, { prefs.isFeat(k) }) { prefs.setFeat(k, it) }, matchM())
        }
    }

    // ===== TAB: ИГРА =====
    private fun fillTabGame(box: LinearLayout) {
        box.addView(header("ТУРБО И ПРОИЗВОДИТЕЛЬНОСТЬ"))
        box.addView(btn(if (prefs.isActive()) "● Турбо ВКЛ — выключить" else "○ Турбо ВЫКЛ — включить") {
            val next = !prefs.isActive()
            prefs.setActive(next)
            it.text = if (next) "● Турбо ВКЛ — выключить" else "○ Турбо ВЫКЛ — включить"
            Thread {
                try {
                    val di = DeviceInfo(this)
                    if (next) di.activateMaxPerformance(
                        prefs.getTargetHz(), prefs.getPerfMode(), prefs.isGpuBoost(), prefs.getLastPackage()
                    ) else di.deactivateMaxPerformance()
                } catch (_: Throwable) {}
            }.start()
        }, matchM())
        box.addView(stepper("Целевой Hz", { "${prefs.getTargetHz()}" }) {
            val cur = prefs.getTargetHz()
            val opts = intArrayOf(60, 90, 120, 144, 165)
            val idx = opts.indexOfFirst { it >= cur }.let { if (it < 0) 0 else it }
            val next = opts[((idx + it).coerceIn(0, opts.lastIndex))]
            prefs.setTargetHz(next)
        })
        box.addView(toggle("GPU Boost", { prefs.isGpuBoost() }) { prefs.setGpuBoost(it) }, matchM())
        box.addView(toggle("Лимит фона", { prefs.isBackgroundLimit() }) { prefs.setBackgroundLimit(it) }, matchM())
        box.addView(toggle("Threads boost", { prefs.isThreadsBoost() }) { prefs.setThreadsBoost(it) }, matchM())
        listOf(
            "g_touch_opt" to "Оптимизация тачскрина",
            "g_input_lag" to "Снижение input lag",
            "g_vsync_off" to "VSync off (mock)",
            "g_max_fps" to "Max FPS unlock (mock)",
            "g_thermal" to "Thermal headroom",
            "g_dns_fast" to "Быстрый DNS",
            "g_wifi_scan" to "Блок WiFi scan",
            "g_bt_scan" to "Блок BT scan",
            "g_notifs" to "Глушить уведомления",
            "g_calls" to "Не беспокоить",
            "g_brightness_lock" to "Фикс яркости",
            "g_auto_rotate_off" to "Блок автоповорота",
            "g_nav_hide" to "Скрыть навигацию",
            "g_status_hide" to "Скрыть статус-бар",
            "g_screenshot" to "Быстрый скриншот",
            "g_record" to "Запись экрана (mock)",
            "g_cast_block" to "Блок каста",
            "g_secure" to "Secure flag",
            "g_ping_prio" to "Приоритет сети",
            "g_packet_opt" to "Оптимизация пакетов"
        ).forEach { (k, t) ->
            box.addView(toggle(t, { prefs.isFeat(k) }) { prefs.setFeat(k, it) }, matchM())
        }
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

    // ===== TAB: ЕЩЁ =====
    private fun fillTabMore(box: LinearLayout) {
        box.addView(header("МЕНЮ"))
        box.addView(cycle("Тема панели", {
            arrayOf("Orange", "Ice", "Pink", "Matrix")[prefs.getPanelTheme()]
        }) {
            prefs.setPanelTheme((prefs.getPanelTheme() + 1) % 4)
            closePanel(); openPanel()
        }, matchM())
        box.addView(stepper("Общая прозрачность", { "${prefs.getOverlayMasterOpacity()}%" }) {
            prefs.setOverlayMasterOpacity(prefs.getOverlayMasterOpacity() + it * 5)
            applyPrefsToViews()
        })
        box.addView(toggle("Закрепить панель", { prefs.isPanelLocked() }) { prefs.setPanelLocked(it) }, matchM())
        box.addView(toggle("Сильная вибрация", { prefs.isHapticStrong() }) { prefs.setHapticStrong(it) }, matchM())
        box.addView(header("ДОП. ФИЧИ"))
        // bulk feature list — organized, stored via isFeat
        val more = listOf(
            "m_auto_close" to "Авто-закрытие меню",
            "m_compact" to "Компактные строки",
            "m_large_text" to "Крупный текст",
            "m_icons" to "Иконки в меню",
            "m_search" to "Поиск по фичам",
            "m_favorites" to "Избранные",
            "m_profiles" to "Профили оверлея",
            "m_import" to "Импорт настроек",
            "m_export" to "Экспорт настроек",
            "m_reset" to "Сброс оверлея",
            "m_debug" to "Debug overlay",
            "m_log" to "Лог действий",
            "m_safe_mode" to "Safe mode",
            "m_perf_overlay" to "Perf overlay",
            "m_battery_saver" to "Экономия для оверлея",
            "m_low_power_fx" to "Лёгкие эффекты",
            "m_no_anim" to "Без анимаций",
            "m_rtl" to "RTL меню",
            "m_en" to "English labels",
            "m_tips" to "Подсказки",
            "m_gesture_open" to "Жест открытия",
            "m_double_tap" to "Дабл-тап по знаку",
            "m_long_press" to "Лонг-пресс меню",
            "m_edge_swipe" to "Свайп с края",
            "m_volume_key" to "Кнопка громкости",
            "m_notif_toggle" to "Тоггл из шторки",
            "m_qs_tile" to "Quick Settings tile",
            "m_auto_game" to "Авто-детект игр",
            "m_blacklist" to "Чёрный список",
            "m_whitelist" to "Белый список",
            "m_per_game" to "Настройки на игру",
            "m_cloud_sync" to "Облако (mock)",
            "m_share" to "Поделиться пресетом",
            "m_rate" to "Оценить",
            "m_about" to "О приложении",
            "m_changelog" to "Changelog",
            "m_beta" to "Beta-фичи",
            "m_experimental" to "Экспериментальные",
            "m_accessibility" to "A11y-совместимость",
            "m_talkback" to "TalkBack-friendly",
            "m_colorblind" to "Дальтонизм-палитра",
            "m_high_contrast" to "Высокий контраст",
            "m_reduce_motion" to "Меньше движения",
            "m_haptic_off" to "Без вибрации",
            "m_sound_tick" to "Звук клика",
            "m_confirm" to "Подтверждение опасных",
            "m_lock_settings" to "Замок настроек",
            "m_pin" to "PIN на меню",
            "m_stealth" to "Стелс-режим",
            "m_stream_safe" to "Stream-safe HUD"
        )
        more.forEach { (k, t) ->
            box.addView(toggle(t, { prefs.isFeat(k) }) { prefs.setFeat(k, it) }, matchM())
        }
        box.addView(lbl("Всего пунктов в меню: 100+", 9f, 0xFF6B7280.toInt()).apply {
            setPadding(0, dp(10), 0, 0); gravity = Gravity.CENTER
        })
    }

    private fun closePanel() {
        try { panel?.let { wm.removeView(it) } } catch (_: Throwable) {}
        panel = null; panelLp = null; statusTv = null
        contentBox = null; tabBar = null
    }

    private fun refreshStatus() {
        val tv = statusTv ?: return
        Thread {
            if (info == null) info = DeviceInfo(this)
            val i = info ?: return@Thread
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
        }.start()
    }

    private fun refreshStatsBar() {
        val tv = statsBar ?: return
        Thread {
            if (info == null) info = DeviceInfo(this)
            val i = info
            val parts = mutableListOf<String>()
            if (prefs.isFpsHud()) parts.add("FPS~$lastFps")
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
            if (prefs.isActive()) parts.add("TURBO")
            val text = if (parts.isEmpty()) "TB" else parts.joinToString(" · ")
            handler.post { try { tv.text = text } catch (_: Throwable) {} }
        }.start()
    }

    /** Полноэкранный passthrough-слой эффектов. */
    private class FxOverlayView(context: android.content.Context) : View(context) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private var grid = false; private var fov = false; private var vignette = false
        private var scan = false; private var night = false; private var fog = false
        private var recoil = false; private var safe = false; private var edge = false
        private var radar = false; private var lowHp = false; private var chroma = false
        private var t = 0f

        init {
            isClickable = false; isFocusable = false; isFocusableInTouchMode = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        fun applyPrefs(prefs: AppPrefs) {
            grid = prefs.isGridOverlay(); fov = prefs.isFovCircle()
            vignette = prefs.isVignette(); scan = prefs.isScanlines()
            night = prefs.isNightTint(); fog = prefs.isFogOverlay()
            recoil = prefs.isRecoilHelper(); safe = prefs.isSafeZone()
            edge = prefs.isEdgeFlash(); radar = prefs.isRadarFrame()
            lowHp = prefs.isLowHpVignette(); chroma = prefs.isChromatic()
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            val cx = w / 2f; val cy = h / 2f
            t += 0.016f
            if (night) {
                p.style = Paint.Style.FILL; p.color = 0x33001A33.toInt()
                canvas.drawRect(0f, 0f, w, h, p)
            }
            if (vignette || lowHp) {
                val radial = android.graphics.RadialGradient(
                    cx, cy, minOf(w, h) * 0.72f,
                    intArrayOf(0x00000000, if (lowHp) 0x55FF2020.toInt() else 0x66000000),
                    floatArrayOf(0.55f, 1f),
                    android.graphics.Shader.TileMode.CLAMP
                )
                p.shader = radial; p.style = Paint.Style.FILL
                canvas.drawRect(0f, 0f, w, h, p); p.shader = null
            }
            if (fog) {
                p.style = Paint.Style.FILL; p.color = 0x22FFFFFF
                canvas.drawRect(0f, 0f, w, h * 0.12f, p)
                canvas.drawRect(0f, h * 0.88f, w, h, p)
            }
            if (scan) {
                p.style = Paint.Style.STROKE; p.strokeWidth = 1f; p.color = 0x18FFFFFF
                var y = 0f
                while (y < h) { canvas.drawLine(0f, y, w, y, p); y += 3f }
            }
            if (grid) {
                p.style = Paint.Style.STROKE; p.strokeWidth = 1f; p.color = 0x33FFFFFF
                canvas.drawLine(w / 3f, 0f, w / 3f, h, p)
                canvas.drawLine(2 * w / 3f, 0f, 2 * w / 3f, h, p)
                canvas.drawLine(0f, h / 3f, w, h / 3f, p)
                canvas.drawLine(0f, 2 * h / 3f, w, 2 * h / 3f, p)
            }
            if (fov) {
                p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = 0x55F2580A.toInt()
                canvas.drawCircle(cx, cy, minOf(w, h) * 0.22f, p)
            }
            if (recoil) {
                p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = 0x88FF3B30.toInt()
                canvas.drawLine(cx, cy, cx, cy - h * 0.18f, p)
            }
            if (safe) {
                p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = 0x44FFFFFF
                val m = w * 0.06f
                canvas.drawRect(m, m, w - m, h - m, p)
            }
            if (edge) {
                val a = ((sin(t * 3.0) * 0.5 + 0.5) * 80).toInt().coerceIn(20, 90)
                p.style = Paint.Style.STROKE; p.strokeWidth = 8f
                p.color = Color.argb(a, 255, 60, 40)
                canvas.drawRect(4f, 4f, w - 4f, h - 4f, p)
            }
            if (radar) {
                p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = 0x55FFFFFF
                val rw = w * 0.18f
                canvas.drawRect(16f, 16f, 16f + rw, 16f + rw, p)
            }
            if (chroma) {
                p.style = Paint.Style.STROKE; p.strokeWidth = 1f
                p.color = 0x22FF0000; canvas.drawCircle(cx - 2f, cy, minOf(w, h) * 0.2f, p)
                p.color = 0x2200FFFF; canvas.drawCircle(cx + 2f, cy, minOf(w, h) * 0.2f, p)
            }
            if (edge || lowHp) postInvalidateOnAnimation()
        }
    }
}
