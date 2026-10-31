package com.example.turboboost

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.roundToInt

class SenseFinderScreen(private val ctx: Context) {

    var onBack: (() -> Unit)? = null
    var onResult: ((sens: Float, scope: Float, accel: Float) -> Unit)? = null

    private lateinit var root: FrameLayout
    private lateinit var overlay: SwipeOverlay

    private var stage = 0
    private var targetDegrees = 180f
    private var rectW = 800
    private var rectH = 260
    private var swipeMs = 0L
    private var hasSwipe = false
    private var swipePath = mutableListOf<PointF>()

    private val accent = 0xFFF2580A.toInt()
    private val accentSoft = 0x44F2580A
    private val bgDark = Color.parseColor("#05070A")

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    private fun marginPx(): Int {
        val dm = ctx.resources.displayMetrics
        val xdpi = if (dm.xdpi < 100f || dm.xdpi.isNaN()) dm.density * 160f else dm.xdpi
        val px = (xdpi * 1.5f / 2.54f).toInt()
        return px.coerceIn(dp(24), dm.widthPixels / 6)
    }

    private fun screenW(): Int = ctx.resources.displayMetrics.widthPixels
    private fun screenH(): Int = ctx.resources.displayMetrics.heightPixels

    private fun maxRectW(): Int = screenW() - 2 * marginPx() - dp(20)
    private fun maxRectH(): Int = screenH() - 2 * marginPx() - dp(260)

    fun build(): View {
        root = FrameLayout(ctx)
        root.setBackgroundColor(bgDark)
        rectW = maxRectW().coerceAtMost(1000)
        rectH = dp(260)
        showSetup()
        return root
    }

    private fun showSetup() {
        stage = 0
        root.removeAllViews()

        val content = LinearLayout(ctx)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(marginPx(), dp(90), marginPx(), dp(30))
        root.addView(content, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val title = TextView(ctx)
        title.text = "Настройка области свайпа"
        title.setTextColor(Color.WHITE)
        title.textSize = 20f
        title.typeface = Typeface.DEFAULT_BOLD
        content.addView(title)

        val sub = TextView(ctx)
        sub.text = "Выбери прямоугольник, в котором обычно двигаешь палец. Свайп будет считаться по его ширине."
        sub.setTextColor(Color.parseColor("#99FFFFFF"))
        sub.textSize = 12f
        sub.setPadding(0, dp(4), 0, dp(20))
        content.addView(sub)

        addSlider(content, "Ширина: $rectW px", 200, maxRectW(), rectW) { v ->
            rectW = v
            showSetup()
        }
        addSlider(content, "Высота: $rectH px", 80, maxRectH(), rectH) { v ->
            rectH = v
            showSetup()
        }

        val preview = LinearLayout(ctx)
        preview.orientation = LinearLayout.VERTICAL
        preview.gravity = Gravity.CENTER
        preview.setPadding(dp(8), dp(8), dp(8), dp(8))
        preview.background = glass()
        content.addView(preview, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) })

        val boxPreview = View(ctx)
        boxPreview.setBackground(rectBg())
        preview.addView(boxPreview, LinearLayout.LayoutParams(
            (rectW.toFloat() / maxRectW() * (screenW() - 2 * marginPx() - dp(32))).toInt().coerceAtLeast(dp(120)),
            (rectH.toFloat() / maxRectH() * dp(140)).toInt().coerceAtLeast(dp(40))
        ))

        val spacer = View(ctx)
        content.addView(spacer, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        val btn = btn("Дальше", accent)
        btn.setOnClickListener { showSwipe() }
        content.addView(btn, LinearLayout.LayoutParams(MATCH_PARENT, dp(52)).apply { topMargin = dp(12) })

        root.addView(topBar("Подбор сенсы"), FrameLayout.LayoutParams(MATCH_PARENT, dp(72)))
    }

