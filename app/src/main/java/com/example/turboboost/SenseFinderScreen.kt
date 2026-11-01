package com.example.turboboost

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

private const val TURN_K = 0.18f
private const val MIN_PX = 40f
private const val NEAR = 0.2f
private const val WORLD_R = 12f
private const val FLOOR_Y = -2f
private const val CEIL_Y = 4f
private const val TAN_HALF_FOV = 0.7002f
private const val ACCEL_MIN = 0.01f
private const val ACCEL_MAX = 0.09f

class SenseFinderScreen(private val ctx: Context) {

    var onBack: (() -> Unit)? = null
    var onResult: ((sens: Float, scope: Float, accel: Float) -> Unit)? = null

    private class Step(val title: String, val dYaw: Float, val dPitch: Float, val startPitch: Float)

    private val steps = listOf(
        Step("180° ВПРАВО", 180f, 0f, 0f),
        Step("180° ВЛЕВО", -180f, 0f, 0f),
        Step("180° ВПРАВО", 180f, 0f, 0f),
        Step("180° ВЛЕВО", -180f, 0f, 0f)
    )

    private var game = "standoff"

    private lateinit var root: FrameLayout
    private lateinit var world: SimpleView
    private lateinit var titleTv: TextView
    private lateinit var hintTv: TextView
    private lateinit var progressTv: TextView
    private lateinit var degTv: TextView
    private lateinit var actionBtn: TextView
    private lateinit var gameRow: LinearLayout

    private var idx = -1
    private var yaw = 0f
    private var pitch = 0f
    private var axisSum = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var tracking = false
    private var locked = false
    private val samples = ArrayList<Float>()
    private val speeds = ArrayList<Float>()
    private var strokeStart = 0L
    private var stepMs = 0f
    private lateinit var resultBox: LinearLayout

    private val finishRunnable = Runnable { finishStep() }

    private var activeId = -1
    private var lastHx = 0f
    private var lastHy = 0f
    private var lastHt = 0L
    private var hoverOn = false
    private var mouseUsed = false

    private fun gameBaseSens(): Float = when (game) {
        "pubg" -> 90f
        else -> 2.0f
    }

    private fun gameSensMax(): Float = when (game) {
        "pubg" -> 130f
        else -> 5.0f
    }

    private fun gameSensMin(): Float = when (game) {
        "pubg" -> 60f
        else -> 0.30f
    }

    private fun gameScopeRatio(): Float = when (game) {
        "pubg" -> 0.9f
        else -> 0.875f
    }

    private fun gameUsesInteger(): Boolean = game == "pubg"

    private fun gameTargetLabel(): String = when (game) {
        "pubg" -> "PUBG → Настройки → Чувствительность (FPP измерено, TPP = FPP + 20%, прицелы рассчитаны от TPP)"
        else -> "Standoff 2 → Настройки → Управление"
    }

