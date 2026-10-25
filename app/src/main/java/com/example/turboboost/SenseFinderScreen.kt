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

private const val TURN_K = 0.18f      // градусов камеры на 1 пиксель свайпа
private const val MIN_PX = 40f        // минимальный свайп, чтобы шаг засчитался
private const val NEAR = 0.2f         // ближняя плоскость отсечения
private const val WORLD_R = 12f       // радиус комнаты
private const val FLOOR_Y = -2f
private const val CEIL_Y = 4f
private const val TAN_HALF_FOV = 0.7002f // tan(35°) -> вертикальный FOV 70°
private const val SENS_MAX = 5.00f
private const val ACCEL_MAX = 0.20f

/**
 * Подбор сенсы Standoff 2 в 3D-комнате.
 * Задания: повернуть камеру на 90°, потом на 180° вправо / влево / вверх / вниз.
 * Измеряем, сколько пикселей свайпа игрок тратит на заданный угол, и считаем сенсу.
 */
class SenseFinderScreen(private val ctx: Context) {

    var onBack: (() -> Unit)? = null
    var onResult: ((sens: Float, scope: Float, accel: Float) -> Unit)? = null

    private class Step(val title: String, val dYaw: Float, val dPitch: Float, val startPitch: Float)

    private val steps = listOf(
        Step("90° ВПРАВО", 90f, 0f, 0f),
        Step("90° ВЛЕВО", -90f, 0f, 0f),
        Step("90° ВВЕРХ", 0f, 90f, 0f),
        Step("90° ВНИЗ", 0f, -90f, 0f),
        Step("180° ВПРАВО", 180f, 0f, 0f),
        Step("180° ВЛЕВО", -180f, 0f, 0f),
        Step("180° ВВЕРХ", 0f, 180f, -90f),
        Step("180° ВНИЗ", 0f, -180f, 90f)
    )

    private lateinit var root: FrameLayout
    private lateinit var world: SimpleView
    private lateinit var titleTv: TextView
    private lateinit var hintTv: TextView
    private lateinit var progressTv: TextView
    private lateinit var degTv: TextView
    private lateinit var actionBtn: TextView

    // -1 = intro, 0..7 = шаги, 8 = результат
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
        degTv.setTextColor(Color.parseColor("#5B8DEF"))
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
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.RECTANGLE
        bg.cornerRadius = dp(14).toFloat()
        bg.setColor(Color.parseColor("#5B8DEF"))
        actionBtn.background = bg
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
        // мышь / трекпад (движение без нажатия) и клавиатура
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

    /** Палец, стилус или мышь с зажатой кнопкой. */
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

    /** Мышь или трекпад без нажатия кнопки: крутим камеру по движению курсора. */
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

    /** Угол, на который камера реально повернулась в нужную сторону. */
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
        samples.add(axisSum / target)
        if (stepMs > 50f) speeds.add(axisSum / stepMs)

        val turned = max(0f, turnedDeg(st))
        val diff = abs(turned - target) / target
        world.showTarget = true
        world.showLabels = true
        world.flash = true
        world.flashColor = if (diff <= 0.12f) Color.parseColor("#3300FF88") else Color.parseColor("#33FFAA00")
        degTv.text = "${turned.roundToInt()}° из ${target.roundToInt()}°"
        hintTv.text = when {
            diff <= 0.12f -> "Точно!"
            turned < target -> "Недокрутил на ${(target - turned).roundToInt()}°"
            else -> "Перекрутил на ${(turned - target).roundToInt()}°"
        }
        world.invalidate()