    private fun showSwipe() {
        stage = 1
        hasSwipe = false
        swipePath.clear()
        root.removeAllViews()

        overlay = SwipeOverlay(ctx)
        overlay.rectW = rectW
        overlay.rectH = rectH
        overlay.path = swipePath
        overlay.hasSwipe = { hasSwipe }
        root.addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val bottom = LinearLayout(ctx)
        bottom.orientation = LinearLayout.VERTICAL
        bottom.setPadding(marginPx(), dp(8), marginPx(), dp(16))
        bottom.setBackgroundColor(Color.parseColor("#CC05070A"))
        val lp = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        lp.gravity = Gravity.BOTTOM
        root.addView(bottom, lp)

        val degRow = LinearLayout(ctx)
        degRow.orientation = LinearLayout.HORIZONTAL
        bottom.addView(degRow, LinearLayout.LayoutParams(MATCH_PARENT, dp(44)))

        listOf(45f, 90f, 180f).forEach { d ->
            val b = btn(d.toInt().toString() + "°", if (targetDegrees == d) accent else Color.parseColor("#333333"))
            b.setTextColor(Color.WHITE)
            b.setOnClickListener {
                targetDegrees = d
                showSwipe()
            }
            degRow.addView(b, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                marginStart = dp(2); marginEnd = dp(2)
            })
        }