    fun build(): View {
        try {
            (ctx as? android.app.Activity)?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } catch (_: Throwable) {
        }

        root = FrameLayout(ctx)
        root.setBackgroundColor(Color.parseColor("#030508"))
        root.keepScreenOn = true

        world = SimpleView(ctx)
        root.addView(
            world,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val overlay = LinearLayout(ctx)
        overlay.orientation = LinearLayout.VERTICAL
        overlay.setPadding(dp(20), dp(14), dp(20), dp(14))
        root.addView(
            overlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val top = LinearLayout(ctx)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL

        val back = TextView(ctx)
        back.text = "← Назад"
        back.setTextColor(Color.WHITE)
        back.textSize = 14f
        back.setPadding(dp(4), dp(6), dp(12), dp(6))
        back.setOnClickListener { finish() }
        top.addView(back)

        progressTv = TextView(ctx)
        progressTv.setTextColor(Color.parseColor("#88FFFFFF"))
        progressTv.textSize = 12f
        progressTv.gravity = Gravity.END
        top.addView(
            progressTv,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        overlay.addView(top)

        overlay.addView(
            View(ctx),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        titleTv = TextView(ctx)
        titleTv.setTextColor(Color.WHITE)
        titleTv.textSize = 20f
        titleTv.gravity = Gravity.CENTER
        titleTv.typeface = Typeface.DEFAULT_BOLD
        val titleLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        titleLp.bottomMargin = dp(6)
        overlay.addView(titleTv, titleLp)

        degTv = TextView(ctx)
        degTv.setTextColor(Color.parseColor("#F2580A"))
        degTv.textSize = 24f
        degTv.gravity = Gravity.CENTER
        degTv.typeface = Typeface.DEFAULT_BOLD
        val degLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        degLp.bottomMargin = dp(8)
        overlay.addView(degTv, degLp)

        resultBox = LinearLayout(ctx)
        resultBox.orientation = LinearLayout.VERTICAL
        resultBox.visibility = View.GONE
        val boxLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        boxLp.leftMargin = dp(70)
        boxLp.rightMargin = dp(70)
        boxLp.bottomMargin = dp(8)
        overlay.addView(resultBox, boxLp)

        gameRow = LinearLayout(ctx)
        gameRow.orientation = LinearLayout.HORIZONTAL
        gameRow.setPadding(0, dp(6), 0, dp(6))
        listOf(
            "standoff" to "Standoff 2",
            "pubg" to "PUBG"
        ).forEach { (id, label) ->
            val b = TextView(ctx)
            b.text = label
            b.gravity = Gravity.CENTER
            b.setTextColor(Color.WHITE)
            b.textSize = 13f
            b.typeface = Typeface.DEFAULT_BOLD
            b.setPadding(dp(6), dp(10), dp(6), dp(10))
            b.background = pillBg(if (id == game) Color.parseColor("#5B8DEF") else Color.parseColor("#333333"))
            b.tag = id
            b.setOnClickListener {
                game = id
                updateGameRow()
                showStep()
            }
            gameRow.addView(b, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            })
        }
        overlay.addView(gameRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(6) })

        hintTv = TextView(ctx)
        hintTv.setTextColor(Color.parseColor("#CCFFFFFF"))
        hintTv.textSize = 13f
        hintTv.gravity = Gravity.CENTER
        hintTv.setLineSpacing(4f, 1f)
        val hintLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        hintLp.bottomMargin = dp(14)
        overlay.addView(hintTv, hintLp)

        actionBtn = TextView(ctx)
        actionBtn.text = "НАЧАТЬ"
        actionBtn.setTextColor(Color.WHITE)
        actionBtn.textSize = 15f
        actionBtn.gravity = Gravity.CENTER
        actionBtn.setPadding(dp(28), dp(14), dp(28), dp(14))
        actionBtn.background = pillBg(Color.parseColor("#5B8DEF"))
        actionBtn.setOnClickListener { onAction() }
        val btnLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        btnLp.gravity = Gravity.CENTER_HORIZONTAL
        overlay.addView(actionBtn, btnLp)

        overlay.addView(
            View(ctx),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.55f)
        )

        root.setOnTouchListener { _, ev -> handleTouch(ev) }
        root.setOnHoverListener { _, ev -> handleHover(ev) }
        root.isFocusable = true
        root.isFocusableInTouchMode = true
        root.setOnKeyListener { _, keyCode, ev ->
            if (ev.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK -> {
                    finish()
                    true
                }
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_SPACE -> {
                    if (idx == -1 || idx >= steps.size) {
                        onAction()
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
        root.post { try { root.requestFocus() } catch (_: Throwable) {} }
        showStep()
        return root
    }

    private fun updateGameRow() {
        if (!::gameRow.isInitialized) return
        for (i in 0 until gameRow.childCount) {
            val b = gameRow.getChildAt(i) as TextView
            val id = b.tag as? String ?: continue
            b.background = pillBg(if (id == game) Color.parseColor("#5B8DEF") else Color.parseColor("#333333"))
        }
    }

    private fun pillBg(color: Int): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = dp(12).toFloat()
        g.setColor(color)
        return g
    }

    private fun noteSource(ev: MotionEvent) {
        try {
            if (ev.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE ||
                ev.isFromSource(android.view.InputDevice.SOURCE_MOUSE)
            ) mouseUsed = true
        } catch (_: Throwable) {
        }
    }

    private fun applyMove(st: Step, dx: Float, dy: Float) {
        if (st.dYaw != 0f) {
            val sgn = if (st.dYaw > 0f) 1f else -1f
            yaw = (yaw + dx * TURN_K).coerceIn(-360f, 360f)
            axisSum += dx * sgn
        } else {
            val sgn = if (st.dPitch > 0f) 1f else -1f
            pitch = (pitch - dy * TURN_K).coerceIn(-90f, 90f)
            axisSum += -dy * sgn
        }
        world.yaw = yaw
        world.pitch = pitch
        world.invalidate()
    }

    private fun scheduleFinish(delayMs: Long) {
        if (axisSum >= MIN_PX) {
            root.removeCallbacks(finishRunnable)
            root.postDelayed(finishRunnable, delayMs)
        }
    }

    private fun endStroke(ev: MotionEvent) {
        if (tracking) stepMs += (ev.eventTime - strokeStart).toFloat()
        tracking = false
        activeId = -1
        scheduleFinish(700L)
    }

    private fun handleTouch(ev: MotionEvent): Boolean {
        if (idx < 0 || idx >= steps.size || locked) return false
        val st = steps[idx]
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                noteSource(ev)
                root.removeCallbacks(finishRunnable)
                strokeStart = ev.eventTime
                activeId = ev.getPointerId(0)
                lastX = ev.x
                lastY = ev.y
                tracking = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return true
                val pi = ev.findPointerIndex(activeId)
                if (pi < 0) return true
                val x = ev.getX(pi)
                val y = ev.getY(pi)
                applyMove(st, x - lastX, y - lastY)
                lastX = x
                lastY = y
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (ev.getPointerId(ev.actionIndex) == activeId) endStroke(ev)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endStroke(ev)
        }
        return true
    }

    private fun handleHover(ev: MotionEvent): Boolean {
        if (idx < 0 || idx >= steps.size || locked) return false
        noteSource(ev)
        val st = steps[idx]
        when (ev.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER -> {
                lastHx = ev.x
                lastHy = ev.y
                lastHt = ev.eventTime
                hoverOn = true
            }
            MotionEvent.ACTION_HOVER_MOVE -> {
                if (!hoverOn) {
                    lastHx = ev.x
                    lastHy = ev.y
                    lastHt = ev.eventTime
                    hoverOn = true
                    return true
                }
                root.removeCallbacks(finishRunnable)
                val dt = (ev.eventTime - lastHt).coerceIn(0L, 100L)
                stepMs += dt.toFloat()
                applyMove(st, ev.x - lastHx, ev.y - lastHy)
                lastHx = ev.x
                lastHy = ev.y
                lastHt = ev.eventTime
                scheduleFinish(900L)
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                hoverOn = false
            }
        }
        return true
    }

    private fun turnedDeg(st: Step): Float {
        return if (st.dYaw != 0f) {
            val sgn = if (st.dYaw > 0f) 1f else -1f
            yaw * sgn
        } else {
            val sgn = if (st.dPitch > 0f) 1f else -1f
            (pitch - st.startPitch) * sgn
        }
    }

    private fun finishStep() {
        if (locked || idx < 0 || idx >= steps.size) return
        if (axisSum < MIN_PX) return
        val st = steps[idx]
        locked = true
        tracking = false
        val target = if (st.dYaw != 0f) abs(st.dYaw) else abs(st.dPitch)
        val turned = max(10f, turnedDeg(st))
        val stepSens = (gameBaseSens() * target / turned).coerceIn(gameSensMin(), gameSensMax())
        samples.add(stepSens)
        if (stepMs > 50f) speeds.add(axisSum / stepMs)

        val diff = abs(turned - target) / target
        world.showTarget = true
        world.showLabels = true
        world.flash = true
        world.flashColor = if (diff <= 0.05f) Color.parseColor("#3300FF88") else Color.parseColor("#33FFAA00")
        degTv.text = "${turned.roundToInt()}° из ${target.roundToInt()}°"
        hintTv.text = when {
            diff <= 0.05f -> "Точно! sens = ${fmt(stepSens)}"
            turned < target -> "Недокрутил на ${(target - turned).roundToInt()}° → sens = ${fmt(stepSens)}"
            else -> "Перекрутил на ${(turned - target).roundToInt()}° → sens = ${fmt(stepSens)}"
        }
        world.invalidate()

        root.postDelayed(Runnable {
            locked = false
            world.flash = false
            idx++
            showStep()
        }, 1500L)
    }private fun onAction() {
    if (idx == -1) {
        idx = 0
        showStep()
    } else if (idx >= steps.size) {
        val r = computeResult()
        val text = gameTargetLabel() + "\nSensitivity: " + fmt(r.first) + "\nScope: " + fmt(r.second) + "\nAcceleration: " + fmt(r.third)
        try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("sens", text))
            Toast.makeText(ctx, "Скопировано", Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
        }
        onResult?.invoke(r.first, r.second, r.third)
        finish()
    }
}

private fun showStep() {
    axisSum = 0f
    stepMs = 0f
    resultBox.visibility = View.GONE
    tracking = false
    world.showTarget = false
    world.flash = false

    if (idx < 0) {
        gameRow.visibility = View.VISIBLE
        updateGameRow()
        yaw = 0f
        pitch = 0f
        applyCamera()
        titleTv.text = "Подбор сенсы"
        progressTv.text = ""
        degTv.text = ""
        hintTv.text =
            "Выбери игру. Тест на 180° — сколько ты хочешь разворачиваться одним свайпом.\n\n" +
            "ВАЖНО: тест измеряет FPP (от 1-го лица). TPP (от 3-го лица)\n" +
            "и прицелы 2x–8x будут рассчитаны математически от FPP.\n\n" +
            "Ты в 3D-комнате. Будут 4 задания: развернуться на 180°\n" +
            "вправо и влево по 2 раза.\n" +
            "Крути камеру свайпом так, как привык в игре, и отпусти,\n" +
            "когда по твоим ощущениям повернулся на 180°.\n\n" +
            "Средняя sens для " + gameBaseSens().let { fmt(it) } + "."
        actionBtn.visibility = View.VISIBLE
        actionBtn.text = "НАЧАТЬ"
        world.showLabels = true
        world.showHud = false
    } else if (idx < steps.size) {
        gameRow.visibility = View.GONE
        val st = steps[idx]
        yaw = 0f
        pitch = st.startPitch
        applyCamera()
        world.markerYaw = st.dYaw
        world.markerPitch = st.startPitch + st.dPitch
        world.showLabels = false
        world.showHud = true
        titleTv.text = st.title
        progressTv.text = "Шаг ${idx + 1} / ${steps.size}"
        degTv.text = ""
        hintTv.text =
            "Развернись на 180° ${if (st.dYaw > 0f) "вправо" else "влево"} так, как делаешь это в игре.\n" +
            "Это FPP (от 1-го лица). Можно несколько свайпов. Закончил: отпусти и подожди."
        actionBtn.visibility = View.GONE
    } else {
        gameRow.visibility = View.GONE
        yaw = 0f
        pitch = 0f
        applyCamera()
        val r = computeResult()
        titleTv.text = "Идеальная сенса (180°)"
        progressTv.text = "Готово"
        degTv.text = ""
        resultBox.removeAllViews()
        if (game == "pubg") {
            val tpp = r.first + 20f
            addSliderRow("FPP No Scope (Camera) — измерено", r.first, gameSensMax())
            addSliderRow("FPP No Scope (ADS)", r.second, gameSensMax())
            addSliderRow("TPP No Scope (Camera) — FPP + 20", tpp, gameSensMax())
            addSliderRow("TPP No Scope (ADS)", (tpp * 0.9f), gameSensMax())
            addSliderRow("Red Dot / Holo (Camera)", (tpp * 0.50f), gameSensMax())
            addSliderRow("2x (Camera)", (tpp * 0.30f), gameSensMax())
            addSliderRow("3x (Camera)", (tpp * 0.18f), gameSensMax())
            addSliderRow("4x (Camera)", (tpp * 0.15f), gameSensMax())
            addSliderRow("6x (Camera)", (tpp * 0.10f), gameSensMax())
            addSliderRow("8x (Camera)", (tpp * 0.08f), gameSensMax())
        } else {
            addSliderRow("Чувствительность (Sensitivity)", r.first, gameSensMax())
            addSliderRow("Чувствительность прицела (Scope Sensitivity)", r.second, gameSensMax())
            addSliderRow("Ускорение (Acceleration)", r.third, ACCEL_MAX)
        }
        resultBox.visibility = View.VISIBLE
        val noteText = when (game) {
            "pubg" -> "FPP No Scope измерен: Camera " + fmt(r.first) + "%. TPP No Scope = FPP + 20 = " + fmt(r.first + 20f) + "%. Остальные прицелы рассчитаны от TPP. Gyroscope подбирай отдельно."
            else -> "Aim Smoothing = 0.00. Если сенса «плавает» — снизь Acceleration на 0.01."
        }
        hintTv.text = gameTargetLabel() + ": выставь эти значения.\n" + noteText +
            (if (mouseUsed) "\nВвод мышью: для мыши значения менее точны." else "")
        actionBtn.visibility = View.VISIBLE
        actionBtn.text = "СКОПИРОВАТЬ И ВЫЙТИ"
        world.showLabels = true
        world.showHud = false
    }
    world.invalidate()
}

private fun applyCamera() {
    world.yaw = yaw
    world.pitch = pitch
}

private fun fmt(v: Float): String {
    return if (gameUsesInteger()) {
        v.roundToInt().toString() + "%"
    } else {
        String.format(java.util.Locale.US, "%.2f", v)
    }
}

private fun r2(v: Float): Float = (v * 100f).roundToInt() / 100f

private fun median(list: List<Float>, def: Float): Float {
    if (list.isEmpty()) return def
    val sorted = list.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
}

private fun addSliderRow(label: String, value: Float, maxValue: Float) {
    val row = LinearLayout(ctx)
    row.orientation = LinearLayout.VERTICAL
    val rowLp = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
    rowLp.bottomMargin = dp(8)

    val head = LinearLayout(ctx)
    head.orientation = LinearLayout.HORIZONTAL
    val name = TextView(ctx)
    name.text = label
    name.setTextColor(Color.WHITE)
    name.textSize = 13f
    head.addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    val num = TextView(ctx)
    num.text = fmt(value)
    num.setTextColor(Color.parseColor("#5B8DEF"))
    num.textSize = 16f
    num.typeface = Typeface.DEFAULT_BOLD
    head.addView(num)
    row.addView(head)

    val track = LinearLayout(ctx)
    track.orientation = LinearLayout.HORIZONTAL
    val trackBg = GradientDrawable()
    trackBg.cornerRadius = dp(3).toFloat()
    trackBg.setColor(Color.parseColor("#33FFFFFF"))
    track.background = trackBg
    val frac = (value / maxValue).coerceIn(0.03f, 0.97f)
    val fill = View(ctx)
    val fillBg = GradientDrawable()
    fillBg.cornerRadius = dp(3).toFloat()
    fillBg.setColor(Color.parseColor("#5B8DEF"))
    fill.background = fillBg
    track.addView(fill, LinearLayout.LayoutParams(0, dp(6), frac))
    track.addView(View(ctx), LinearLayout.LayoutParams(0, dp(6), 1f - frac))
    val trackLp = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
    trackLp.topMargin = dp(4)
    row.addView(track, trackLp)

    resultBox.addView(row, rowLp)
}

private fun computeResult(): Triple<Float, Float, Float> {
    val base = gameBaseSens()
    val minv = gameSensMin()
    val maxv = gameSensMax()
    val ratio = gameScopeRatio()
    val rawMedian = median(samples, base).coerceIn(minv, maxv)
    val sens = if (gameUsesInteger()) rawMedian.roundToInt().toFloat() else r2(rawMedian)
    val scope = if (gameUsesInteger()) (sens * ratio).roundToInt().toFloat().coerceIn(minv, maxv)
                else r2((sens * ratio).coerceIn(minv, maxv))
    val accel = if (game == "pubg") 0f else {
        val speed = median(speeds, 1.5f)
        r2((ACCEL_MAX - speed * 0.04f).coerceIn(ACCEL_MIN, ACCEL_MAX))
    }
    return Triple(sens, scope, accel)
}

private fun finish() {
    try {
        root.removeCallbacks(finishRunnable)
    } catch (_: Throwable) {
    }
    try {
        (ctx as? android.app.Activity)?.requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    } catch (_: Throwable) {
    }
    onBack?.invoke()
}

private fun dp(v: Int): Int {
    return (v * ctx.resources.displayMetrics.density).toInt()
}
