package com.example.turboboost

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView

class OptimizerUI(private val ctx: Context) {

    lateinit var root: LinearLayout
    lateinit var btnToggle: TextView
    lateinit var cardCpu: TextView
    lateinit var cardGpu: TextView
    lateinit var cardScreen: TextView
    lateinit var cardBattery: TextView
    lateinit var cardTemp: TextView
    lateinit var cardChip: TextView
    lateinit var statusResult: TextView

    var onToggle: (() -> Unit)? = null

    fun build(): LinearLayout {
        root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#0B0F1A"))
        root.fitsSystemWindows = true
        root.setPadding(dp(18), dp(44), dp(18), dp(20))

        val title = TextView(ctx)
        title.text = "TurboBoost"
        title.setTextColor(Color.WHITE)
        title.textSize = 30f
        title.gravity = Gravity.CENTER
        root.addView(title, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val sub = TextView(ctx)
        sub.text = "Мониторинг частот"
        sub.setTextColor(Color.parseColor("#AAFFFFFF"))
        sub.textSize = 13f
        sub.gravity = Gravity.CENTER
        val subLp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        subLp.topMargin = dp(2)
        root.addView(sub, subLp)

        cardChip = card("Чип: —")
        root.addView(cardChip, cardLp())
        cardCpu = card("CPU: —")
        root.addView(cardCpu, cardLp())
        cardGpu = card("GPU: —")
        root.addView(cardGpu, cardLp())
        cardScreen = card("Экран: —")
        root.addView(cardScreen, cardLp())
        cardBattery = card("Батарея: —")
        root.addView(cardBattery, cardLp())
        cardTemp = card("Температура: —")
        root.addView(cardTemp, cardLp())
        statusResult = card("Нажмите кнопку")
        root.addView(statusResult, cardLp())

        val spacer = View(ctx)
        root.addView(spacer, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        btnToggle = TextView(ctx)
        btnToggle.text = "ВКЛЮЧИТЬ"
        btnToggle.setTextColor(Color.WHITE)
        btnToggle.textSize = 20f
        btnToggle.gravity = Gravity.CENTER
        btnToggle.background = buttonBlue()
        btnToggle.elevation = dp(12).toFloat()
        btnToggle.setOnClickListener { onToggle?.invoke() }
        root.addView(btnToggle, LinearLayout.LayoutParams(MATCH_PARENT, dp(72)))

        animateIn()
        return root
    }

    private fun animateIn() {
        val views = listOf(cardChip, cardCpu, cardGpu, cardScreen, cardBattery, cardTemp, statusResult, btnToggle)
        for ((i, v) in views.withIndex()) {
            v.alpha = 0f
            v.translationY = dp(30).toFloat()
            v.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(400)
                .setStartDelay(i * 50L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    fun setActive(active: Boolean) {
        if (active) {
            btnToggle.text = "ОТКЛЮЧИТЬ"
            btnToggle.background = buttonRed()
        } else {
            btnToggle.text = "ВКЛЮЧИТЬ"
            btnToggle.background = buttonBlue()
        }
    }

    fun setBatteryLowBlock(block: Boolean) {
        btnToggle.alpha = if (block) 0.4f else 1f
    }

    private fun card(text: String): TextView {
        val tv = TextView(ctx)
        tv.text = text
        tv.setTextColor(Color.WHITE)
        tv.textSize = 14f
        tv.gravity = Gravity.CENTER_VERTICAL
        tv.setPadding(dp(18), dp(14), dp(18), dp(14))
        tv.background = cardBg()
        tv.elevation = dp(6).toFloat()
        return tv
    }

    private fun cardLp(): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        lp.topMargin = dp(8)
        return lp
    }

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    private fun cardBg(): LayerDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = dp(22).toFloat()
        g.colors = intArrayOf(
            Color.parseColor("#66FFFFFF"),
            Color.parseColor("#33FFFFFF"),
            Color.parseColor("#22FFFFFF")
        )
        g.gradientType = GradientDrawable.LINEAR_GRADIENT
        g.orientation = GradientDrawable.Orientation.TL_BR
        g.setStroke(dp(1), Color.parseColor("#88FFFFFF"))

        val edge = object : GradientDrawable() {
            override fun draw(canvas: Canvas) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                val w = bounds.width().toFloat()
                val h = bounds.height().toFloat()
                val shader = LinearGradient(
                    0f, 0f, w, 0f,
                    intArrayOf(
                        Color.parseColor("#55FFFFFF"),
                        Color.parseColor("#00FFFFFF"),
                        Color.parseColor("#00FFFFFF"),
                        Color.parseColor("#55FFFFFF")
                    ),
                    floatArrayOf(0f, 0.15f, 0.85f, 1f),
                    Shader.TileMode.CLAMP
                )
                p.shader = shader
                canvas.drawRoundRect(0f, 0f, w, h, dp(22).toFloat(), dp(22).toFloat(), p)
            }
        }
        edge.shape = GradientDrawable.RECTANGLE
        return LayerDrawable(arrayOf(g, edge))
    }

    private fun buttonBlue(): LayerDrawable {
        return btnBase(
            intArrayOf(
                Color.parseColor("#5B8DEF"),
                Color.parseColor("#7BA7F5"),
                Color.parseColor("#5B8DEF")
            )
        )
    }

    private fun buttonRed(): LayerDrawable {
        return btnBase(
            intArrayOf(
                Color.parseColor("#EF4444"),
                Color.parseColor("#F87171"),
                Color.parseColor("#EF4444")
            )
        )
    }

    private fun btnBase(colors: IntArray): LayerDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = dp(24).toFloat()
        g.colors = colors
        g.gradientType = GradientDrawable.LINEAR_GRADIENT
        g.orientation = GradientDrawable.Orientation.LEFT_RIGHT
        g.setStroke(dp(1), Color.parseColor("#CCFFFFFF"))
        return LayerDrawable(arrayOf(g))
    }
}