        val hint = TextView(ctx)
        hint.text = if (hasSwipe) "Свайп записан (" + swipeMs + " мс). Нажми «Измерить»"
                   else "Проведи пальцем от левой красной линии до правой"
        hint.setTextColor(Color.parseColor("#CCFFFFFF"))
        hint.textSize = 12f
        hint.setPadding(dp(4), dp(10), dp(4), dp(8))
        bottom.addView(hint)

        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        bottom.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, dp(50)))

        val again = btn("Заново", Color.parseColor("#333333"))
        again.setOnClickListener { showSwipe() }
        row.addView(again, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginEnd = dp(6) })

        val measure = btn("Измерить", accent)
        measure.setOnClickListener {
            if (!hasSwipe) {
                Toast.makeText(ctx, "Сначала проведи свайп", Toast.LENGTH_SHORT).show()
            } else {
                showResult()
            }
        }
        row.addView(measure, LinearLayout.LayoutParams(0, dp(50), 1f))

        root.addView(topBar("Свайп по прямоугольнику"), FrameLayout.LayoutParams(MATCH_PARENT, dp(72)))
    }private fun showResult() {
    stage = 2
    root.removeAllViews()

    val s = computeResult()

    val content = LinearLayout(ctx)
    content.orientation = LinearLayout.VERTICAL
    content.setPadding(marginPx(), dp(90), marginPx(), dp(30))
    root.addView(content, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

    val title = TextView(ctx)
    title.text = "Результат"
    title.setTextColor(Color.WHITE)
    title.textSize = 22f
    title.typeface = Typeface.DEFAULT_BOLD
    content.addView(title)

    val sub = TextView(ctx)
    sub.text = "Введи эти значения в Standoff 2 → Настройки → Управление"
    sub.setTextColor(Color.parseColor("#99FFFFFF"))
    sub.textSize = 12f
    sub.setPadding(0, dp(4), 0, dp(20))
    content.addView(sub)

    addResultRow(content, "Чувствительность", s.first, accent)
    addResultRow(content, "Чувствительность прицела", s.second, accent)
    addResultRow(content, "Ускорение", s.third, Color.parseColor("#00C7FF"))

    val note = TextView(ctx)
    note.text = "Aim Smoothing в игре поставь в 0.00.\nЕсли сенса «плавает» — снизь Acceleration на 0.01."
    note.setTextColor(Color.parseColor("#88FFFFFF"))
    note.textSize = 11f
    note.setPadding(dp(4), dp(16), dp(4), 0)
    content.addView(note)

    val spacer = View(ctx)
    content.addView(spacer, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

    val copy = btn("Копировать значения", accent)
    copy.setOnClickListener {
        val text = "Standoff 2\nSensitivity: " + fmt(s.first) + "\nScope Sensitivity: " + fmt(s.second) + "\nAcceleration: " + fmt(s.third)
        try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("SO2 sens", text))
            Toast.makeText(ctx, "Скопировано", Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {}
        onResult?.invoke(s.first, s.second, s.third)
    }
    content.addView(copy, LinearLayout.LayoutParams(MATCH_PARENT, dp(52)).apply { bottomMargin = dp(8) })

    val again = btn("Пройти заново", Color.parseColor("#333333"))
    again.setOnClickListener {
        hasSwipe = false
        swipePath.clear()
        showSetup()
    }
    content.addView(again, LinearLayout.LayoutParams(MATCH_PARENT, dp(46)))

    root.addView(topBar("Готово"), FrameLayout.LayoutParams(MATCH_PARENT, dp(72)))
}

private fun computeResult(): Triple<Float, Float, Float> {
    val baselinePpd = 6.0f
    val baseSens = 2.0f
    val w = rectW.toFloat().coerceAtLeast(100f)
    val rawSens = baseSens * targetDegrees * baselinePpd / w
    val sens = rawSens.coerceIn(0.30f, 5.00f)
    val scope = (sens * 0.875f).coerceIn(0.30f, 5.00f)

    val speedPxPerMs = if (swipeMs > 0L) w / swipeMs.toFloat() else 1.0f
    val accel = when {
        speedPxPerMs >= 1.5f -> 0.00f
        speedPxPerMs >= 0.5f -> 0.03f
        else -> 0.05f
    }
    return Triple(r2(sens), r2(scope), r2(accel))
}

private fun r2(v: Float): Float = (v * 100f).roundToInt() / 100f

private fun fmt(v: Float): String = String.format(java.util.Locale.US, "%.2f", v)

private fun topBar(titleStr: String): FrameLayout {
    val bar = FrameLayout(ctx)
    bar.setBackgroundColor(Color.parseColor("#CC05070A"))
    val h = LinearLayout(ctx)
    h.orientation = LinearLayout.HORIZONTAL
    h.gravity = Gravity.CENTER_VERTICAL
    h.setPadding(dp(16), dp(12), dp(16), dp(12))
    bar.addView(h, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    val back = TextView(ctx)
    back.text = "← Назад"
    back.setTextColor(Color.WHITE)
    back.textSize = 14f
    back.setPadding(dp(4), dp(6), dp(12), dp(6))
    back.setOnClickListener {
        if (stage == 0) onBack?.invoke() else showSetup()
    }
    h.addView(back)
    val t = TextView(ctx)
    t.text = titleStr
    t.setTextColor(accent)
    t.textSize = 16f
    t.typeface = Typeface.DEFAULT_BOLD
    t.gravity = Gravity.CENTER
    h.addView(t, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
    return bar
}

private fun addSlider(parent: LinearLayout, label: String, min: Int, max: Int, cur: Int, onChanged: (Int) -> Unit) {
    val tv = TextView(ctx)
    tv.text = label
    tv.setTextColor(Color.WHITE)
    tv.textSize = 12f
    tv.setPadding(dp(4), dp(10), dp(4), dp(4))
    parent.addView(tv)
    val sb = SeekBar(ctx)
    sb.max = (max - min).coerceAtLeast(1)
    sb.progress = (cur - min).coerceIn(0, sb.max)
    sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {
            val p = seekBar?.progress ?: 0
            onChanged(min + p)
        }
    })
    parent.addView(sb, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
}

private fun addResultRow(parent: LinearLayout, label: String, value: Float, color: Int) {
    val row = LinearLayout(ctx)
    row.orientation = LinearLayout.HORIZONTAL
    row.gravity = Gravity.CENTER_VERTICAL
    row.setPadding(dp(14), dp(14), dp(14), dp(14))
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

private fun btn(text: String, color: Int): TextView {
    val t = TextView(ctx)
    t.text = text
    t.setTextColor(Color.WHITE)
    t.textSize = 14f
    t.typeface = Typeface.DEFAULT_BOLD
    t.gravity = Gravity.CENTER
    t.background = pill(color)
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

private fun rectBg(): GradientDrawable {
    val g = GradientDrawable()
    g.shape = GradientDrawable.RECTANGLE
    g.cornerRadius = dp(8).toFloat()
    g.setColor(Color.parseColor("#22F2580A"))
    g.setStroke(dp(2), accent)
    return g
}

private fun pill(color: Int): GradientDrawable {
    val g = GradientDrawable()
    g.shape = GradientDrawable.RECTANGLE
    g.cornerRadius = dp(12).toFloat()
    g.setColor(color)
    return g
}    private inner class SwipeOverlay(context: Context) : View(context) {
        var rectW = 0
        var rectH = 0
        var path: MutableList<PointF> = mutableListOf()
        var hasSwipe: () -> Boolean = { false }

        private val margin = marginPx()
        private val paintBox = Paint(Paint.ANTI_ALIAS_FLAG)
        private val paintLine = Paint(Paint.ANTI_ALIAS_FLAG)
        private val paintPath = Paint(Paint.ANTI_ALIAS_FLAG)
        private val paintText = Paint(Paint.ANTI_ALIAS_FLAG)

        private var startX = 0f
        private var endX = 0f
        private var startTime = 0L
        private var isTracking = false

        private var left = 0f
        private var top = 0f
        private var right = 0f
        private var bottom = 0f

        init {
            paintBox.style = Paint.Style.STROKE
            paintBox.strokeWidth = dp(2).toFloat()
            paintBox.color = accent
            paintLine.style = Paint.Style.STROKE
            paintLine.strokeWidth = dp(3).toFloat()
            paintLine.color = 0xFFFF3B30.toInt()
            paintPath.style = Paint.Style.STROKE
            paintPath.strokeWidth = dp(4).toFloat()
            paintPath.color = 0xFFFFEB3B.toInt()
            paintPath.strokeCap = Paint.Cap.ROUND
            paintPath.strokeJoin = Paint.Join.ROUND
            paintText.color = Color.WHITE
            paintText.textSize = dp(12).toFloat()
            paintText.typeface = Typeface.DEFAULT_BOLD
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            left = (w - rectW) / 2f
            right = left + rectW
            top = (h - rectH) / 2f
            bottom = top + rectH
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(left, top, right, bottom, paintBox)
            canvas.drawLine(left, top, left, bottom, paintLine)
            canvas.drawLine(right, top, right, bottom, paintLine)
            canvas.drawText("старт", left - dp(4), top - dp(8), paintText.apply { textAlign = Paint.Align.RIGHT })
            canvas.drawText("финиш", right + dp(4), top - dp(8), paintText.apply { textAlign = Paint.Align.LEFT })

            if (path.size > 1) {
                val p = Path()
                p.moveTo(path[0].x, path[0].y)
                for (i in 1 until path.size) p.lineTo(path[i].x, path[i].y)
                canvas.drawPath(p, paintPath)
            }
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            val x = ev.x
            val y = ev.y
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (x in left..right && y in top..bottom) {
                        isTracking = true
                        startX = x
                        endX = x
                        startTime = System.currentTimeMillis()
                        path.clear()
                        path.add(PointF(x, y))
                        invalidate()
                    }
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isTracking) return true
                    endX = x
                    path.add(PointF(x, y))
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!isTracking) return true
                    isTracking = false
                    endX = x
                    val elapsed = System.currentTimeMillis() - startTime
                    val dx = endX - startX
                    val minLen = rectW * 0.40f
                    val reachedRight = endX >= right - (right - left) * 0.15f
                    if (dx >= minLen && reachedRight) {
                        swipeMs = elapsed.coerceAtLeast(1)
                        swipePath = path
                        hasSwipe = true
                        toast("Свайп записан")
                        showSwipe()
                    } else {
                        toast("Проведи ещё раз: от левой линии до правой")
                        path.clear()
                        hasSwipe = false
                        invalidate()
                    }
                    return true
                }
            }
            return super.onTouchEvent(ev)
        }

        private fun toast(s: String) {
            Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()
        }
    }
}
