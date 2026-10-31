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
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.roundToInt

class SenseFinderScreen(private val ctx: Context) {

    var onBack: (() -> Unit)? = null
    var onResult: ((sens: Float, scope: Float, accel: Float) -> Unit)? = null

    private lateinit var root: FrameLayout
    private lateinit var world: WorldView

    private var targetDeg = 180f
    private var hasSwipe = false
    private var swipeMs = 0L
    private var measuredDeg = 0f
    private var swipeDx = 0f
    private var lastSens = 0f
    private var lastScope = 0f
    private var lastAccel = 0f

    private val accent = 0xFFF2580A.toInt()
    private val bgDark = Color.parseColor("#030508")

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun build(): View {
        try {
            (ctx as? android.app.Activity)?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } catch (_: Throwable) {}

        root = FrameLayout(ctx)
        root.setBackgroundColor(bgDark)

        world = WorldView(ctx)
        root.addView(world, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        buildUi()
        return root
    }

    private fun buildUi() {
        val top = LinearLayout(ctx)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(dp(16), dp(10), dp(16), dp(10))
        top.setBackgroundColor(Color.parseColor("#99000000"))
        val lpTop = FrameLayout.LayoutParams(MATCH_PARENT, dp(52))
        lpTop.gravity = Gravity.TOP
        root.addView(top, lpTop)

        val back = TextView(ctx)
        back.text = "← Назад"
        back.setTextColor(Color.WHITE)
        back.textSize = 14f
        back.setPadding(dp(4), dp(6), dp(14), dp(6))
        back.setOnClickListener { finish() }
        top.addView(back)

        val title = TextView(ctx)
        title.text = "Подбор сенсы"
        title.setTextColor(accent)
        title.textSize = 16f
        title.typeface = Typeface.DEFAULT_BOLD
        title.gravity = Gravity.CENTER
        top.addView(title, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        val spacer = TextView(ctx)
        spacer.text = "   "
        top.addView(spacer)

        val bottom = LinearLayout(ctx)
        bottom.orientation = LinearLayout.HORIZONTAL
        bottom.gravity = Gravity.CENTER_VERTICAL
        bottom.setPadding(dp(16), dp(8), dp(16), dp(14))
        bottom.setBackgroundColor(Color.parseColor("#99000000"))
        val lpBottom = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        lpBottom.gravity = Gravity.BOTTOM
        root.addView(bottom, lpBottom)

        listOf(45f, 90f, 180f).forEach { d ->
            val b = pill(d.toInt().toString() + "°", if (targetDeg == d) accent else Color.parseColor("#333333"))
            b.setOnClickListener {
                targetDeg = d
                rebuildBottom()
            }
            bottom.addView(b, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            })
        }

        val action = pill(if (hasSwipe) "Измерить" else "Проведи свайп", if (hasSwipe) Color.parseColor("#34D399") else Color.parseColor("#555555"))
        action.setOnClickListener {
            if (!hasSwipe) {
                Toast.makeText(ctx, "Сначала проведи свайп", Toast.LENGTH_SHORT).show()
            } else {
                showResult()
            }
        }
        bottom.addView(action, LinearLayout.LayoutParams(0, dp(48), 1.2f).apply {
            marginStart = dp(10)
            marginEnd = dp(4)
        })

        val hint = TextView(ctx)
        hint.text = if (hasSwipe) "Готово. Нажми «Измерить»" else "Красные столбы — ориентир. Свайпни между ними"
        hint.setTextColor(Color.parseColor("#CCFFFFFF"))
        hint.textSize = 11f
        hint.gravity = Gravity.CENTER
        hint.setPadding(dp(16), dp(4), dp(16), dp(2))
        val lpHint = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        lpHint.gravity = Gravity.BOTTOM
        lpHint.bottomMargin = dp(70)
        root.addView(hint, lpHint)
    }

    private fun rebuildBottom() {
        root.removeAllViews()
        root.addView(world, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        buildUi()
        world.yaw = 0f
        world.pitch = 0f
        world.invalidate()
    }

    private fun showResult() {
        root.removeAllViews()
        root.setBackgroundColor(bgDark)

        val content = LinearLayout(ctx)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(40), dp(80), dp(40), dp(30))
        root.addView(content, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val s = computeResult()
        lastSens = s.first
        lastScope = s.second
        lastAccel = s.third

        val title = TextView(ctx)
        title.text = "Результат"
        title.setTextColor(Color.WHITE)
        title.textSize = 22f
        title.typeface = Typeface.DEFAULT_BOLD
        content.addView(title)

        val sub = TextView(ctx)
        sub.text = "Введи в Standoff 2 → Настройки → Управление"
        sub.setTextColor(Color.parseColor("#99FFFFFF"))
        sub.textSize = 12f
        sub.setPadding(0, dp(4), 0, dp(16))
        content.addView(sub)

        addRow(content, "Чувствительность", s.first, accent)
        addRow(content, "Чувствительность прицела", s.second, accent)
        addRow(content, "Ускорение", s.third, Color.parseColor("#00C7FF"))

        val note = TextView(ctx)
        note.text = "Aim Smoothing = 0.00. Если сенса «плавает» — снизь Acceleration на 0.01."
        note.setTextColor(Color.parseColor("#88FFFFFF"))
        note.textSize = 11f
        note.setPadding(dp(4), dp(14), dp(4), 0)
        content.addView(note)

        val spacer = View(ctx)
        content.addView(spacer, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        val copyExit = pill("Скопировать и выйти", accent)
        copyExit.setOnClickListener {
            val text = "Standoff 2\nSensitivity: " + fmt(lastSens) + "\nScope Sensitivity: " + fmt(lastScope) + "\nAcceleration: " + fmt(lastAccel)
            try {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("SO2 sens", text))
                Toast.makeText(ctx, "Скопировано", Toast.LENGTH_SHORT).show()
            } catch (_: Throwable) {}
            onResult?.invoke(lastSens, lastScope, lastAccel)
            finish()
        }
        content.addView(copyExit, LinearLayout.LayoutParams(MATCH_PARENT, dp(52)).apply { bottomMargin = dp(8) })

        val again = pill("Заново", Color.parseColor("#333333"))
        again.setOnClickListener {
            hasSwipe = false
            measuredDeg = 0f
            swipeDx = 0f
            rebuildBottom()
        }
        content.addView(again, LinearLayout.LayoutParams(MATCH_PARENT, dp(46)))
    }

    private fun computeResult(): Triple<Float, Float, Float> {
        val baseSens = 2.0f
        val safeMeasured = measuredDeg.coerceAtLeast(1f)
        val rawSens = baseSens * targetDeg / safeMeasured
        val sens = rawSens.coerceIn(0.30f, 5.00f)
        val scope = (sens * 0.875f).coerceIn(0.30f, 5.00f)

        val speedPxPerMs = if (swipeMs > 0L) swipeDx / swipeMs.toFloat() else 1.0f
        val accel = when {
            speedPxPerMs >= 1.5f -> 0.00f
            speedPxPerMs >= 0.5f -> 0.03f
            else -> 0.05f
        }
        return Triple(r2(sens), r2(scope), r2(accel))
    }

    private fun r2(v: Float): Float = (v * 100f).roundToInt() / 100f

    private fun fmt(v: Float): String = String.format(java.util.Locale.US, "%.2f", v)

    private fun addRow(parent: LinearLayout, label: String, value: Float, color: Int) {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(14), dp(12), dp(14), dp(12))
        row.background = glass()
        val name = TextView(ctx)
        name.text = label
        name.setTextColor(Color.WHITE)
        name.textSize = 14f
        row.addView(name, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val num = TextView(ctx)
        num.text = fmt(value)
        num.setTextColor(color)
        num.textSize = 22f
        num.typeface = Typeface.DEFAULT_BOLD
        row.addView(num)
        parent.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })
    }

