package com.example.turboboost

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.view.animation.LinearInterpolator
import android.widget.TextView

/** Текст с бегущим радужным градиентом. */
class RainbowTextView(context: Context) : TextView(context) {
    private var shift = 0f
    private var animator: ValueAnimator? = null
    private val rainbow = intArrayOf(
        Color.parseColor("#FF3B30"), Color.parseColor("#FF9500"), Color.parseColor("#FFE600"),
        Color.parseColor("#34C759"), Color.parseColor("#00C7FF"), Color.parseColor("#5B6CFF"),
        Color.parseColor("#C04DFF"), Color.parseColor("#FF3B30")
    )

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator?.cancel()
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 2600L
        a.repeatCount = ValueAnimator.INFINITE
        a.interpolator = LinearInterpolator()
        a.addUpdateListener {
            shift = it.animatedValue as Float
            invalidate()
        }
        a.start()
        animator = a
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = Math.max(width.toFloat(), 1f)
        val x0 = shift * w
        paint.shader = LinearGradient(x0, 0f, x0 + w, 0f, rainbow, null, Shader.TileMode.REPEAT)
        super.onDraw(canvas)
    }
}
