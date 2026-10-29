package com.example.turboboost

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class SenseCustomScreen(
    private val ctx: Context,
    private val prefs: AppPrefs
) {
    var onBack: (() -> Unit)? = null
    var onApply: (() -> Unit)? = null
    var onReset: (() -> Unit)? = null

    private lateinit var root: FrameLayout
    private lateinit var bg: BgView
    private val t: ThemePalette.Theme get() = ThemePalette.get(prefs.getThemeIndex())
    private val isGrid: Boolean get() = t.isGrid

    private fun accentDrawable(): Drawable {
        if (isGrid) return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14).toFloat()
            colors = intArrayOf(0xFF5A5A5A.toInt(), 0xFF454545.toInt())
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            setStroke(dp(2), t.primary)
        }
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = dp(14).toFloat()
            colors = intArrayOf(t.primary, t.secondary)
            orientation = GradientDrawable.Orientation.LEFT_RIGHT
            setStroke(dp(1), 0x33FFFFFF)
        }
    }

    private fun unselDrawable(): Drawable {
        if (isGrid) return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14).toFloat()
            colors = intArrayOf(0xFF3A3A3A.toInt(), 0xFF2A2A2A.toInt())
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            setStroke(dp(2), 0x99FF8A00.toInt())
        }
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = dp(14).toFloat()
            colors = intArrayOf(0x1AFFFFFF, 0x10FFFFFF)
            orientation = GradientDrawable.Orientation.TL_BR
            setStroke(dp(1), 0x22FFFFFF)
        }
    }

    private fun sectionBg(): Drawable {
        if (isGrid) return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(20).toFloat()
            colors = intArrayOf(0xFF2A2A2A.toInt(), 0xFF1F1F1F.toInt())
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            setStroke(dp(1), 0x66FF8A00.toInt())
        }
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = dp(20).toFloat()
            colors = intArrayOf(0x1CFFFFFF, 0x0DFFFFFF)
            orientation = GradientDrawable.Orientation.TL_BR
            setStroke(dp(1), 0x1FFFFFFF)
        }
    }

    fun build(): FrameLayout {
        root = FrameLayout(ctx)
        root.setBackgroundColor(if (isGrid) Color.parseColor("#1A1A1A") else Color.parseColor("#05070A"))
        bg = BgView(ctx)
        bg.setTheme(prefs.getThemeIndex())
        root.addView(bg, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(80), dp(16), dp(24))
        }
        val scroll = ScrollView(ctx).apply {
            isFillViewport = true
            setBackgroundColor(Color.TRANSPARENT)
            addView(content, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            setPadding(0, dp(72), 0, 0)
        }
        root.addView(scroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val sensSection = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
            background = sectionBg()
        }
        sensSection.addView(sectionHeader("ЧУВСТВИТЕЛЬНОСТЬ"))
        sensSection.addView(stepperRow("Pointer Speed", { prefs.getPointerSpeed().toString() }, { d -> prefs.setPointerSpeed(prefs.getPointerSpeed() + d) }),
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
        sensSection.addView(rateBlock(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) })
        content.addView(sensSection, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val smoothSection = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
            background = sectionBg()
        }
        smoothSection.addView(sectionHeader("СГЛАЖИВАНИЕ"))
        val row1 = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        row1.addView(compactToggle(
            "Без предикта",
            "Отключает предсказание движения",
            prefs.isTouchPredictionOff()
        ) { prefs.setTouchPredictionOff(it) },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(10) })
        row1.addView(compactToggle(
            "Мгновенный тач",
            "Убирает минимальную задержку",
            prefs.isTouchBlockingZero()
        ) { prefs.setTouchBlockingZero(it) },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        smoothSection.addView(row1, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })

        val row2 = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        row2.addView(compactToggle(
            "Без агрегации",
            "Меньше группировки событий",
            prefs.isMotionAggregationOff()
        ) { prefs.setMotionAggregationOff(it) },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(10) })
        row2.addView(View(ctx), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        smoothSection.addView(row2, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) })
        content.addView(smoothSection, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) })

        val apply = TextView(ctx).apply {
            text = "ПРИМЕНИТЬ"
            setTextColor(Color.WHITE); textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = accentDrawable()
            setPadding(0, dp(16), 0, dp(16))
            setOnClickListener {
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                onApply?.invoke()
            }
        }
        content.addView(apply, LinearLayout.LayoutParams(MATCH_PARENT, dp(54)).apply { topMargin = dp(20) })

        val reset = TextView(ctx).apply {
            text = "СБРОСИТЬ"
            setTextColor(Color.WHITE); textSize = 14f
            gravity = Gravity.CENTER
            background = unselDrawable()
            setPadding(0, dp(14), 0, dp(14))
            setOnClickListener {
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                onReset?.invoke()
            }
        }
        content.addView(reset, LinearLayout.LayoutParams(MATCH_PARENT, dp(48)).apply { topMargin = dp(10) })

        content.addView(hint(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(16) })

        root.addView(buildTopBar(), FrameLayout.LayoutParams(MATCH_PARENT, dp(72)))
        return root
    }

    private fun sectionHeader(text: String): TextView = TextView(ctx).apply {
        this.text = text
        setTextColor(Color.parseColor("#99FFFFFF"))
        textSize = 11f
        letterSpacing = 0.18f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun buildTopBar(): FrameLayout {
        val bar = FrameLayout(ctx)
        bar.setBackgroundColor(if (isGrid) Color.parseColor("#CC1A1A1A") else Color.parseColor("#CC05070A"))
        val h = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        bar.addView(h, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        val back = ImageView(ctx).apply {
            setImageResource(android.R.drawable.ic_menu_revert)
            setColorFilter(Color.WHITE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { onBack?.invoke() }
        }
        h.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) })
        val title = TextView(ctx).apply {
            text = "Кастомная сенса"
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER_VERTICAL
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        h.addView(title, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        return bar
    }

    private fun stepperRow(title: String, value: () -> String, onDelta: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = unselDrawable()
        }
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(ctx).apply {
            text = title; setTextColor(Color.WHITE); textSize = 14f
        })
        val v = TextView(ctx).apply {
            text = value(); setTextColor(t.primary); textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(2), 0, 0)
        }
        col.addView(v)
        row.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row.addView(btnCircle("−") { onDelta(-1); v.text = value() })
        row.addView(btnCircle("+") { onDelta(1); v.text = value() }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = dp(8) })
        return row
    }

    private fun rateBlock(): LinearLayout {
        val wrapper = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
            background = unselDrawable()
        }
        wrapper.addView(TextView(ctx).apply {
            text = "Частота опроса сенсора (Гц)"
            setTextColor(Color.WHITE)
            textSize = 14f
        })
        val rates = listOf(0, 60, 120, 180, 240, 500, 1000)
        val row1 = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        val buttons = mutableListOf<TextView>()
        rates.forEach { r ->
            val b = TextView(ctx).apply {
                text = if (r == 0) "Авто" else r.toString()
                setTextColor(Color.WHITE)
                textSize = 12f
                gravity = Gravity.CENTER
                background = if (prefs.getSenseTouchRate() == r) accentDrawable() else unselDrawable()
                setPadding(dp(4), dp(10), dp(4), dp(10))
                setOnClickListener {
                    prefs.setSenseTouchRate(r)
                    buttons.forEachIndexed { i, v ->
                        v.background = if (rates[i] == r) accentDrawable() else unselDrawable()
                    }
                }
            }
            buttons.add(b)
            row1.addView(b, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
        }
        wrapper.addView(row1, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        wrapper.addView(TextView(ctx).apply {
            text = "1000 Гц поддерживают не все экраны — если сенса пропадёт, верни 240 или 120"
            setTextColor(Color.parseColor("#88FFFFFF"))
            textSize = 10f
            setPadding(0, dp(8), 0, 0)
        })
        return wrapper
    }

    private fun compactToggle(title: String, subtitle: String, initial: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val cell = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = unselDrawable()
        }
        val titleTv = TextView(ctx).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val subTv = TextView(ctx).apply {
            text = subtitle
            setTextColor(Color.parseColor("#88FFFFFF"))
            textSize = 10f
            setPadding(0, dp(2), 0, dp(10))
            setLineSpacing(dp(1).toFloat(), 1f)
        }
        val sw = ToggleSwitch(ctx).apply {
            onColor = t.primary
            doubleBorder = false
            setOn(initial, false)
        }
        sw.onToggle = { v -> onChange(v) }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        row.addView(sw, LinearLayout.LayoutParams(dp(52), dp(32)))
        cell.addView(titleTv)
        cell.addView(subTv)
        cell.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        return cell
    }

    private fun btnCircle(text: String, click: () -> Unit): TextView = TextView(ctx).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 20f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        background = unselDrawable()
        setPadding(dp(14), dp(8), dp(14), dp(8))
        setOnClickListener { performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY); click() }
    }

    private fun hint(): TextView = TextView(ctx).apply {
        text = "«Без предикта» отключает предсказание движения — палец идёт 1:1 без сглаживания. Работает на Android 13+.\n\n" +
            "«Мгновенный тач» убирает минимальную задержку между касаниями — резкие тапы срабатывают быстрее.\n\n" +
            "«Без агрегации» уменьшает группировку событий сенсора — на части прошивок снижает задержку.\n\n" +
            "Настройки применяются при включении турбо (кнопка «ОПТИМИЗИРОВАТЬ»)."
        setTextColor(Color.parseColor("#88FFFFFF"))
        textSize = 11f
        setLineSpacing(dp(2).toFloat(), 1f)
        setPadding(dp(6), dp(8), dp(6), dp(8))
    }

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    class BgView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private var tIdx = 0
        fun setTheme(i: Int) { tIdx = i; invalidate() }
        override fun onDraw(canvas: Canvas) {
            val tt = ThemePalette.get(tIdx)
            if (tt.isGrid) {
                val sh = LinearGradient(0f, 0f, 0f, height.toFloat(),
                    intArrayOf(0xFF2A2A2A.toInt(), 0xFF1F1F1F.toInt(), 0xFF1A1A1A.toInt()),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                paint.shader = sh
                canvas.drawPaint(paint)
                val d = resources.displayMetrics.density
                val step = 56f * d
                gridPaint.color = tt.primary
                gridPaint.alpha = 55
                gridPaint.strokeWidth = 1.2f * d
                var x = 0f
                while (x < width) { canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint); x += step }
                var y = 0f
                while (y < height) { canvas.drawLine(0f, y, width.toFloat(), y, gridPaint); y += step }
                gridPaint.strokeWidth = 2f * d
                gridPaint.alpha = 80
                val step2 = step * 4f
                x = 0f
                while (x < width) { canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint); x += step2 }
                y = 0f
                while (y < height) { canvas.drawLine(0f, y, width.toFloat(), y, gridPaint); y += step2 }
            } else {
                canvas.drawColor(Color.parseColor("#05070A"))
            }
        }
    }
}