    private fun pill(text: String, color: Int): TextView {
        val t = TextView(ctx)
        t.text = text
        t.setTextColor(Color.WHITE)
        t.textSize = 14f
        t.typeface = Typeface.DEFAULT_BOLD
        t.gravity = Gravity.CENTER
        t.background = pillBg(color)
        return t
    }

    private fun glass(): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = dp(14).toFloat()
        g.setColor(Color.parseColor("#14FFFFFF"))
        g.setStroke(dp(1), Color.parseColor("#22FFFFFF"))
        return g
    }

    private fun pillBg(color: Int): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = dp(12).toFloat()
        g.setColor(color)
        return g
    }

    private fun finish() {
        try {
            (ctx as? android.app.Activity)?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } catch (_: Throwable) {}
        onBack?.invoke()
    }

    private inner class WorldView(context: Context) : View(context) {
        var yaw = 0f
        var pitch = 0f

        private val tanHalfFov = 0.7002f
        private val near = 0.2f
        private val roomR = 12f
        private val floorY = -2f
        private val ceilY = 4f

        private val pGrid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x3344AAFF
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        }
        private val pPillarBlue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF2F5BD3.toInt()
            strokeWidth = 6f
            style = Paint.Style.STROKE
        }
        private val pPillarBlueSoft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x552F5BD3
            strokeWidth = 14f
            style = Paint.Style.STROKE
        }
        private val pPillarRed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFF3B30.toInt()
            strokeWidth = 8f
            style = Paint.Style.STROKE
        }
        private val pPillarRedSoft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x55FF3B30
            strokeWidth = 20f
            style = Paint.Style.STROKE
        }
        private val pHorizon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99FFFFFF.toInt()
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        private val pCross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        private val pCrossDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.FILL
        }

        private var startX = 0f
        private var startY = 0f
        private var startYaw = 0f
        private var startTime = 0L
        private var tracking = false
        private var cx = 0f
        private var cy = 0f
        private var fpx = 1f
        private var cyw = 1f
        private var syw = 0f
        private var cp = 1f
        private var sp = 0f

        private val ca = FloatArray(3)
        private val cb = FloatArray(3)

        private fun sinD(a: Float): Float = Math.sin(Math.toRadians(a.toDouble())).toFloat()
        private fun cosD(a: Float): Float = Math.cos(Math.toRadians(a.toDouble())).toFloat()

        private fun toCam(x: Float, y: Float, z: Float, out: FloatArray) {
            val xc = x * cyw - z * syw
            val z0 = x * syw + z * cyw
            out[0] = xc
            out[1] = y * cp - z0 * sp
            out[2] = z0 * cp + y * sp
        }

        private fun line3(canvas: Canvas, x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, paint: Paint) {
            toCam(x1, y1, z1, ca)
            toCam(x2, y2, z2, cb)
            var ax = ca[0]; var ay = ca[1]; var az = ca[2]
            var bx = cb[0]; var by = cb[1]; var bz = cb[2]
            if (az < near && bz < near) return
            if (az < near) {
                val t = (near - az) / (bz - az)
                val nx = ax + (bx - ax) * t
                val ny = ay + (by - ay) * t
                ax = nx; ay = ny; az = near
            } else if (bz < near) {
                val t = (near - az) / (bz - az)
                val nx = ax + (bx - ax) * t
                val ny = ay + (by - ay) * t
                bx = nx; by = ny; bz = near
            }
            canvas.drawLine(
                cx + ax / az * fpx, cy - ay / az * fpx,
                cx + bx / bz * fpx, cy - by / bz * fpx,
                paint
            )
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            cx = w / 2f
            cy = h / 2f
            fpx = (h / 2f) / tanHalfFov
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (width <= 0 || height <= 0) return
            canvas.drawColor(0xFF030508.toInt())

            cyw = cosD(yaw)
            syw = sinD(yaw)
            cp = cosD(pitch)
            sp = sinD(pitch)

            var g = -24f
            while (g <= 24f) {
                line3(canvas, g, floorY, -24f, g, floorY, 24f, pGrid)
                line3(canvas, -24f, floorY, g, 24f, floorY, g, pGrid)
                line3(canvas, g, ceilY, -24f, g, ceilY, 24f, pGrid)
                line3(canvas, -24f, ceilY, g, 24f, ceilY, g, pGrid)
                g += 4f
            }

            var i = 0
            while (i < 48) {
                val a1 = i * 7.5f
                val a2 = (i + 1) * 7.5f
                line3(canvas, roomR * sinD(a1), 0f, roomR * cosD(a1),
                    roomR * sinD(a2), 0f, roomR * cosD(a2), pHorizon)
                i++
            }

            var k = 0
            while (k < 12) {
                val ang = k * 30f
                val px = roomR * sinD(ang)
                val pz = roomR * cosD(ang)
                val isRed = (k == 3 || k == 9)
                if (isRed) {
                    line3(canvas, px, floorY, pz, px, ceilY, pz, pPillarRedSoft)
                    line3(canvas, px, floorY, pz, px, ceilY, pz, pPillarRed)
                } else {
                    line3(canvas, px, floorY, pz, px, ceilY, pz, pPillarBlueSoft)
                    line3(canvas, px, floorY, pz, px, ceilY, pz, pPillarBlue)
                }
                k++
            }

            val ch = minOf(width, height) * 0.03f
            canvas.drawLine(cx - ch, cy, cx - ch * 0.3f, cy, pCross)
            canvas.drawLine(cx + ch * 0.3f, cy, cx + ch, cy, pCross)
            canvas.drawLine(cx, cy - ch, cx, cy - ch * 0.3f, pCross)
            canvas.drawLine(cx, cy + ch * 0.3f, cx, cy + ch, pCross)
            canvas.drawCircle(cx, cy, 2f, pCrossDot)
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    tracking = true
                    startX = ev.x
                    startY = ev.y
                    startYaw = yaw
                    startTime = System.currentTimeMillis()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!tracking) return true
                    val dx = ev.x - startX
                    yaw = startYaw + dx * 0.15f
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!tracking) return true
                    tracking = false
                    val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
                    val dx = ev.x - startX
                    val minLen = width * 0.35f
                    if (abs(dx) < minLen) {
                        Toast.makeText(ctx, "Слишком короткий свайп — проведи длиннее", Toast.LENGTH_SHORT).show()
                        return true
                    }
                    val turned = abs(dx) * 0.15f
                    swipeMs = elapsed
                    measuredDeg = turned
                    swipeDx = abs(dx)
                    hasSwipe = true
                    rebuildBottom()
                    return true
                }
            }
            return super.onTouchEvent(ev)
        }
    }
}