        root.postDelayed(Runnable {
            locked = false
            world.flash = false
            idx++
            showStep()
        }, 1500L)
    }

    private fun onAction() {
        if (idx == -1) {
            idx = 0
            showStep()
        } else if (idx >= steps.size) {
            val r = computeResult()
            val text = "Standoff 2\nSensitivity: ${fmt(r.first)}\nScope Sensitivity: ${fmt(r.second)}\nAcceleration: ${fmt(r.third)}"
            try {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("SO2 sens", text))
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
            yaw = 0f
            pitch = 0f
            applyCamera()
            titleTv.text = "Подбор сенсы Standoff 2"
            progressTv.text = ""
            degTv.text = ""
            hintTv.text =
                "Ты в 3D-комнате. Будут задания: повернуться на 90°, потом на 180°\n" +
                "вправо, влево, вверх и вниз.\n" +
                "Крути камеру свайпами так, как привык в игре, и отпусти, когда\n" +
                "по твоим ощущениям повернулся на нужный угол."
            actionBtn.visibility = View.VISIBLE
            actionBtn.text = "НАЧАТЬ"
            world.showLabels = true
            world.showHud = false
        } else if (idx < steps.size) {
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
                "Поверни камеру на ${st.title.lowercase()} так, как делаешь это в игре.\n" +
                "Можно несколько свайпов. Закончил: просто отпусти и подожди."
            actionBtn.visibility = View.GONE
        } else {
            yaw = 0f
            pitch = 0f
            applyCamera()
            val r = computeResult()
            titleTv.text = "Идеальная сенса"
            progressTv.text = "Готово"
            degTv.text = ""
            resultBox.removeAllViews()
            addSliderRow("Чувствительность (Sensitivity)", r.first, SENS_MAX)
            addSliderRow("Чувствительность прицела (Scope Sensitivity)", r.second, SENS_MAX)
            addSliderRow("Ускорение (Acceleration)", r.third, ACCEL_MAX)
            resultBox.visibility = View.VISIBLE
            hintTv.text =
                "Standoff 2 → Настройки → Управление: выставь эти три значения.\n" +
                "Если в игре есть Aim Smoothing (сглаживание), поставь 0.00, иначе прицел «плавает».\n" +
                "Ориентировочно: подстрой на ±0.2, если нужно." +
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

    private fun fmt(v: Float): String = String.format(java.util.Locale.US, "%.2f", v)

    private fun r2(v: Float): Float = (v * 100f).roundToInt() / 100f

    private fun median(list: List<Float>, def: Float): Float {
        if (list.isEmpty()) return def
        val sorted = list.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }

    /** Строка как в настройках игры: название, значение и ползунок. */
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

    /** Возвращает: Sensitivity, Scope Sensitivity, Acceleration (два знака после запятой, как в игре). */
    private fun computeResult(): Triple<Float, Float, Float> {
        // пикселей свайпа на 1 градус: медиана, чтобы выбросы не портили результат
        val ppd = median(samples, 5.5f)
        var dpi = try {
            ctx.resources.displayMetrics.xdpi
        } catch (_: Throwable) {
            0f
        }
        if (dpi < 100f) dpi = try {
            ctx.resources.displayMetrics.density * 160f
        } catch (_: Throwable) {
            400f
        }
        if (mouseUsed) dpi = 400f // у мыши нет физического dpi, берём типичное значение
        val inchPerDeg = ppd / dpi
        // опорная точка: типичная сенса 2.30 соответствует примерно 0.0167 дюйма на градус
        val sens = r2((2.30f * 0.0167f / inchPerDeg).coerceIn(0.30f, SENS_MAX))
        val scope = r2((sens * 0.78f).coerceIn(0.30f, SENS_MAX))
        // ускорение: быстрые свайпы -> ускорение не нужно, медленные -> небольшое
        val speed = median(speeds, 1.5f)
        val accel = when {
            speed >= 2.2f -> 0.00f
            speed >= 1.4f -> 0.03f
            else -> 0.05f
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

    /** Простой 3D-рендер на Canvas: комната из сетки, колонн и колец, камера крутится по yaw/pitch. */
    private class SimpleView(context: Context) : View(context) {
        var yaw = 0f
        var pitch = 0f
        var markerYaw = 0f
        var markerPitch = 0f
        var showTarget = false
        var showLabels = true
        var showHud = false
        var flash = false
        var flashColor = 0

        private var cx = 0f
        private var cy = 0f
        private var fpx = 1f
        private var cyw = 1f
        private var syw = 0f
        private var cp = 1f
        private var sp = 0f

        private val ca = FloatArray(3)
        private val cb = FloatArray(3)

        private val pGrid = Paint(Paint.ANTI_ALIAS_FLAG)
        private val pPillar = Paint(Paint.ANTI_ALIAS_FLAG)
        private val pPillar0 = Paint(Paint.ANTI_ALIAS_FLAG)
        private val pHorizon = Paint(Paint.ANTI_ALIAS_FLAG)
        private val pRing = Paint(Paint.ANTI_ALIAS_FLAG)
        private val pCross = Paint(Paint.ANTI_ALIAS_FLAG)
        private val pMarkFill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val pMarkRing = Paint(Paint.ANTI_ALIAS_FLAG)
        private val pText = Paint(Paint.ANTI_ALIAS_FLAG)
        private val pFlash = Paint()

        init {
            pGrid.color = Color.parseColor("#2A44AAFF")
            pGrid.strokeWidth = 1.5f
            pGrid.style = Paint.Style.STROKE
            pPillar.color = Color.parseColor("#885B8DEF")
            pPillar.strokeWidth = 3f
            pPillar.style = Paint.Style.STROKE
            pPillar0.color = Color.parseColor("#FFFF5555")
            pPillar0.strokeWidth = 5f
            pPillar0.style = Paint.Style.STROKE
            pHorizon.color = Color.parseColor("#99FFFFFF")
            pHorizon.strokeWidth = 2.5f
            pHorizon.style = Paint.Style.STROKE
            pRing.color = Color.parseColor("#335B8DEF")
            pRing.strokeWidth = 2f
            pRing.style = Paint.Style.STROKE
            pCross.color = Color.parseColor("#CCFFFFFF")
            pCross.strokeWidth = 2f
            pCross.style = Paint.Style.STROKE
            pMarkFill.color = Color.parseColor("#66FF4D4D")
            pMarkFill.style = Paint.Style.FILL
            pMarkRing.color = Color.parseColor("#FFFF4D4D")
            pMarkRing.strokeWidth = 4f
            pMarkRing.style = Paint.Style.STROKE
            pText.color = Color.parseColor("#CCFFFFFF")
            pText.textSize = 28f
            pText.textAlign = Paint.Align.CENTER
            pText.typeface = Typeface.DEFAULT_BOLD
        }

        private fun sinD(a: Float): Float = Math.sin(Math.toRadians(a.toDouble())).toFloat()
        private fun cosD(a: Float): Float = Math.cos(Math.toRadians(a.toDouble())).toFloat()

        /** Мировые координаты -> координаты камеры (x вправо, y вверх, z вперёд). */
        private fun toCam(x: Float, y: Float, z: Float, out: FloatArray) {
            val xc = x * cyw - z * syw
            val z0 = x * syw + z * cyw
            out[0] = xc
            out[1] = y * cp - z0 * sp
            out[2] = z0 * cp + y * sp
        }

        private fun line3(
            canvas: Canvas,
            x1: Float, y1: Float, z1: Float,
            x2: Float, y2: Float, z2: Float,
            paint: Paint
        ) {
            toCam(x1, y1, z1, ca)
            toCam(x2, y2, z2, cb)
            var ax = ca[0]
            var ay = ca[1]
            var az = ca[2]
            var bx = cb[0]
            var by = cb[1]
            var bz = cb[2]
            if (az < NEAR && bz < NEAR) return
            if (az < NEAR) {
                val t = (NEAR - az) / (bz - az)
                val nx = ax + (bx - ax) * t
                val ny = ay + (by - ay) * t
                ax = nx
                ay = ny
                az = NEAR
            } else if (bz < NEAR) {
                val t = (NEAR - az) / (bz - az)
                val nx = ax + (bx - ax) * t
                val ny = ay + (by - ay) * t
                bx = nx
                by = ny
                bz = NEAR
            }
            canvas.drawLine(
                cx + ax / az * fpx, cy - ay / az * fpx,
                cx + bx / bz * fpx, cy - by / bz * fpx,
                paint
            )
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            cx = w / 2f
            cy = h / 2f
            fpx = (h / 2f) / TAN_HALF_FOV
            cyw = cosD(yaw)
            syw = sinD(yaw)
            cp = cosD(pitch)
            sp = sinD(pitch)

            if (flash) {
                pFlash.color = flashColor
                canvas.drawRect(0f, 0f, w, h, pFlash)
            }

            // пол и потолок: сетка
            var g = -24f
            while (g <= 24f) {
                line3(canvas, g, FLOOR_Y, -24f, g, FLOOR_Y, 24f, pGrid)
                line3(canvas, -24f, FLOOR_Y, g, 24f, FLOOR_Y, g, pGrid)
                line3(canvas, g, CEIL_Y, -24f, g, CEIL_Y, 24f, pGrid)
                line3(canvas, -24f, CEIL_Y, g, 24f, CEIL_Y, g, pGrid)
                g += 4f
            }

            // три большие окружности: горизонт и два вертикальных кольца
            var i = 0
            while (i < 48) {
                val a1 = i * 7.5f
                val a2 = (i + 1) * 7.5f
                line3(canvas, WORLD_R * sinD(a1), 0f, WORLD_R * cosD(a1),
                    WORLD_R * sinD(a2), 0f, WORLD_R * cosD(a2), pHorizon)
                line3(canvas, 0f, WORLD_R * sinD(a1), WORLD_R * cosD(a1),
                    0f, WORLD_R * sinD(a2), WORLD_R * cosD(a2), pRing)
                line3(canvas, WORLD_R * cosD(a1), WORLD_R * sinD(a1), 0f,
                    WORLD_R * cosD(a2), WORLD_R * sinD(a2), 0f, pRing)
                i++
            }

            // колонны каждые 30°
            var k = 0
            while (k < 12) {
                val ang = k * 30f
                val px = WORLD_R * sinD(ang)
                val pz = WORLD_R * cosD(ang)
                line3(canvas, px, FLOOR_Y, pz, px, CEIL_Y, pz, if (k == 0) pPillar0 else pPillar)
                if (showLabels) {
                    toCam(px, 1.2f, pz, ca)
                    if (ca[2] > NEAR) {
                        val sx = cx + ca[0] / ca[2] * fpx
                        val sy = cy - ca[1] / ca[2] * fpx
                        canvas.drawText("${(ang).toInt()}°", sx, sy, pText)
                    }
                }
                k++
            }

            if (showTarget) drawMarker(canvas, w, h)

            // прицел
            canvas.drawLine(cx - 14f, cy, cx + 14f, cy, pCross)
            canvas.drawLine(cx, cy - 14f, cx, cy + 14f, pCross)
        }

        private fun drawMarker(canvas: Canvas, w: Float, h: Float) {
            val mx = WORLD_R * sinD(markerYaw) * cosD(markerPitch)
            val my = WORLD_R * sinD(markerPitch)
            val mz = WORLD_R * cosD(markerYaw) * cosD(markerPitch)
            toCam(mx, my, mz, ca)
            var drawn = false
            if (ca[2] > NEAR) {
                val sx = cx + ca[0] / ca[2] * fpx
                val sy = cy - ca[1] / ca[2] * fpx
                if (sx > 0f && sx < w && sy > 0f && sy < h) {
                    val r = max(16f, 1.0f / ca[2] * fpx)
                    canvas.drawCircle(sx, sy, r, pMarkFill)
                    canvas.drawCircle(sx, sy, r, pMarkRing)
                    drawn = true
                }
            }
            if (!drawn) {
                var nx = ca[0]
                var ny = ca[1]
                var len = hypot(nx, ny)
                if (len < 0.001f) {
                    nx = 1f
                    ny = 0f
                    len = 1f
                }
                val ex = cx + nx / len * (w * 0.40f)
                val ey = cy - ny / len * (h * 0.36f)
                canvas.drawCircle(ex, ey, 20f, pMarkFill)
                canvas.drawCircle(ex, ey, 20f, pMarkRing)
            }
        }
    }
}
